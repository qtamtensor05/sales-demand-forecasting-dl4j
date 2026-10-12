package com.dl4j.salesforecast.model;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Same-store population only. No reuse of the historical 12-series baseline. */
public final class GlobalExperimentReport {
    public static String format(GlobalExperimentRunner.Result result, ExperimentConfiguration config) {
        String report = format(result,config.isSmoke());
        return config.isBenchmark() ? report.replace("FULL SINGLE-STORE GLOBAL VALIDATION",
                "BENCHMARK ONLY: 50-item runtime check, not full model-quality experiment") : report;
    }
    private GlobalExperimentReport() { }
    public static String format(GlobalExperimentRunner.Result result, boolean smoke) {
        var data=result.data;var weekly=result.weekly;
        StringBuilder out=new StringBuilder(smoke?"SMOKE ONLY: pipeline verification, not model-quality evidence.\n":"FULL SINGLE-STORE GLOBAL VALIDATION\n");
        out.append(String.format(Locale.ROOT,"Store %d | items %s | global fits %d | train windows %d | validation origins %d | forecasts/seed %d%n",
                data.storeId,data.items.stream().map(i->i.id).collect(Collectors.toList()),result.fits.size(),data.train.size(),data.validation.size(),weekly.count));
        out.append("Train 2013-2015; validation 2016; test 2017 not evaluated/selected.\n"
                +"One model/seed, shared weights across all included items. MSE weekly residual, per-item train-only scaling.\n"
                +"Primary: per-item metric -> seed mean per item -> equal-weight macro over items.\n"
                +"Overall +/- is sample std of per-seed macro, NOT std across items or a confidence interval.\n"
                +"Pooled metric weights forecast points; pooled RMSE below is averaged across seeds, not an ensemble.\n"
                +"Train loss: sample-weighted mean of post-update minibatch scaled MSE (weights change between batches).\n\n");
        var mae=stats(result,f->f.validation.macroMae);var rmse=stats(result,f->f.validation.macroRmse);
        out.append("Model | Macro MAE | Macro RMSE | Pooled MAE | Pooled RMSE | RMSE improvement vs Weekly %\n");
        out.append(String.format(Locale.ROOT,"Weekly | %.6f | %.6f | %.6f | %.6f | 0%n",weekly.macroMae,weekly.macroRmse,weekly.pooledMae,weekly.pooledRmse));
        out.append(String.format(Locale.ROOT,"GlobalResidualAR | %.6f +/- %.6f | %.6f +/- %.6f | %.6f | %.6f | %s%n",
                mae.getMean(),mae.getStandardDeviation(),rmse.getMean(),rmse.getStandardDeviation(),
                mean(result,f->f.validation.pooledMae),mean(result,f->f.validation.pooledRmse),improvement(weekly.macroRmse,rmse.getMean())));
        out.append("\nPER ITEM (metrics averaged across seeds, not averaged predictions)\nItem | Tier | Weekly MAE | Weekly RMSE | Global MAE | Global RMSE | Improvement %\n");
        var tiers=tiers(data);int wins=0;
        for(var item:data.items) {
            double gm=mean(result,f->f.validation.items.get(item.id).mae),gr=mean(result,f->f.validation.items.get(item.id).rmse);
            var w=weekly.items.get(item.id);if(gr<w.rmse) { wins++; }
            out.append(String.format(Locale.ROOT,"%d | %s | %.6f | %.6f | %.6f | %.6f | %s%n",item.id,tiers.get(item.id),w.mae,w.rmse,gm,gr,improvement(w.rmse,gr)));
        }
        out.append("Global beats Weekly (strict item seed-mean RMSE): ").append(wins).append('/').append(data.items.size()).append('\n');
        out.append("\nHORIZON | Weekly macro RMSE | Global macro RMSE | Improvement %\n");
        for(int h=1;h<=ExperimentConfiguration.HORIZON;h++) {
            final int horizon=h;
            double w=weekly.items.values().stream().mapToDouble(i->i.horizonRmse(horizon)).average().orElseThrow();
            double g=mean(result,f->f.validation.items.values().stream().mapToDouble(i->i.horizonRmse(horizon)).average().orElseThrow());
            out.append(String.format(Locale.ROOT,"H%d | %.6f | %.6f | %s%n",h,w,g,improvement(w,g)));
        }
        out.append("\nDEMAND TIER (train mean ranked tertiles, all items retained)\nTier | Items | Weekly RMSE | Global RMSE | Improvement %\n");
        for(String tier:List.of("LOW","MEDIUM","HIGH")) {
            var ids=tiers.entrySet().stream().filter(e->e.getValue().equals(tier)).map(Map.Entry::getKey).collect(Collectors.toList());
            if(ids.isEmpty()) { continue; }
            double w=ids.stream().mapToDouble(i->weekly.items.get(i).rmse).average().orElseThrow();
            double g=mean(result,f->ids.stream().mapToDouble(i->f.validation.items.get(i).rmse).average().orElseThrow());
            out.append(String.format(Locale.ROOT,"%s | %d | %.6f | %.6f | %s%n",tier,ids.size(),w,g,improvement(w,g)));
        }
        out.append("\nSEED | macro MAE | macro RMSE | pooled RMSE | best epoch | epochs run\n");
        for(var fit:result.fits) { out.append(String.format(Locale.ROOT,"%d | %.6f | %.6f | %.6f | %d | %d%n",fit.seed,fit.validation.macroMae,fit.validation.macroRmse,fit.validation.pooledRmse,fit.bestEpoch,fit.history.size())); }
        out.append(String.format(Locale.ROOT,"Seed macro RMSE mean %.6f sample std %.6f min %.6f max %.6f%n",rmse.getMean(),rmse.getStandardDeviation(),
                result.fits.stream().mapToDouble(f->f.validation.macroRmse).min().orElseThrow(),result.fits.stream().mapToDouble(f->f.validation.macroRmse).max().orElseThrow()));
        var selected=com.dl4j.salesforecast.output.GlobalModelArtifacts.select(result.fits);
        out.append(String.format(Locale.ROOT,"Selected deployment model: seed %d | epoch %d | validation macro RMSE %.6f | checkpoint models/seed-%d/model.zip%n",
                selected.seed,selected.bestEpoch,selected.validation.macroRmse,selected.seed));
        out.append("Validation selects epoch and reports metrics; overlapping targets; test was observed historically, not pristine holdout.\n");
        return out.toString();
    }
    public static Map<Integer,String> tiers(SingleStoreGlobalDataset data) {
        var ordered=new ArrayList<>(data.items);ordered.sort(Comparator.comparingDouble((SingleStoreGlobalDataset.Item i)->i.trainMean).thenComparingInt(i->i.id));
        Map<Integer,String> result=new TreeMap<>();String[] names={"LOW","MEDIUM","HIGH"};
        for(int tier=0;tier<3;tier++) for(int index=tier*ordered.size()/3;index<(tier+1)*ordered.size()/3;index++) { result.put(ordered.get(index).id,names[tier]); }
        return result;
    }
    private static ExperimentRunner.Stats stats(GlobalExperimentRunner.Result r,ToDoubleFunction<GlobalResidualLstmForecaster.Fit> f) {
        return ExperimentRunner.summarizeValues(r.fits.stream().mapToDouble(f).boxed().collect(Collectors.toList()));
    }
    private static double mean(GlobalExperimentRunner.Result r,ToDoubleFunction<GlobalResidualLstmForecaster.Fit> f) { return stats(r,f).getMean(); }
    private static String improvement(double baseline,double value) { return baseline==0?"N/A":String.format(Locale.ROOT,"%+.4f",100*(baseline-value)/baseline); }
}
