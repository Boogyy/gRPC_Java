package vk.grpc.grpc;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import vk.grpc.model.KvRecord;
import vk.grpc.proto.*;
import vk.grpc.repository.KvRepository;

import java.util.Optional;
import java.util.stream.Stream;

public class KvGrpcService extends KvServiceGrpc.KvServiceImplBase {
    private final KvRepository repository;

    public KvGrpcService (KvRepository repository) {
        this.repository = repository;
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        try {
            String key = request.getKey();
            Optional<KvRecord> found = repository.get(key);

            GetResponse.Builder builder = GetResponse.newBuilder();

            if(found.isEmpty()) {
                builder.setFound(false);
            } else {
                KvRecord record = found.get();
                builder.setFound(true);
                if (record.getValue() != null) {
                    builder.setValue(ByteString.copyFrom(record.getValue()));
                }
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void put(PutRequest request, StreamObserver<PutResponse> responseObserver) {
        try {
            KvRecord kvRecord = new KvRecord();
            kvRecord.setKey(request.getKey());

            if (request.hasValue()) {
                kvRecord.setValue(request.getValue().toByteArray());
            } else {
                kvRecord.setValue(null);
            }

            repository.put(kvRecord);

            responseObserver.onNext(PutResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Put failed: " + e.getMessage())
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {
        try {
            String key = request.getKey();
            boolean result = repository.delete(key);

            DeleteResponse.Builder builder = DeleteResponse.newBuilder();

            builder.setDeleted(result);

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void count(CountRequest request, StreamObserver<CountResponse> responseObserver) {
        try {
            long result = repository.count();

            CountResponse.Builder builder = CountResponse.newBuilder();

            builder.setCount(result);

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void range(RangeRequest request, StreamObserver<RangeItem> responseObserver) {
        try (Stream<KvRecord> found = repository.range(request.getKeySince(),
                request.getKeyTo())) {

            found.forEach(kvRecord -> {
                RangeItem.Builder builder = RangeItem.newBuilder();

                builder.setKey(kvRecord.getKey());
                if(kvRecord.getValue() != null) {
                    builder.setValue(ByteString.copyFrom(kvRecord.getValue()));
                }

                responseObserver.onNext(builder.build());
            });

            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}