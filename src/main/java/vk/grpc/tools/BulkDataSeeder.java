package vk.grpc.tools;

import vk.grpc.model.KvRecord;
import vk.grpc.repository.TarantoolKvRepository;

import java.nio.charset.StandardCharsets;

public class BulkDataSeeder {

    public static void main(String[] args) throws Exception {
        String tarantoolHost = System.getProperty("tarantool.host", "localhost");
        int tarantoolPort = Integer.parseInt(System.getProperty("tarantool.port", "3303"));
        String tarantoolUser = System.getProperty("tarantool.user", "app");
        String tarantoolPassword = System.getProperty("tarantool.password", "app");

        long total = Long.parseLong(System.getProperty("total", "5000000"));
        long startIndex = Long.parseLong(System.getProperty("startIndex", "0"));
        long progressEvery = Long.parseLong(System.getProperty("progressEvery", "100000"));

        try (TarantoolKvRepository repository = new TarantoolKvRepository(
                tarantoolHost,
                tarantoolPort,
                tarantoolUser,
                tarantoolPassword
        )) {
            long startedAt = System.nanoTime();

            for (long i = startIndex; i < startIndex + total; i++) {
                KvRecord record = new KvRecord();
                record.setKey(formatKey(i));

                // Раз в 1000 записей - null, чтобы проверить nullable value
                if (i % 1000 == 0) {
                    record.setValue(null);
                } else {
                    record.setValue(("value-" + i).getBytes(StandardCharsets.UTF_8));
                }

                repository.put(record);

                long inserted = i - startIndex + 1;
                if (inserted % progressEvery == 0) {
                    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                    double rate = inserted / Math.max(1.0, elapsedMs / 1000.0);

                    System.out.printf(
                            "Inserted %,d / %,d records, rate = %,.2f rec/s, count() = %,d%n",
                            inserted,
                            total,
                            rate,
                            repository.count()
                    );
                }
            }

            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            double rate = total / Math.max(1.0, elapsedMs / 1000.0);

            System.out.printf(
                    "DONE. Inserted %,d records in %,d ms, avg rate = %,.2f rec/s, final count = %,d%n",
                    total,
                    elapsedMs,
                    rate,
                    repository.count()
            );
        }
    }

    private static String formatKey(long i) {
        return String.format("key-%07d", i);
    }
}