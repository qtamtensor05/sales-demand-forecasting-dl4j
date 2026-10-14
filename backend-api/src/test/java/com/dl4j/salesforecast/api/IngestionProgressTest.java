package com.dl4j.salesforecast.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class IngestionProgressTest {
    @Test
    void parsesARealSparkProgressMarker() {
        Optional<SparkSubmitter.Progress> progress = SparkSubmitter.parseProgress(
                "INGESTION_PROGRESS stage=WRITE_GOLD completed=730 total=1000");

        assertTrue(progress.isPresent());
        assertEquals("WRITE_GOLD", progress.get().stage());
        assertEquals(730L, progress.get().completedRows());
        assertEquals(1000L, progress.get().totalRows());
    }

    @Test
    void ignoresOrdinarySparkLogLines() {
        assertTrue(SparkSubmitter.parseProgress("WARN TaskSetManager: stage running").isEmpty());
    }
}
