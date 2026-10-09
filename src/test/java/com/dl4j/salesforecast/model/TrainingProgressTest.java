package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainingProgressTest {
    @Test
    void formatsCompletedEpochWithGlobalFitPosition() {
        TrainingProgress progress = TrainingProgress.forFit(
                1, 12, 3, 5, true, "HIGH", 2, 43, 42L, 30);

        assertEquals("[17/120] Direct | HIGH | store=2 item=43 | seed=42 | epoch=8/30",
                progress.formatCompletedEpoch(8));
    }

    @Test
    void numbersFirstAndLastFitAcrossSeriesSeedsAndModels() {
        assertEquals(1, TrainingProgress.forFit(
                0, 12, 0, 5, true, "LOW", 1, 1, 42L, 30).getFitNumber());
        assertEquals(120, TrainingProgress.forFit(
                11, 12, 4, 5, false, "HIGH", 10, 28, 99L, 30).getFitNumber());
    }
}
