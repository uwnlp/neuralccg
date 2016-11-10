package edu.uw.neuralccg.util;

import java.util.Iterator;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.github.kentonl.pipegraph.util.LambdaUtil.SideEffect;

public class RandomBlockingQueue<E> {
    private final Object[] elements;
    private final ReentrantLock lock;
    private final Condition enoughElements;
    private final Condition notFull;
    private final Random random;
    private final int minSize;
    private int size;

    public RandomBlockingQueue(int minSize, int maxSize, Random random) {
        this.lock = new ReentrantLock();
        this.enoughElements = lock.newCondition();
        this.notFull = lock.newCondition();
        this.elements = new Object[maxSize];
        this.random = random;
        this.minSize = minSize;
        this.size = 0;
    }

    public void offer(E element) throws InterruptedException {
        Objects.requireNonNull(element);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            while (size >= elements.length) {
                notFull.await();
            }
            elements[size++] = element;
            if (size >= minSize) {
                enoughElements.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public E poll() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            while (size < minSize) {
                enoughElements.await();
            }
            final int randomIndex = random.nextInt(size);
            final E randomElement = (E) elements[randomIndex];
            elements[randomIndex] = elements[--size];
            notFull.signal();
            return randomElement;
        } finally {
            lock.unlock();
        }
    }

    // Return a callback to terminate the offering thread.
    public SideEffect offerStream(final Supplier<Stream<E>> supplier) {
        final AtomicBoolean keepStreaming = new AtomicBoolean(true);
        final Thread thread = new Thread(() -> {
            while (keepStreaming.get()) {
                final Iterator<E> iterator = supplier.get().iterator();
                while (keepStreaming.get() && iterator.hasNext()) {
                    try {
                        this.offer(iterator.next());
                    } catch (final InterruptedException e) {
                        // Do nothing if we are asked to terminate.
                    }
                }
            }
        });
        thread.start();
        return () -> {
            keepStreaming.set(false);
            thread.interrupt();
            try {
                thread.join();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }
}