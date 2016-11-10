package edu.uw.neuralccg.evaluation.analysis;

import com.google.common.base.Stopwatch;

import com.github.kentonl.pipegraph.util.CollectionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.util.Util;
import edu.uw.neuralccg.AnalysisProto.EvaluationProto;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.util.RetrievalStatistics;

public class EvaluationStatistics {
    public static final Logger log = LoggerFactory.getLogger(EvaluationStatistics.class);

    private final RetrievalStatistics overallStats;
    private final AtomicInteger parsableSentenceCount;
    private final AtomicInteger sentenceCount;
    private final Stopwatch parseTime;
    private final AtomicInteger correctlySupertaggedWords;
    private final AtomicInteger totalWords;

    public EvaluationStatistics() {
        overallStats = new RetrievalStatistics();
        sentenceCount = new AtomicInteger(0);
        parsableSentenceCount = new AtomicInteger(0);
        correctlySupertaggedWords = new AtomicInteger(0);
        totalWords = new AtomicInteger(0);
        parseTime = Stopwatch.createUnstarted();
    }

    public EvaluationProto.Builder toProto() {
        return EvaluationProto.newBuilder()
                .setRecall(100.0 * overallStats.getRecall())
                .setPrecision(100.0 * overallStats.getPrecision())
                .setF1(100.0 * overallStats.getF1())
                .setParsable(100.0 * parsableSentenceCount.doubleValue() / sentenceCount.doubleValue())
                .setSpeed(sentenceCount.get() * 1000.0 / parseTime.elapsed(TimeUnit.MILLISECONDS));
    }

    public void log() {
        overallStats.log();
        log.info("Supertag accuracy: {}%", 100.0 * correctlySupertaggedWords.doubleValue() / totalWords.get());
        log.info(String.format("%.2f%% parsable.", 100.0 * parsableSentenceCount.doubleValue() / sentenceCount.doubleValue()));
        log.info("Parse speed: {} sentences per second.", sentenceCount.get() * 1000.0 / parseTime.elapsed(TimeUnit.MILLISECONDS));
    }

    public Stopwatch getParseTime() {
        return parseTime;
    }

    public void updateStats(final Set<ResolvedDependency> goldDependencies,
                            final List<Category> goldCategories,
                            final List<Util.Scored<SyntaxTreeNode>> predictedParses,
                            final DependencyEvaluator evaluator) {
        sentenceCount.incrementAndGet();
        if (predictedParses != null) {
            parsableSentenceCount.incrementAndGet();
        } else {
            evaluator.evaluate(goldDependencies, null, overallStats);
            return;
        }

        final SyntaxTreeNode topParse = predictedParses.get(0).getObject();
        evaluator.evaluate(goldDependencies, topParse, overallStats);
        final int numCorrect = (int) CollectionUtil.zip(
                topParse.getLeaves().stream().map(SyntaxTreeNode::getCategory),
                goldCategories.stream(), Object::equals).filter(Boolean::booleanValue).count();
        correctlySupertaggedWords.addAndGet(numCorrect);
        totalWords.addAndGet(goldCategories.size());
    }

}