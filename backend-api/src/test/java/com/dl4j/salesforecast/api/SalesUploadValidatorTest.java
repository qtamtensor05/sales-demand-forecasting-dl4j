package com.dl4j.salesforecast.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SalesUploadValidatorTest {
    private final SalesUploadValidator validator = new SalesUploadValidator();

    @Test
    void acceptsRequiredSchemaAndReturnsParsedRows() {
        byte[] csv = ("date,store,item,sales\n"
                + "2018-01-01,1,1,12\n"
                + "2018-01-02,1,1,9\n").getBytes(StandardCharsets.UTF_8);

        SalesUploadValidator.ValidatedCsv result = validator.validate(csv);

        assertEquals(2, result.rows().size());
        assertEquals(12, result.rows().get(0).sales());
    }

    @Test
    void rejectsEmptyMalformedNegativeAndDuplicateRows() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "date,store,item,sales\nnot-a-date,1,1,2\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "date,store,item,sales\n2018-01-01,1,1,-1\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                ("date,store,item,sales\n2018-01-01,1,1,1\n2018-01-01,1,1,2\n")
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsWrongHeaderAndBlankDataFile() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "day,store,item,sales\n2018-01-01,1,1,2\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "date,store,item,sales\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsStoresAndItemsOutsideCurrentDashboardScope() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "date,store,item,sales\n2018-01-01,2,1,2\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(
                "date,store,item,sales\n2018-01-01,1,51,2\n".getBytes(StandardCharsets.UTF_8)));
    }
}
