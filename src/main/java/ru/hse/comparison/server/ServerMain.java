package ru.hse.comparison.server;

import ru.hse.comparison.server.impl.BlockingServer;
import ru.hse.comparison.server.impl.NonBlockingServer;

import java.io.IOException;
import java.util.Scanner;

public class ServerMain {
    public static void main(String[] args) {
        Thread thread = new Thread(() -> {
            try {
                new NonBlockingServer().start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String line = scanner.nextLine();
            if (line.equals("exit")) {
                thread.interrupt();
                break;
            }
        }
    }
}

