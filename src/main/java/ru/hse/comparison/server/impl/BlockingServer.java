package ru.hse.comparison.server.impl;

import ru.hse.comparison.Constants;
import ru.hse.comparison.server.Server;
import ru.hse.comparison.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockingServer implements Server {
    private final ExecutorService serverSocketService = Executors.newSingleThreadExecutor();
    private final ExecutorService workerThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 2);

    private volatile boolean isWorking = true;

    private final ConcurrentHashMap.KeySetView<ClientData, Boolean> clients = ConcurrentHashMap.newKeySet();

    @Override
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(Constants.PORT);
        serverSocketService.submit(() -> acceptClients(serverSocket));

        while (!Thread.interrupted());

        isWorking = false;
        serverSocket.close();
        workerThreadPool.shutdown();
        serverSocketService.shutdown();
        clients.forEach(ClientData::close);
    }

    private void acceptClients(ServerSocket serverSocket) {
        try (ServerSocket ignored = serverSocket) {
            while (isWorking) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("client accepted");
                    ClientData clientData = new ClientData(socket);
                    clients.add(clientData);
                    clientData.processClient();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientData {
        private final Socket socket;
        public final ExecutorService responseWriter = Executors.newSingleThreadExecutor();
        public final ExecutorService requestReader = Executors.newSingleThreadExecutor();

        private final InputStream inputStream;
        private final OutputStream outputStream;

        private volatile boolean working = true;

        private ClientData(Socket socket) throws IOException {
            this.socket = socket;
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
        }

        public void sendResponse(List<Integer> data) {
            responseWriter.submit(() -> {
                try {
                    Utils.writeArray(outputStream, data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        public void processClient() {
            requestReader.submit(() -> {
                try {
                    while(working) {
                        ArrayList<Integer> data = Utils.readArray(inputStream);
                        workerThreadPool.submit(() -> sendResponse(Utils.sortArray(data)));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        public void close() {
            working = false;
            responseWriter.shutdown();
            requestReader.shutdown();
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
