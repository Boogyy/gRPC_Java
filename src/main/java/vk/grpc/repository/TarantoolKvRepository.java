package vk.grpc.repository;

import io.tarantool.client.TarantoolClient;
import io.tarantool.client.factory.TarantoolFactory;
import io.tarantool.mapping.TarantoolResponse;
import vk.grpc.model.KvRecord;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TarantoolKvRepository implements KvRepository, AutoCloseable {

    private static final int RANGE_PAGE_SIZE = 2000;
    private final TarantoolClient client;

    public TarantoolKvRepository(String host, int port, String user, String password) {
        try {
            this.client = TarantoolFactory.box()
                    .withHost(host)
                    .withPort(port)
                    .withUser(user)
                    .withPassword(password)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Tarantool client", e);
        }
    }

    @Override
    public Optional<KvRecord> get(String key) {
        try {
            TarantoolResponse<List<List>> response =
                    client.call("kv_get", Collections.singletonList(key), List.class).get();

            List<List> data = response.get();

            if (data == null || data.isEmpty() || data.get(0) == null) {
                return Optional.empty();
            }

            List<?> tuple = data.get(0);

            if (tuple.size() < 2) {
                throw new IllegalStateException("Invalid tuple returned from kv_get: " + tuple);
            }

            KvRecord record = new KvRecord();
            record.setKey((String) tuple.get(0));
            record.setValue(toBytes(tuple.get(1)));

            return Optional.of(record);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling kv_get", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Tarantool call kv_get failed", e.getCause());
        }
    }

    @Override
    public void put(KvRecord kvRecord) {
        try {
            client.call(
                    "kv_put",
                    java.util.Arrays.asList(kvRecord.getKey(), kvRecord.getValue())
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling kv_put", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                cause.printStackTrace();
                throw new RuntimeException(
                        "Tarantool call kv_put failed: " + cause.getClass().getSimpleName() +
                                (cause.getMessage() != null ? " - " + cause.getMessage() : ""),
                        cause
                );
            }
            throw new RuntimeException("Tarantool call kv_put failed", e);
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            TarantoolResponse<List<Boolean>> response =
                    client.call("kv_delete", Collections.singletonList(key), Boolean.class).get();

            List<Boolean> data = response.get();

            return data != null && !data.isEmpty() && Boolean.TRUE.equals(data.get(0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling kv_delete", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Tarantool call kv_delete failed", e.getCause());
        }
    }


    @Override
    public long count() {
        try {
            TarantoolResponse<List<Number>> response = client.call
                    ("kv_count", Number.class).get();

            List<Number> data = response.get();

            if (data == null || data.isEmpty() || data.get(0) == null) {
                return 0L;
            }



            return data.get(0).longValue();


        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling kv_count", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Tarantool call kv_count failed", e.getCause());
        }
    }

    @Override
    public Stream<KvRecord> range(String from, String to) {
        Iterator<KvRecord> iterator = new RangeIterator(from, to);

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false
        );
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    private final class RangeIterator implements Iterator<KvRecord> {
        private final String from;
        private final String to;

        private List<KvRecord> currentPage = Collections.emptyList();
        private int currentIndex = 0;
        private Object after = "";
        private boolean finished = false;
        private boolean firstLoad = true;

        private RangeIterator(String from, String to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean hasNext() {
            loadIfNeeded();
            return currentIndex < currentPage.size();
        }

        @Override
        public KvRecord next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return currentPage.get(currentIndex++);
        }

        @SuppressWarnings("unchecked")
        private void loadIfNeeded() {
            while (!finished && (firstLoad || currentIndex >= currentPage.size())) {
                firstLoad = false;

                try {
                    TarantoolResponse<List<List>> response =
                            client.call(
                                    "kv_range_page",
                                    Arrays.asList(from, to, RANGE_PAGE_SIZE, after),
                                    List.class
                            ).get();

                    List<List> data = response.get();

                    if (data == null || data.isEmpty() || data.get(0) == null) {
                        currentPage = Collections.emptyList();
                        finished = true;
                        return;
                    }

                    List<?> page = data.get(0);

                    if (page.size() < 2) {
                        throw new IllegalStateException("Invalid response from kv_range_page: " + page);
                    }

                    List<?> items = (List<?>) page.get(0);
                    Object nextPos = page.get(1);

                    List<KvRecord> records = new ArrayList<>();

                    for (Object itemObj : items) {
                        List<?> tuple = (List<?>) itemObj;

                        if (tuple.size() < 2) {
                            throw new IllegalStateException("Invalid tuple returned from kv_range_page: " + tuple);
                        }

                        KvRecord record = new KvRecord();
                        record.setKey((String) tuple.get(0));
                        record.setValue(toBytes(tuple.get(1)));
                        records.add(record);
                    }

                    currentPage = records;
                    currentIndex = 0;

                    if (nextPos == null || records.isEmpty()) {
                        finished = true;
                    } else {
                        after = nextPos;
                    }

                    if (records.isEmpty()) {
                        finished = true;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while calling kv_range_page", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException("Tarantool call kv_range_page failed", e.getCause());
                }
            }
        }
    }

    private byte[] toBytes(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof byte[] bytes) {
            return bytes;
        }

        if (rawValue instanceof List<?> list) {
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                bytes[i] = ((Number) list.get(i)).byteValue();
            }
            return bytes;
        }

        throw new IllegalStateException("Unsupported binary value type: " + rawValue.getClass());
    }
}