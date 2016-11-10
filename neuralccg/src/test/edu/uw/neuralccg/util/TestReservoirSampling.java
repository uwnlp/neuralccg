package edu.uw.neuralccg.util;

import junit.framework.TestCase;

import org.hamcrest.Matchers;
import org.junit.Assert;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class TestReservoirSampling extends TestCase {
    private final static int MAX_SAMPLE = 100;
    private final static int NUM_SAMPLES = 100;
    private final static int NUM_ROUNDS = 10000;
    private final static double EPSILON = 0.1;
    private final Random random = new Random(0);

    public void testReservoirSampler() {
        final int[] histogram = new int[MAX_SAMPLE];
        for (int i = 0; i < NUM_ROUNDS; i++) {
            final ReservoirSampler<Integer> sampler = new ReservoirSampler<>(NUM_SAMPLES, random);
            IntStream.range(0, MAX_SAMPLE).forEach(x -> sampler.add(x));
            sampler.sampleStream().forEach(x -> histogram[x]++);
            Assert.assertThat((int) sampler.sampleStream().count(), Matchers.equalTo(NUM_SAMPLES));
        }
        for (final int x : histogram) {
            Assert.assertThat((double) x, Matchers.closeTo(NUM_ROUNDS * NUM_SAMPLES / (double) MAX_SAMPLE, EPSILON));
        }
    }

    public void testReservoirSamplingCollector() {
        final int[] histogram = new int[MAX_SAMPLE];
        for (int i = 0; i < NUM_ROUNDS; i++) {
            final List<Integer> samples = IntStream.range(0, MAX_SAMPLE)
                    .mapToObj(x -> x)
                    .collect(DataUtil.reservoirSamplingCollector(NUM_SAMPLES, random));
            samples.stream().forEach(x -> histogram[x]++);
            Assert.assertThat(samples.size(), Matchers.equalTo(NUM_SAMPLES));
        }
        for (final int x : histogram) {
            Assert.assertThat((double) x, Matchers.closeTo(NUM_ROUNDS * NUM_SAMPLES / (double) MAX_SAMPLE, EPSILON));
        }
    }
}