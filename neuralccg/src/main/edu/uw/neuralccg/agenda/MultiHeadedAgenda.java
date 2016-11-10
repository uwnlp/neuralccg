package edu.uw.neuralccg.agenda;

import com.google.common.base.Preconditions;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.Agenda;

public class MultiHeadedAgenda implements Agenda {
    private final Agenda baseAgenda, otherAgenda;
    private final Set<AgendaItem> ignoredItems;

    public MultiHeadedAgenda(final Agenda baseAgenda,
                             final Agenda otherAgenda) {
        this.baseAgenda = baseAgenda;
        this.otherAgenda = otherAgenda;
        this.ignoredItems = new HashSet<>();
    }

    public Comparator<AgendaItem> comparator() {
        return baseAgenda.comparator();
    }

    public AgendaItem peekOther() {
        while (ignoredItems.remove(otherAgenda.peek())) {
            otherAgenda.poll();
        }
        Preconditions.checkState(otherAgenda.size() - ignoredItems.size() == baseAgenda.size());
        return otherAgenda.peek();
    }

    @Override
    public boolean add(final AgendaItem item) {
        final boolean baseAdded = baseAgenda.add(item);
        final boolean alternateAdded = otherAgenda.add(item);
        Preconditions.checkState(baseAdded == alternateAdded);
        Preconditions.checkState(otherAgenda.size() - ignoredItems.size() == baseAgenda.size());
        return baseAdded;
    }

    @Override
    public AgendaItem poll() {
        if (baseAgenda.isEmpty()) {
            return null;
        }
        final AgendaItem removedItem = baseAgenda.poll();
        ignoredItems.add(removedItem);
        Preconditions.checkState(otherAgenda.size() - ignoredItems.size() == baseAgenda.size());
        return removedItem;
    }

    @Override
    public AgendaItem peek() {
        return baseAgenda.peek();
    }

    @Override
    public int size() {
        return baseAgenda.size();
    }

    @Override
    public Iterator<AgendaItem> iterator() {
        return baseAgenda.iterator();
    }
}
