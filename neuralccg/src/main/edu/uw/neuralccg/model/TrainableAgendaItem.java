package edu.uw.neuralccg.model;

import com.google.common.base.Preconditions;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.AgendaItem;


public class TrainableAgendaItem extends AgendaItem {
    // Avoid ties.
    private static AtomicLong counter = new AtomicLong(0);
    private final boolean isGold;
    private final boolean includeDeps;
    private final boolean isTight;
    private final long index;
    private Optional<TrainableAgendaItem> tightened;
    private double loss;

    public TrainableAgendaItem(final SyntaxTreeNode node,
                               final double insideScore,
                               final double outsideScoreUpperbound,
                               final int startIndex,
                               final int length,
                               final boolean includeDeps,
                               final boolean isGold,
                               final double loss,
                               final boolean isTight) {
        super(node, insideScore, outsideScoreUpperbound, startIndex, length, includeDeps);
        this.isGold = isGold;
        this.includeDeps = includeDeps;
        this.isTight = isTight;
        this.tightened = Optional.empty();
        this.loss = loss;
        this.index = counter.getAndIncrement();
    }

    public TrainableAgendaItem(final SyntaxTreeNode node,
                               final double insideScore,
                               final double outsideScoreUpperbound,
                               final int startIndex,
                               final int length,
                               final boolean includeDeps,
                               final boolean isGold,
                               final double loss) {
        this(node, insideScore, outsideScoreUpperbound, startIndex, length, includeDeps, isGold, loss, false);
    }

    @Override
    public int compareTo(final AgendaItem other) {
        int result = super.compareTo(other);
        if (result == 0) {
            return indexCompare(this, other);
        } else {
            return result;
        }
    }

    private static int indexCompare(final AgendaItem item1, final AgendaItem item2) {
        int result = Long.compare(((TrainableAgendaItem) item1).index, ((TrainableAgendaItem) item2).index);
        Preconditions.checkState((item1 == item2) == (result == 0));
        return result;
    }

    public static double getLoss(final AgendaItem item) {
        return ((TrainableAgendaItem) item).loss;
    }

    public static int lossAugmentedCompare(final AgendaItem item1, final AgendaItem item2) {
        int result = Double.compare(getLoss(item2) + item2.getCost(), getLoss(item1) + item1.getCost());
        if (result == 0) {
            return indexCompare(item1, item2);
        } else {
            return result;
        }
    }

    public static boolean isGold(final AgendaItem item) {
        return ((TrainableAgendaItem) item).isGold;
    }

    public static boolean isTight(final AgendaItem item) {
        return ((TrainableAgendaItem) item).isTight;
    }

    public static boolean isTrainable(final AgendaItem item) {
        return item instanceof TrainableAgendaItem;
    }

    public static TrainableAgendaItem tighten(final AgendaItem item,
                                              final Supplier<Double> delta) {
        final TrainableAgendaItem trainableItem = (TrainableAgendaItem) item;
        if (!trainableItem.tightened.isPresent()) {
            trainableItem.tightened = Optional.of(new TrainableAgendaItem(
                    trainableItem.getParse(),
                    trainableItem.getInsideScore() + delta.get(),
                    trainableItem.getOutsideScoreUpperbound(),
                    trainableItem.getStartOfSpan(),
                    trainableItem.getSpanLength(),
                    trainableItem.includeDeps,
                    trainableItem.isGold,
                    trainableItem.loss,
                    true));
            trainableItem.tightened.get().tightened = trainableItem.tightened;
        }
        return trainableItem.tightened.get();
    }
}