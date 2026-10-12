package com.dl4j.salesforecast.model;

import java.util.List;

/** Explicit opt-in to the controlled residual comparison; smoke is never a research result. */
public final class ExperimentConfiguration {
    private final boolean residual;
    private final boolean smoke;
    private final boolean huber;
    private final boolean benchmark;
    private final int storeId;
    private final int batchSize;
    private final List<Long> globalSeeds;
    private final boolean preflight;
    public static final int ITEM_IDENTITY_COUNT = 50;
    public static final int INPUT_DAYS = 30;
    public static final int HORIZON = 7;

    private ExperimentConfiguration(boolean residual, boolean smoke) {
        this(residual, smoke, false);
    }
    private ExperimentConfiguration(boolean residual, boolean smoke, boolean huber) {
        this(residual, smoke, huber, 0, 64);
    }
    private ExperimentConfiguration(boolean residual, boolean smoke, boolean huber, int storeId, int batchSize) {
        this(residual, smoke, huber, storeId, batchSize, false, List.of(), false);
    }
    private ExperimentConfiguration(boolean residual, boolean smoke, boolean huber, int storeId, int batchSize, boolean benchmark) {
        this(residual, smoke, huber, storeId, batchSize, benchmark, List.of(), false);
    }
    private ExperimentConfiguration(boolean residual, boolean smoke, boolean huber, int storeId, int batchSize,
                                    boolean benchmark, List<Long> globalSeeds, boolean preflight) {
        this.residual = residual;
        this.smoke = smoke;
        this.huber = huber;
        this.storeId = storeId;
        this.batchSize = batchSize;
        this.benchmark = benchmark;
        this.globalSeeds = List.copyOf(globalSeeds);
        this.preflight = preflight;
    }
    public static ExperimentConfiguration legacy() { return new ExperimentConfiguration(false, false); }
    public static ExperimentConfiguration residual() { return new ExperimentConfiguration(true, false); }
    public static ExperimentConfiguration smoke() { return new ExperimentConfiguration(true, true); }
    public static ExperimentConfiguration fromArgs(String[] args) {
        if (args.length >= 1 && (args[0].matches("--global-store(?:-smoke|-benchmark)?(?:=\\d+)?") || "--global-preflight".equals(args[0]))) {
            boolean preflight = "--global-preflight".equals(args[0]);
            int store = 1, batch = 64; List<Long> seeds = new java.util.ArrayList<>();
            for (int i=preflight ? 1 : 0;i<args.length;i++) {
                String arg=args[i];
                if (i==0) { if(arg.contains("=")) store=Integer.parseInt(arg.substring(arg.indexOf('=')+1)); continue; }
                if(arg.matches("--batch-size=\\d+")) batch=Integer.parseInt(arg.substring(arg.indexOf('=')+1));
                else if(arg.matches("--seeds=\\d+(,\\d+)*")) for(String value:arg.substring(arg.indexOf('=')+1).split(",")) seeds.add(Long.parseLong(value));
                else if(arg.matches("--global-store=\\d+") && preflight) store=Integer.parseInt(arg.substring(arg.indexOf('=')+1));
                else throw new IllegalArgumentException("Global usage: --global-store[=storeId] [--seeds=42,123,2026] [--batch-size=64]");
            }
            if (store <= 0 || batch <= 0) { throw new IllegalArgumentException("Store and batch size must be positive."); }
            boolean smoke=args[0].contains("-smoke"), benchmark=args[0].contains("-benchmark");
            if(seeds.isEmpty()) seeds=smoke||benchmark ? List.of(42L) : List.of(42L,123L,2026L);
            if(new java.util.HashSet<>(seeds).size()!=seeds.size()) throw new IllegalArgumentException("Seeds must be unique and ordered.");
            return new ExperimentConfiguration(true, smoke, false, store, batch, benchmark, seeds, preflight);
        }
        if (args.length == 0) { return legacy(); }
        if (args.length == 1 && "--residual".equals(args[0])) { return residual(); }
        if (args.length == 1 && "--residual-smoke".equals(args[0])) { return smoke(); }
        if (args.length == 1 && "--residual-huber".equals(args[0])) { return new ExperimentConfiguration(true, false, true); }
        if (args.length == 1 && "--residual-huber-smoke".equals(args[0])) { return new ExperimentConfiguration(true, true, true); }
        throw new IllegalArgumentException("Usage: [--residual | --residual-smoke | --residual-huber | --residual-huber-smoke]");
    }
    public boolean includesResidual() { return residual; }
    public boolean isSmoke() { return smoke; }
    public boolean isHuberComparison() { return huber; }
    public boolean isGlobalStore() { return storeId > 0; }
    public boolean isBenchmark() { return benchmark; }
    public boolean isPreflight() { return preflight; }
    public int getStoreId() { return storeId; }
    public int getBatchSize() { return batchSize; }
    public List<Integer> getItemIds() {
        return smoke ? List.of(1, 25, 50) : java.util.stream.IntStream.rangeClosed(1, ITEM_IDENTITY_COUNT)
                .boxed().collect(java.util.stream.Collectors.toUnmodifiableList());
    }
    public int getMaxEpochs() { return smoke || benchmark ? 2 : SalesLstmForecaster.DEFAULT_SEARCH_EPOCHS; }
    public int getPatience() { return SalesLstmForecaster.DEFAULT_EARLY_STOPPING_PATIENCE; }
    public int getSeriesCount() { return isGlobalStore() ? getItemIds().size() : smoke ? 1 : 12; }
    public List<Long> getSeeds() { return isGlobalStore() ? globalSeeds : smoke ? List.of(42L) : ExperimentRunner.DEFAULT_SEEDS; }
    public List<String> getModels() { return isGlobalStore() ? List.of("GlobalResidualAR") : huber ? List.of("ResidualAR", "ResidualARHuber")
            : residual ? List.of("Direct", "AR", "ResidualAR") : List.of("Direct", "AR"); }
    public int getFitCount() { return isGlobalStore() ? getSeeds().size() : getSeriesCount() * getSeeds().size() * getModels().size(); }
}
