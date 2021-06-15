package ru.hse.comparison.server.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.hse.comparison.Constants;
import ru.hse.comparison.protos.IntArray;
import ru.hse.comparison.server.Server;
import ru.hse.comparison.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.nio.channels.SelectionKey;

public class NonBlockingServer implements Server {
    private Selector readerSelector;
    private Selector writerSelector;

    private final ExecutorService workerThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 2);
    private final ExecutorService serverSocketService = Executors.newSingleThreadExecutor();
    private final ExecutorService readerService = Executors.newSingleThreadExecutor();
    private final ExecutorService writerService = Executors.newSingleThreadExecutor();

    private volatile boolean isWorking = false;

    private final ConcurrentLinkedQueue<ClientData> newClients = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ClientData> clientsReadyWrite = new ConcurrentLinkedQueue<>();

    private final ConcurrentHashMap.KeySetView<NonBlockingServer.ClientData, Boolean> clients = ConcurrentHashMap.newKeySet();

    @Override
    public void start() throws IOException {
        isWorking = true;
        readerSelector = Selector.open();
        writerSelector = Selector.open();
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(Constants.PORT));
        serverSocketService.submit(new ClientAcceptor(serverSocket));
        readerService.submit(new MessageReader());
        writerService.submit(new MessageWriter());

        while (!Thread.interrupted());

        isWorking = false;
        readerService.shutdown();
        writerService.shutdown();
        readerSelector.close();
        writerSelector.close();
        serverSocket.close();
        workerThreadPool.shutdown();
        serverSocketService.shutdown();
        clients.forEach(ClientData::close);
    }

    private class MessageReader implements Runnable {
        @Override
        public void run() {
            while (isWorking) {
                try {
                    readerSelector.select();
                    while(!newClients.isEmpty()) {
                        ClientData clientData = newClients.poll();
                        clientData.getSocketChannel().register(readerSelector, SelectionKey.OP_READ, clientData);
                    }
                    Set<SelectionKey> selectionKeys = readerSelector.selectedKeys();
                    Iterator<SelectionKey> it = selectionKeys.iterator();
                    while (it.hasNext()) {
                        SelectionKey selectionKey = it.next();
                        if (selectionKey.isReadable()) {
                            ClientData clientData = (ClientData) selectionKey.attachment();
                            clientData.processRead();
                            it.remove();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class MessageWriter implements Runnable {
        @Override
        public void run() {
            while (isWorking) {
                try {
                    writerSelector.select();
                    while(!clientsReadyWrite.isEmpty()) {
                        ClientData clientData = clientsReadyWrite.poll();
                        clientData.getSocketChannel().register(writerSelector, SelectionKey.OP_WRITE, clientData);
                    }
                    Set<SelectionKey> selectionKeys = writerSelector.selectedKeys();
                    Iterator<SelectionKey> it = selectionKeys.iterator();
                    while (it.hasNext()) {
                        SelectionKey selectionKey = it.next();
                        if (selectionKey.isWritable()) {
                            ClientData clientData = (ClientData) selectionKey.attachment();
                            if (clientData.processWrite()) {
                                clientData.getSocketChannel().keyFor(writerSelector).interestOps(0);
                            }
                            it.remove();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class ClientAcceptor implements Runnable {

        private final ServerSocketChannel serverSocketChannel;

        private ClientAcceptor(ServerSocketChannel serverSocketChannel) {
            this.serverSocketChannel = serverSocketChannel;
        }

        @Override
        public void run() {
            try (ServerSocketChannel ignored = serverSocketChannel) {
                while (isWorking) {
                    try {
                        SocketChannel socketChannel = serverSocketChannel.accept();
                        socketChannel.configureBlocking(false);
                        ClientData clientData = new ClientData(socketChannel);
                        clients.add(clientData);
                        newClients.add(clientData);
                        readerSelector.wakeup();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ClientData {
        private final SocketChannel socketChannel;

        private final ByteBuffer sizeBuf = ByteBuffer.allocate(Integer.BYTES);
        private ByteBuffer dataBuf = null;

        private final ConcurrentLinkedQueue<ByteBuffer> buffersReadyToWrite = new ConcurrentLinkedQueue<>();
        private ByteBuffer currentWriteBuffer;

        private int currentMessageSize = 0;
        private int currentDataBufSize = 0;
        private int currentSizeBufSize = 0;

        private ClientData(SocketChannel socketChannel) {
            this.socketChannel = socketChannel;
        }

        public void processRead() throws IOException {
            if (dataBuf != null) {
                currentDataBufSize += socketChannel.read(dataBuf);
                if (currentDataBufSize == currentMessageSize) {
                    workerThreadPool.submit(new Worker(this, dataBuf));
                    dataBuf = null;
                    currentDataBufSize = 0;
                    currentMessageSize = 0;
                }
            } else {
                currentSizeBufSize += socketChannel.read(sizeBuf);
                if (currentSizeBufSize == Integer.BYTES) {
                    sizeBuf.flip();
                    currentMessageSize = sizeBuf.getInt();
                    sizeBuf.clear();
                    currentSizeBufSize = 0;
                    dataBuf = ByteBuffer.allocate(currentMessageSize);
                }
            }
        }

        public boolean processWrite() throws IOException {
            if (currentWriteBuffer == null || currentWriteBuffer.position() == currentWriteBuffer.capacity()) {
                currentWriteBuffer = buffersReadyToWrite.poll();
            }
            socketChannel.write(currentWriteBuffer);
            return buffersReadyToWrite.isEmpty();
        }

        public void addNewBuffer(ByteBuffer buf) {
            buffersReadyToWrite.add(buf);
        }

        public SocketChannel getSocketChannel() {
            return socketChannel;
        }

        public void close() {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class Worker implements Runnable {
        private final ClientData clientData;
        private final ByteBuffer dataBuf;

        public Worker(ClientData clientData, ByteBuffer dataBuf) {
            this.clientData = clientData;
            this.dataBuf = dataBuf;
        }

        @Override
        public void run() {
            ArrayList<Integer> array;
            try {
                array = new ArrayList<>(IntArray.parseFrom(dataBuf.array()).getElemList());
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
            List<Integer> sortArray = Utils.sortArray(array);
            byte[] resultMessage = IntArray.newBuilder().setSize(sortArray.size()).addAllElem(sortArray).build().toByteArray();
            byte[] sizeBuf = ByteBuffer.allocate(Integer.BYTES).putInt(resultMessage.length).array();
            ByteBuffer result = ByteBuffer.allocate(sizeBuf.length + resultMessage.length).put(sizeBuf).put(resultMessage);
            result.flip();
            clientData.addNewBuffer(result);
            clientsReadyWrite.add(clientData);
            writerSelector.wakeup();
        }
    }
}
