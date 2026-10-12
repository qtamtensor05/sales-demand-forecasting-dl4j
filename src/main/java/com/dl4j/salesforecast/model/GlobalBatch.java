package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import java.util.List;

/** CPU flat buffers copied to the backend once per batch, not scalar GPU writes. */
public final class GlobalBatch {
    public static final int DYNAMIC = SalesPreprocessor.getInputFeatureNames().size();
    public static final int CHANNELS = DYNAMIC + ExperimentConfiguration.ITEM_IDENTITY_COUNT;
    private GlobalBatch() { }
    public static int identityChannel(int item) {
        if (item < 1 || item > ExperimentConfiguration.ITEM_IDENTITY_COUNT) { throw new IllegalArgumentException("Unknown item"); }
        return DYNAMIC + item - 1;
    }
    static float[] features(List<SingleStoreGlobalDataset.Sample> samples, List<List<List<Double>>> sequences) {
        int days = ExperimentConfiguration.INPUT_DAYS;
        float[] data = new float[samples.size() * CHANNELS * days];
        for (int row = 0; row < samples.size(); row++) {
            var vectors = sequences == null ? samples.get(row).window.getInputFeatures() : sequences.get(row);
            for (int t = 0; t < days; t++) {
                for (int f = 0; f < DYNAMIC; f++) {
                    double value = vectors.get(t).get(f);
                    if (!Double.isFinite(value)) { throw new IllegalStateException("Nonfinite global feature"); }
                    data[(row * CHANNELS + f) * days + t] = (float)value;
                }
                data[(row * CHANNELS + identityChannel(samples.get(row).item.id)) * days + t] = 1;
            }
        }
        return data;
    }
    public static DataSet training(List<SingleStoreGlobalDataset.Sample> samples) {
        if (samples.isEmpty()) { throw new IllegalArgumentException("Empty batch"); }
        int n = samples.size(), days = ExperimentConfiguration.INPUT_DAYS;
        float[] labels = new float[n * days], mask = new float[n * days];
        for (int row = 0; row < n; row++) {
            var sample = samples.get(row);
            labels[row * days + days - 1] = (float)ResidualLstmForecaster.scaledTarget(sample.window, 0, sample.item.scaler);
            mask[row * days + days - 1] = 1;
        }
        return new DataSet(Nd4j.create(features(samples,null),new long[]{n,CHANNELS,days},'c'),
                Nd4j.create(labels,new long[]{n,1,days},'c'),null,Nd4j.create(mask,new long[]{n,days},'c'));
    }
    static void close(DataSet data) {
        data.getFeatures().close(); data.getLabels().close(); data.getLabelsMaskArray().close();
    }
}
