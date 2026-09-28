package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MultiSeriesExperimentRunnerTest {
    @Test
    void selectsEvenlySpacedSeriesFromLowMediumAndHighTrainSalesTertiles() {
        List<MultiSeriesExperimentRunner.SeriesProfile> profiles = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            profiles.add(new MultiSeriesExperimentRunner.SeriesProfile(i, 1, i));
        }

        List<MultiSeriesExperimentRunner.SelectedSeries> selected =
                MultiSeriesExperimentRunner.selectRepresentativeSeries(profiles, 2);

        assertEquals(6, selected.size());
        assertEquals(List.of("LOW", "LOW", "MEDIUM", "MEDIUM", "HIGH", "HIGH"),
                selected.stream().map(MultiSeriesExperimentRunner.SelectedSeries::getStratum).collect(Collectors.toList()));
        assertEquals(List.of(3, 8, 13, 18, 23, 28), selected.stream()
                .map(row -> row.getProfile().getStoreId()).collect(Collectors.toList()));
    }

    @Test
    void rejectsTooFewProfilesOrAnEmptyTierSample() {
        assertThrows(IllegalArgumentException.class,
                () -> MultiSeriesExperimentRunner.selectRepresentativeSeries(List.of(), 1));
        List<MultiSeriesExperimentRunner.SeriesProfile> profiles = List.of(
                new MultiSeriesExperimentRunner.SeriesProfile(1, 1, 1),
                new MultiSeriesExperimentRunner.SeriesProfile(2, 1, 2),
                new MultiSeriesExperimentRunner.SeriesProfile(3, 1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> MultiSeriesExperimentRunner.selectRepresentativeSeries(profiles, 2));
    }
}
