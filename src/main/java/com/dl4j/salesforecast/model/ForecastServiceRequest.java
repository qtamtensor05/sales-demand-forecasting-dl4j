package com.dl4j.salesforecast.model;

import java.time.LocalDate;

/** Validated immutable request for one seven-day forecast origin. */
public final class ForecastServiceRequest {
    public final int itemId;
    public final LocalDate originDate;

    private ForecastServiceRequest(int itemId, LocalDate originDate) {
        this.itemId = itemId;
        this.originDate = originDate;
    }

    public static ForecastServiceRequest of(int itemId, String originDate) {
        if (itemId < 1 || itemId > ExperimentConfiguration.ITEM_IDENTITY_COUNT) {
            throw new IllegalArgumentException("itemId must be between 1 and 50.");
        }
        try {
            return new ForecastServiceRequest(itemId, LocalDate.parse(originDate));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("originDate must use ISO yyyy-MM-dd.", failure);
        }
    }
}
