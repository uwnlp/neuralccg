package edu.uw.neuralccg.render;

import com.hp.gagawa.java.Node;
import com.hp.gagawa.java.elements.Div;
import com.hp.gagawa.java.elements.H2;
import com.hp.gagawa.java.elements.H4;
import com.typesafe.config.Config;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.neuralccg.AnalysisProto.EvaluationEvent;
import edu.uw.neuralccg.AnalysisProto.EvaluationProto;
import edu.uw.neuralccg.util.DataUtil;
import com.github.kentonl.pipegraph.util.C3Util;
import com.github.kentonl.pipegraph.util.ConfigUtil;
import com.github.kentonl.pipegraph.util.ConfigUtil.ConfigBuilder;
import com.github.kentonl.pipegraph.util.ConfigUtil.ConfigListBuilder;
import com.github.kentonl.pipegraph.web.renderer.IResourceRenderer;

public class EvaluationRenderer implements IResourceRenderer<EvaluationEvent> {
    @Override
    public String getKey() {
        return EvaluationEvent.class.toString();
    }

    @Override
    public boolean canRenderStream() {
        return true;
    }

    @Override
    public Node render(final EvaluationEvent evaluationEvent, final Config arguments) {
        throw new UnsupportedOperationException();
    }

    public static String xify(final String name) {
        return name.replaceAll("\\.", "_") + "_x";
    }

    public static String yify(final String name) {
        return name.replaceAll("\\.", "_");
    }

    private static Node renderGroupedEvents(final Map<String, List<EvaluationEvent>> groupedEvents,
                                            final String xAxis,
                                            final String yAxis,
                                            final Function<EvaluationEvent, Number> getX,
                                            final Function<EvaluationEvent, Number> getY) {
        final ConfigBuilder xs = ConfigUtil.builder();
        for (final String name : groupedEvents.keySet()) {
            xs.add(yify(name), xify(name));
        }

        final ConfigListBuilder columns = ConfigUtil.listBuilder();
        for (final Map.Entry<String, List<EvaluationEvent>> nameAndEvents : groupedEvents.entrySet()) {
            final ConfigListBuilder xColumn = ConfigUtil.listBuilder().add(xify(nameAndEvents.getKey()));
            final ConfigListBuilder yColumn = ConfigUtil.listBuilder().add(yify(nameAndEvents.getKey()));
            for (final EvaluationEvent event : nameAndEvents.getValue()) {
                xColumn.add(getX.apply(event));
                yColumn.add(getY.apply(event));
            }
            columns.add(xColumn.build());
            columns.add(yColumn.build());
        }

        final Config axis = ConfigUtil.builder()
                .add("x", ConfigUtil.builder()
                        .add("label", ConfigUtil.builder()
                                .add("text", xAxis)
                                .add("position", "outer-middle")
                                .build().root())
                        .build().root())
                .add("y", ConfigUtil.builder()
                        .add("label", ConfigUtil.builder()
                                .add("text", yAxis)
                                .add("position", "outer-middle")
                                .build().root())
                        .build().root())
                .build();

        final Config grid = ConfigUtil.builder()
                .add("y", ConfigUtil.builder().add("show", true).build().root())
                .build();
        final Div graph = C3Util.renderGraph(
                RandomStringUtils.randomAlphabetic(6),
                ConfigUtil.builder()
                        .add("xs", xs.build().root())
                        .add("columns", columns.build())
                        .build(),
                axis,
                grid);
        return new Div().appendChild(new H4().appendText(yAxis)).appendChild(graph);
    }

    private static List<EvaluationEvent> getMaxF1Events(final List<EvaluationEvent> sortedEvents, Function<EvaluationEvent, EvaluationProto> getEval) {
        final List<EvaluationEvent> maxF1Events = new ArrayList<>();
        double maxF1 = Double.NEGATIVE_INFINITY;
        for (final EvaluationEvent event : sortedEvents) {
            maxF1 = Math.max(maxF1, getEval.apply(event).getF1());
            final EvaluationEvent.Builder maxF1Event = event.toBuilder();
            maxF1Event.getEvalBuilder().setF1(maxF1);
            maxF1Event.getEvalBackoffBuilder().setF1(maxF1);
            maxF1Events.add(maxF1Event.build());
        }
        return maxF1Events;
    }

    public Node renderStream(final Stream<EvaluationEvent> evaluationEvents, final Config arguments) {
        final Div root = new Div();

        final Map<String, List<EvaluationEvent>> groupedEvents = evaluationEvents
                .collect(Collectors.groupingBy(EvaluationEvent::getName));
        groupedEvents.values().forEach(events -> Collections.sort(events, Comparator.comparing(EvaluationEvent::getSteps)));
        root.appendChild(new H2().appendText("Evaluations"));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "Dev F1 ", EvaluationEvent::getSteps, e -> e.getEval().getF1()));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "Dev F1 with backoff", EvaluationEvent::getSteps, e -> e.getEvalBackoff().getF1()));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "Optimal %", EvaluationEvent::getSteps, e -> e.getEval().getParsable()));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "Sentences per second", EvaluationEvent::getSteps, e -> e.getEval().getSpeed()));

        final Map<String, List<EvaluationEvent>> maxedEvents = DataUtil.mapToMap(groupedEvents, e -> getMaxF1Events(e, EvaluationEvent::getEval));
        root.appendChild(renderGroupedEvents(maxedEvents, "Steps", "Max Dev F1", EvaluationEvent::getSteps, e -> e.getEval().getF1()));

        final Map<String, List<EvaluationEvent>> maxedBackoffEvents = DataUtil.mapToMap(groupedEvents, e -> getMaxF1Events(e, EvaluationEvent::getEvalBackoff));
        root.appendChild(renderGroupedEvents(maxedBackoffEvents, "Steps", "Max Dev F1 with backoff", EvaluationEvent::getSteps, e -> e.getEvalBackoff().getF1()));

        root.appendChild(new H2().appendText("Parser Statistics"));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "Mean neural score ", EvaluationEvent::getSteps, e -> e.getParseStats().getNeuralScore()));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "Mean neural queries ", EvaluationEvent::getSteps, e -> e.getParseStats().getNeuralQueries()));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "Mean final agenda size", EvaluationEvent::getSteps, e -> e.getParseStats().getAgendaSize()));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "Mean final forest size", EvaluationEvent::getSteps, e -> e.getParseStats().getChartSize()));
        root.appendChild(renderGroupedEvents(groupedEvents, "Steps", "% neural queries per hypothesis", EvaluationEvent::getSteps, e -> e.getParseStats().getNeuralQueryRatio()));

        return root;
    }
}
