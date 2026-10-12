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
        this(residual, smoke, huber, storeId, batchSize, false);
    }
    private ExperimentConfiguration(boolean residual, boolean smoke, boolean huber, int storeId, int batchSize, boolean benchmark) {
        this.residual = residual;
        this.smoke = smoke;
        this.huber = huber;
        this.storeId = storeId;
        this.batchSize = batchSize;
        this.benchmark = benchmark;
    }
    public static ExperimentConfiguration legacy() { return new ExperimentConfiguration(false, false); }
    public static ExperimentConfiguration residual() { return new ExperimentConfiguration(true, false); }
    public static ExperimentConfiguration smoke() { return new ExperimentConfiguration(true, true); }
    public static ExperimentConfiguration fromArgs(String[] args) {
        if (args.length >= 1 && args[0].matches("--global-store(?:-smoke|-benchmark)?(?:=\\d+)?")) {
            if (args.length > 2 || (args.length == 2 && !args[1].matches("--batch-size=\\d+"))) {
                throw new IllegalArgumentException("Global usage: --global-store[-smoke][=storeId] [--batch-size=64]");
            }
            int store = args[0].contains("=") ? Integer.parseInt(args[0].split("=")[1]) : 1;
            int batch = args.length == 2 ? Integer.parseInt(args[1].split("=")[1]) : 64;
            if (store <= 0 || batch <= 0) { throw new IllegalArgumentException("Store and batch size must be positive."); }
            return new ExperimentConfiguration(true, args[0].contains("-smoke"), false, store, batch, args[0].contains("-benchmark"));
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
    public int getStoreId() { return storeId; }
    public int getBatchSize() { return batchSize; }
    public List<Integer> getItemIds() {
        return smoke ? List.of(1, 25, 50) : java.util.stream.IntStream.rangeClosed(1, ITEM_IDENTITY_COUNT)
                .boxed().collect(java.util.stream.Collectors.toUnmodifiableList());
    }
    public int getMaxEpochs() { return smoke || benchmark ? 2 : SalesLstmForecaster.DEFAULT_SEARCH_EPOCHS; }
    public int getPatience() { return SalesLstmForecaster.DEFAULT_EARLY_STOPPING_PATIENCE; }
    public int getSeriesCount() { return isGlobalStore() ? getItemIds().size() : smoke ? 1 : 12; }
    public List<Long> getSeeds() { return smoke || benchmark ? List.of(42L) : ExperimentRunner.DEFAULT_SEEDS; }
    public List<String> getModels() { return isGlobalStore() ? List.of("GlobalResidualAR") : huber ? List.of("ResidualAR", "ResidualARHuber")
            : residual ? List.of("Direct", "AR", "ResidualAR") : List.of("Direct", "AR"); }
    public int getFitCount() { return isGlobalStore() ? getSeeds().size() : getSeriesCount() * getSeeds().size() * getModels().size(); }
}
