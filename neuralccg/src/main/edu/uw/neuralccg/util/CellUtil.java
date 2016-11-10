package edu.uw.neuralccg.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.ChartCell;
import com.github.kentonl.pipegraph.util.LambdaUtil.SideEffect;

public class CellUtil {
    public static class BeamSearchChartCell extends ChartCell {
        private final List<AgendaItem> entries = new ArrayList<>();
        private final int beamSize;
        private final SideEffect indicatePruning;

        public BeamSearchChartCell(final int beamSize, final SideEffect indicatePruning) {
            this.beamSize = beamSize;
            this.indicatePruning = indicatePruning;
        }

        @Override
        public Collection<AgendaItem> getEntries() {
            return entries;
        }

        @Override
        public boolean add(final Object key, final AgendaItem newEntry) {
            if (entries.size() >= beamSize) {
                indicatePruning.perform();
                return false;
            } else {
                return entries.add(newEntry);
            }
        }

        @Override
        public int size() {
            return entries.size();
        }

        public static ChartCellFactory factory (final int beamSize, final SideEffect indicatePruning) {
            return new ChartCellFactory() {
                @Override
                public ChartCell make() {
                    return new BeamSearchChartCell(beamSize, indicatePruning);
                }
            };
        }
    }

    public static class NbestChartCell extends ChartCell {
        private final ListMultimap<Object, AgendaItem> keyToEntries = ArrayListMultimap.create();
        private final int nbest;
        private final double nbestBeam;

        public NbestChartCell(final int nbest, final double nbestBeam) {
            this.nbest = nbest;
            this.nbestBeam = nbestBeam;
        }

        @Override
        public Collection<AgendaItem> getEntries() {
            return keyToEntries.values();
        }

        @Override
        public boolean add(final Object key, final AgendaItem newEntry) {
            final List<AgendaItem> existing = keyToEntries.get(key);
            if (existing.size() > nbest
                    || (existing.size() > 0 && newEntry.getCost() < existing.get(0).getCost() + Math.log(nbestBeam))) {
                return false;
            } else {
                // Only cache out hashes for nodes that get added to the chart.
                keyToEntries.put(key, newEntry);
                return true;
            }

        }

        @Override
        public int size() {
            return keyToEntries.size();
        }

        public static ChartCellFactory factory (final int nbest, final double nbestBeam) {
            return new ChartCellFactory() {
                @Override
                public ChartCell make() {
                    return new NbestChartCell(nbest, nbestBeam);
                }
            };
        }
    }
}