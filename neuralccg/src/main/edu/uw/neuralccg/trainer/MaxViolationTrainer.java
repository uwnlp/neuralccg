package edu.uw.neuralccg.trainer;

import java.util.List;
import java.util.Optional;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.util.Util.Scored;
import com.github.kentonl.pipegraph.util.tuple.Triplet;

public class MaxViolationTrainer extends ViolationTrainer {
    private Optional<Triplet<AgendaItem, AgendaItem, Double>> maxViolation;
    private int violationLimit;
    private int violationCount;

    @Override
    public String getKey() {
        return "max-violation";
    }

    @Override
    public void handleNewSentence(final List<InputWord> sentence) {
        super.handleNewSentence(sentence);
        this.maxViolation = Optional.empty();
        this.violationLimit = getViolationLimit();
        this.violationCount = 0;
    }

    protected int getViolationLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected boolean handleViolation(final AgendaItem incorrect,
                                      final AgendaItem correct,
                                      final double violation) {
        if (violation > maxViolation.map(Triplet::third).orElse(0.0)) {
            maxViolation = Optional.of(Triplet.of(incorrect, correct, violation));
        }
        violationCount++;
        return violationCount < violationLimit;
    }

    @Override
    public void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result,
                                       final Agenda agenda,
                                       final int chartSize) {
        super.handleSearchCompletion(result, agenda, chartSize);
        maxViolation.ifPresent(p -> model.update(p.first(), p.second()));
    }
}

