package edu.uw.neuralccg.util;

import com.google.common.collect.MinMaxPriorityQueue;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import com.github.kentonl.pipegraph.util.tuple.Pair;

public class ReservoirSampler<T> {
    private final MinMaxPriorityQueue<Pair<Double, T>> minQueue;
    private final ToDoubleFunction<Integer> computeWeight;
    private final Random random;
    private final AtomicInteger count;

    public ReservoirSampler(final int k, final Random random, final ToDoubleFunction<Integer> computeWeight) {
        this.minQueue = MinMaxPriorityQueue
                .<Pair<Double, T>>orderedBy((x, y) -> Double.compare(x.first(), y.first()))
                .maximumSize(k).create();
        this.computeWeight = computeWeight;
        this.random = random;
        this.count = new AtomicInteger(0);
    }

    public ReservoirSampler(final int k, final Random random) {
        this(k, random, i -> 1.0);
    }

    public ReservoirSampler(final int k) {
        this(k, new Random());
    }

    public void add(final T sample) {
        minQueue.add(Pair.of(random.nextDouble() * computeWeight.applyAsDouble(count.getAndIncrement()), sample));
    }

    public void addAll(final ReservoirSampler<T> other) {
        this.minQueue.addAll(other.minQueue);
        this.count.addAndGet(other.count.get());
    }

    public Stream<T> sampleStream() {
        return minQueue.stream().map(Pair::second);
    }
}
