package com.dl4j.salesforecast.model;

import java.util.*;

public final class GlobalExperimentRunner {
    private GlobalExperimentRunner() { }
    public static Result run(String path, ExperimentConfiguration config) {
        var profile = new com.dl4j.salesforecast.performance.GlobalPerformanceProfile();
        return run(SingleStoreGlobalDataset.load(path,config,profile),config,profile);
    }
    public static Result run(String path, ExperimentConfiguration config, java.nio.file.Path runDirectory) {
        var profile = new com.dl4j.salesforecast.performance.GlobalPerformanceProfile();
        return run(SingleStoreGlobalDataset.load(path,config,profile),config,profile,runDirectory);
    }
    public static Result run(SingleStoreGlobalDataset data, ExperimentConfiguration config) {
        return run(data,config,new com.dl4j.salesforecast.performance.GlobalPerformanceProfile());
    }
    public static Result run(SingleStoreGlobalDataset data, ExperimentConfiguration config, java.nio.file.Path runDirectory) {
        return run(data,config,new com.dl4j.salesforecast.performance.GlobalPerformanceProfile(),runDirectory);
    }
    private static Result run(SingleStoreGlobalDataset data, ExperimentConfiguration config,
            com.dl4j.salesforecast.performance.GlobalPerformanceProfile profile) {
        return run(data,config,profile,null);
    }
    private static Result run(SingleStoreGlobalDataset data, ExperimentConfiguration config,
            com.dl4j.salesforecast.performance.GlobalPerformanceProfile profile, java.nio.file.Path runDirectory) {
        if(!config.isGlobalStore() || data.storeId!=config.getStoreId()
                || !data.items.stream().map(i->i.id).collect(java.util.stream.Collectors.toList()).equals(config.getItemIds())) {
            throw new IllegalArgumentException("Global configuration/item coverage mismatch");
        }
        long featureBytes=4L*config.getBatchSize()*GlobalBatch.CHANNELS*ExperimentConfiguration.INPUT_DAYS;
        System.out.printf("GLOBAL store=%d items=%d globalFits=%d trainWindows=%d validationOrigins=%d batchSize=%d featureBytesPerBatch=%d%n",
                data.storeId,data.items.size(),config.getFitCount(),data.train.size(),data.validation.size(),config.getBatchSize(),featureBytes);
        for(var item:data.items) { System.out.printf("item=%d trainWindows=%d validationOrigins=%d%n",item.id,item.train.getWindows().size(),item.validation.getWindows().size()); }
        if(data.items.stream().map(i->i.train.getWindows().size()).distinct().count()>1) {
            System.out.println("WARNING: unequal training windows per item; no resampling applied.");
        }
        profile.count("items",data.items.size()); profile.count("trainWindows",data.train.size());
        profile.count("validationOriginsPerEvaluation",data.validation.size());
        profile.count("forecastsPerEvaluation",(long)data.validation.size()*ExperimentConfiguration.HORIZON);
        long fingerprintStart=System.nanoTime();
        profile.evidence("trainWindowsSha256",GlobalDataFingerprint.of(data.train));
        profile.evidence("validationWindowsSha256",GlobalDataFingerprint.of(data.validation));
        profile.record("audit.windowFingerprints",fingerprintStart);
        long started=System.nanoTime();
        var weekly=GlobalMetrics.weekly(data); profile.record("weekly",started);
        List<GlobalResidualLstmForecaster.Fit> fits=new ArrayList<>();
        for(long seed:config.getSeeds()) { fits.add(GlobalResidualLstmForecaster.fit(data,config,seed,profile,
                runDirectory==null?null:runDirectory.resolve("models").resolve("seed-"+seed))); }
        return new Result(data,weekly,fits,profile);
    }
    public static final class Result {
        public final SingleStoreGlobalDataset data;
        public final GlobalMetrics weekly;
        public final List<GlobalResidualLstmForecaster.Fit> fits;
        public final com.dl4j.salesforecast.performance.GlobalPerformanceProfile profile;
        private Result(SingleStoreGlobalDataset data,GlobalMetrics weekly,List<GlobalResidualLstmForecaster.Fit> fits,
                com.dl4j.salesforecast.performance.GlobalPerformanceProfile profile) {
            this.data=data;this.weekly=weekly;this.fits=List.copyOf(fits);
            this.profile=profile;
        }
    }
}
