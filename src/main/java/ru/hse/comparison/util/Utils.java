package ru.hse.comparison.util;

import ru.hse.comparison.protos.IntArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Utils {
    public static ArrayList<Integer> readArray(InputStream inputStream) throws IOException {
        byte[] sizeBuf = inputStream.readNBytes(Integer.BYTES);
        int size = ByteBuffer.wrap(sizeBuf).getInt();
        byte[] dataBuf = inputStream.readNBytes(size);
        return new ArrayList<>(IntArray.parseFrom(dataBuf).getElemList());
    }

    public static void writeArray(OutputStream outputStream, List<Integer> data) throws IOException {
        byte[] dataBuf = IntArray.newBuilder().setSize(data.size()).addAllElem(data).build().toByteArray();
        byte[] sizeBuf = ByteBuffer.allocate(Integer.BYTES).putInt(dataBuf.length).array();
        outputStream.write(sizeBuf);
        outputStream.write(dataBuf);
    }

    public static List<Integer> sortArray(ArrayList<Integer> data) {
        int[] arr = data.stream().mapToInt(Integer::intValue).toArray();
        for (int i = 1; i < arr.length; ++i) {
            for (int j = i; j > 0; --j) {
                if (arr[j] < arr[j - 1]) {
                    int tmp = arr[j];
                    arr[j] = arr[j - 1];
                    arr[j - 1] = tmp;
                }
            }
        }
        return Arrays.stream(arr).boxed().collect(Collectors.toList());
    }

    private Utils() {}
}

