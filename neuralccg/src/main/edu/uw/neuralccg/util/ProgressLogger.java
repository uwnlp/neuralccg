package edu.uw.neuralccg.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class ProgressLogger {
    public static final Logger log = LoggerFactory
            .getLogger(ProgressLogger.class);

    private final AtomicInteger count;
    private final String description;
    private final int logFrequency;
    private final int maximum;
    private final BiConsumer<Integer, Integer> setProgress;

    public ProgressLogger(int logFrequency, int maximum, String description,
                          BiConsumer<Integer, Integer> setProgress) {
        this.count = new AtomicInteger(0);
        this.logFrequency = logFrequency;
        this.maximum = maximum;
        this.description = description;
        this.setProgress = setProgress;
        this.setProgress.accept(count.get(), maximum);
    }

    public void maybeLog() {
        if (count.incrementAndGet() % logFrequency == 0) {
            if (maximum > 0) {
                log.info(description + " : {}/{}", count, Integer.toString(maximum));
            } else {
                log.info(description + " : {}", count);
            }
        }
        setProgress.accept(count.get(), maximum);
    }

    public int getProgress() {
        return count.get();
    }
}
