package com.dl4j.salesforecast.api;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ForecastCommandTest {
    @Test void acceptsStoreOneItemAndIsoOrigin() {
        ForecastCommand command = ForecastCommand.validated(25, "2017-06-30");
        assertEquals(25, command.itemId);
        assertEquals(LocalDate.of(2017, 6, 30), command.originDate);
    }

    @Test void rejectsMissingOrOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> ForecastCommand.validated(null, "2017-06-30"));
        assertThrows(IllegalArgumentException.class, () -> ForecastCommand.validated(0, "2017-06-30"));
        assertThrows(IllegalArgumentException.class, () -> ForecastCommand.validated(51, "2017-06-30"));
        assertThrows(IllegalArgumentException.class, () -> ForecastCommand.validated(1, "30-06-2017"));
    }
}
