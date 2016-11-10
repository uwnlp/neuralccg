package edu.uw.neuralccg.trainer;

import com.github.kentonl.pipegraph.util.CollectionUtil;

import java.util.List;
import java.util.Objects;

import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.agenda.PredicatedAgenda;

public class CrfTrainer extends Trainer {

    @Override
    public String getKey() {
        return "crf";
    }

    @Override
    public boolean handleChartInsertion(final Agenda agenda) {
        super.handleChartInsertion(agenda);
        final Agenda goldAgenda = ((PredicatedAgenda) agenda).getTrueAgenda();

        // Only keep searching if there will be at least another gold candidates on the agenda after the next pop.
        return goldAgenda.size() > 1;
    }

    @Override
    public void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result,
                                       final Agenda agenda,
                                       final int chartSize) {
        super.handleSearchCompletion(result, agenda, chartSize);
        final PredicatedAgenda predicatedAgenda = (PredicatedAgenda) agenda;
        if (!predicatedAgenda.getTrueAgenda().isEmpty() && !predicatedAgenda.getFalseAgenda().isEmpty()) {
            model.update(
                    CollectionUtil.streamWhile(predicatedAgenda.getFalseAgenda()::poll, Objects::nonNull),
                    CollectionUtil.streamWhile(predicatedAgenda.getTrueAgenda()::poll, Objects::nonNull),
                    true);
        }
    }

}

