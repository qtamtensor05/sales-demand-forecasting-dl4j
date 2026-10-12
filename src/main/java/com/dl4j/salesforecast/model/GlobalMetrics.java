package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.evaluation.WeeklyNaiveBaseline;
import java.util.*;

/** Original-sales macro across items and point-weighted pooled metrics are deliberately separate. */
public final class GlobalMetrics {
    public final Map<Integer, ItemScore> items;
    public final Map<Integer, List<ValidationPrediction>> predictions;
    public final double macroMae, macroRmse, pooledMae, pooledRmse;
    public final long count;

    private GlobalMetrics(Map<Integer, ItemScore> scores, Map<Integer, List<ValidationPrediction>> predictions) {
        if (scores.isEmpty()) { throw new IllegalArgumentException("No item metrics"); }
        this.items = Collections.unmodifiableMap(new TreeMap<>(scores));
        Map<Integer,List<ValidationPrediction>> copy = new TreeMap<>();
        predictions.forEach((id, rows) -> copy.put(id,List.copyOf(rows)));
        this.predictions = Collections.unmodifiableMap(copy);
        macroMae = scores.values().stream().mapToDouble(s->s.mae).average().orElseThrow();
        macroRmse = scores.values().stream().mapToDouble(s->s.rmse).average().orElseThrow();
        count = scores.values().stream().mapToLong(s->s.count).sum();
        pooledMae = scores.values().stream().mapToDouble(s->s.mae*s.count).sum()/count;
        pooledRmse = Math.sqrt(scores.values().stream().mapToDouble(s->s.rmse*s.rmse*s.count).sum()/count);
        if (!Double.isFinite(macroRmse) || !Double.isFinite(pooledRmse)) { throw new IllegalStateException("Nonfinite global metrics"); }
    }
    public static GlobalMetrics of(Map<Integer,List<ValidationPrediction>> predictions) {
        Map<Integer,ItemScore> scores = new TreeMap<>();
        predictions.forEach((id, rows)->scores.put(id,new ItemScore(rows)));
        return new GlobalMetrics(scores,predictions);
    }
    public static GlobalMetrics weekly(SingleStoreGlobalDataset data) {
        Map<Integer,ItemScore> scores = new TreeMap<>();
        for(var item:data.items) { scores.put(item.id,new ItemScore(WeeklyNaiveBaseline.evaluate(item.validation))); }
        return new GlobalMetrics(scores,Map.of());
    }
    public GlobalMetrics withoutPredictions() { return new GlobalMetrics(items,Map.of()); }
    public static boolean improves(double candidate, double best) { return Double.isFinite(candidate) && candidate < best-1e-8; }

    public static final class ItemScore {
        public final long count;
        public final double mae,rmse;
        private final double[] horizonMae,horizonRmse;
        private ItemScore(List<ValidationPrediction> rows) {
            count=rows.size(); double absolute=0,squared=0;
            horizonMae=new double[ExperimentConfiguration.HORIZON];horizonRmse=new double[ExperimentConfiguration.HORIZON];
            int[] counts=new int[ExperimentConfiguration.HORIZON];
            for(var p:rows) {
                double error=p.getPrediction()-p.getActual();
                if(!Double.isFinite(error)) { throw new IllegalArgumentException("Nonfinite prediction"); }
                absolute+=Math.abs(error);squared+=error*error;int h=p.getHorizon()-1;
                horizonMae[h]+=Math.abs(error);horizonRmse[h]+=error*error;counts[h]++;
            }
            mae=absolute/count;rmse=Math.sqrt(squared/count);
            for(int h=0;h<counts.length;h++) {
                if(counts[h]==0) { throw new IllegalArgumentException("Missing horizon"); }
                horizonMae[h]/=counts[h];horizonRmse[h]=Math.sqrt(horizonRmse[h]/counts[h]);
            }
        }
        private ItemScore(WeeklyNaiveBaseline.BaselineResult result) {
            count=result.getForecastCount();mae=result.getMae();rmse=result.getRmse();
            horizonMae=result.getHorizons().stream().mapToDouble(h->h.getMae()).toArray();
            horizonRmse=result.getHorizons().stream().mapToDouble(h->h.getRmse()).toArray();
        }
        public double horizonMae(int h) { return horizonMae[h-1]; }
        public double horizonRmse(int h) { return horizonRmse[h-1]; }
    }
}
