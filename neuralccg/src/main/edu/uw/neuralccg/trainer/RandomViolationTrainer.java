package edu.uw.neuralccg.trainer;

import java.util.List;
import java.util.Random;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.util.ReservoirSampler;
import com.github.kentonl.pipegraph.util.tuple.Pair;

public class RandomViolationTrainer extends ViolationTrainer {
    private final static int NUM_SAMPLES = 1;
    private ReservoirSampler<Pair<AgendaItem, AgendaItem>> violationSampler;
    private Random random = new Random(0);

    @Override
    public String getKey() {
        return "random-violation";
    }

    protected double computeWeight(int i) {
        return 1.0;
    }

    @Override
    public void handleNewSentence(final List<InputWord> sentence) {
        super.handleNewSentence(sentence);
        this.violationSampler = new ReservoirSampler<>(NUM_SAMPLES, random, this::computeWeight);
    }

    @Override
    public boolean handleViolation(final AgendaItem incorrect,
                                   final AgendaItem correct,
                                   final double violation) {
        violationSampler.add(Pair.of(incorrect, correct));
        return true;
    }

    @Override
    public void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result,
                                       final Agenda agenda,
                                       final int chartSize) {
        super.handleSearchCompletion(result, agenda, chartSize);
        model.update(violationSampler.sampleStream().map(Pair::first), violationSampler.sampleStream().map(Pair::second));
    }
}

