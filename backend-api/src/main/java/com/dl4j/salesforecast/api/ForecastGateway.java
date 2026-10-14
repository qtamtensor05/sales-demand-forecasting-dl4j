package com.dl4j.salesforecast.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
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

    @Transactional
    Map<String,Object> createBatch(String originDate) {
        LocalDate origin;
        try { origin = LocalDate.parse(originDate); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("originDate must use ISO yyyy-MM-dd", failure); }
        try {
            HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
            JsonNode response = rest.postForObject(serviceUrl + "/forecast/batch",
                    new HttpEntity<>(Map.of("originDate", origin.toString()), headers), JsonNode.class);
            if (response == null || !response.has("items") || response.path("items").size() != 50) {
                throw new IllegalStateException("Forecast service did not return all 50 products");
            }
            Long modelId = jdbc.queryForObject("SELECT id FROM model_versions WHERE status='ACTIVE' ORDER BY id DESC LIMIT 1", Long.class);
            List<Map<String,Object>> items = new ArrayList<>();
            double totalInference = response.path("inferenceMillis").asDouble();
            for (JsonNode item : response.path("items")) {
                int itemId = item.path("itemId").asInt();
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO forecast_requests(id,store_id,item_id,origin_date,model_version_id,status,inference_millis) VALUES (?,?,?,?,?,'COMPLETED',?)",
                        id, 1, itemId, Date.valueOf(origin), modelId, totalInference / 50.0);
                for (JsonNode row : item.path("forecasts")) {
                    jdbc.update("INSERT INTO forecast_results(request_id,horizon,target_date,prediction,weekly_naive,actual) VALUES (?,?,?,?,?,?)",
                            id, row.path("horizon").asInt(), Date.valueOf(row.path("targetDate").asText()),
                            row.path("prediction").asDouble(), row.path("weeklyNaive").asDouble(),
                            row.path("actual").isNull() ? null : row.path("actual").asDouble());
                }
                Map<String,Object> result = new HashMap<>();
                result.put("requestId", id); result.put("itemId", itemId);
                result.put("forecasts", jdbc.queryForList("SELECT horizon,target_date AS \"targetDate\",prediction,weekly_naive AS \"weeklyNaive\",actual FROM forecast_results WHERE request_id=? ORDER BY horizon", id));
                items.add(result);
            }
            Map<String,Object> result = new HashMap<>();
            result.put("storeId", 1); result.put("originDate", origin.toString());
            result.put("itemCount", items.size()); result.put("inferenceMillis", totalInference); result.put("items", items);
            return result;
        } catch (RestClientResponseException failure) {
            throw new IllegalArgumentException("Forecast service rejected the batch request: " + failure.getResponseBodyAsString());
        }
    }

    Map<String, Object> find(UUID id) {
        Map<String, Object> request = jdbc.queryForMap("SELECT id,store_id AS \"storeId\",item_id AS \"itemId\",origin_date AS \"originDate\",status,inference_millis AS \"inferenceMillis\",created_at AS \"createdAt\" FROM forecast_requests WHERE id=?", id);
        request.put("forecasts", jdbc.queryForList("SELECT horizon,target_date AS \"targetDate\",prediction,weekly_naive AS \"weeklyNaive\",actual FROM forecast_results WHERE request_id=? ORDER BY horizon", id));
        return request;
    }
}
