package edu.uw.neuralccg.agenda;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.function.Function;
import java.util.function.Predicate;

import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.Agenda;

/*
 * Element comparisons can be based on a lower bound of the true value to minimize.
 * The head of the queue is guaranteed to have the minimal true value.
 */
public class LazyAgenda implements Agenda {
    private final Comparator<AgendaItem> comparator;
    private final PriorityQueue<AgendaItem> queue;
    private final Predicate<AgendaItem> isTight;
    private final Function<AgendaItem, AgendaItem> tighten;

    public LazyAgenda(final Predicate<AgendaItem> isTight,
                      final Function<AgendaItem, AgendaItem> tighten,
                      final Comparator<AgendaItem> comparator) {
        this.comparator = comparator;
        this.queue = new PriorityQueue<>(1000, comparator);
        this.isTight = isTight;
        this.tighten = tighten;
    }

    @Override
    public Comparator<AgendaItem> comparator() {
        return comparator;
    }

    private void tightenHead() {
        if (!queue.isEmpty()) {
            while (!isTight.test(queue.peek())) {
                queue.add(tighten.apply(queue.poll()));
            }
        }
    }

    @Override
    public boolean add(final AgendaItem item) {
        return queue.add(item);
    }

    @Override
    public AgendaItem poll() {
        tightenHead();
        return queue.poll();
    }

    @Override
    public AgendaItem peek() {
        tightenHead();
        return queue.peek();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public Iterator<AgendaItem> iterator() {
        // Assume every item must already be tight for now if we are iterating...
        return queue.stream().map(tighten::apply).iterator();
    }
}
