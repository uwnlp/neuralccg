package edu.uw.neuralccg.util;

import com.google.common.collect.Sets;

import com.googlecode.charts4j.AxisLabels;
import com.googlecode.charts4j.AxisLabelsFactory;
import com.googlecode.charts4j.AxisStyle;
import com.googlecode.charts4j.AxisTextAlignment;
import com.googlecode.charts4j.BarChart;
import com.googlecode.charts4j.BarChartPlot;
import com.googlecode.charts4j.Color;
import com.googlecode.charts4j.Data;
import com.googlecode.charts4j.GCharts;
import com.googlecode.charts4j.LineChart;
import com.googlecode.charts4j.Plots;
import com.googlecode.charts4j.ScatterPlot;
import com.googlecode.charts4j.ScatterPlotData;
import com.hp.gagawa.java.elements.Img;

import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import com.github.kentonl.pipegraph.util.tuple.Pair;

public class ChartUtil {

    private ChartUtil() {
    }

    public static List<Color> getDistinctColors(int k) {
        return Stream.of("000000", "00FF00", "0000FF", "FF0000", "01FFFE",
                "FFA6FE", "FFDB66", "006401", "010067", "95003A", "007DB5",
                "FF00F6", "FFEEE8", "774D00", "90FB92", "0076FF", "D5FF00",
                "FF937E", "6A826C", "FF029D", "FE8900", "7A4782", "7E2DD2",
                "85A900", "FF0056", "A42400", "00AE7E", "683D3B", "BDC6FF",
                "263400", "BDD393", "00B917", "9E008E", "001544", "C28C9F",
                "FF74A3", "01D0FF", "004754", "E56FFE", "788231", "0E4CA1",
                "91D0CB", "BE9970", "968AE8", "BB8800", "43002C", "DEFF74",
                "00FFC6", "FFE502", "620E00", "008F9C", "98FF52", "7544B1",
                "B500FF", "00FF78", "FF6E41", "005F39", "6B6882", "5FAD4E",
                "A75740", "A5FFD2", "FFB167", "009BFF", "E85EBE").limit(k)
                .map(Color::newColor).collect(Collectors.toList());
    }

    public static String webWrap(String chartUrl) {
        return new Img("", chartUrl).write();
    }

    public static <T> Collector<T, ?, String> histogramCollector() {
        return histogramCollector((x, y) -> 0);
    }

    public static <T> Collector<T, ?, String> histogramCollector(
            Comparator<T> comparator) {
        return new Collector<T, Map<T, Integer>, String>() {
            @Override
            public final BiConsumer<Map<T, Integer>, T> accumulator() {
                return (counts, sample) -> counts.merge(sample, 1,
                        Integer::sum);
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Sets.newHashSet(Characteristics.UNORDERED);
            }

            @Override
            public BinaryOperator<Map<T, Integer>> combiner() {
                return (x, y) -> {
                    if (x.size() > y.size()) {
                        y.forEach((k, v) -> x.merge(k, v, Integer::sum));
                        return x;
                    } else {
                        x.forEach((k, v) -> y.merge(k, v, Integer::sum));
                        return y;
                    }
                };
            }

            @Override
            public Function<Map<T, Integer>, String> finisher() {
                return counts -> {
                    if (counts.isEmpty()) {
                        return "";
                    }
                    final List<T> orderedKeys = counts.keySet().stream()
                            .sorted(comparator).collect(Collectors.toList());
                    final int yMax = counts.values().stream()
                            .max(Integer::compare).get();
                    final Data data = Data
                            .newData(orderedKeys.stream()
                                    .map(key -> normalize(counts.get(key), 0,
                                            yMax))
                                    .collect(Collectors.toList()));
                    final BarChartPlot plot = Plots.newBarChartPlot(data,
                            Color.RED);
                    final BarChart chart = GCharts.newBarChart(plot);

                    final AxisStyle axisStyle = AxisStyle.newAxisStyle(
                            Color.BLACK, 13, AxisTextAlignment.CENTER);
                    final AxisLabels yAxis = AxisLabelsFactory
                            .newAxisLabels("Count", 50.0);
                    yAxis.setAxisStyle(axisStyle);
                    final AxisLabels xAxis = AxisLabelsFactory
                            .newAxisLabels("Sample", 50.0);
                    xAxis.setAxisStyle(axisStyle);

                    chart.addXAxisLabels(AxisLabelsFactory.newAxisLabels(
                            orderedKeys.stream().map(Object::toString)
                                    .collect(Collectors.toList())));
                    chart.addYAxisLabels(AxisLabelsFactory
                            .newNumericRangeAxisLabels(0, yMax));
                    chart.addYAxisLabels(yAxis);
                    chart.addXAxisLabels(xAxis);

                    chart.setSize(600, 480);
                    chart.setGrid(100, 10, 3, 2);
                    chart.setTitle(
                            "Histogram over " + counts.values().stream()
                                    .mapToInt(x -> x).sum() + " samples",
                            Color.BLACK, 16);
                    return chart.toURLString();
                };
            }

            @Override
            public Supplier<Map<T, Integer>> supplier() {
                return HashMap::new;
            }
        };
    }

    public static <T extends Number> Collector<T, ?, String> smoothedHistogramCollector(double variance) {
        return Collectors.collectingAndThen(Collectors.toList(), samples -> {
            final DoubleSummaryStatistics xSummary = samples.stream()
                    .mapToDouble(Number::doubleValue).summaryStatistics();
            final NormalDistribution kernel = new NormalDistribution(0, variance);
            final double[] counts = DoubleStream
                    .iterate(xSummary.getMin(),
                            x -> x + (xSummary.getMax() - xSummary.getMin())
                                    / 100.0)
                    .limit(100)
                    .map(x -> samples.stream()
                            .mapToDouble(
                                    y -> kernel.density(y.doubleValue() - x))
                            .sum())
                    .toArray();

            final double yMax = Arrays.stream(counts).max().getAsDouble();

            final Data data = Data.newData(
                    Arrays.stream(counts).map(x -> 100.0 * x / yMax).toArray());

            final LineChart chart = GCharts.newLineChart(Plots.newLine(data));
            final AxisStyle axisStyle = AxisStyle.newAxisStyle(Color.BLACK, 13,
                    AxisTextAlignment.CENTER);
            final AxisLabels xAxis = AxisLabelsFactory.newAxisLabels("Value",
                    50.0);
            xAxis.setAxisStyle(axisStyle);
            final AxisLabels yAxis = AxisLabelsFactory
                    .newAxisLabels("Smoothed counts", 50.0);
            yAxis.setAxisStyle(axisStyle);
            chart.addXAxisLabels(AxisLabelsFactory.newNumericRangeAxisLabels(
                    xSummary.getMin(), xSummary.getMax()));
            chart.addYAxisLabels(
                    AxisLabelsFactory.newNumericRangeAxisLabels(0, yMax));
            chart.addXAxisLabels(xAxis);
            chart.addYAxisLabels(yAxis);
            chart.setSize(600, 480);
            chart.setGrid(100, 10, 3, 2);
            chart.setTitle(
                    "Smoothed histogram over " + samples.size() + " samples",
                    Color.BLACK, 16);
            return chart.toURLString();
        });
    }


    public static double normalize(double x, double min, double max) {
        return 100 * (x - min) / (max - min);
    }

    public static <X extends Number, Y extends Number> Collector<Pair<X, Y>, ?, String> scatterCollector() {
        return scatterCollector("X", "Y");
    }

    public static <X extends Number, Y extends Number> Collector<Pair<X, Y>, ?, String> scatterCollector(
            String xLabel, String yLabel) {
        return new Collector<Pair<X, Y>, Pair<List<X>, List<Y>>, String>() {

            @Override
            public BiConsumer<Pair<List<X>, List<Y>>, Pair<X, Y>> accumulator() {
                return (lists, pair) -> {
                    lists.first().add(pair.first());
                    lists.second().add(pair.second());
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }

            @Override
            public BinaryOperator<Pair<List<X>, List<Y>>> combiner() {
                return (left, right) -> {
                    left.first().addAll(right.first());
                    left.second().addAll(right.second());
                    return left;
                };
            }

            @Override
            public Function<Pair<List<X>, List<Y>>, String> finisher() {
                return (lists) -> {
                    final DoubleSummaryStatistics xSummary = lists.first()
                            .stream().mapToDouble(x -> x.doubleValue())
                            .summaryStatistics();
                    final DoubleSummaryStatistics ySummary = lists.second()
                            .stream().mapToDouble(y -> y.doubleValue())
                            .summaryStatistics();
                    final Data xData = Data.newData(lists.first().stream()
                            .mapToDouble(x -> normalize(x.doubleValue(),
                                    xSummary.getMin(), xSummary.getMax()))
                            .toArray());
                    final Data yData = Data.newData(lists.second().stream()
                            .mapToDouble(y -> normalize(y.doubleValue(),
                                    ySummary.getMin(), ySummary.getMax()))
                            .toArray());
                    final ScatterPlotData plotData = Plots
                            .newScatterPlotData(xData, yData);
                    final ScatterPlot chart = GCharts.newScatterPlot(plotData);
                    final AxisStyle axisStyle = AxisStyle.newAxisStyle(
                            Color.BLACK, 13, AxisTextAlignment.CENTER);
                    final AxisLabels xAxis = AxisLabelsFactory
                            .newAxisLabels(xLabel, 50.0);
                    xAxis.setAxisStyle(axisStyle);
                    final AxisLabels yAxis = AxisLabelsFactory
                            .newAxisLabels(yLabel, 50.0);
                    yAxis.setAxisStyle(axisStyle);
                    chart.addXAxisLabels(
                            AxisLabelsFactory.newNumericRangeAxisLabels(
                                    xSummary.getMin(), xSummary.getMax()));
                    chart.addYAxisLabels(
                            AxisLabelsFactory.newNumericRangeAxisLabels(
                                    ySummary.getMin(), ySummary.getMax()));
                    chart.addXAxisLabels(xAxis);
                    chart.addYAxisLabels(yAxis);
                    chart.setSize(600, 480);
                    chart.setGrid(100, 10, 3, 2);
                    chart.setTitle("Scatter plot over " + lists.first().size()
                            + " samples", Color.BLACK, 16);
                    return chart.toURLString();
                };
            }

            @Override
            public Supplier<Pair<List<X>, List<Y>>> supplier() {
                return () -> Pair.of(new ArrayList<>(), new ArrayList<>());
            }
        };
    }

}