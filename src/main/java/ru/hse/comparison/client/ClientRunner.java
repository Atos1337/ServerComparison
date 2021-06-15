package ru.hse.comparison.client;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class
ClientRunner {
    private static final int N = 20;

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        List<Future<Void>> futures = threadPool.invokeAll(
                IntStream.range(0, N).mapToObj(Client::new).collect(Collectors.toList())
        );
        for (Future<Void> future : futures) {
            future.get();
        }
        threadPool.shutdown();
    }
}

