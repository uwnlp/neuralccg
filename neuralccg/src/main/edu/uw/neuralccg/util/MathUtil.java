package edu.uw.neuralccg.util;

import com.google.common.collect.Sets;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

import com.github.kentonl.pipegraph.util.tuple.Pair;

public class MathUtil {
    private MathUtil() {
    }


    public static <T extends Number> Collector<Pair<T, T>, ?, Double> correlationCollector() {
        return correlationCollector(Pair::first, Pair::second);
    }

    public static <T> Collector<T, ?, Double> correlationCollector(
            Function<T, Number> xGetter, Function<T, Number> yGetter) {
        return new Collector<T, Pair<List<Double>, List<Double>>, Double>() {
            @Override
            public BiConsumer<Pair<List<Double>, List<Double>>, T> accumulator() {
                return (lists, sample) -> {
                    lists.first().add(xGetter.apply(sample).doubleValue());
                    lists.second().add(yGetter.apply(sample).doubleValue());
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }

            @Override
            public BinaryOperator<Pair<List<Double>, List<Double>>> combiner() {
                return (left, right) -> {
                    left.first().addAll(right.first());
                    left.second().addAll(right.second());
                    return left;
                };
            }

            @Override
            public Function<Pair<List<Double>, List<Double>>, Double> finisher() {
                return (lists) -> Optional.of(lists)
                        .filter(xy -> xy.first().size() > 2)
                        .filter(xy -> xy.second().size() > 2)
                        .map(xy -> new PearsonsCorrelation().correlation(
                                xy.first().stream().mapToDouble(x -> x).toArray(),
                                xy.second().stream().mapToDouble(y -> y).toArray())).orElse(Double.NaN);
            }

            @Override
            public Supplier<Pair<List<Double>, List<Double>>> supplier() {
                return () -> Pair.of(new ArrayList<>(), new ArrayList<>());
            }
        };
    }

    public static double cosineSimilarity(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException(
                    "Vector dimensions should be equal");
        }
        double xySum = 0;
        double xSum = 0;
        double ySum = 0;
        for (int i = 0; i < x.length; i++) {
            xySum += x[i] * y[i];
            xSum += x[i] * x[i];
            ySum += y[i] * y[i];
        }
        return xySum / (Math.sqrt(xSum) * Math.sqrt(ySum));
    }

    public static double harmonicMean(double x, double y) {
        return 2 * x * y / (x + y);
    }

    public static double logSumExp(Collection<Double> xs) {
        if (xs.size() == 1) {
            return xs.stream().findFirst().get();
        }
        final double max = xs.stream().mapToDouble(x -> x).max().getAsDouble();
        return max
                + Math.log(xs.stream()
                .filter(x -> x != Double.NEGATIVE_INFINITY)
                .mapToDouble(x -> Math.exp(x - max)).sum());
    }

    public static <T> Collector<T, ?, Double> pmiCollector(Predicate<T> pred1,
                                                           Predicate<T> pred2) {
        return new Collector<T, PMICounter, Double>() {
            @Override
            public BiConsumer<PMICounter, T> accumulator() {
                return (counter, sample) -> {
                    if (pred1.test(sample)) {
                        counter.xCount.incrementAndGet();
                    }
                    if (pred2.test(sample)) {
                        counter.yCount.incrementAndGet();
                    }
                    if (pred1.and(pred2).test(sample)) {
                        counter.xyCount.incrementAndGet();
                    }
                    counter.totalCount.incrementAndGet();
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Sets.newHashSet(Characteristics.UNORDERED);
            }

            @Override
            public BinaryOperator<PMICounter> combiner() {
                return PMICounter::merge;
            }

            @Override
            public Function<PMICounter, Double> finisher() {
                return PMICounter::getPMI;
            }

            @Override
            public Supplier<PMICounter> supplier() {
                return PMICounter::new;
            }
        };
    }

    private static class PMICounter {
        public final AtomicInteger xCount = new AtomicInteger(0);
        public final AtomicInteger yCount = new AtomicInteger(0);
        public final AtomicInteger xyCount = new AtomicInteger(0);
        public final AtomicInteger totalCount = new AtomicInteger(0);

        public double getPMI() {
            return Math.log(xyCount.get()) - Math.log(xCount.get())
                    - Math.log(yCount.get()) + Math.log(totalCount.get());
        }

        public PMICounter merge(PMICounter other) {
            this.xCount.addAndGet(other.xCount.get());
            this.yCount.addAndGet(other.yCount.get());
            this.xyCount.addAndGet(other.xyCount.get());
            this.totalCount.addAndGet(other.totalCount.get());
            return this;
        }
    }
}