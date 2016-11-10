package edu.uw.neuralccg.trainer;

import com.typesafe.config.Config;

import java.util.List;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.syntax.parser.ParserListener;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.evaluation.analysis.ParserStatistics;
import edu.uw.neuralccg.model.TrainableAgendaItem;
import edu.uw.neuralccg.model.TrainableModel;
import com.github.kentonl.pipegraph.registry.IRegisterable;

public abstract class Trainer implements IRegisterable, ParserListener {
    private ParserStatistics stats;
    private int stepCount;
    private boolean madeMistake;
    protected TrainableModel model;

    public void handleArguments(final Config arguments) {
        // Do nothing.
    }

    public void setModel(final TrainableModel model) {
        this.model = model;
    }

    public void setStats(final ParserStatistics stats) {
        this.stats = stats;
    }

    @Override
    public void handleNewSentence(final List<InputWord> sentence) {
        stepCount = 0;
        madeMistake = false;
    }

    @Override
    public boolean handleChartInsertion(final Agenda agenda) {
        if (!TrainableAgendaItem.isGold(agenda.peek()) && !madeMistake) {
            getStats().addStepsUntilMistake(stepCount);
            madeMistake = true;
        }
        stepCount++;
        return true;
    }

    @Override
    public void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result,
                                       final Agenda agenda,
                                       final int chartSize) {
        if (!madeMistake) {
            getStats().addStepsUntilMistake(stepCount);
            madeMistake = true;
        }
    }

    public ParserStatistics getStats() {
        return stats;
    }
}
