package vk.grpc.tools;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import vk.grpc.proto.*;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class GrpcVerificationRunner {

    public static void main(String[] args) throws Exception {
        String grpcHost = System.getProperty("grpc.host", "localhost");
        int grpcPort = Integer.parseInt(System.getProperty("grpc.port", "9091"));
        long maxKey = Long.parseLong(System.getProperty("maxKey", "5000000"));
        int randomGets = Integer.parseInt(System.getProperty("randomGets", "1000"));

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(grpcHost, grpcPort)
                .usePlaintext()
                .build();

        KvServiceGrpc.KvServiceBlockingStub stub = KvServiceGrpc.newBlockingStub(channel);

        try {
            System.out.println(" COUNT ");
            CountResponse countResponse = stub.count(CountRequest.newBuilder().build());
            System.out.println("count = " + countResponse.getCount());

            System.out.println("\n GET missing ");
            GetResponse missing = stub.get(GetRequest.newBuilder()
                    .setKey("missing-key")
                    .build());
            System.out.println("found = " + missing.getFound());

            System.out.println("\n GET existing non-null ");
            GetResponse existing = stub.get(GetRequest.newBuilder()
                    .setKey(formatKey(1))
                    .build());
            System.out.println("found = " + existing.getFound());
            System.out.println("hasValue = " + existing.hasValue());
            if (existing.hasValue()) {
                System.out.println("value bytes size = " + existing.getValue().size());
            }

            System.out.println("\n GET existing null ");
            GetResponse nullValue = stub.get(GetRequest.newBuilder()
                    .setKey(formatKey(0)) // по seeder это null
                    .build());
            System.out.println("found = " + nullValue.getFound());
            System.out.println("hasValue = " + nullValue.hasValue());

            System.out.println("\n RANGE small interval ");
            String from = formatKey(1000);
            String to = formatKey(1999);

            Iterator<RangeItem> iterator = stub.range(
                    RangeRequest.newBuilder()
                            .setKeySince(from)
                            .setKeyTo(to)
                            .build()
            );

            int rangeCount = 0;
            int nullCount = 0;

            while (iterator.hasNext()) {
                RangeItem item = iterator.next();
                rangeCount++;
                if (!item.hasValue()) {
                    nullCount++;
                }
            }

            System.out.println("range [" + from + ", " + to + "] count = " + rangeCount);
            System.out.println("range null values = " + nullCount);


            System.out.println("\n DELETE check ");
            String tempKey = "temp-delete-check";
            stub.put(PutRequest.newBuilder()
                    .setKey(tempKey)
                    .setValue(com.google.protobuf.ByteString.copyFromUtf8("x"))
                    .build());

            DeleteResponse deleteResponse = stub.delete(DeleteRequest.newBuilder()
                    .setKey(tempKey)
                    .build());
            System.out.println("deleted = " + deleteResponse.getDeleted());

            GetResponse afterDelete = stub.get(GetRequest.newBuilder()
                    .setKey(tempKey)
                    .build());
            System.out.println("found after delete = " + afterDelete.getFound());

        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static String formatKey(long i) {
        return String.format("key-%07d", i);
    }
}