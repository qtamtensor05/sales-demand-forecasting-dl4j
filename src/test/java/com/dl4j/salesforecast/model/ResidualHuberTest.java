package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import static org.junit.jupiter.api.Assertions.*;

class ResidualHuberTest {
    @Test void masksNumericalGradientAndSerializationRespectNd4jContract() {
        var loss = new HuberLoss();
        var labels = Nd4j.zeros(org.nd4j.linalg.api.buffer.DataType.DOUBLE, 2, 2);
        var output = Nd4j.create(new double[][]{{.3, -2}, {1.5, -.2}});
        var original = output.dup();
        var mask = Nd4j.create(new double[][]{{1, 0}, {1, 1}});
        var activation = Activation.TANH.getActivationFunction();
        var gradient = loss.computeGradient(labels, output, activation, mask);
        for (int i = 0; i < 4; i++) {
            double value = output.getDouble(i), epsilon = 1e-5;
            output.putScalar(i, value + epsilon);
            double plus = loss.computeScore(labels, output, activation, mask, false);
            output.putScalar(i, value - epsilon);
            double minus = loss.computeScore(labels, output, activation, mask, false);
            output.putScalar(i, value);
            assertEquals((plus-minus)/(2*epsilon), gradient.getDouble(i), 1e-7);
        }
        assertEquals(original, output);
        var identity = Activation.IDENTITY.getActivationFunction();
        var pair = loss.computeGradientAndScore(labels, output, identity, mask, true);
        assertEquals(loss.computeScore(labels, output, identity, mask, true), pair.getFirst());
        assertEquals(loss.computeGradient(labels, output, identity, mask), pair.getSecond());
        var rowMask = Nd4j.create(new double[]{1, 0}, new long[]{2, 1});
        assertEquals(0, loss.computeGradient(labels, output, identity, rowMask).getRow(1).norm2Number().doubleValue());
        assertEquals(0, loss.computeScoreArray(labels, output, identity, rowMask).getDouble(1));
        assertThrows(IllegalArgumentException.class, () -> loss.computeScore(Nd4j.zeros(3,1), output, identity, null, true));
        var config = new org.deeplearning4j.nn.conf.NeuralNetConfiguration.Builder().list()
                .layer(new org.deeplearning4j.nn.conf.layers.RnnOutputLayer.Builder(loss).nIn(2).nOut(1)
                        .activation(Activation.IDENTITY).build()).build();
        var restored = org.deeplearning4j.nn.conf.MultiLayerConfiguration.fromJson(config.toJson());
        assertInstanceOf(HuberLoss.class, ((org.deeplearning4j.nn.conf.layers.RnnOutputLayer)
                restored.getConf(0).getLayer()).getLossFn());
    }

    @Test void pairedReportUsesSeriesMeansAndStrictWins() {
        var rows = java.util.List.of(
                new ResidualComparisonReport.Row("LOW",1,1,42,0,new double[]{10,12,6},new double[]{10,12,6}),
                new ResidualComparisonReport.Row("LOW",1,1,99,0,new double[]{10,12,14},new double[]{10,12,14}),
                new ResidualComparisonReport.Row("HIGH",2,1,42,0,new double[]{20,22,18},new double[]{20,22,18}),
                new ResidualComparisonReport.Row("HIGH",2,1,99,0,new double[]{20,22,18},new double[]{20,22,18}));
        String report = ResidualComparisonReport.formatRows(rows, java.util.List.of("Weekly","ResidualAR","ResidualARHuber"),1);
        assertTrue(report.contains("ResidualARHuber | 14.0000 +/- 5.6569 | 14.0000 +/- 5.6569 | +6.67 | +6.67 | +17.65"));
        assertTrue(report.contains("Weekly 1/2; ResidualAR 2/2;"));
        assertTrue(report.contains("RMSE std 2.8284 | RMSE mean 14.0000 min 12.0000 max 16.0000"));
    }
    @Test void huberHasQuadraticAndLinearRegionsAndBoundedGradient() {
        ILossFunction loss = assertDoesNotThrow(() -> (ILossFunction) Class.forName(
                "com.dl4j.salesforecast.model.HuberLoss").getConstructor().newInstance());
        var labels = Nd4j.zeros(7, 1);
        var output = Nd4j.create(new double[]{-2, -1, -.5, 0, .5, 1, 2}, new long[]{7, 1});
        var activation = Activation.IDENTITY.getActivationFunction();
        assertArrayEquals(new double[]{1.5, .5, .125, 0, .125, .5, 1.5},
                loss.computeScoreArray(labels, output, activation, null).toDoubleVector(), 1e-7);
        assertArrayEquals(new double[]{-1, -1, -.5, 0, .5, 1, 1},
                loss.computeGradient(labels, output, activation, null).toDoubleVector(), 1e-7);
        assertEquals(4.25 / 7, loss.computeScore(labels, output, activation, null, true), 1e-7);
    }

    @Test void cliRunsOnlyPairedResidualModels() {
        var smoke = assertDoesNotThrow(() -> ExperimentConfiguration.fromArgs(new String[]{"--residual-huber-smoke"}));
        assertEquals(java.util.List.of("ResidualAR", "ResidualARHuber"), smoke.getModels());
        assertEquals(2, smoke.getFitCount());
        assertEquals(120, ExperimentConfiguration.fromArgs(new String[]{"--residual-huber"}).getFitCount());
        assertEquals(120, ExperimentConfiguration.legacy().getFitCount());
        assertEquals(180, ExperimentConfiguration.residual().getFitCount());
    }
}
