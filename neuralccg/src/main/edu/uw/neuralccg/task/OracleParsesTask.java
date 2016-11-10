package edu.uw.neuralccg.task;

import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;
import com.github.kentonl.pipegraph.util.LambdaUtil;
import com.github.kentonl.pipegraph.util.tuple.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util;
import edu.uw.neuralccg.HtmlProto;
import edu.uw.neuralccg.HtmlProto.Html;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.evaluation.analysis.EvaluationStatistics;
import edu.uw.neuralccg.model.OracleModel.GoldInputToParser;
import edu.uw.neuralccg.model.OracleModel.OracleModelFactory;
import edu.uw.neuralccg.printer.GatedHtmlPrinter;
import edu.uw.neuralccg.util.EasySRLUtil;
import edu.uw.neuralccg.util.ProgressLogger;
import edu.uw.neuralccg.util.SerializationUtil;

public class OracleParsesTask implements ITask<Html> {
    public static final Logger log = LoggerFactory.getLogger(OracleParsesTask.class);

    @Override
    public String getKey() {
        return "oracle-parses";
    }

    @Override
    public Stream<Html> run(Stage stage) {
        final File modelDir = new File(
                stage.getArguments().getString("model_dir"));
        final Stream<DependencyParse> goldCorpus = stage.read("gold", Serialized.class)
                .map(SerializationUtil::<DependencyParse>deserialize);
        final DependencyEvaluator evaluator = stage.read("evaluator", Serialized.class)
                .findAny()
                .map(SerializationUtil::<DependencyEvaluator>deserialize)
                .get();

        final Collection<Category> categories;
        try {
            categories = TaggerEmbeddings.loadCategories(new File(modelDir, "categories"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final OracleModelFactory oracleModelFactory = new OracleModelFactory(categories, evaluator);


        final Parser parser = EasySRLUtil.parserBuilder(stage.getArguments())
                .modelFactory(oracleModelFactory)
                .build();

        final ProgressLogger progressLogger = new ProgressLogger(
                100,
                (int) stage.read("gold", Serialized.class).count(),
                "Parsed sentence",
                stage::setProgress);

        log.info("Parsing...");
        final EvaluationStatistics allStats = new EvaluationStatistics();
        final EvaluationStatistics parsableStats = new EvaluationStatistics();
        final GatedHtmlPrinter printer = new GatedHtmlPrinter();
        final List<HtmlProto.Html> results = goldCorpus
                .map(GoldInputToParser::new)
                .filter(input -> input.getInputWords().size() <= parser.getMaxSentenceLength())
                .map(input -> Pair.of(input, parser.doParsing(input)))
                .peek(inputAndParses -> allStats.updateStats(
                        inputAndParses.first().getGoldDependencies(),
                        inputAndParses.first().getGoldCategories(),
                        inputAndParses.second(),
                        evaluator))
                .filter(inputAndParses -> inputAndParses.second() != null)
                .peek(inputAndParses -> parsableStats.updateStats(
                        inputAndParses.first().getGoldDependencies(),
                        inputAndParses.first().getGoldCategories(),
                        inputAndParses.second(),
                        evaluator))
                .map(Pair::second)
                .filter(Objects::nonNull)
                .map(predicted -> printer.print(predicted
                        .stream()
                        .map(Util.Scored::getObject)
                        .collect(Collectors.toList()), 0))
                .map(htmlContent -> HtmlProto.Html.newBuilder().setContent(htmlContent).build())
                .peek(LambdaUtil.toConsumer(progressLogger::maybeLog))
                .collect(Collectors.toList());
        log.info("=====All stats====");
        allStats.log();
        log.info("==================");
        log.info("==Parsable stats==");
        parsableStats.log();
        log.info("==================");
        return results.stream();
    }
}