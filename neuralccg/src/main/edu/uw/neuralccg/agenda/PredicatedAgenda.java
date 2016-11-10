package edu.uw.neuralccg.agenda;

import com.google.common.collect.Iterators;

import java.util.Comparator;
import java.util.Iterator;
import java.util.function.Predicate;

import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.Agenda;

public class PredicatedAgenda implements Agenda {
    private final Predicate<AgendaItem> classifier;
    private final Agenda trueAgenda, falseAgenda;
    private final Comparator<AgendaItem> comparator;

    // Both provided agendas are assumed to use the provided comparator.
    public PredicatedAgenda(final Predicate<AgendaItem> classifier,
                            final Agenda trueAgenda,
                            final Agenda falseAgenda,
                            final Comparator<AgendaItem> comparator) {
        this.classifier = classifier;
        this.trueAgenda = trueAgenda;
        this.falseAgenda = falseAgenda;
        this.comparator = comparator;
    }

    public Comparator<AgendaItem> comparator() {
        return comparator;
    }

    public Agenda getTrueAgenda() {
        return trueAgenda;
    }

    public Agenda getFalseAgenda() {
        return falseAgenda;
    }

    @Override
    public boolean add(final AgendaItem item) {
        if (classifier.test(item)) {
            return trueAgenda.add(item);
        } else {
            return falseAgenda.add(item);
        }
    }

    @Override
    public AgendaItem poll() {
        if (trueAgenda.isEmpty()) {
            return falseAgenda.poll();
        } else if (falseAgenda.isEmpty()) {
            return trueAgenda.poll();
        } else if (comparator.compare(trueAgenda.peek(), falseAgenda.peek()) < 0) {
            return trueAgenda.poll();
        } else {
            return falseAgenda.poll();
        }
    }

    @Override
    public AgendaItem peek() {
        if (trueAgenda.isEmpty()) {
            return falseAgenda.peek();
        } else if (falseAgenda.isEmpty()) {
            return trueAgenda.peek();
        } else if (comparator.compare(trueAgenda.peek(), falseAgenda.peek()) < 0) {
            return trueAgenda.peek();
        } else {
            return falseAgenda.peek();
        }
    }

    @Override
    public Iterator<AgendaItem> iterator() {
        return Iterators.concat(trueAgenda.iterator(), falseAgenda.iterator());
    }

    @Override
    public int size() {
        return trueAgenda.size() + falseAgenda.size();
    }
}
