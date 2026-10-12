package com.dl4j.salesforecast;

import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.nio.file.Path;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

/** Fast, synthetic CUDA diagnostic. It never reads the sales dataset. */
class Nd4jCudaDiagnosticTest {
    @TempDir Path temporaryDirectory;

    @Test
    void verifiesCudaFromDiscoveryThroughLstmPersistence() throws Exception {
        report("A Java runtime", System.getProperty("java.version") + " / " + System.getProperty("os.arch"));

        Nd4jBackend discovered = null;
        for (Nd4jBackend candidate : ServiceLoader.load(Nd4jBackend.class)) {
            report("B ServiceLoader", candidate.getClass().getName());
            if (candidate.getClass().getName().contains("JCublasBackend")) discovered = candidate;
        }
        assertNotNull(discovered, "ServiceLoader did not discover JCublasBackend");
        assertTrue(discovered.isAvailable(), "JCublasBackend was discovered but is unavailable");
        report("C JCublasBackend class load", discovered.getClass().getProtectionDomain()
                .getCodeSource().getLocation().toString());

        String backend = Nd4j.getBackend().getClass().getName();
        assertEquals("org.nd4j.linalg.jcublas.JCublasBackend", backend);
        report("D Nd4j initialization", "PASS");
        report("E Nd4j.getBackend", backend);
        report("F execution environment", Nd4j.getExecutioner().getEnvironmentInformation().toString());
        report("F CUDA devices", String.valueOf(Nd4j.getAffinityManager().getNumberOfDevices()));
        report("F selected device", String.valueOf(Nd4j.getAffinityManager().getDeviceForCurrentThread()));

        try (INDArray left = Nd4j.ones(2, 3); INDArray right = Nd4j.ones(3, 2);
             INDArray product = left.mmul(right)) {
            assertEquals(3.0, product.getDouble(0, 0), 1e-6);
            report("G tensor allocation", "PASS");
            report("H matrix operation", "PASS");
            Nd4j.getExecutioner().commit();
            assertEquals(3.0, product.sumNumber().doubleValue() / 4.0, 1e-6);
            report("I synchronize and consume", "PASS");
        }

        MultiLayerNetwork network = smallGlobalShapeNetwork();
        Path model = temporaryDirectory.resolve("cuda-smoke-model.zip");
        try (INDArray input = Nd4j.rand(new long[]{4, 56, 30});
             INDArray labels = Nd4j.rand(new long[]{4, 1, 30})) {
            DataSet batch = new DataSet(input, labels);
            try (INDArray before = network.output(input, false)) {
                assertArrayEquals(new long[]{4, 1, 30}, before.shape());
                assertFalse(before.isNaN().any());
                report("GPU smoke LSTM forward", "PASS");
            }
            double loss = network.score(batch);
            assertTrue(Double.isFinite(loss));
            report("GPU smoke loss", "PASS: " + loss);
            network.fit(batch);
            report("GPU smoke backward/update", "PASS");
            try (INDArray after = network.output(input, false)) {
                assertFalse(after.isNaN().any());
                report("GPU smoke second forward", "PASS");
            }
            ModelSerializer.writeModel(network, model.toFile(), true);
            report("GPU smoke model save", "PASS");
            MultiLayerNetwork restored = ModelSerializer.restoreMultiLayerNetwork(model.toFile(), true);
            try (INDArray restoredOutput = restored.output(input, false)) {
                assertFalse(restoredOutput.isNaN().any());
                report("GPU smoke model load", "PASS");
                report("GPU smoke finite prediction", "PASS");
            } finally { restored.close(); }
        } finally {
            network.close();
        }
    }

    private static MultiLayerNetwork smallGlobalShapeNetwork() {
        MultiLayerNetwork network = new MultiLayerNetwork(new NeuralNetConfiguration.Builder()
                .seed(42).updater(new Adam(0.001)).list()
                .layer(new LSTM.Builder().nIn(56).nOut(8).activation(Activation.TANH).build())
                .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .nIn(8).nOut(1).activation(Activation.IDENTITY).build())
                .build());
        network.init();
        return network;
    }

    private static void report(String step, String result) {
        System.out.println("CUDA_DIAGNOSTIC " + step + ": " + result);
    }
}
