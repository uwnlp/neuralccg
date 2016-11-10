package edu.uw.neuralccg.trainer;

import java.util.ArrayList;
import java.util.List;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.util.Util.Scored;

public class SumViolationTrainer extends ViolationTrainer {
    private List<AgendaItem> incorrectItems, correctItems;

    @Override
    public String getKey() {
        return "sum-violation";
    }

    @Override
    public void handleNewSentence(final List<InputWord> sentence) {
        super.handleNewSentence(sentence);
        this.incorrectItems = new ArrayList<>();
        this.correctItems = new ArrayList<>();
    }

    @Override
    protected boolean handleViolation(final AgendaItem incorrect,
                                      final AgendaItem correct,
                                      final double violation) {
        incorrectItems.add(incorrect);
        correctItems.add(correct);
        return true;
    }

    @Override
    public void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result,
                                       final Agenda agenda,
                                       final int chartSize) {
        super.handleSearchCompletion(result, agenda, chartSize);
        model.update(incorrectItems.stream(), correctItems.stream());
    }
}

