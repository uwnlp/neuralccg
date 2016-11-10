package edu.uw.neuralccg.evaluation.analysis;

import com.google.common.util.concurrent.AtomicDouble;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.syntax.parser.ParserListener;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.AnalysisProto.ParseStatsProto;

public class ParserStatistics implements ParserListener {
    public static final Logger log = LoggerFactory.getLogger(ParserStatistics.class);

    private AtomicInteger stepsUntilMistake;
    private AtomicInteger sentenceCount;
    private AtomicDouble neuralScore;
    private AtomicInteger neuralCount;
    private AtomicInteger agendaCount;
    private AtomicInteger chartCount;
    private AtomicInteger goldParsesFound;

    public ParserStatistics() {
        clear();
    }

    public void clear() {
        this.sentenceCount = new AtomicInteger(0);
        this.stepsUntilMistake = new AtomicInteger(0);
        this.agendaCount = new AtomicInteger(0);
        this.chartCount = new AtomicInteger(0);
        this.neuralScore = new AtomicDouble(0);
        this.neuralCount = new AtomicInteger(0);
        this.goldParsesFound = new AtomicInteger(0);
    }

    public void log() {
        log.info("Mean neural score: {}", neuralScore.get() / neuralCount.get());
        log.info("Mean neural network queries: {}", neuralCount.doubleValue() / sentenceCount.get());
        log.info("Mean final agenda size: {}", agendaCount.doubleValue() / sentenceCount.get());
        log.info("Mean final chart size: {}", chartCount.doubleValue() / sentenceCount.get());
        log.info("Neural network queries: {}%", 100.0 * neuralCount.doubleValue() / (agendaCount.doubleValue() + chartCount.doubleValue()));
        log.info("Mean steps until mistake: {}", stepsUntilMistake.doubleValue() / sentenceCount.get());
        log.info("Gold parses found: {}%", 100.0 * goldParsesFound.doubleValue() / sentenceCount.get());
    }

    private static double maybeDivide(final double x, final double y) {
        if (y == 0) {
            return 0;
        } else {
            return x / y;
        }
    }

    public ParseStatsProto toProto() {
        return ParseStatsProto.newBuilder()
                .setNeuralScore(maybeDivide(neuralScore.get(), neuralCount.get()))
                .setNeuralQueries(maybeDivide(neuralCount.doubleValue(), sentenceCount.get()))
                .setAgendaSize(maybeDivide(agendaCount.get(), sentenceCount.get()))
                .setChartSize(maybeDivide(chartCount.get(), sentenceCount.get()))
                .setNeuralQueryRatio(100.0 * maybeDivide(neuralCount.doubleValue(), (agendaCount.doubleValue() + chartCount.doubleValue())))
                .build();
    }

    public void addNeuralScore(double score) {
        neuralScore.addAndGet(score);
        neuralCount.incrementAndGet();
    }

    public void addStepsUntilMistake(int steps) {
        stepsUntilMistake.addAndGet(steps);
    }

    @Override
    public void handleNewSentence(final List<InputWord> words) {
    }

    @Override
    public boolean handleChartInsertion(final Agenda agenda) {
        return true;
    }

    @Override
    public void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result,
                                       final Agenda agenda,
                                       final int chartSize) {
        sentenceCount.incrementAndGet();
        chartCount.addAndGet(chartSize);
        if (agenda != null) {
            agendaCount.addAndGet(agenda.size());
        }
    }

    public void discountBackoff() {
        sentenceCount.decrementAndGet();
    }
}