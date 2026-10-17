package com.aaron.evalops.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Executes case work concurrently without creating an unbounded task queue. */
final class BoundedEvaluationRunner {
    private BoundedEvaluationRunner() { }

    static <I, O> List<O> run(List<I> inputs, Executor executor, Function<I, O> evaluate,
                              BiFunction<I, String, O> failedCase) {
        List<CompletableFuture<O>> futures = new ArrayList<>(inputs.size());
        for (I input : inputs) {
            CompletableFuture<O> future = new CompletableFuture<>();
            try {
                executor.execute(() -> {
                    try {
                        future.complete(evaluate.apply(input));
                    } catch (RuntimeException failure) {
                        future.complete(failedCase.apply(input, "EVALUATION_FAILED"));
                    }
                });
            } catch (RejectedExecutionException saturated) {
                future.complete(failedCase.apply(input, "EVALUATION_CAPACITY_REACHED"));
            }
            futures.add(future);
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }
}
