package com.dl4j.salesforecast.model;

import java.time.LocalDate;

/** One validation forecast in original sales units. */
public final class ValidationPrediction {
    private final LocalDate originDate;
    private final LocalDate targetDate;
    private final int horizon;
    private final double actual;
    private final double prediction;
    private final double weeklyNaive;

    ValidationPrediction(LocalDate originDate, LocalDate targetDate, int horizon,
                         double actual, double prediction, double weeklyNaive) {
        this.originDate = originDate;
        this.targetDate = targetDate;
        this.horizon = horizon;
        this.actual = actual;
        this.prediction = prediction;
        this.weeklyNaive = weeklyNaive;
    }

    public LocalDate getOriginDate() { return originDate; }
    public LocalDate getTargetDate() { return targetDate; }
    public int getHorizon() { return horizon; }
    public double getActual() { return actual; }
    public double getPrediction() { return prediction; }
    public double getWeeklyNaive() { return weeklyNaive; }
}
