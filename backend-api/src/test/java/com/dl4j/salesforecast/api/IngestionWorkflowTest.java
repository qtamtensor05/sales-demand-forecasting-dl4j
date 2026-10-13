package com.dl4j.salesforecast.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class IngestionWorkflowTest {
    private static final byte[] CSV = "date,store,item,sales\n2018-01-01,1,1,12\n".getBytes(StandardCharsets.UTF_8);
    @Mock ObjectStorage storage;
    @Mock IngestionRunRepository runs;
    @Mock IngestionWorker worker;
    @Mock SparkSubmitter spark;

    @Test
    void sameChecksumReturnsExistingRunWithoutUploadingOrProcessingAgain() {
        Map<String,Object> existing = new HashMap<>();
        existing.put("id", 7L); existing.put("status", "COMPLETED");
        when(runs.findByChecksum(any())).thenReturn(existing);
        IngestionService service = new IngestionService(new SalesUploadValidator(), storage, runs, worker);

        Map<String,Object> result = service.accept(new MockMultipartFile("file", "sales.csv", "text/csv", CSV));

        assertEquals(true, result.get("duplicate"));
        verify(storage, never()).put(any(), any());
        verify(worker, never()).process(anyLong());
    }

    @Test
    void sparkFailureMarksRunFailedAndNeverPublishesSales() {
        when(runs.find(9L)).thenReturn(Map.of("sourceUri", "s3a://bronze/uploads/a/sales.csv"));
        when(storage.get("uploads/a/sales.csv")).thenReturn(CSV);
        when(spark.submit(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq("s3a://bronze/uploads/a/sales.csv"), any()))
                .thenReturn(new SparkSubmitter.Result(false, "spark failed"));
        IngestionWorker ingestionWorker = new IngestionWorker(runs, storage, new SalesUploadValidator(), spark);

        ingestionWorker.process(9L);

        verify(runs).running(9L);
        verify(runs).failed(anyLong(), any(), anyLong());
        verify(runs, never()).publish(anyLong(), any(), anyLong());
    }

    @Test
    void retryIsOnlyScheduledForFailedRun() {
        when(runs.find(4L)).thenReturn(new HashMap<>(Map.of("id", 4L, "status", "FAILED")));
        IngestionService service = new IngestionService(new SalesUploadValidator(), storage, runs, worker);

        Map<String,Object> result = service.retry(4L);

        assertEquals(true, result.get("retryScheduled"));
        verify(worker).process(4L);
    }
}
