package com.dl4j.salesforecast.output;

import com.dl4j.salesforecast.model.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** Global schema uses distinct files; legacy exporters and column meanings stay intact. */
public final class GlobalArtifactExporter {
    private GlobalArtifactExporter() { }
    public static String write(GlobalExperimentRunner.Result result,ExperimentConfiguration config,Path directory) throws IOException {
        if(result.data.storeId!=config.getStoreId() || !result.data.items.stream().map(i->i.id).collect(java.util.stream.Collectors.toList()).equals(config.getItemIds())
                || !result.fits.stream().map(f->f.seed).collect(java.util.stream.Collectors.toList()).equals(config.getSeeds())) {
            throw new IllegalArgumentException("Global result coverage mismatch");
        }
        for(var fit:result.fits) {
            if(fit.bestEpoch<1 || fit.bestEpoch>fit.history.size() || fit.history.size()>config.getMaxEpochs()
                    || !fit.validation.items.keySet().equals(new HashSet<>(config.getItemIds()))) { throw new IllegalArgumentException("Invalid fit coverage"); }
            for(var item:result.data.items) {
                if(fit.validation.predictions.get(item.id).size()!=item.validation.getWindows().size()*ExperimentConfiguration.HORIZON) { throw new IllegalArgumentException("Missing predictions"); }
            }
        }
        try(var scaling=writer(directory,"global-series-scaling.csv");var predictions=writer(directory,"global-validation-predictions.csv");
            var metrics=writer(directory,"global-item-seed-metrics.csv");var epochs=writer(directory,"global-epoch-history.csv")) {
            scaling.write("store,item,train_min,train_max,train_scale,train_mean,train_window_count,validation_window_count\n");
            for(var item:result.data.items) { scaling.write(String.format(Locale.ROOT,"%d,%d,%.10f,%.10f,%.10f,%.10f,%d,%d%n",result.data.storeId,item.id,item.scaler.getMin(),item.scaler.getMax(),item.scaler.getScale(),item.trainMean,item.train.getWindows().size(),item.validation.getWindows().size())); }
            predictions.write("store,item,seed,model,origin_date,target_date,horizon,actual_sales,weekly_baseline,predicted_residual,predicted_sales\n");
            metrics.write("store,item,seed,model,mae,rmse,best_epoch,epochs_run,forecast_count\n");
            epochs.write("seed,epoch,train_loss_scaled,validation_macro_mae,validation_macro_rmse,validation_pooled_mae,validation_pooled_rmse,best_flag,train_seconds,validation_seconds\n");
            for(var fit:result.fits) {
                for(var item:result.data.items) {
                    var score=fit.validation.items.get(item.id);
                    long metricStart=System.nanoTime();
                    metrics.write(String.format(Locale.ROOT,"%d,%d,%d,GlobalResidualAR,%.10f,%.10f,%d,%d,%d%n",result.data.storeId,item.id,fit.seed,score.mae,score.rmse,fit.bestEpoch,fit.history.size(),score.count));
                    result.profile.record("artifact.metrics",metricStart);
                    long predictionStart=System.nanoTime();
                    for(var p:fit.validation.predictions.get(item.id)) {
                        predictions.write(String.format(Locale.ROOT,"%d,%d,%d,GlobalResidualAR,%s,%s,%d,%.10f,%.10f,%.10f,%.10f%n",result.data.storeId,item.id,fit.seed,p.getOriginDate(),p.getTargetDate(),p.getHorizon(),p.getActual(),p.getWeeklyNaive(),p.getPrediction()-p.getWeeklyNaive(),p.getPrediction()));
                    }
                    result.profile.record("artifact.predictions",predictionStart);
                }
                long epochStart=System.nanoTime();
                for(var epoch:fit.history) {
                    var v=epoch.validation;
                    epochs.write(String.format(Locale.ROOT,"%d,%d,%.10f,%.10f,%.10f,%.10f,%.10f,%s,%.6f,%.6f%n",fit.seed,epoch.epoch,epoch.trainingLoss,v.macroMae,v.macroRmse,v.pooledMae,v.pooledRmse,epoch.epoch==fit.bestEpoch,epoch.trainSeconds,epoch.validationSeconds));
                }
                result.profile.record("artifact.metrics",epochStart);
            }
        }
        long reportStart=System.nanoTime();
        String report=GlobalExperimentReport.format(result,config);
        result.profile.record("artifact.reportGeneration",reportStart);
        return report;
    }
    private static BufferedWriter writer(Path directory,String name) throws IOException { return Files.newBufferedWriter(directory.resolve(name),StandardCharsets.UTF_8); }
}
