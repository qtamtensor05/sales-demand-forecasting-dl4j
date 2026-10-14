package com.dl4j.salesforecast.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

final class Store1FeatureLogicTest {
    @Test
    void matchesJavaPreprocessingContracts() {
        assertEquals("s3a://bronze/store-1/train.csv", Store1FeatureLogic.DEFAULT_BRONZE_INPUT);
        assertEquals("TRAIN", Store1FeatureLogic.split(LocalDate.of(2015, 12, 31)));
        assertEquals("VALIDATION", Store1FeatureLogic.split(LocalDate.of(2016, 1, 1)));
        assertEquals("TEST", Store1FeatureLogic.split(LocalDate.of(2017, 1, 1)));

        assertEquals(0.5, Store1FeatureLogic.scale(15.0, 10.0, 20.0), 1e-12);
        assertEquals(5.0, Store1FeatureLogic.scale(15.0, 10.0, 10.0), 1e-12);
        assertEquals(4.0, Store1FeatureLogic.mean(2.0, 4.0, 6.0), 1e-12);

        LocalDate monday = LocalDate.of(2026, 10, 12);
        assertEquals(0.0, Store1FeatureLogic.dayOfWeekSin(monday), 1e-12);
        assertEquals(1.0, Store1FeatureLogic.dayOfWeekCos(monday), 1e-12);
    }
}
