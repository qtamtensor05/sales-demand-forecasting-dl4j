package com.dl4j.salesforecast.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
class ForecastGateway {
    private final JdbcTemplate jdbc;
    private final RestTemplate rest;
    private final String serviceUrl;

    ForecastGateway(JdbcTemplate jdbc, RestTemplate forecastRestTemplate,
            @Value("${forecast.service-url}") String serviceUrl) {
        this.jdbc = jdbc;
        this.rest = forecastRestTemplate;
        this.serviceUrl = serviceUrl;
    }

    @Transactional
    Map<String, Object> create(Integer itemId, String originDate) {
        ForecastCommand command = ForecastCommand.validated(itemId, originDate);
        UUID id = UUID.randomUUID();
        Long modelId = jdbc.queryForObject("SELECT id FROM model_versions WHERE status='ACTIVE' ORDER BY id DESC LIMIT 1", Long.class);
        jdbc.update("INSERT INTO forecast_requests(id,store_id,item_id,origin_date,model_version_id,status) VALUES (?,?,?,?,?,'RUNNING')",
                id, 1, command.itemId, Date.valueOf(command.originDate), modelId);
        try {
            HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of("itemId", command.itemId, "originDate", command.originDate.toString());
            JsonNode response = rest.postForObject(serviceUrl + "/forecast", new HttpEntity<>(payload, headers), JsonNode.class);
            if (response == null || !response.has("forecasts")) throw new IllegalStateException("Forecast service returned no forecasts");
            jdbc.update("UPDATE forecast_requests SET status='COMPLETED', inference_millis=? WHERE id=?",
                    response.path("inferenceMillis").asDouble(), id);
            for (JsonNode row : response.path("forecasts")) {
                jdbc.update("INSERT INTO forecast_results(request_id,horizon,target_date,prediction,weekly_naive,actual) VALUES (?,?,?,?,?,?)",
                        id, row.path("horizon").asInt(), Date.valueOf(row.path("targetDate").asText()),
                        row.path("prediction").asDouble(), row.path("weeklyNaive").asDouble(),
                        row.path("actual").isNull() ? null : row.path("actual").asDouble());
            }
            return find(id);
        } catch (RestClientResponseException failure) {
            jdbc.update("UPDATE forecast_requests SET status='FAILED', error_message=? WHERE id=?", failure.getResponseBodyAsString(), id);
            throw new IllegalArgumentException("Forecast service rejected the request: " + failure.getResponseBodyAsString());
        } catch (RuntimeException failure) {
            jdbc.update("UPDATE forecast_requests SET status='FAILED', error_message=? WHERE id=?", failure.getMessage(), id);
            throw failure;
        }
    }

    Map<String, Object> find(UUID id) {
        Map<String, Object> request = jdbc.queryForMap("SELECT id,store_id AS \"storeId\",item_id AS \"itemId\",origin_date AS \"originDate\",status,inference_millis AS \"inferenceMillis\",created_at AS \"createdAt\" FROM forecast_requests WHERE id=?", id);
        request.put("forecasts", jdbc.queryForList("SELECT horizon,target_date AS \"targetDate\",prediction,weekly_naive AS \"weeklyNaive\",actual FROM forecast_results WHERE request_id=? ORDER BY horizon", id));
        return request;
    }
}
