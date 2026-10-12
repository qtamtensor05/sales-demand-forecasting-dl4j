package com.dl4j.salesforecast.model;

import java.util.*;

/** Deterministic calibration policy: fastest candidate within a bounded RMSE regression. */
public final class GlobalCalibrationSelection {
    public static final double DEFAULT_MAX_RMSE_REGRESSION_PERCENT=1.0;
    private GlobalCalibrationSelection() { }

    public static Candidate select(List<Candidate> candidates,double maxRegressionPercent) {
        if(candidates==null || candidates.isEmpty() || !Double.isFinite(maxRegressionPercent) || maxRegressionPercent<0) throw new IllegalArgumentException("Valid calibration candidates and threshold required");
        Candidate baseline=candidates.stream().filter(c->c.trainBatchSize==64&&c.validationBatchSize==256).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Calibration baseline 64/256 is required"));
        if(candidates.stream().anyMatch(c->!c.isValid())) throw new IllegalArgumentException("Calibration values must be finite and positive");
        double limit=baseline.validationMacroRmse*(1+maxRegressionPercent/100.0);
        return candidates.stream().filter(c->c.validationMacroRmse<=limit+1e-12).min(Comparator
                .comparingDouble((Candidate c)->c.secondsPerEpoch)
                .thenComparingDouble(c->c.validationMacroRmse)
                .thenComparingInt(c->c.trainBatchSize)).orElseThrow();
    }

    public static final class Candidate {
        public final int trainBatchSize,validationBatchSize;
        public final double validationMacroRmse,secondsPerEpoch;
        public Candidate(int trainBatchSize,int validationBatchSize,double validationMacroRmse,double secondsPerEpoch) {
            this.trainBatchSize=trainBatchSize;this.validationBatchSize=validationBatchSize;
            this.validationMacroRmse=validationMacroRmse;this.secondsPerEpoch=secondsPerEpoch;
        }
        private boolean isValid() { return trainBatchSize>0&&validationBatchSize>0&&Double.isFinite(validationMacroRmse)&&validationMacroRmse>=0&&Double.isFinite(secondsPerEpoch)&&secondsPerEpoch>0; }
    }
}
