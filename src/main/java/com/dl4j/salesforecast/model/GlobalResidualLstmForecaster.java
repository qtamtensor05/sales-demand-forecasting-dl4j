package com.dl4j.salesforecast.model;

import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import java.util.*;
import com.dl4j.salesforecast.performance.GlobalPerformanceProfile;

/** One set of weights per seed; both training and seven-step inference are bounded by batch size. */
public final class GlobalResidualLstmForecaster {
    private GlobalResidualLstmForecaster() { }
    public static Fit fit(SingleStoreGlobalDataset data, ExperimentConfiguration config, long seed) {
        return fit(data, config, seed, new GlobalPerformanceProfile());
    }
    static Fit fit(SingleStoreGlobalDataset data, ExperimentConfiguration config, long seed, GlobalPerformanceProfile profile) {
        long initStart=System.nanoTime();
        var network = new MultiLayerNetwork(new NeuralNetConfiguration.Builder().seed(seed)
                .updater(new Adam(ExperimentRunner.DEFAULT_LEARNING_RATE)).list()
                .layer(new LSTM.Builder().nIn(GlobalBatch.CHANNELS).nOut(ExperimentRunner.DEFAULT_HIDDEN_UNITS)
                        .activation(Activation.TANH).build())
                .layer(new RnnOutputLayer.Builder(LossMode.MSE.createLoss()).nIn(ExperimentRunner.DEFAULT_HIDDEN_UNITS)
                        .nOut(1).activation(Activation.IDENTITY).build()).build());
        network.init();
        profile.record("network.init",initStart);
        INDArray best = null;
        GlobalMetrics bestValidation = null;
        try {
            double bestRmse=Double.POSITIVE_INFINITY;int bestEpoch=0,stale=0;
            List<Epoch> history=new ArrayList<>();
            for(int epoch=1;epoch<=config.getMaxEpochs();epoch++) {
                long start=System.nanoTime();double loss=0;
                var order=data.trainingOrder(seed,epoch);
                long batches=0;
                for(int from=0;from<order.size();from+=config.getBatchSize()) {
                    long batchStart=System.nanoTime();
                    var batch=GlobalBatch.training(order.subList(from,Math.min(from+config.getBatchSize(),order.size())));
                    profile.record("train.tensorPreparation",batchStart);
                    try {
                        long operation=System.nanoTime(); network.fit(batch); profile.record("train.fit",operation);
                        operation=System.nanoTime(); loss+=network.score(batch)*batch.numExamples(); profile.record("train.score",operation);
                    }
                    finally { GlobalBatch.close(batch); }
                    profile.record("train.batches",batchStart); batches++;
                }
                loss/=order.size();
                if(!Double.isFinite(loss)) { throw new IllegalStateException("Nonfinite training loss"); }
                profile.record("train.total",start); profile.record("seed."+seed+".epoch."+epoch+".train",start);
                profile.count("train.batches",batches); profile.count("seed."+seed+".epoch."+epoch+".batches",batches);
                double trainSeconds=seconds(start);long evaluationStart=System.nanoTime();
                var evaluation=evaluate(input->network.output(input,false),data.validation,config.getBatchSize(),profile);
                double validationSeconds=seconds(evaluationStart);
                profile.record("seed."+seed+".epoch."+epoch+".validation",evaluationStart);
                history.add(new Epoch(epoch,loss,evaluation.withoutPredictions(),trainSeconds,validationSeconds));
                System.out.printf(Locale.ROOT,"GlobalResidualAR seed=%d epoch=%d/%d trainLoss=%.8f macroRMSE=%.6f trainSeconds=%.3f validationSeconds=%.3f%n",
                        seed,epoch,config.getMaxEpochs(),loss,evaluation.macroRmse,trainSeconds,validationSeconds);
                if(GlobalMetrics.improves(evaluation.macroRmse,bestRmse)) {
                    bestRmse=evaluation.macroRmse;bestEpoch=epoch;stale=0;
                    if(best!=null) { best.close(); }
                    best=network.params().dup();
                    // Immutable predictions already computed at these exact weights. Retain only
                    // the selected epoch, not every epoch, and keep all per-epoch validation calls.
                    bestValidation=evaluation;
                } else if(++stale>=config.getPatience()) { break; }
            }
            if(best==null) { throw new IllegalStateException("No finite checkpoint"); }
            network.setParams(best);
            long finalStart=System.nanoTime();
            if(bestValidation==null) { throw new IllegalStateException("Missing selected validation snapshot"); }
            profile.count("validation.cachedCheckpointResults",1);
            profile.record("validation.finalCheckpoint",finalStart);
            return new Fit(seed,bestEpoch,history,bestValidation);
        } finally {
            // Fit snapshots retain metrics, never a resident model per item or seed.
            network.close(); if(best!=null && !best.wasClosed()) { best.close(); }
        }
    }
    private static double seconds(long start) { return (System.nanoTime()-start)/1e9; }

    public static GlobalMetrics evaluate(MultiLayerNetwork network, List<SingleStoreGlobalDataset.Sample> samples, int batchSize) {
        return evaluate((input)->network.output(input,false),samples,batchSize);
    }
    /** Predictor boundary allows tests to audit exactly the inputs sent at each horizon. */
    static GlobalMetrics evaluate(java.util.function.Function<INDArray,INDArray> predictor,
                                  List<SingleStoreGlobalDataset.Sample> samples,int batchSize) {
        return evaluate(predictor,samples,batchSize,new GlobalPerformanceProfile());
    }
    static GlobalMetrics evaluate(java.util.function.Function<INDArray,INDArray> predictor,
                                  List<SingleStoreGlobalDataset.Sample> samples,int batchSize,GlobalPerformanceProfile profile) {
        if(batchSize<=0 || samples.isEmpty()) { throw new IllegalArgumentException("Nonempty validation and positive batch required"); }
        long evaluationStart=System.nanoTime();
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
                long tensorStart=System.nanoTime();
                try(INDArray input=Nd4j.create(GlobalBatch.features(batch,sequences),new long[]{batch.size(),GlobalBatch.CHANNELS,days},'c')) {
                    profile.record("validation.tensorPreparation",tensorStart);
                    long outputStart=System.nanoTime();
                    try(INDArray output=predictor.apply(input)) {
                    profile.record("validation.modelOutput",outputStart); profile.count("modelOutputCalls",1);
                    if(!Arrays.equals(output.shape(),new long[]{batch.size(),1,days})) { throw new IllegalStateException("Invalid global output shape"); }
                    for(int row=0;row<batch.size();row++) {
                        var sample=batch.get(row);var window=sample.window;
                        double anchor=ResidualLstmForecaster.baseline(window,h);
                        long readStart=System.nanoTime();
                        double residual=output.getDouble(row,0,days-1);
                        profile.record("validation.outputRead",readStart);
                        double sales=ResidualLstmForecaster.reconstruct(anchor,residual,sample.item.scaler);
                        rows.computeIfAbsent(sample.item.id,k->new ArrayList<>()).add(new ValidationPrediction(
                                window.getInputDates().get(days-1),window.getTargetDates().get(h),h+1,
                                window.getRawTarget().get(h),sales,anchor));
                        var sequence=sequences.get(row);var history=histories.get(row);
                        long featureStart=System.nanoTime();
                        sequence.remove(0);
                        sequence.add(AutoregressiveLstmForecaster.createFeatureVector(window.getTargetDates().get(h),sales,history,sample.item.scaler));
                        history.add(sales);
                        profile.record("validation.featureReconstruction",featureStart);
                    }
                    }
                }
            }
        }
        profile.record("validation.rollout",evaluationStart);
        long metricsStart=System.nanoTime(); var metrics=GlobalMetrics.of(rows);
        profile.record("validation.metrics",metricsStart);profile.record("validation.total",evaluationStart);
        profile.count("validation.evaluations",1);profile.count("validation.forecasts",metrics.count);
        return metrics;
    }

    public static final class Epoch {
        public final int epoch;
        public final double trainingLoss,trainSeconds,validationSeconds;
        public final GlobalMetrics validation;
        private Epoch(int epoch,double loss,GlobalMetrics metrics,double trainSeconds,double validationSeconds) {
            this.epoch=epoch;trainingLoss=loss;validation=metrics;this.trainSeconds=trainSeconds;this.validationSeconds=validationSeconds;
        }
    }
    public static final class Fit {
        public final long seed;
        public final int bestEpoch;
        public final List<Epoch> history;
        public final GlobalMetrics validation;
        private Fit(long seed,int bestEpoch,List<Epoch> history,GlobalMetrics validation) {
            this.seed=seed;this.bestEpoch=bestEpoch;this.history=List.copyOf(history);this.validation=validation;
        }
    }
}
