package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.output.GlobalModelArtifacts;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.factory.Nd4j;

/** CPU-only HTTP inference service for the immutable seed-123 deployment package. */
public final class ForecastServiceMain {
    private static final Gson JSON = new Gson();

    private final SingleStoreGlobalDataset data;
    private final MultiLayerNetwork network;
    private final Path deployment;
    private final String modelHash;

    private ForecastServiceMain(Path dataset, Path deployment) throws Exception {
        this.deployment = deployment;
        ExperimentConfiguration config = ExperimentConfiguration.fromArgs(
                new String[] {"--global-store=1", "--validation-batch-size=64"});
        this.data = SingleStoreGlobalDataset.load(dataset.toString(), config);
        Path model = deployment.resolve("model.zip");
        this.network = GlobalModelArtifacts.loadCheckpoint(model);
        this.modelHash = sha256(model);
    }

    public static void main(String[] args) throws Exception {
        Path dataset = Path.of(System.getenv().getOrDefault("DATASET_PATH", "/opt/data/train.csv"));
        Path deployment = Path.of(System.getenv().getOrDefault("MODEL_DIR", "/opt/model"));
        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8083"));
        ForecastServiceMain service = new ForecastServiceMain(dataset, deployment);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", service::health);
        server.createContext("/metadata", service::metadata);
        server.createContext("/forecast", service::forecast);
        server.setExecutor(Executors.newFixedThreadPool(4));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> service.network.close()));
        server.start();
        System.out.printf("FORECAST_SERVICE_READY port=%d backend=%s modelSha256=%s%n",
                port, Nd4j.getBackend().getClass().getName(), service.modelHash);
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        send(exchange, 200, Map.of("status", "UP", "service", "forecast-service",
                "modelLoaded", true, "seed", 123, "modelSha256", modelHash,
                "backend", Nd4j.getBackend().getClass().getName()));
    }

    private void metadata(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        JsonObject metadata = JSON.fromJson(Files.readString(deployment.resolve("model-metadata.json")), JsonObject.class);
        metadata.addProperty("modelSha256", modelHash);
        metadata.addProperty("runtimeBackend", Nd4j.getBackend().getClass().getName());
        send(exchange, 200, metadata);
    }

    private void forecast(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            JsonObject body = JSON.fromJson(reader, JsonObject.class);
            ForecastServiceRequest request = ForecastServiceRequest.of(
                    body.get("itemId").getAsInt(), body.get("originDate").getAsString());
            SingleStoreGlobalDataset.Sample sample = findSample(request.itemId, request.originDate);
            long started = System.nanoTime();
            GlobalMetrics metrics;
            synchronized (network) {
                metrics = GlobalResidualLstmForecaster.evaluate(network, List.of(sample), 1);
            }
            List<Map<String, Object>> values = new ArrayList<>();
            for (ValidationPrediction prediction : metrics.predictions.get(request.itemId)) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("horizon", prediction.getHorizon());
                value.put("targetDate", prediction.getTargetDate().toString());
                value.put("prediction", prediction.getPrediction());
                value.put("weeklyNaive", prediction.getWeeklyNaive());
                value.put("actual", prediction.getActual());
                values.add(value);
            }
            send(exchange, 200, Map.of("storeId", 1, "itemId", request.itemId,
                    "originDate", request.originDate.toString(), "seed", 123,
                    "modelSha256", modelHash, "inferenceMillis", (System.nanoTime() - started) / 1_000_000.0,
                    "forecasts", values));
        } catch (IllegalArgumentException failure) {
            send(exchange, 400, Map.of("error", "invalid_request", "message", failure.getMessage()));
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            send(exchange, 500, Map.of("error", "inference_failed", "message", failure.getMessage()));
        }
    }

    private SingleStoreGlobalDataset.Sample findSample(int itemId, LocalDate origin) {
        for (List<SingleStoreGlobalDataset.Sample> split : List.of(data.train, data.validation, data.test)) {
            for (SingleStoreGlobalDataset.Sample sample : split) {
                List<LocalDate> inputDates = sample.window.getInputDates();
                if (sample.item.id == itemId && inputDates.get(inputDates.size() - 1).equals(origin)) {
                    return sample;
                }
            }
        }
        throw new IllegalArgumentException("No valid seven-day forecast origin for item/date.");
    }

    private static void send(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] payload = JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder out = new StringBuilder();
        for (byte value : digest.digest()) {
            out.append(String.format("%02x", value));
        }
        return out.toString();
    }
}
