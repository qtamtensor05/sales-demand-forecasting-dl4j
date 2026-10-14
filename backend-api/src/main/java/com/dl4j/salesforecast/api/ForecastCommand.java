package com.dl4j.salesforecast.api;

import java.time.LocalDate;

final class ForecastCommand {
    final int itemId;
    final LocalDate originDate;

    ForecastCommand(int itemId, LocalDate originDate) {
        this.itemId = itemId;
        this.originDate = originDate;
    }

    static ForecastCommand validated(Integer itemId, String originDate) {
        if (itemId == null || itemId < 1 || itemId > 50) {
            throw new IllegalArgumentException("itemId must be between 1 and 50");
        }
        try {
            return new ForecastCommand(itemId, LocalDate.parse(originDate));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("originDate must use ISO yyyy-MM-dd", failure);
        }
    }
}
