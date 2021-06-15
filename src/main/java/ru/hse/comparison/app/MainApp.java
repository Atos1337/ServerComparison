package ru.hse.comparison.app;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import ru.hse.comparison.Constants;
import ru.hse.comparison.client.ClientRunner;
import ru.hse.comparison.server.Server;
import ru.hse.comparison.server.impl.BlockingServer;
import ru.hse.comparison.server.impl.NonBlockingServer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

public class MainApp {
    public enum ServerType {
        BLOCKING,
        NON_BLOCKING
    }

    public enum Criteria {
        ARRAY_SIZE,
        CLIENT_NUMBER,
        SENDING_DELTA
    }

    private static EnumMap<ServerType, Server> servers = new EnumMap<>(ServerType.class);

    static {
        servers.put(ServerType.BLOCKING, new BlockingServer());
        servers.put(ServerType.NON_BLOCKING, new NonBlockingServer());
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        Options options = new Options();
        options.addOption("blocking", false, "isBlockArchitecture");
        options.addOption("nonblocking", false, "isNonBlockArchitecture");
        options.addOption("requests", true, "number of requests(X)");
        options.addOption("size", true, "test if arraySize is criteria else value of it(N)");
        options.addOption("clients", true, "test if clientNumber is criteria else value of it(M)");
        options.addOption("delta", true, "test if sendingDelta is criteria else value of it(D)");
        options.addOption("start", true, "lowerBoundValue");
        options.addOption("end", true, "upperBoundValue");
        options.addOption("step", true, "step of criteria value");
        options.addOption("resdir", true, "result directory, current directory is default");

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "server-comparison", options );

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            e.printStackTrace();
            System.out.println("Something went wrong! Please, try again");
            return;
        }
        Server server = null;
        ServerType serverType = ServerType.BLOCKING;
        if (cmd.hasOption("blocking")) {
            server = servers.get(ServerType.BLOCKING);
            serverType = ServerType.BLOCKING;
        } else if (cmd.hasOption("nonblocking")) {
            server = servers.get(ServerType.NON_BLOCKING);
            serverType = ServerType.NON_BLOCKING;
        }
        Criteria criteria = Criteria.ARRAY_SIZE;
        int arraySize = 0;
        int clientNumber = 0;
        int sendingDelta = 0;
        int requests = Integer.parseInt(cmd.getOptionValue("requests"));
        if (cmd.getOptionValue("size").equals("test")) {
            criteria = Criteria.ARRAY_SIZE;
        } else {
            arraySize = Integer.parseInt(cmd.getOptionValue("size"));
        }
        if (cmd.getOptionValue("clients").equals("test")) {
            criteria = Criteria.CLIENT_NUMBER;
        } else {
            clientNumber = Integer.parseInt(cmd.getOptionValue("clients"));
        }
        if (cmd.getOptionValue("delta").equals("test")) {
            criteria = Criteria.SENDING_DELTA;
        } else {
            sendingDelta = Integer.parseInt(cmd.getOptionValue("delta"));
        }
        int lowerBound = Integer.parseInt(cmd.getOptionValue("start"));
        int upperBound = Integer.parseInt(cmd.getOptionValue("end"));
        int step = Integer.parseInt(cmd.getOptionValue("step"));
        String resultDirectory = ".";
        if (cmd.hasOption("resdir")) {
            resultDirectory = cmd.getOptionValue("resdir");
        }
        Map<Integer, Long> results = new TreeMap<>();
        Server finalServer = server;
        Thread serverThread = new Thread(() -> {
            assert finalServer != null;
            try {
                finalServer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
        Thread.sleep(Constants.SLEEP);
        for (int i = lowerBound; i <= upperBound; i += step) {
            switch (criteria) {
                case CLIENT_NUMBER:
                    results.put(i, new ClientRunner(arraySize, requests, i, sendingDelta).run().average());
                    break;
                case ARRAY_SIZE:
                    results.put(i, new ClientRunner(i, requests, clientNumber, sendingDelta).run().average());
                    break;
                case SENDING_DELTA:
                    results.put(i, new ClientRunner(arraySize, requests, clientNumber, i).run().average());
                    break;
            }
        }
        Path dir = Files.createDirectories(Paths.get(resultDirectory));
        String[] HEADERS = new String[2];
        try (PrintStream description = new PrintStream(Files.newOutputStream(dir.resolve("description.txt")))) {
            description.println("In results.csv first column is value of criteria, second is average time of handle request on client side");
            switch (serverType) {
                case BLOCKING:
                    description.println("Architecture = Blocking");
                    HEADERS[1] = "Blocking";
                    break;
                case NON_BLOCKING:
                    description.println("Architecture = NonBlocking");
                    HEADERS[1] = "NonBlocking";
                    break;
            }
            description.println("Number of Requests = " + requests);
            switch (criteria) {
                case ARRAY_SIZE:
                    description.println("Size of Array is testing criteria");
                    description.println("Number of Clients = " + clientNumber);
                    description.println("Delta of Sending = " + sendingDelta);
                    HEADERS[0] = "ArraySize";
                    break;
                case CLIENT_NUMBER:
                    description.println("Size of Array = " + arraySize);
                    description.println("Number of Clients is testing criteria");
                    description.println("Delta of Sending = " + sendingDelta);
                    HEADERS[0] = "ClientNumber";
                    break;
                case SENDING_DELTA:
                    description.println("Size of Array = " + arraySize);
                    description.println("Number of Clients = " + clientNumber);
                    description.println("Delta of Sending is testing criteria");
                    HEADERS[0] = "SendingDelta";
                    break;
            }
        }
        FileWriter out = new FileWriter(Paths.get(dir.toString(), "results.csv").toString());
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT
                .withHeader(HEADERS))) {
            results.forEach((criteriaValue, averageTime) -> {
                try {
                    printer.printRecord(criteriaValue, averageTime);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        serverThread.interrupt();
    }
}
