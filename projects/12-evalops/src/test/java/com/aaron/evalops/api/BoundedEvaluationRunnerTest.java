package com.aaron.evalops.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BoundedEvaluationRunnerTest {
    @Test
    void executesConcurrentlyWithinWorkerLimitAndPreservesInputOrder() {
        var executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4));
        var active = new AtomicInteger();
        var maximumActive = new AtomicInteger();
        try {
            List<Integer> output = BoundedEvaluationRunner.run(List.of(1, 2, 3, 4), executor, value -> {
                int now = active.incrementAndGet();
                maximumActive.accumulateAndGet(now, Math::max);
                try { Thread.sleep(value == 1 ? 80 : 20); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                finally { active.decrementAndGet(); }
                return value * 10;
            }, (value, reason) -> -1);

            assertEquals(List.of(10, 20, 30, 40), output);
            assertTrue(maximumActive.get() > 1);
            assertTrue(maximumActive.get() <= 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reportsQueueSaturationAsAFailedCaseInsteadOfGrowingWorkUnboundedly() throws Exception {
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        FutureTask<List<String>> run = new FutureTask<>(() -> BoundedEvaluationRunner.run(
                List.of("slow", "queued", "rejected"), executor, value -> {
                    if (value.equals("slow")) {
                        firstStarted.countDown();
                        try { releaseFirst.await(); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    }
                    return value;
                }, (value, reason) -> value + ":" + reason));
        Thread caller = new Thread(run, "bounded-runner-test-caller");
        caller.start();
        try {
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertEquals(List.of("slow", "queued", "rejected:EVALUATION_CAPACITY_REACHED"),
                    run.get(2, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            caller.join(2_000);
        }
    }
}
