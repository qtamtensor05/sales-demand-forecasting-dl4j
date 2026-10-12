package com.dl4j.salesforecast.model;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.util.*;
import java.util.function.Function;

/** Frozen pre-optimization rollout oracle; no network/model definition is duplicated. */
final class ReferenceGlobalValidation {
    static GlobalMetrics evaluate(Function<INDArray,INDArray> predictor,
                                  List<SingleStoreGlobalDataset.Sample> samples,int batchSize) {
        Map<Integer,List<ValidationPrediction>> rows=new TreeMap<>();
        int days=ExperimentConfiguration.INPUT_DAYS;
        for(int from=0;from<samples.size();from+=batchSize) {
            var batch=samples.subList(from,Math.min(from+batchSize,samples.size()));
            List<List<Double>> histories=new ArrayList<>();List<List<List<Double>>> sequences=new ArrayList<>();
            for(var sample:batch) {
                histories.add(new ArrayList<>(sample.window.getRawInput()));
                sequences.add(new ArrayList<>(sample.window.getInputFeatures()));
            }
            for(int h=0;h<ExperimentConfiguration.HORIZON;h++) {
                try(INDArray input=Nd4j.create(GlobalBatch.features(batch,sequences),new long[]{batch.size(),GlobalBatch.CHANNELS,days},'c');
                    INDArray output=predictor.apply(input)) {
                    for(int row=0;row<batch.size();row++) {
                        var sample=batch.get(row);var window=sample.window;
                        double anchor=ResidualLstmForecaster.baseline(window,h);
                        double sales=ResidualLstmForecaster.reconstruct(anchor,output.getDouble(row,0,days-1),sample.item.scaler);
                        rows.computeIfAbsent(sample.item.id,k->new ArrayList<>()).add(new ValidationPrediction(
                                window.getInputDates().get(days-1),window.getTargetDates().get(h),h+1,
                                window.getRawTarget().get(h),sales,anchor));
                        var sequence=sequences.get(row);var history=histories.get(row);
                        sequence.remove(0);
                        sequence.add(AutoregressiveLstmForecaster.createFeatureVector(window.getTargetDates().get(h),sales,history,sample.item.scaler));
                        history.add(sales);
                    }
                }
            }
        }
        return GlobalMetrics.of(rows);
    }
}
