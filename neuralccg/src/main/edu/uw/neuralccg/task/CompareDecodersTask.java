package edu.uw.neuralccg.task;

import com.google.common.collect.ImmutableList;

import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.Model.ModelFactory;
import edu.uw.easysrl.syntax.model.SupertagFactoredModel.SupertagFactoredModelFactory;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.syntax.parser.ChartCell.ChartCellFactory;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.parser.ParserBeamSearch;
import edu.uw.easysrl.syntax.parser.ParserBuilder;
import edu.uw.easysrl.syntax.parser.ParserListener;
import edu.uw.easysrl.syntax.parser.ParserReranking;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.AnalysisProto.EvaluationEvent;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.TrainProto.RunConfig;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.evaluation.analysis.ParserStatistics;
import edu.uw.neuralccg.model.OracleModel.GoldInputToParser;
import edu.uw.neuralccg.model.TreeFactoredModel.TreeFactoredModelFactory;
import edu.uw.neuralccg.util.CellUtil;
import edu.uw.neuralccg.util.EasySRLUtil;
import edu.uw.neuralccg.util.ProgressLogger;
import edu.uw.neuralccg.util.SerializationUtil;

public class CompareDecodersTask implements ITask<EvaluationEvent> {
    public static final Logger log = LoggerFactory.getLogger(CompareDecodersTask.class);

    File modelDir;
    DependencyEvaluator evaluator;
    File checkpointPath;
    List<Category> categories;
    ParserBuilder<?> backoffParserBuilder;
    List<GoldInputToParser> devInputs;
    Stage stage;

    @Override
    public String getKey() {
        return "compare-decoders";
    }


    private EvaluationEvent evaluateAstar() {
        final ProgressLogger progressLogger = new ProgressLogger(
                100,
                devInputs.size(),
                "Decoded sentence with A*",
                stage::setProgress);

        log.info("Evaluating A* search...");
        final EvaluationEvent astarEvaluationEvent = EvaluateParserTask.evaluateCheckpoint(
                checkpointPath,
                devInputs,
                stage.getArguments(),
                backoffParserBuilder,
                evaluator,
                categories,
                Optional.of(progressLogger));
        return astarEvaluationEvent;
    }

    private EvaluationEvent evaluateReranking(final int nbest) {

        final ProgressLogger progressLogger = new ProgressLogger(
                100,
                devInputs.size(),
                "Decoded sentence with " + nbest + "-best reranking",
                stage::setProgress);

        log.info("Evaluating {}-best reranker...", nbest);
        final AtomicInteger baseResultCount = new AtomicInteger(0);
        final ParserListener nbestListener = new ParserListener() {
            @Override
            public void handleNewSentence(List<InputWord> words) {
            }

            @Override
            public boolean handleChartInsertion(Agenda agenda) {
                return true;
            }

            @Override
            public void handleSearchCompletion(List<Scored<SyntaxTreeNode>> result, Agenda agenda, int chartSize) {
                baseResultCount.addAndGet(result == null ? 0 : result.size());
            }
        };

        final ParserStatistics nbestParseStats = new ParserStatistics();

        final ParserAStar.Builder nbestBuilder = EasySRLUtil.parserBuilder(stage.getArguments())
                .modelFactory(new SupertagFactoredModelFactory(null, categories, true))
                .maxAgendaSize(Integer.MAX_VALUE)
                .maxChartSize(250000)
                .nBest(nbest)
                .allowUnseenRules(false)
                .listeners(ImmutableList.of(nbestListener, nbestParseStats));

        final Parser nbestParser = new ParserAStar(nbestBuilder) {
            @Override
            protected ChartCellFactory chooseCellFactory(final ModelFactory modelFactory, final int nbest) {
                return CellUtil.NbestChartCell.factory(nbest, nbestBeam);
            }
        };

        final ParserBuilder rerankerBuilder = EasySRLUtil.parserBuilder(
                new ParserReranking.Builder(modelDir, nbestParser),
                stage.getArguments());

        final EvaluationEvent rerankerEvaluationEvent = EvaluateParserTask.evaluateCheckpoint(
                rerankerBuilder,
                checkpointPath,
                devInputs,
                stage.getArguments(),
                backoffParserBuilder,
                evaluator,
                categories,
                Optional.of(progressLogger),
                true);

        log.info("Base reranker result mean count: " + baseResultCount.doubleValue() / devInputs.size());
        nbestParseStats.log();

        return rerankerEvaluationEvent;
    }


    private EvaluationEvent evaluateBeamSearch(final int beamSize) {
        final ProgressLogger progressLogger = new ProgressLogger(
                100,
                devInputs.size(),
                "Decoded sentence with beam search",
                stage::setProgress);

        log.info("Evaluating beam search with a beam size of {}", beamSize);

        final ParserBuilder beamSearchBuilder = EasySRLUtil.parserBuilder(
                new ParserBeamSearch.Builder(modelDir, beamSize),
                stage.getArguments())
                .nBest(beamSize);

        final EvaluationEvent beamSearchEvaluationEvent = EvaluateParserTask.evaluateCheckpoint(
                beamSearchBuilder,
                checkpointPath,
                devInputs,
                stage.getArguments(),
                backoffParserBuilder,
                evaluator,
                categories,
                Optional.of(progressLogger),
                false);
        return beamSearchEvaluationEvent;
    }

    @Override
    public Stream<EvaluationEvent> run(Stage stage) {
        this.stage = stage;
        this.modelDir = new File(stage.getArguments().getString("model_dir"));

        this.evaluator = stage.read("evaluator", Serialized.class)
                .findAny()
                .map(SerializationUtil::<DependencyEvaluator>deserialize)
                .get();

        this.checkpointPath = new File(stage.getArguments().getString("checkpoint_path"));

        try {
            this.categories = TaggerEmbeddings.loadCategories(new File(modelDir, "categories"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        TreeFactoredModelFactory.initializeCNN(RunConfig.newBuilder()
                .setMemory(stage.getArguments().getInt("native_memory")).build());

        this.backoffParserBuilder = EasySRLUtil.backoffParserBuilder(stage.getArguments());

        log.info("Reading dev data...");
        this.devInputs =
                TrainParserTask.getTaggedInputs(stage, "dev-inputs", "dev-tags", categories)
                        .filter(input -> input.getInputWords().size() <= backoffParserBuilder.getMaxSentenceLength())
                        .collect(Collectors.toList());

        log.info("Found {} dev sentences.", devInputs.size());

        evaluateAstar();
        evaluateReranking(10);
        evaluateReranking(100);
        evaluateBeamSearch(2);
        evaluateBeamSearch(4);
        evaluateBeamSearch(8);
        return Stream.empty();
    }
}
