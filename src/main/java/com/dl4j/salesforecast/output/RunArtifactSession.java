package com.dl4j.salesforecast.output;

import com.dl4j.salesforecast.model.ErrorAnalysisRunner;
import com.dl4j.salesforecast.model.MultiSeriesExperimentRunner;
import com.dl4j.salesforecast.model.SeedMetricsExporter;
import com.dl4j.salesforecast.model.TrainingHistoryExporter;
import com.dl4j.salesforecast.model.ValidationPredictionExporter;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Owns one immutable run directory and its status manifest. */
public final class RunArtifactSession implements AutoCloseable {
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_SSS");
    private final Path directory;
    private final Path manifest;
    private final String runId;
    private final String datasetPath;
    private final String datasetSha256;
    private final Instant startedAt;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private PrintStream logStream;

    private RunArtifactSession(Path directory, String runId, String datasetPath,
                               String datasetSha256, Instant startedAt) {
        this.directory = directory;
        this.manifest = directory.resolve("run.json");
        this.runId = runId;
        this.datasetPath = datasetPath;
        this.datasetSha256 = datasetSha256;
        this.startedAt = startedAt;
    }

    public static RunArtifactSession start(Path runsRoot, Path dataset) throws IOException {
        if (runsRoot == null || dataset == null || !Files.isRegularFile(dataset)) {
            throw new IllegalArgumentException("Runs root and an existing dataset are required.");
        }
        String backend = Nd4j.getBackend().getClass().getSimpleName().toLowerCase();
        String suffix = backend.contains("cuda") || backend.contains("cublas") ? "gpu" : "cpu";
        String runId = ID_FORMAT.format(LocalDateTime.now()) + "_" + suffix;
        Path directory = runsRoot.resolve(runId);
        Files.createDirectories(directory);
        RunArtifactSession session = new RunArtifactSession(directory, runId,
                dataset.toAbsolutePath().normalize().toString(), sha256(dataset), Instant.now());
        session.writeManifest("RUNNING", null);
        Files.writeString(runsRoot.resolve("latest.txt"), runId + "\n", StandardCharsets.UTF_8);
        session.installConsoleCapture();
        return session;
    }

    public void complete(MultiSeriesExperimentRunner.MultiSeriesResult result,
                         ErrorAnalysisRunner.AnalysisResult analysis) throws IOException {
        TrainingHistoryExporter.writeCsv(result, directory.resolve("epoch-history.csv"));
        SeedMetricsExporter.writeCsv(result, directory.resolve("seed-metrics.csv"));
        ValidationPredictionExporter.writeCsv(result, directory.resolve("validation-predictions.csv"));
        String summary = MultiSeriesExperimentRunner.formatReport(result) + "\n"
                + ErrorAnalysisRunner.formatReport(analysis);
        Files.writeString(directory.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("report.html"), html(summary), StandardCharsets.UTF_8);
        writeManifest("SUCCESS", null);
    }

    public void fail(Throwable failure) {
        try { writeManifest("FAILED", failure == null ? "Unknown failure" : failure.toString()); }
        catch (IOException ignored) { }
    }

    public Path getDirectory() { return directory; }

    private void installConsoleCapture() throws IOException {
        originalOut = System.out;
        originalErr = System.err;
        logStream = new PrintStream(Files.newOutputStream(directory.resolve("console.log")),
                true, StandardCharsets.UTF_8.name());
        System.setOut(new PrintStream(new TeeOutputStream(originalOut, logStream), true,
                StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(new TeeOutputStream(originalErr, logStream), true,
                StandardCharsets.UTF_8.name()));
    }

    @Override
    public void close() {
        if (originalOut != null) { System.setOut(originalOut); }
        if (originalErr != null) { System.setErr(originalErr); }
        if (logStream != null) { logStream.close(); }
        originalOut = null;
        originalErr = null;
        logStream = null;
    }

    private void writeManifest(String status, String error) throws IOException {
        String backend = Nd4j.getBackend().getClass().getName();
        String json = "{\n"
                + "  \"runId\": \"" + escape(runId) + "\",\n"
                + "  \"status\": \"" + status + "\",\n"
                + "  \"startedAt\": \"" + startedAt + "\",\n"
                + "  \"updatedAt\": \"" + Instant.now() + "\",\n"
                + "  \"javaVersion\": \"" + escape(System.getProperty("java.version")) + "\",\n"
                + "  \"backend\": \"" + escape(backend) + "\",\n"
                + "  \"datasetPath\": \"" + escape(datasetPath) + "\",\n"
                + "  \"datasetSha256\": \"" + datasetSha256 + "\",\n"
                + "  \"series\": 12,\n  \"seeds\": [42, 123, 2026, 7, 99],\n"
                + "  \"models\": [\"Direct\", \"AR\"],\n  \"maxEpochs\": 30,\n"
                + "  \"selectionSplit\": \"validation-2016\",\n"
                + "  \"testUsedForSelection\": false"
                + (error == null ? "\n" : ",\n  \"error\": \"" + escape(error) + "\"\n")
                + "}\n";
        Path temporary = manifest.resolveSibling("run.json.tmp");
        Files.writeString(temporary, json, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, manifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, manifest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String html(String summary) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Validation run report</title>"
                + "<style>body{font-family:system-ui;margin:2rem;max-width:1100px}pre{white-space:pre-wrap;"
                + "background:#f6f8fa;padding:1rem;border-radius:8px}</style></head><body>"
                + "<h1>Validation run report</h1><p>Source tables: epoch-history.csv, seed-metrics.csv, "
                + "validation-predictions.csv.</p><pre>" + escapeHtml(summary) + "</pre></body></html>";
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = in.read(buffer)) >= 0) { digest.update(buffer, 0, count); }
            }
            StringBuilder out = new StringBuilder();
            for (byte value : digest.digest()) { out.append(String.format("%02x", value)); }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;
        private TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }
        @Override public synchronized void write(int value) throws IOException {
            first.write(value); second.write(value);
        }
        @Override public synchronized void write(byte[] data, int offset, int length) throws IOException {
            first.write(data, offset, length); second.write(data, offset, length);
        }
        @Override public synchronized void flush() throws IOException { first.flush(); second.flush(); }
        @Override public void close() throws IOException { flush(); }
    }
}
