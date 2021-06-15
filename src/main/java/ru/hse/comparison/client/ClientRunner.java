package ru.hse.comparison.client;

import ru.hse.comparison.Constants;
import ru.hse.comparison.statistics.Statistics;
import ru.hse.comparison.util.Utils;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ClientRunner {
    private final int sizeArray;
    private final int numberRequests;
    private final int numberClients;
    private final int deltaSending;

    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    private final long[][] sendingTime;
    private final Statistics statistics = new Statistics();
    private volatile boolean anyFinished = false;

    public ClientRunner(int sizeArray, int numberRequests, int numberClients, int deltaSending) {
        this.sizeArray = sizeArray;
        this.numberRequests = numberRequests;
        this.numberClients = numberClients;
        this.deltaSending = deltaSending;
        sendingTime = new long[numberClients][numberRequests];
    }

    public Statistics run() throws ExecutionException, InterruptedException {
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < numberClients; ++i) {
            //System.out.println("Client accepted");
            futures.add(clientPool.submit(new Client(i)));
        }
        for (Future<Void> f : futures) {
            f.get();
        }
        clientPool.shutdown();
        return statistics;
    }

    private class Client implements Callable<Void> {
        private final int id;
        private final ExecutorService writeService = Executors.newSingleThreadExecutor();

        private Client(int id) {
            this.id = id;
        }

        @Override
        public Void call() throws Exception {
            try (Socket socket = new Socket(Constants.HOST, Constants.PORT)) {
                List<Future<Void>> futures = new ArrayList<>();
                for (int i = 0; i < numberRequests; ++i) {
                    List<Integer> data = generateArray();
                    sendingTime[id][i] = System.currentTimeMillis();
                    Utils.writeArray(socket.getOutputStream(), data);
                    int finalI = i;
                    futures.add(writeService.submit(() -> {
                        List<Integer> result = Utils.readArray(socket.getInputStream());
                        if (!anyFinished) {
                            statistics.add(System.currentTimeMillis() - sendingTime[id][finalI]);
                        }
                        //checkData(data, result);
                        return null;
                    }));
                    Thread.sleep(deltaSending);
                }
                for (Future<?> future : futures) {
                    future.get();
                }
                writeService.shutdown();
            } finally {
                anyFinished = true;
            }
            return null;
        }
    }

    private List<Integer> generateArray() {
        Random r = new Random();
        return IntStream.generate(r::nextInt).limit(sizeArray).boxed().collect(Collectors.toList());
    }

    private void checkData(List<Integer> data, List<Integer> sortedData) {
        boolean isOk = data.size() == sortedData.size();
        for (int i = 1; i < sortedData.size(); i++) {
            if (sortedData.get(i - 1) > sortedData.get(i)) {
                isOk = false;
                break;
            }
        }
        if (isOk) {
            System.out.println("Client is OK");
        } else {
            System.out.println("Client fails");
        }
    }
}

