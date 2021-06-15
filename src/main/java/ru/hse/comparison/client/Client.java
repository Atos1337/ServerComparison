package ru.hse.comparison.client;

import ru.hse.comparison.Constants;
import ru.hse.comparison.util.Utils;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Client implements Callable<Void> {
    private final int id;

    public Client(int id) {
        this.id = id;
    }

    @Override
    public Void call() throws Exception {
        List<Integer> data = generateArray();
        try(Socket socket = new Socket(Constants.HOST, Constants.PORT)) {
            var inputStream = socket.getInputStream();
            var outputStream = socket.getOutputStream();
            Utils.writeArray(outputStream, data);
            List<Integer> sortedData = Utils.readArray(inputStream);
            checkData(data, sortedData);
        }
        return null;
    }

    private List<Integer> generateArray() {
        Random r = new Random();
        return IntStream.generate(r::nextInt).limit(Constants.SIZE).boxed().collect(Collectors.toList());
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
            System.out.println("Client " + id + " is OK");
        } else {
            System.out.println("Client " + id + " fails");
        }
    }
}
