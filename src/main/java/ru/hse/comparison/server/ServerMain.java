package ru.hse.comparison.server;

import ru.hse.comparison.server.impl.BlockingServer;
import ru.hse.comparison.server.impl.NonBlockingServer;

import java.io.IOException;

public class ServerMain {
    public static void main(String[] args) throws IOException {
        new BlockingServer().start();
    }
}

