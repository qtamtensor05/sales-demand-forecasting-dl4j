package com.dl4j.salesforecast.model;

import java.util.List;

/** Explicit opt-in to the controlled residual comparison; smoke is never a research result. */
public final class ExperimentConfiguration {
    private final boolean residual;
    private final boolean smoke;

    private ExperimentConfiguration(boolean residual, boolean smoke) {
        this.residual = residual;
        this.smoke = smoke;
    }
    public static ExperimentConfiguration legacy() { return new ExperimentConfiguration(false, false); }
    public static ExperimentConfiguration residual() { return new ExperimentConfiguration(true, false); }
    public static ExperimentConfiguration smoke() { return new ExperimentConfiguration(true, true); }
    public static ExperimentConfiguration fromArgs(String[] args) {
        if (args.length == 0) { return legacy(); }
        if (args.length == 1 && "--residual".equals(args[0])) { return residual(); }
        if (args.length == 1 && "--residual-smoke".equals(args[0])) { return smoke(); }
        throw new IllegalArgumentException("Usage: [--residual | --residual-smoke]");
    }
    public boolean includesResidual() { return residual; }
    public boolean isSmoke() { return smoke; }
    public int getMaxEpochs() { return smoke ? 2 : SalesLstmForecaster.DEFAULT_SEARCH_EPOCHS; }
    public int getPatience() { return SalesLstmForecaster.DEFAULT_EARLY_STOPPING_PATIENCE; }
    public int getSeriesCount() { return smoke ? 1 : 12; }
    public List<Long> getSeeds() { return smoke ? List.of(42L) : ExperimentRunner.DEFAULT_SEEDS; }
    public List<String> getModels() { return residual ? List.of("Direct", "AR", "ResidualAR") : List.of("Direct", "AR"); }
    public int getFitCount() { return getSeriesCount() * getSeeds().size() * getModels().size(); }
}
