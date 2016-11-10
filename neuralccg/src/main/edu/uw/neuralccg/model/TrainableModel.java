package edu.uw.neuralccg.model;

import java.util.Optional;
import java.util.stream.Stream;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.neuralccg.trainer.Trainer;

public abstract class TrainableModel extends Model {

    protected TrainableModel(final int sentenceLength, final Optional<Trainer> trainer) {
        super(sentenceLength);
        trainer.ifPresent(t -> t.setModel(this));
    }

    public void update(final AgendaItem incorrect,
                       final AgendaItem correct) {
        update(incorrect, correct, false);
    }

    public void update(final Stream<AgendaItem> incorrect,
                       final Stream<AgendaItem> correct) {
        update(incorrect, correct, false);
    }

    public void update(final AgendaItem incorrect,
                       final AgendaItem correct,
                       final boolean useCrfLoss) {
        update(Stream.of(incorrect), Stream.of(correct), useCrfLoss);
    }

    public abstract void update(final Stream<AgendaItem> incorrect,
                                final Stream<AgendaItem> correct,
                                final boolean useCrfLoss);

    public static abstract class TrainableModelFactory extends ModelFactory {
        protected Optional<Trainer> trainer = Optional.empty();

        public void setTrainer(final Optional<Trainer> trainer) {
            this.trainer = trainer;
        }

        @Override
        public abstract TrainableModel make(final InputToParser sentence);
    }
}
