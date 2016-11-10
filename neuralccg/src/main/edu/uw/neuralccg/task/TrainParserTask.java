package edu.uw.neuralccg.task;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;
import com.github.kentonl.pipegraph.util.CollectionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.TaggerflowProtos.TaggedSentence;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.syntax.tagger.TaggerflowLSTM;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.AnalysisProto.EvaluationEvent;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.SyntaxProto.CategoryProto;
import edu.uw.neuralccg.TrainProto.RunConfig;
import edu.uw.neuralccg.TrainProto.ScorerConfig;
import edu.uw.neuralccg.TrainProto.TrainConfig;
import edu.uw.neuralccg.TrainProto.WordEmbedding;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.evaluation.analysis.EvaluationStatistics;
import edu.uw.neuralccg.evaluation.analysis.ParserStatistics;
import edu.uw.neuralccg.model.OracleModel.GoldInputToParser;
import edu.uw.neuralccg.model.TreeFactoredModel.TreeFactoredModelFactory;
import edu.uw.neuralccg.trainer.Trainer;
import edu.uw.neuralccg.util.EasySRLUtil;
import edu.uw.neuralccg.util.ProgressLogger;
import edu.uw.neuralccg.util.SerializationUtil;
import edu.uw.neuralccg.util.SyntaxUtil;

public class TrainParserTask implements ITask<EvaluationEvent> {
    public static final Logger log = LoggerFactory.getLogger(TrainParserTask.class);

    @Override
    public String getKey() {
        return "train-parser";
    }

    public static Stream<GoldInputToParser> getTaggedInputs(final Stage stage,
                                                            final String inputsName,
                                                            final String tagsName,
                                                            final List<Category> categories) {
        return CollectionUtil.zip(
                stage.read(inputsName, Serialized.class)
                        .map(SerializationUtil::<GoldInputToParser>deserialize),
                stage.read(tagsName, TaggedSentence.class),
                (input, taggedSentence) ->
                        new GoldInputToParser(input, TaggerflowLSTM.getScoredCategories(taggedSentence, categories)));
    }

    @Override
    public Stream<EvaluationEvent> run(Stage stage) {
        final File modelDir = new File(
                stage.getArguments().getString("model_dir"));
        final int numEpochs = stage.getArguments().getInt("epochs");

        final DependencyEvaluator evaluator = stage.read("evaluator", Serialized.class)
                .findAny()
                .map(SerializationUtil::<DependencyEvaluator>deserialize)
                .get();

        final List<Category> categories;
        try {
            categories = TaggerEmbeddings.loadCategories(new File(modelDir, "categories"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final ParserStatistics parseStats = new ParserStatistics();
        final Trainer trainer = stage
                .getContext()
                .getRegistry()
                .create(Trainer.class, stage.getArguments().getString("trainer"));
        trainer.setStats(parseStats);
        trainer.handleArguments(stage.getArguments());

        final String checkpointName = stage.getArguments().hasPath("name") ?
                stage.getArguments().getString("name") :
                stage.getName();
        final File checkpointDir = new File(new File("checkpoints", stage.getContext().getExperimentName()), checkpointName);
        checkpointDir.mkdirs();

        final TrainConfig.Builder trainConfig = TrainConfig.newBuilder()
                .setUpdateMethod(stage.getArguments().getString("update_method"));

        synchronized (TreeFactoredModelFactory.class) {
            final Iterable<CategoryProto> possibleCategories;

            if (stage.getArguments().getBoolean("use_compositional_categories")) {
                possibleCategories = () -> SyntaxUtil.getPossibleCategories(modelDir)
                        .stream()
                        .flatMap(SyntaxUtil::atomicStream)
                        .distinct()
                        .sorted(Comparator.comparing(Category::toString))
                        .map(SyntaxUtil::toProto)
                        .map(CategoryProto.Builder::build)
                        .iterator();
            } else {
                possibleCategories = () -> SyntaxUtil.getPossibleCategories(modelDir)
                        .stream()
                        .sorted(Comparator.comparing(Category::toString))
                        .map(SyntaxUtil::toProto)
                        .map(CategoryProto.Builder::build)
                        .iterator();
            }

            final ScorerConfig.Builder scorerConfig = ScorerConfig.newBuilder()
                    .setCategoryDimensions(stage.getArguments().getInt("category_dimensions"))
                    .setCellDimensions(stage.getArguments().getInt("cell_dimensions"))
                    .setNumLayers(stage.getArguments().getInt("num_layers"))
                    .setWordDimensions(stage.getArguments().getInt("word_dimensions"))
                    .setScoreSupertags(stage.getArguments().getBoolean("score_supertags"))
                    .setUseNonterminalCategories(stage.getArguments().getBoolean("use_nonterminal_categories"))
                    .setCoupleGates(stage.getArguments().getBoolean("couple_gates"))
                    .setUseOutputGate(stage.getArguments().getBoolean("use_output_gate"))
                    .setUseRecursion(stage.getArguments().getBoolean("use_recursion"))
                    .setUseCharLstm(stage.getArguments().getBoolean("use_char_lstm"))
                    .setDropoutProbability(stage.getArguments().getDouble("dropout_probability"));

            log.info("Initializing from scratch:\n{}", scorerConfig.toString());

            scorerConfig.addAllWord(() -> stage
                    .read("embeddings", WordEmbedding.class)
                    .map(WordEmbedding::getWord).iterator());
            scorerConfig.addAllCategory(possibleCategories);

            log.info("Using {} words.", scorerConfig.getWordCount());
            log.info("Using {} categories.", scorerConfig.getCategoryCount());

            trainConfig.addAllInitialEmbedding(() -> stage
                    .read("embeddings", WordEmbedding.class).iterator());

            TreeFactoredModelFactory.initializeCNN(RunConfig.newBuilder()
                    .setMemory(stage.getArguments().getInt("native_memory"))
                    .setSeed(stage.getArguments().getInt("seed"))
                    .build());

            final TreeFactoredModelFactory modelFactory = new TreeFactoredModelFactory(
                    Optional.empty(),
                    categories,
                    stage.getArguments(),
                    false,
                    true,
                    Optional.of(evaluator),
                    scorerConfig.build(),
                    Optional.of(trainConfig.build()),
                    Optional.of(parseStats));

            final Parser parser = EasySRLUtil.parserBuilder(new ParserAStar.Builder(modelDir), stage.getArguments())
                    .modelFactory(modelFactory)
                    .listeners(ImmutableList.of(trainer, parseStats, modelFactory))
                    .build();

            log.info("Reading training data...");
            final List<GoldInputToParser> trainInputs =
                    getTaggedInputs(stage, "train-inputs", "train-tags", categories)
                            .filter(input -> input.getOracleParse() != null)
                            .limit(stage.getArguments().hasPath("train_limit") ?
                                    stage.getArguments().getLong("train_limit") : Long.MAX_VALUE)
                            .collect(Collectors.toList());

            final Random random = new Random(stage.getArguments().getInt("seed"));

            log.info("Found {} train sentences.", trainInputs.size());

            log.info("Training...");
            final int checkpointFrequency = stage.getArguments().getInt("checkpoint_frequency");
            final boolean saveCheckpoints = stage.getArguments().getBoolean("save_checkpoints");
            int stepCount = 0;

            final ProgressLogger progressLogger = new ProgressLogger(
                    10000,
                    trainInputs.size() * numEpochs,
                    "Trained sentence",
                    stage::setProgress);

            for (int i = 0; i < numEpochs; i++) {
                parseStats.clear();
                final EvaluationStatistics trainStats = new EvaluationStatistics();
                final Stopwatch epochTime = Stopwatch.createStarted();
                Collections.shuffle(trainInputs, random);
                for (final GoldInputToParser trainInput : trainInputs) {
                    trainStats.getParseTime().start();
                    modelFactory.setTrainer(Optional.of(trainer));
                    final List<Scored<SyntaxTreeNode>> result = parser.doParsing(trainInput);
                    modelFactory.setTrainer(Optional.empty());
                    trainStats.getParseTime().stop();
                    trainStats.updateStats(
                            trainInput.getGoldDependencies(),
                            trainInput.getGoldCategories(),
                            result,
                            evaluator);
                    if (saveCheckpoints && stepCount > 0 && stepCount % checkpointFrequency == 0) {
                        final File checkpointFile = new File(checkpointDir, stepCount + ".pb");
                        log.info("Saving checkpoint to {}", checkpointFile.getAbsolutePath());
                        modelFactory.saveCheckpoint(checkpointFile);
                    }
                    stepCount++;
                    progressLogger.maybeLog();
                }
                log.info("Finished training epoch {} in {} seconds.", i, epochTime.elapsed(TimeUnit.SECONDS));
                log.info("====Parser stats=====");
                parseStats.log();
                log.info("=====================");
                log.info("=====Train stats=====");
                trainStats.log();
                log.info("=====================");
            }
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Stream.empty();
        }
    }
}
