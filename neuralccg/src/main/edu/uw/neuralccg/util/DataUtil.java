package edu.uw.neuralccg.util;

import com.github.kentonl.pipegraph.util.CollectionUtil;
import com.github.kentonl.pipegraph.util.LambdaUtil;
import com.github.kentonl.pipegraph.util.LambdaUtil.SideEffect;
import com.github.kentonl.pipegraph.util.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DataUtil {
    private DataUtil() {
    }

    public static Collector<?, ?, ?> sideEffectCollector(final SideEffect sideEffect) {
        return new Collector<Object, Object, Object>() {
            @Override
            public BiConsumer<Object, Object> accumulator() {
                return (x, y) -> {
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }

            @Override
            public BinaryOperator<Object> combiner() {
                return (x, y) -> null;
            }

            @Override
            public Function<Object, Object> finisher() {
                return LambdaUtil.toFunction(sideEffect);
            }

            @Override
            public Supplier<Object> supplier() {
                return () -> null;
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <T> Collector<T, ?, List<Pair<Double, T>>> maximaCollector(List<ToDoubleFunction<T>> criteria) {
        return new Collector<T, List<Pair<Double, T>>, List<Pair<Double, T>>>() {
            @Override
            public BiConsumer<List<Pair<Double, T>>, T> accumulator() {
                return (maxima, sample) -> {
                    for (int i = 0; i < criteria.size(); i++) {
                        double sampleCriterion = criteria.get(i).applyAsDouble(sample);
                        if (maxima.get(i) == null || sampleCriterion > maxima.get(i).first()) {
                            maxima.set(i, Pair.of(sampleCriterion, sample));
                        }
                    }
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return EnumSet.of(Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
            }

            @Override
            public BinaryOperator<List<Pair<Double, T>>> combiner() {
                return (left, right) -> {
                    for (int i = 0; i < criteria.size(); i++) {
                        if (right.get(i).first() > left.get(i).first()) {
                            left.set(i, right.get(i));
                        }
                    }
                    return left;
                };
            }

            @Override
            public Function<List<Pair<Double, T>>, List<Pair<Double, T>>> finisher() {
                return Function.identity();
            }

            @Override
            public Supplier<List<Pair<Double, T>>> supplier() {
                return () -> Arrays.asList(new Pair[criteria.size()]);
            }
        };
    }

    public static <T> Collector<T, ?, List<T>> reservoirSamplingCollector(int k) {
        return reservoirSamplingCollector(k, new Random());
    }

    public static <T> Collector<T, ?, List<T>> reservoirSamplingCollector(int k, Random random) {
        return new Collector<T, ReservoirSampler<T>, List<T>>() {
            @Override
            public BiConsumer<ReservoirSampler<T>, T> accumulator() {
                return (sampler, sample) -> sampler.add(sample);
            }

            @Override
            public Set<Characteristics> characteristics() {
                return EnumSet.of(Characteristics.UNORDERED);
            }

            @Override
            public BinaryOperator<ReservoirSampler<T>> combiner() {
                return (left, right) -> {
                    left.addAll(right);
                    return left;
                };
            }

            @Override
            public Function<ReservoirSampler<T>, List<T>> finisher() {
                return sampler -> sampler.sampleStream().collect(Collectors.toList());
            }

            @Override
            public Supplier<ReservoirSampler<T>> supplier() {
                return () -> new ReservoirSampler<>(k, random);
            }
        };
    }

    public static <T> Collector<T, ?, List<List<T>>> partitionCollector(int k) {
        return partitionCollector(k, new Random());
    }

    public static <T> Collector<T, ?, List<List<T>>> partitionCollector(int k, Random rand) {
        return new Collector<T, List<List<T>>, List<List<T>>>() {
            @Override
            public BiConsumer<List<List<T>>, T> accumulator() {
                return (partitions, item) -> partitions.get(rand.nextInt(k)).add(item);
            }

            @Override
            public Set<Characteristics> characteristics() {
                return EnumSet.of(Characteristics.IDENTITY_FINISH, Characteristics.UNORDERED);
            }

            @Override
            public BinaryOperator<List<List<T>>> combiner() {
                return (left, right) -> CollectionUtil
                        .zip(left.stream(), right.stream())
                        .map(lr -> {
                            lr.first().addAll(lr.second());
                            return lr.first();
                        }).collect(Collectors.toList());
            }

            @Override
            public Function<List<List<T>>, List<List<T>>> finisher() {
                return Function.identity();
            }

            @Override
            public Supplier<List<List<T>>> supplier() {
                return () -> IntStream.range(0, k)
                        .mapToObj(i -> new ArrayList<T>())
                        .collect(Collectors.toList());
            }
        };
    }

    public static <T> Collector<T, ?, List<List<T>>> orderedPartitionCollector(int maxPartitionSize) {
        return new Collector<T, List<List<T>>, List<List<T>>>() {
            @Override
            public BiConsumer<List<List<T>>, T> accumulator() {
                return (partitions, item) -> {
                    if (partitions.isEmpty() || partitions.get(partitions.size() - 1).size() >= maxPartitionSize) {
                        partitions.add(new ArrayList<>());
                    }
                    partitions.get(partitions.size() - 1).add(item);
                };
            }

            @Override
            public Set<Characteristics> characteristics() {
                return EnumSet.of(Characteristics.IDENTITY_FINISH);
            }

            @Override
            public BinaryOperator<List<List<T>>> combiner() {
                return (left, right) -> {
                    left.addAll(right);
                    return left;
                };
            }

            @Override
            public Function<List<List<T>>, List<List<T>>> finisher() {
                return Function.identity();
            }

            @Override
            public Supplier<List<List<T>>> supplier() {
                return () -> new ArrayList<>();
            }
        };
    }

    public static <T> Stream<Stream<T>> lazyPartition(final Stream<T> stream, final int maxPartitionSize) {
        final Iterator<T> iterator = stream.iterator();
        final Iterable<Stream<T>> partitionedIterable = () -> new Iterator<Stream<T>>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public Stream<T> next() {
                List<T> partition = new ArrayList<>();
                while (partition.size() < maxPartitionSize && iterator.hasNext()) {
                    partition.add(iterator.next());
                }
                return partition.stream();
            }
        };
        return StreamSupport.stream(partitionedIterable.spliterator(), false);
    }

    public static <X, Y, Z> Map<X, Z> mapToMap(Map<X, Y> inputMap,
                                               Function<Y, Z> mapper) {
        return inputMap
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                entry -> entry.getKey(),
                                entry -> mapper.apply(entry.getValue())));
    }
}
