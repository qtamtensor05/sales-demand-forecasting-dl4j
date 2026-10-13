package com.dl4j.salesforecast.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApiController.class)
class ApiControllerAnalyticsTest {
    @Autowired private MockMvc mvc;
    @MockBean private JdbcTemplate jdbc;
    @MockBean private ForecastGateway forecasts;
    @MockBean private IngestionService ingestions;

    @Test
    void returnsRankedMonthlyProductDemand() throws Exception {
        when(jdbc.queryForList(anyString(), eq(2017), eq(5)))
                .thenReturn(List.of(Map.of(
                        "month", "2017-01",
                        "itemId", 17,
                        "totalSales", 2500,
                        "averageDailySales", 80.6,
                        "peakDailySales", 104,
                        "rank", 1)));

        mvc.perform(get("/api/analytics/monthly-demand")
                        .param("year", "2017").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2017-01"))
                .andExpect(jsonPath("$[0].itemId").value(17))
                .andExpect(jsonPath("$[0].totalSales").value(2500));
    }

    @Test
    void rejectsInvalidAnalyticsParameters() throws Exception {
        mvc.perform(get("/api/analytics/monthly-demand")
                        .param("year", "2017").param("limit", "51"))
                .andExpect(status().isBadRequest());
    }
}
