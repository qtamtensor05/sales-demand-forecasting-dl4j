package com.dl4j.salesforecast.model;

import org.nd4j.linalg.factory.Nd4j;
import java.nio.file.*;
import java.util.Locale;

/** Dataset/environment validation only. This class never constructs or fits a network. */
public final class GlobalPreflight {
    private GlobalPreflight() { }
    public static Result run(Path dataset,Path output,ExperimentConfiguration config) throws java.io.IOException {
        if(!config.isGlobalStore() || !config.isPreflight()) throw new IllegalArgumentException("Preflight configuration required");
        if(!Files.isRegularFile(dataset) || !Files.isReadable(dataset)) throw new IllegalArgumentException("Dataset is missing or unreadable: "+dataset);
        Files.createDirectories(output);Path models=output.resolve("models");Files.createDirectories(models);
        Path probe=Files.createTempFile(models,"write-probe-",".tmp");Files.delete(probe);
        var data=SingleStoreGlobalDataset.load(dataset.toString(),config);
        if(data.train.size()!=52_300 || data.validation.size()!=18_000) throw new IllegalStateException("Unexpected global window counts");
        String backend=Nd4j.getBackend().getClass().getName();boolean gpu=backend.toLowerCase(Locale.ROOT).contains("cuda")||backend.toLowerCase(Locale.ROOT).contains("cublas");
        return new Result(backend,gpu,data.train.size(),data.validation.size(),(long)data.validation.size()*ExperimentConfiguration.HORIZON);
    }
    public static final class Result {
        public final String backend;public final boolean gpu;public final int trainWindows,validationOrigins;public final long forecastsPerSeed;
        private Result(String backend,boolean gpu,int trainWindows,int validationOrigins,long forecastsPerSeed) { this.backend=backend;this.gpu=gpu;this.trainWindows=trainWindows;this.validationOrigins=validationOrigins;this.forecastsPerSeed=forecastsPerSeed; }
    }
}
