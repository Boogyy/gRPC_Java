package vk.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import vk.grpc.grpc.KvGrpcService;
import vk.grpc.repository.TarantoolKvRepository;

public class Application {

    public static void main(String[] args) throws Exception {
        String tarantoolHost = System.getProperty("tarantool.host", "localhost");
        int tarantoolPort = Integer.parseInt(System.getProperty("tarantool.port", "3303"));
        String tarantoolUser = System.getProperty("tarantool.user", "app");
        String tarantoolPassword = System.getProperty("tarantool.password", "app");
        int grpcPort = Integer.parseInt(System.getProperty("grpc.port", "9091"));

        TarantoolKvRepository repository = new TarantoolKvRepository(
                tarantoolHost,
                tarantoolPort,
                tarantoolUser,
                tarantoolPassword
        );

        KvGrpcService kvGrpcService = new KvGrpcService(repository);

        Server server = ServerBuilder.forPort(grpcPort)
                .addService(kvGrpcService)
                .build()
                .start();

        System.out.println("gRPC server started on port " + grpcPort);
        System.out.println("Connected to Tarantool at " + tarantoolHost + ":" + tarantoolPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            server.shutdown();
            try {
                repository.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        server.awaitTermination();
    }
}