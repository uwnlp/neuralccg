package edu.uw.neuralccg.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.AnalysisProto.EvaluatedParse;
import edu.uw.neuralccg.AnalysisProto.ParseComparison;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.TrainProto.RunConfig;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.model.OracleModel.GoldInputToParser;
import edu.uw.neuralccg.model.TreeFactoredModel.TreeFactoredModelFactory;
import edu.uw.neuralccg.util.EasySRLUtil;
import edu.uw.neuralccg.util.ProgressLogger;
import edu.uw.neuralccg.util.RetrievalStatistics;
import edu.uw.neuralccg.util.SerializationUtil;
import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;

public class DiffParsersTask implements ITask<ParseComparison> {
    public static final Logger log = LoggerFactory.getLogger(DiffParsersTask.class);

    @Override
    public String getKey() {
        return "diff-parsers";
    }

    @Override
    public Stream<ParseComparison> run(Stage stage) {
        final File modelDir = new File(stage.getArguments().getString("model_dir"));

        final DependencyEvaluator evaluator = stage.read("evaluator", Serialized.class)
                .findAny()
                .map(SerializationUtil::<DependencyEvaluator>deserialize)
                .get();

        final File checkpointPath = new File(stage.getArguments().getString("checkpoint_dir"), "checkpoint.pb");

        final Collection<Category> categories;
        try {
            categories = TaggerEmbeddings.loadCategories(new File(modelDir, "categories"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final ProgressLogger progressLogger = new ProgressLogger(
                100,
                (int) stage.read("dev", Serialized.class).count(),
                "Analyzed sentence",
                stage::setProgress);


        TreeFactoredModelFactory.initializeCNN(RunConfig.newBuilder()
                .setMemory(stage.getArguments().getInt("native_memory")).build());

        synchronized (TreeFactoredModelFactory.class) {
            final TreeFactoredModelFactory modelFactory = new TreeFactoredModelFactory(
                    Optional.empty(),
                    categories,
                    stage.getArguments(),
                    false,
                    true,
                    Optional.empty(),
                    checkpointPath,
                    Optional.empty(),
                    Optional.empty());


            final Parser parser = EasySRLUtil.parserBuilder(stage.getArguments())
                    .modelFactory(modelFactory)
                    .listeners(Collections.singletonList(modelFactory))
                    .build();

            final Parser backoffParser = EasySRLUtil.backoffParserBuilder(stage.getArguments()).build();

            log.info("Reading dev data...");
            final List<GoldInputToParser> devInputs = stage.read("dev", Serialized.class)
                    .map(SerializationUtil::<GoldInputToParser>deserialize)
                    .filter(gold -> gold.getInputWords().size() <= parser.getMaxSentenceLength())
                    .collect(Collectors.toList());

            log.info("Found {} dev sentences.", devInputs.size());

            final List<ParseComparison> comparisons = new ArrayList<>();

            log.info("Analyzing...");
            for (final GoldInputToParser input : devInputs) {
                final List<Scored<SyntaxTreeNode>> result = parser.doParsing(input);
                final List<Scored<SyntaxTreeNode>> backoffResult = backoffParser.doParsing(input);
                if (result != null && backoffResult != null) {
                    final SyntaxTreeNode parse = result.get(0).getObject();
                    final SyntaxTreeNode backoffParse = backoffResult.get(0).getObject();
                    comparisons.add(ParseComparison.newBuilder()
                            .setFirst(EvaluatedParse.newBuilder()
                                    .setParse(SerializationUtil.serialize(parse))
                                    .setF1(evaluator.evaluate(input.getGoldDependencies(), parse).getF1()))
                            .setSecond(EvaluatedParse.newBuilder()
                                    .setParse(SerializationUtil.serialize(backoffParse))
                                    .setF1(evaluator.evaluate(input.getGoldDependencies(), backoffParse).getF1()))
                            .setDifference(getSimilarity(backoffParse, parse, evaluator))
                            .setGold(EvaluatedParse.newBuilder()
                                    .setParse(SerializationUtil.serialize(input.getOracleParse()))
                                    .setF1(evaluator.evaluate(input.getGoldDependencies(), input.getOracleParse()).getF1()))
                            .build());
                }
                progressLogger.maybeLog();
            }
            return comparisons.stream().sorted((x, y) -> Double.compare(x.getDifference(), y.getDifference()));
        }
    }

    public static double getSimilarity(final SyntaxTreeNode first,
                                       final SyntaxTreeNode second,
                                       final DependencyEvaluator evaluator) {
        final RetrievalStatistics stats = new RetrievalStatistics().update(evaluator.predictedDependencyStream(first),
                evaluator.predictedDependencyStream(second));
        if (stats.getGold() == 0 && stats.getPredicted() == 0) {
            return 1.0;
        } else {
            return stats.getF1();
        }
    }
}
