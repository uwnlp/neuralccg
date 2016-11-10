package edu.uw.neuralccg.trainer;

import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.neuralccg.agenda.MultiHeadedAgenda;
import edu.uw.neuralccg.agenda.PredicatedAgenda;
import edu.uw.neuralccg.model.TrainableAgendaItem;

public abstract class ViolationTrainer extends Trainer {
    protected abstract boolean handleViolation(final AgendaItem incorrect,
                                               final AgendaItem correct,
                                               final double violation);

    @Override
    public boolean handleChartInsertion(final Agenda agenda) {
        super.handleChartInsertion(agenda);
        final PredicatedAgenda predicatedAgenda = (PredicatedAgenda) agenda;
        if (predicatedAgenda.getTrueAgenda().isEmpty()) {
            // Everything in the agenda is incorrect and we have no hope of finding more violations.
            // This happens when the supertagger beam is too aggressive.
            return false;
        }
        if (predicatedAgenda.getFalseAgenda().isEmpty()) {
            // Agenda can still produce incorrect candidates in the future, but there can be no
            // violations at this step. This happens when the supertagger beam is too aggressive.
            return true;
        }

        // Correct parses should have no loss. So in cases where the true max score and loss is a
        // a correct candidate, the violation will be negative and no update will be performed.
        final AgendaItem maxScoreAndLoss = ((MultiHeadedAgenda) predicatedAgenda.getFalseAgenda()).peekOther();
        final AgendaItem maxCorrectScore = predicatedAgenda.getTrueAgenda().peek();

        final double violation = maxScoreAndLoss.getCost()
                + TrainableAgendaItem.getLoss(maxScoreAndLoss)
                - maxCorrectScore.getCost();
        if (violation >= 0) {
            if (!handleViolation(maxScoreAndLoss, maxCorrectScore, violation)) {
                return false;
            }
        }
        return true;
    }
}

