package com.dl4j.salesforecast;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.factory.Nd4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendConfigurationTest {
    @Test
    void loadsCudaBackendForTraining() {
        String backendClass = Nd4j.getBackend().getClass().getName();
        assertTrue(backendClass.toLowerCase().contains("jcublas"),
                () -> "Expected CUDA backend but loaded " + backendClass);
    }
}
