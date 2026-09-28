package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentRunnerTest {
    @Test
    void computesMeanAndSampleStandardDeviation() {
        ExperimentRunner.Stats stats = ExperimentRunner.summarizeValues(List.of(1.0, 2.0, 3.0));
        assertEquals(2.0, stats.getMean(), 1e-12);
        assertEquals(1.0, stats.getStandardDeviation(), 1e-12);
    }

    @Test
    void rejectsEmptyOrNonFiniteSamples() {
        assertThrows(IllegalArgumentException.class, () -> ExperimentRunner.summarizeValues(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentRunner.summarizeValues(List.of(1.0, Double.NaN)));
    }
}
