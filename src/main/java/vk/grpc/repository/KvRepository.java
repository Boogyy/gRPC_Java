package vk.grpc.repository;
import vk.grpc.model.KvRecord;
import java.util.Optional;
import java.util.stream.Stream;


public interface KvRepository {
    Optional<KvRecord> get(String key);
    void put(KvRecord kvRecord);
    boolean delete(String key);
    Stream<KvRecord> range(String from, String to);
    long count();
}
