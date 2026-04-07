package vk.grpc.tools;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import vk.grpc.proto.KvServiceGrpc;
import vk.grpc.proto.RangeItem;
import vk.grpc.proto.RangeRequest;

import java.util.Iterator;
import java.util.Locale;

public class LargeRangeRunner {

    public static void main(String[] args) throws Exception {
        String grpcHost = System.getProperty("grpc.host", "localhost");
        int grpcPort = Integer.parseInt(System.getProperty("grpc.port", "9091"));

        long fromIndex = Long.parseLong(System.getProperty("fromIndex", "0"));
        long toIndex = Long.parseLong(System.getProperty("toIndex", "99999"));

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(grpcHost, grpcPort)
                .usePlaintext()
                .build();

        KvServiceGrpc.KvServiceBlockingStub stub = KvServiceGrpc.newBlockingStub(channel);

        String fromKey = formatKey(fromIndex);
        String toKey = formatKey(toIndex);

        long startedAt = System.nanoTime();

        Iterator<RangeItem> iterator = stub.range(
                RangeRequest.newBuilder()
                        .setKeySince(fromKey)
                        .setKeyTo(toKey)
                        .build()
        );

        long count = 0;
        long nullCount = 0;
        String firstKey = null;
        String lastKey = null;

        while (iterator.hasNext()) {
            RangeItem item = iterator.next();

            if (count == 0) {
                firstKey = item.getKey();
            }
            lastKey = item.getKey();
            count++;
        }

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        double rate = count / Math.max(1.0, elapsedMs / 1000.0);

        long expected = toIndex >= fromIndex ? (toIndex - fromIndex + 1) : 0;

        System.out.println(" LARGE RANGE ");
        System.out.println("fromKey = " + fromKey);
        System.out.println("toKey = " + toKey);
        System.out.println("expected = " + expected);
        System.out.println("actual = " + count);
        System.out.println("firstKey = " + firstKey);
        System.out.println("lastKey = " + lastKey);
        System.out.printf(Locale.US, "rate = %.2f items/s%n", rate);

        channel.shutdownNow();
    }

    private static String formatKey(long i) {
        return String.format("key-%07d", i);
    }
}