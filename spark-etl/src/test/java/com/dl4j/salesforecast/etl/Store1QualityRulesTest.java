package com.dl4j.salesforecast.etl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Store1QualityRulesTest {
    @Test void acceptsOnlyTheVerifiedStore1Panel() {
        assertDoesNotThrow(() -> Store1QualityRules.requireExpected(
                91_300, 50, 0, 0, 54_750, 18_300, 18_250));
        assertThrows(IllegalStateException.class, () -> Store1QualityRules.requireExpected(
                91_299, 50, 0, 0, 54_750, 18_300, 18_249));
        assertThrows(IllegalStateException.class, () -> Store1QualityRules.requireExpected(
                91_300, 50, 1, 0, 54_750, 18_300, 18_250));
    }
}
