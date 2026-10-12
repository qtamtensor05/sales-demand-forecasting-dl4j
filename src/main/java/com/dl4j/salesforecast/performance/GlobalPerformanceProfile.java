package com.dl4j.salesforecast.performance;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Single-threaded wall-clock instrumentation. Nested phases overlap; never sum all rows. */
public final class GlobalPerformanceProfile {
    private final long started = System.nanoTime();
    private final Map<String, Long> nanos = new LinkedHashMap<>();
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    public void record(String phase, long start) { nanos.merge(phase, System.nanoTime() - start, Long::sum); }
    public void count(String key, long value) { counts.merge(key, value, Long::sum); }
    public void evidence(String key, Object value) { evidence.put(key, value); }
    public long count(String key) { return counts.getOrDefault(key, 0L); }
    public Map<String, Object> snapshot() {
        Map<String, Double> seconds = new LinkedHashMap<>();
        nanos.forEach((key, value) -> seconds.put(key, value / 1e9));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", "Global runner entry through artifact/manifest completion; excludes Maven, ND4J backend startup, initial dataset hash/session, final profile serialization and console summary. Nested phases overlap. Timings are host wall time, not CUDA kernel time.");
        result.put("totalSeconds", (System.nanoTime() - started) / 1e9);
        result.put("seconds", seconds); result.put("counts", new LinkedHashMap<>(counts));
        result.put("evidence", new LinkedHashMap<>(evidence));
        return result;
    }
    public void write(Path directory) throws IOException {
        Files.writeString(directory.resolve("performance-profile.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(snapshot()) + "\n");
    }
}
