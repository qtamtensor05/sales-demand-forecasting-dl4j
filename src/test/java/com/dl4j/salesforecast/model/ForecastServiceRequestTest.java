package com.dl4j.salesforecast.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

final class ForecastServiceRequestTest {
    @Test
    void validatesItemAndIsoOriginDate() {
        ForecastServiceRequest request = ForecastServiceRequest.of(25, "2017-06-30");
        assertEquals(25, request.itemId);
        assertEquals(LocalDate.of(2017, 6, 30), request.originDate);
        assertThrows(IllegalArgumentException.class, () -> ForecastServiceRequest.of(0, "2017-06-30"));
        assertThrows(IllegalArgumentException.class, () -> ForecastServiceRequest.of(51, "2017-06-30"));
        assertThrows(IllegalArgumentException.class, () -> ForecastServiceRequest.of(1, "30/06/2017"));
    }
}
