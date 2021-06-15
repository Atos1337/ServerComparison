package ru.hse.comparison.statistics;

public class Statistics {
    private long sumTime = 0;
    private long numberRequests = 0;

    public void add(long result) {
        sumTime += result;
        numberRequests++;
    }

    public long average() {
        System.out.println(sumTime);
        System.out.println(numberRequests);
        return sumTime / numberRequests;
    }
}
