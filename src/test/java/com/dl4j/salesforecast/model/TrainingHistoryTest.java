package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrainingHistoryTest {
    @Test
    void epochMetricsExposeComparableScaledLosses() {
        assertDoesNotThrow(() -> SalesLstmForecaster.EpochMetrics.class.getMethod("getTrainingLoss"));
        assertDoesNotThrow(() -> SalesLstmForecaster.EpochMetrics.class.getMethod("getValidationLoss"));
        assertDoesNotThrow(() -> AutoregressiveLstmForecaster.EpochMetrics.class.getMethod("getTrainingLoss"));
        assertDoesNotThrow(() -> AutoregressiveLstmForecaster.EpochMetrics.class.getMethod("getValidationLoss"));
    }

    @Test
    void formatsPlotReadyCsvWithStableColumnsAndRootLocaleDecimals() {
        TrainingHistoryExporter.Row row = new TrainingHistoryExporter.Row(
                "HIGH", 2, 43, "Direct", 42L, 8,
                0.0123456789, 0.023456789, 12.5, true);

        String csv = TrainingHistoryExporter.formatRows(List.of(row));

        assertEquals("stratum,store,item,model,seed,epoch,train_loss_scaled_mse,"
                + "val_loss_scaled_mse,val_rmse_sales,is_best_epoch\n"
                + "HIGH,2,43,Direct,42,8,0.0123456789,0.0234567890,12.5000000000,true\n", csv);
    }
}
