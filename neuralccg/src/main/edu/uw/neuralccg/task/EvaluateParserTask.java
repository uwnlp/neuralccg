package edu.uw.neuralccg.task;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;
import com.typesafe.config.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.parser.ParserBuilder;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.AnalysisProto.EvaluationEvent;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.TrainProto.RunConfig;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.evaluation.analysis.EvaluationStatistics;
import edu.uw.neuralccg.evaluation.analysis.ParserStatistics;
import edu.uw.neuralccg.model.OracleModel.GoldInputToParser;
import edu.uw.neuralccg.model.TreeFactoredModel.TreeFactoredModelFactory;
import edu.uw.neuralccg.util.EasySRLUtil;
import edu.uw.neuralccg.util.ProgressLogger;
import edu.uw.neuralccg.util.SerializationUtil;

public class EvaluateParserTask implements ITask<EvaluationEvent> {
    public static final Logger log = LoggerFactory.getLogger(EvaluateParserTask.class);


    public static EvaluationEvent evaluateCheckpoint(final File checkpointPath,
                                                     final List<GoldInputToParser> inputs,
                                                     final Config arguments,
                                                     final ParserBuilder<?> backoffParserBuilder,
                                                     final DependencyEvaluator evaluator,
                                                     final List<Category> categories,
                                                     final Optional<ProgressLogger> progressLogger) {
        return evaluateCheckpoint(EasySRLUtil.parserBuilder(arguments), checkpointPath, inputs, arguments, backoffParserBuilder, evaluator, categories, progressLogger, true);
    }

    public static EvaluationEvent evaluateCheckpoint(final ParserBuilder<?> parserBuilder,
                                                     final File checkpointPath,
                                                     final List<GoldInputToParser> inputs,
                                                     final Config arguments,
                                                     final ParserBuilder<?>  backoffParserBuilder,
                                                     final DependencyEvaluator evaluator,
                                                     final List<Category> categories,
                                                     final Optional<ProgressLogger> progressLogger,
                                                     final boolean useLazyAgenda) {
        Preconditions.checkArgument(checkpointPath.exists());
        Preconditions.checkArgument(checkpointPath.getName().endsWith(".pb"));
        final long steps = Long.parseLong(checkpointPath.getName().substring(0, checkpointPath.getName().length() - ".pb".length()));

        final ParserStatistics parserStats = new ParserStatistics();
        synchronized (TreeFactoredModelFactory.class) {
            final TreeFactoredModelFactory modelFactory = new TreeFactoredModelFactory(
                    Optional.empty(),
                    categories,
                    arguments,
                    false,
                    useLazyAgenda,
                    Optional.empty(),
                    checkpointPath,
                    Optional.empty(),
                    Optional.of(parserStats));

            final Parser backoffParser = backoffParserBuilder
                    .listeners(Lists.newArrayList(Iterables.concat(backoffParserBuilder.getListeners(), ImmutableList.of(parserStats))))
                    .build();

            final Parser parser = parserBuilder
                    .modelFactory(modelFactory)
                    .listeners(Lists.newArrayList(Iterables.concat(parserBuilder.getListeners(), ImmutableList.of(parserStats, modelFactory))))
                    .build();

            final EvaluationStatistics backoffStats = new EvaluationStatistics();
            final EvaluationStatistics stats = new EvaluationStatistics();
            for (final GoldInputToParser input : inputs) {
                backoffStats.getParseTime().start();
                stats.getParseTime().start();
                final List<Scored<SyntaxTreeNode>> result = parser.doParsing(input);
                stats.getParseTime().stop();
                stats.updateStats(input.getGoldDependencies(), input.getGoldCategories(), result, evaluator);

                if (result != null) {
                    backoffStats.updateStats(input.getGoldDependencies(), input.getGoldCategories(), result, evaluator);
                } else {
                    backoffStats.updateStats(input.getGoldDependencies(), input.getGoldCategories(),  backoffParser.doParsing(input), evaluator);
                    parserStats.discountBackoff();
                }
                backoffStats.getParseTime().stop();
                progressLogger.ifPresent(ProgressLogger::maybeLog);
            }

            log.info("=====Evaluation=====");
            stats.log();
            log.info("===================");

            log.info("=====Evaluation with backoff=====");
            backoffStats.log();
            log.info("===================");

            log.info("=====Parse stats=====");
            parserStats.log();
            log.info("===================");
            final EvaluationEvent evaluationEvent = EvaluationEvent.newBuilder()
                    .setName(checkpointPath.getParentFile().getName())
                    .setCheckpointPath(checkpointPath.getAbsolutePath())
                    .setTimestamp(checkpointPath.lastModified())
                    .setSteps(steps)
                    .setEval(stats.toProto())
                    .setEvalBackoff(backoffStats.toProto())
                    .setParseStats(parserStats.toProto())
                    .build();
            return evaluationEvent;
        }
    }

    @Override
    public String getKey() {
        return "evaluate-parser";
    }

    @Override
    public Stream<EvaluationEvent> run(Stage stage) {
        final File modelDir = new File(stage.getArguments().getString("model_dir"));

        final DependencyEvaluator evaluator = stage.read("evaluator", Serialized.class)
                .findAny()
                .map(SerializationUtil::<DependencyEvaluator>deserialize)
                .get();

        final File checkpointPath = new File(stage.getArguments().getString("checkpoint_path"));

        final List<Category> categories;
        try {
            categories = TaggerEmbeddings.loadCategories(new File(modelDir, "categories"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        TreeFactoredModelFactory.initializeCNN(RunConfig.newBuilder()
                .setMemory(stage.getArguments().getInt("native_memory")).build());

        final ParserBuilder<?> backoffParserBuilder = EasySRLUtil.backoffParserBuilder(stage.getArguments());

        log.info("Reading dev data...");
        final List<GoldInputToParser> devInputs =
                TrainParserTask.getTaggedInputs(stage, "dev-inputs", "dev-tags", categories)
                        .filter(input -> input.getInputWords().size() <= backoffParserBuilder.getMaxSentenceLength())
                        .collect(Collectors.toList());

        log.info("Found {} dev sentences.", devInputs.size());

        final ProgressLogger progressLogger = new ProgressLogger(
                100,
                devInputs.size(),
                "Analyzed sentence",
                stage::setProgress);

        log.info("Evaluating checkpoint...");
        final EvaluationEvent evaluationEvent = evaluateCheckpoint(
                checkpointPath,
                devInputs,
                stage.getArguments(),
                backoffParserBuilder,
                evaluator,
                categories,
                Optional.of(progressLogger));
        return Stream.of(evaluationEvent);
    }
}
