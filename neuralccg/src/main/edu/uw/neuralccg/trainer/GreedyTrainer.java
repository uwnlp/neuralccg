package edu.uw.neuralccg.trainer;

import java.util.List;
import java.util.Optional;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.util.Util.Scored;
import com.github.kentonl.pipegraph.util.tuple.Pair;

public class GreedyTrainer extends ViolationTrainer {
    private Optional<Pair<AgendaItem, AgendaItem>> earliestViolation;

    @Override
    public String getKey() {
        return "greedy";
    }

    @Override
    public void handleNewSentence(final List<InputWord> sentence) {
        super.handleNewSentence(sentence);
        this.earliestViolation = Optional.empty();
    }

    @Override
    protected boolean handleViolation(final AgendaItem incorrect,
                                      final AgendaItem correct,
                                      final double violation) {
        if (!earliestViolation.isPresent()) {
            earliestViolation = Optional.of(Pair.of(incorrect, correct));
        }
        return false;
    }

    @Override
    public void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result,
                                       final Agenda agenda,
                                       final int chartSize) {
        super.handleSearchCompletion(result, agenda, chartSize);
        earliestViolation.ifPresent(p -> model.update(p.first(), p.second()));
    }
}
