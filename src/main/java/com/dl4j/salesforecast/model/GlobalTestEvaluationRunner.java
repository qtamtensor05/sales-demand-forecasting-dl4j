package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.output.GlobalModelArtifacts;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import com.google.gson.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Checkpoint-only Test 2017 evaluation. This class has no training or checkpoint-writing path. */
public final class GlobalTestEvaluationRunner {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<Long> SEEDS=List.of(42L,123L,2026L);
    private static final Map<Long,Integer> EXPECTED_EPOCHS=Map.of(42L,11,123L,20,2026L,13);
    private GlobalTestEvaluationRunner() { }

    public static Result run(Path sourceRun,Path dataset) throws IOException {
        long totalStart=System.nanoTime(); sourceRun=sourceRun.toAbsolutePath().normalize(); dataset=dataset.toAbsolutePath().normalize();
        if(!Files.isDirectory(sourceRun)) throw new IOException("Missing source run: "+sourceRun);
        Path output=sourceRun.resolve("test-evaluation");
        if(Files.exists(output)) throw new IOException("Refusing to overwrite existing test evaluation: "+output);
        JsonObject run=readJson(sourceRun.resolve("run.json"));
        require("SUCCESS".equals(string(run,"status")),"Source run is not SUCCESS");
        require(!bool(run,"testUsedForSelection"),"Source run used test for selection");
        require(SEEDS.equals(longList(run,"seeds")),"Expected source seeds 42,123,2026");
        require(!bool(run,"testEvaluated"),"Source run already evaluated test");
        JsonObject selection=readJson(sourceRun.resolve("deployment/selection.json"));
        require(selection.get("selectedSeed").getAsLong()==123L,"Deployment seed must remain validation-selected seed 123");
        String expectedHash=string(run,"datasetSha256"); String actualHash=sha256(dataset);
        require(expectedHash.equalsIgnoreCase(actualHash),"Dataset SHA-256 mismatch");
        Files.createDirectory(output);
        Instant started=Instant.now(); Map<Long,String> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();
        Map<Long,Double> loadSeconds=new LinkedHashMap<>(),inferenceSeconds=new LinkedHashMap<>();
        try {
            long dataStart=System.nanoTime(); Map<Integer,SalesPreprocessor.MinMaxScaler> scalers=readScalers(sourceRun.resolve("global-series-scaling.csv"));
            var config=ExperimentConfiguration.fromArgs(new String[]{"--global-store=1","--seeds=42,123,2026","--validation-batch-size="+run.get("validationBatchSize").getAsInt()});
            var series=TimeSeriesAnalyzer.analyzeStoreQuietly(dataset.toString(),1); require(series.keySet().equals(expectedItems()),"Dataset item coverage must be 1..50");
            List<SalesPreprocessor.PreprocessingResult> prepared=new ArrayList<>();
            for(int item:expectedItems()) {
                var artifact=scalers.get(item); var s=series.get(item);
                var check=SalesPreprocessor.preprocess(s,LocalDate.of(2015,12,31),LocalDate.of(2016,12,31),30,7);
                require(equalScaler(artifact,check.getScaler()),"Artifact scaler differs from train-only scaler for item "+item);
                prepared.add(SalesPreprocessor.preprocessWithScaler(s,LocalDate.of(2015,12,31),LocalDate.of(2016,12,31),30,7,artifact));
            }
            var data=new SingleStoreGlobalDataset(1,prepared); validateTest(data);
            double dataLoadSeconds=seconds(dataStart); long metricsStart=System.nanoTime(); GlobalMetrics weekly=weekly(data.test); double metricsSeconds=seconds(metricsStart);
            Map<Long,SeedResult> results=new LinkedHashMap<>(); Map<Long,Integer> epochs=new LinkedHashMap<>(); Map<Long,Double> validationRmse=new LinkedHashMap<>();
            for(long seed:SEEDS) {
                Path model=sourceRun.resolve("models/seed-"+seed+"/model.zip"); Path metadataPath=model.getParent().resolve("metadata.json");
                JsonObject metadata=readJson(metadataPath); validateMetadata(metadata,run,seed,actualHash);
                int epoch=metadata.get("bestEpoch").getAsInt(); validateEpoch(sourceRun.resolve("global-epoch-history.csv"),seed,epoch);
                require(epoch==EXPECTED_EPOCHS.get(seed),"Unexpected best epoch for seed "+seed);
                epochs.put(seed,epoch); validationRmse.put(seed,metadata.get("validationMacroRMSE").getAsDouble()); before.put(seed,sha256(model));
                long loadStart=System.nanoTime(); MultiLayerNetwork network=GlobalModelArtifacts.loadCheckpoint(model); loadSeconds.put(seed,seconds(loadStart));
                try {
                    var params=network.params().dup(); long inferenceStart=System.nanoTime();
                    GlobalMetrics evaluated=GlobalResidualLstmForecaster.evaluate(network,data.test,run.get("validationBatchSize").getAsInt());
                    inferenceSeconds.put(seed,seconds(inferenceStart));
                    require(params.equalsWithEps(network.params(),0),"Inference changed weights for seed "+seed); params.close();
                    results.put(seed,new SeedResult(seed,epoch,validationRmse.get(seed),evaluated));
                } finally { network.close(); }
                after.put(seed,sha256(model)); require(before.get(seed).equals(after.get(seed)),"Checkpoint changed for seed "+seed);
            }
            validateKeys(data,weekly,results); metricsStart=System.nanoTime(); Summary summary=new Summary(data,weekly,results); metricsSeconds+=seconds(metricsStart);
            long exportStart=System.nanoTime(); export(output,sourceRun,actualHash,started,data,weekly,results,summary,before,after,epochs,
                    dataLoadSeconds,loadSeconds,inferenceSeconds,metricsSeconds,totalStart); double exportSeconds=seconds(exportStart);
            patchTimings(output.resolve("test-run.json"),exportSeconds,seconds(totalStart),Instant.now());
            return new Result(output,data,weekly,results,summary);
        } catch(Throwable failure) {
            writeFailure(output,sourceRun,actualHash,started,before,after,failure,totalStart);
            if(failure instanceof IOException) throw (IOException)failure;
            throw new IOException("Test evaluation failed",failure);
        }
    }

    static GlobalMetrics weekly(List<SingleStoreGlobalDataset.Sample> samples) {
        Map<Integer,List<ValidationPrediction>> rows=new TreeMap<>();
        for(var sample:samples) for(int h=0;h<ExperimentConfiguration.HORIZON;h++) {
            var w=sample.window; double baseline=ResidualLstmForecaster.baseline(w,h);
            rows.computeIfAbsent(sample.item.id,k->new ArrayList<>()).add(new ValidationPrediction(
                    w.getInputDates().get(ExperimentConfiguration.INPUT_DAYS-1),w.getTargetDates().get(h),h+1,w.getRawTarget().get(h),baseline,baseline));
        }
        return GlobalMetrics.of(rows);
    }
    private static void validateTest(SingleStoreGlobalDataset data) {
        require(data.items.size()==50 && data.test.size()==data.items.stream().mapToInt(i->i.test.getWindows().size()).sum(),"Invalid test coverage");
        require(data.test.stream().allMatch(s->s.window.getTargetDates().stream().allMatch(d->d.getYear()==2017)),"Test targets must stay inside 2017");
    }
    private static void validateKeys(SingleStoreGlobalDataset data,GlobalMetrics weekly,Map<Long,SeedResult> results) {
        Set<String> expected=keys(weekly); require(expected.size()==data.test.size()*7L,"Duplicate Weekly keys");
        for(var result:results.values()) require(keys(result.test).equals(expected),"Model and Weekly test keys differ for seed "+result.seed);
    }
    private static Set<String> keys(GlobalMetrics metrics) {
        Set<String> keys=new HashSet<>(); metrics.predictions.forEach((item,rows)->rows.forEach(p->keys.add(item+"|"+p.getOriginDate()+"|"+p.getTargetDate()+"|"+p.getHorizon()))); return keys;
    }
    private static Map<Integer,SalesPreprocessor.MinMaxScaler> readScalers(Path path) throws IOException {
        Map<Integer,SalesPreprocessor.MinMaxScaler> result=new TreeMap<>();
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8);var csv=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            for(var row:csv) { int store=Integer.parseInt(row.get("store")),item=Integer.parseInt(row.get("item")); require(store==1,"Scaler store mismatch");
                double min=Double.parseDouble(row.get("train_min")),max=Double.parseDouble(row.get("train_max")),scale=Double.parseDouble(row.get("train_scale"));
                var scaler=SalesPreprocessor.MinMaxScaler.fromTrainBounds(min,max); require(Math.abs(scaler.getScale()-scale)<=1e-9,"Invalid scaler scale for item "+item);
                require(result.put(item,scaler)==null,"Duplicate scaler item "+item); }
        }
        require(result.keySet().equals(expectedItems()),"Scaler item coverage must be 1..50"); return result;
    }
    private static boolean equalScaler(SalesPreprocessor.MinMaxScaler a,SalesPreprocessor.MinMaxScaler b) { return Math.abs(a.getMin()-b.getMin())<=1e-9&&Math.abs(a.getMax()-b.getMax())<=1e-9&&Math.abs(a.getScale()-b.getScale())<=1e-9; }
    private static void validateMetadata(JsonObject metadata,JsonObject run,long seed,String hash) {
        require(bool(metadata,"usable")&&metadata.get("seed").getAsLong()==seed,"Invalid metadata seed "+seed);
        require(string(metadata,"runId").equals(string(run,"runId"))&&metadata.get("storeId").getAsInt()==1,"Metadata source run mismatch");
        require(hash.equalsIgnoreCase(string(metadata,"datasetSha256")),"Metadata dataset hash mismatch");
        require(!bool(metadata,"testUsedForSelection"),"Metadata used test for selection");
        require("PER_ITEM_MIN_MAX_FIT_ON_TRAIN_ONLY_NO_REFIT".equals(string(metadata,"scalingPolicy")),"Scaling policy mismatch");
        require(metadata.get("inputDays").getAsInt()==30&&metadata.get("horizon").getAsInt()==7,"Model window schema mismatch");
    }
    private static void validateEpoch(Path path,long seed,int expected) throws IOException {
        int best=-1; double min=Double.POSITIVE_INFINITY; int flags=0;
        try(var reader=Files.newBufferedReader(path);var csv=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            for(var row:csv) if(Long.parseLong(row.get("seed"))==seed) {
                double rmse=Double.parseDouble(row.get("validation_macro_rmse")); if(rmse<min){min=rmse;best=Integer.parseInt(row.get("epoch"));}
                if(Boolean.parseBoolean(row.get("best_flag"))){flags++;require(Integer.parseInt(row.get("epoch"))==expected,"Best flag epoch mismatch");}
            }
        }
        require(best==expected&&flags==1,"Best validation epoch mismatch for seed "+seed);
    }
    private static void export(Path out,Path source,String hash,Instant started,SingleStoreGlobalDataset data,GlobalMetrics weekly,
            Map<Long,SeedResult> results,Summary summary,Map<Long,String> before,Map<Long,String> after,Map<Long,Integer> epochs,
            double dataSeconds,Map<Long,Double> loadSeconds,Map<Long,Double> inferenceSeconds,double metricSeconds,long totalStart) throws IOException {
        writeSeedMetrics(out,results); writeItemMetrics(out,weekly,results); writeHorizonMetrics(out,weekly,results); writePredictions(out,data,results);
        String report=summaryText(summary,results); Files.writeString(out.resolve("test-summary.txt"),report,StandardCharsets.UTF_8);
        Files.writeString(out.resolve("test-report.html"),"<!doctype html><html><head><meta charset=\"utf-8\"><title>Global Test Evaluation</title></head><body><pre>"+escape(report)+"</pre></body></html>",StandardCharsets.UTF_8);
        Map<String,Object> manifest=baseManifest(source,hash,started,before,after); manifest.put("seeds",SEEDS);manifest.put("validationBestEpochs",epochs);
        manifest.put("testPeriod","2017-01-01..2017-12-31");manifest.put("inputDays",30);manifest.put("horizon",7);manifest.put("itemIds",new ArrayList<>(expectedItems()));
        manifest.put("scalingSource","global-series-scaling.csv");manifest.put("evaluationOnly",true);manifest.put("trainingPerformed",false);manifest.put("weightsUpdated",false);manifest.put("testUsedForSelection",false);
        manifest.put("testOrigins",data.test.size());manifest.put("modelForecastPoints",results.values().stream().mapToLong(r->r.test.count).sum());manifest.put("dataLoadSeconds",dataSeconds);
        manifest.put("checkpointLoadSeconds",loadSeconds);manifest.put("inferenceSeconds",inferenceSeconds);manifest.put("metricsSeconds",metricSeconds);manifest.put("exportSeconds",0);manifest.put("totalSeconds",seconds(totalStart));
        manifest.put("completedAt",Instant.now().toString());manifest.put("status","SUCCESS");Files.writeString(out.resolve("test-run.json"),JSON.toJson(manifest)+"\n");
    }
    private static void writeSeedMetrics(Path out,Map<Long,SeedResult> results)throws IOException { StringBuilder s=new StringBuilder("seed,best_epoch,validation_macro_rmse,test_macro_mae,test_macro_rmse,test_pooled_mae,test_pooled_rmse,delta_test_minus_validation,forecast_count\n"); for(var r:results.values())s.append(String.format(Locale.ROOT,"%d,%d,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f,%d%n",r.seed,r.bestEpoch,r.validationRmse,r.test.macroMae,r.test.macroRmse,r.test.pooledMae,r.test.pooledRmse,r.test.macroRmse-r.validationRmse,r.test.count));Files.writeString(out.resolve("test-seed-metrics.csv"),s); }
    private static void writeItemMetrics(Path out,GlobalMetrics weekly,Map<Long,SeedResult> results)throws IOException { StringBuilder s=new StringBuilder("item,seed,weekly_mae,weekly_rmse,global_mae,global_rmse,improvement_rmse_pct,forecast_count\n");for(int item:expectedItems())for(var r:results.values()){GlobalMetrics.ItemScore w=weekly.items.get(item);GlobalMetrics.ItemScore g=r.test.items.get(item);s.append(String.format(Locale.ROOT,"%d,%d,%.10f,%.10f,%.10f,%.10f,%.10f,%d%n",item,r.seed,w.mae,w.rmse,g.mae,g.rmse,improvement(w.rmse,g.rmse),g.count));}Files.writeString(out.resolve("test-item-seed-metrics.csv"),s); }
    private static void writeHorizonMetrics(Path out,GlobalMetrics weekly,Map<Long,SeedResult> results)throws IOException {StringBuilder s=new StringBuilder("horizon,seed,weekly_macro_mae,weekly_macro_rmse,global_macro_mae,global_macro_rmse,improvement_rmse_pct\n");for(int h=1;h<=7;h++)for(var r:results.values()){final int x=h;double wm=weekly.items.values().stream().mapToDouble(i->i.horizonMae(x)).average().orElseThrow(),wr=weekly.items.values().stream().mapToDouble(i->i.horizonRmse(x)).average().orElseThrow(),gm=r.test.items.values().stream().mapToDouble(i->i.horizonMae(x)).average().orElseThrow(),gr=r.test.items.values().stream().mapToDouble(i->i.horizonRmse(x)).average().orElseThrow();s.append(String.format(Locale.ROOT,"%d,%d,%.10f,%.10f,%.10f,%.10f,%.10f%n",h,r.seed,wm,wr,gm,gr,improvement(wr,gr)));}Files.writeString(out.resolve("test-horizon-metrics.csv"),s);}
    private static void writePredictions(Path out,SingleStoreGlobalDataset data,Map<Long,SeedResult> results)throws IOException {try(var w=Files.newBufferedWriter(out.resolve("test-predictions.csv"))){w.write("store,item,seed,origin_date,target_date,horizon,actual_sales,weekly_baseline,predicted_residual,predicted_sales\n");for(var r:results.values())for(int item:expectedItems())for(var p:r.test.predictions.get(item))w.write(String.format(Locale.ROOT,"1,%d,%d,%s,%s,%d,%.10f,%.10f,%.10f,%.10f%n",item,r.seed,p.getOriginDate(),p.getTargetDate(),p.getHorizon(),p.getActual(),p.getWeeklyNaive(),p.getPrediction()-p.getWeeklyNaive(),p.getPrediction()));}}
    private static String summaryText(Summary s,Map<Long,SeedResult> results){StringBuilder o=new StringBuilder("GlobalResidualAR Test 2017 evaluation\n\nTest 2017 is evaluation-only.\nTest metrics were not used for seed, epoch, hyperparameter or checkpoint selection.\nDeployment seed remains seed 123 selected from Validation 2016.\n\nSeed | Best epoch | Validation Macro RMSE | Test Macro RMSE | Delta\n");for(var r:results.values())o.append(String.format(Locale.ROOT,"%d | %d | %.6f | %.6f | %+.6f%n",r.seed,r.bestEpoch,r.validationRmse,r.test.macroRmse,r.test.macroRmse-r.validationRmse));o.append(String.format(Locale.ROOT,"%nTest Macro MAE: %.6f +/- %.6f sample std%nTest Macro RMSE: %.6f +/- %.6f sample std%nPooled MAE mean across seeds: %.6f%nPooled RMSE mean across seeds: %.6f%nWeekly Naive Macro MAE/RMSE: %.6f / %.6f%nWeekly Naive Pooled MAE/RMSE: %.6f / %.6f%nGlobal improvement vs Weekly Macro RMSE: %+.4f%%%nGlobal improvement vs Weekly Pooled RMSE: %+.4f%%%nGlobal beats Weekly: %d / 50 items%n",s.macroMaeMean,s.macroMaeStd,s.macroRmseMean,s.macroRmseStd,s.pooledMaeMean,s.pooledRmseMean,s.weekly.macroMae,s.weekly.macroRmse,s.weekly.pooledMae,s.weekly.pooledRmse,improvement(s.weekly.macroRmse,s.macroRmseMean),improvement(s.weekly.pooledRmse,s.pooledRmseMean),s.wins));o.append("\nItem | Weekly Test RMSE | Global Test RMSE mean | Improvement %\n");for(var x:s.items)o.append(String.format(Locale.ROOT,"%d | %.6f | %.6f | %+.4f%n",x.id,x.weekly,x.global,improvement(x.weekly,x.global)));o.append("\nHorizon | Weekly Macro RMSE | Global Macro RMSE | Improvement %\n");s.horizons.forEach(x->o.append(String.format(Locale.ROOT,"H%d | %.6f | %.6f | %+.4f%n",x.id,x.weekly,x.global,improvement(x.weekly,x.global))));o.append("\nTier | Items | Weekly RMSE | Global RMSE | Improvement %\n");s.tiers.forEach(x->o.append(String.format(Locale.ROOT,"%s | %d | %.6f | %.6f | %+.4f%n",x.name,x.count,x.weekly,x.global,improvement(x.weekly,x.global))));return o.toString();}
    private static Map<String,Object> baseManifest(Path source,String hash,Instant started,Map<Long,String>b,Map<Long,String>a){Map<String,Object>m=new LinkedHashMap<>();m.put("sourceRunId",source.getFileName().toString());m.put("sourceRunPath",source.toString());m.put("datasetSha256",hash);m.put("checkpointSha256Before",b);m.put("checkpointSha256After",a);m.put("startedAt",started.toString());return m;}
    private static void patchTimings(Path path,double export,double total,Instant completed)throws IOException {JsonObject j=readJson(path);j.addProperty("exportSeconds",export);j.addProperty("totalSeconds",total);j.addProperty("completedAt",completed.toString());Files.writeString(path,JSON.toJson(j)+"\n");}
    private static void writeFailure(Path out,Path source,String hash,Instant started,Map<Long,String>b,Map<Long,String>a,Throwable e,long total){try{Map<String,Object>m=baseManifest(source,hash,started,b,a);m.put("evaluationOnly",true);m.put("trainingPerformed",false);m.put("weightsUpdated",false);m.put("testUsedForSelection",false);m.put("completedAt",Instant.now().toString());m.put("totalSeconds",seconds(total));m.put("status","FAILED");m.put("error",e.toString());Files.writeString(out.resolve("test-run.json"),JSON.toJson(m)+"\n");}catch(Exception ignored){}}
    private static JsonObject readJson(Path p)throws IOException{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    private static String sha256(Path p)throws IOException{try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[]b=new byte[1<<16];for(int n;(n=in.read(b))>=0;)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:d.digest())s.append(String.format("%02x",x));return s.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static SortedSet<Integer> expectedItems(){SortedSet<Integer>s=new TreeSet<>();for(int i=1;i<=50;i++)s.add(i);return s;}
    private static String string(JsonObject j,String k){return j.get(k).getAsString();} private static boolean bool(JsonObject j,String k){return j.get(k).getAsBoolean();}
    private static List<Long> longList(JsonObject j,String k){List<Long>x=new ArrayList<>();j.getAsJsonArray(k).forEach(v->x.add(v.getAsLong()));return x;}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);} private static double seconds(long n){return(System.nanoTime()-n)/1e9;} private static double improvement(double w,double g){return w==0?Double.NaN:100*(w-g)/w;} private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}

    public static final class SeedResult {public final long seed;public final int bestEpoch;public final double validationRmse;public final GlobalMetrics test;SeedResult(long s,int e,double v,GlobalMetrics t){seed=s;bestEpoch=e;validationRmse=v;test=t;}}
    public static final class Result {public final Path output;public final SingleStoreGlobalDataset data;public final GlobalMetrics weekly;public final Map<Long,SeedResult> seeds;public final Summary summary;Result(Path o,SingleStoreGlobalDataset d,GlobalMetrics w,Map<Long,SeedResult>s,Summary x){output=o;data=d;weekly=w;seeds=Map.copyOf(s);summary=x;}}
    public static final class Summary {final GlobalMetrics weekly;final double macroMaeMean,macroMaeStd,macroRmseMean,macroRmseStd,pooledMaeMean,pooledRmseMean;final int wins;final List<Group> items,horizons,tiers;Summary(SingleStoreGlobalDataset d,GlobalMetrics w,Map<Long,SeedResult>r){weekly=w;List<GlobalMetrics>m=r.values().stream().map(x->x.test).collect(Collectors.toList());macroMaeMean=mean(m,x->x.macroMae);macroMaeStd=std(m,x->x.macroMae,macroMaeMean);macroRmseMean=mean(m,x->x.macroRmse);macroRmseStd=std(m,x->x.macroRmse,macroRmseMean);pooledMaeMean=mean(m,x->x.pooledMae);pooledRmseMean=mean(m,x->x.pooledRmse);items=new ArrayList<>();int win=0;for(int i:expectedItems()){double global=mean(m,x->x.items.get(i).rmse);items.add(new Group("ITEM",i,w.items.get(i).rmse,global));if(global<w.items.get(i).rmse)win++;}wins=win;horizons=new ArrayList<>();for(int h=1;h<=7;h++){final int z=h;horizons.add(new Group("H"+h,h,w.items.values().stream().mapToDouble(x->x.horizonRmse(z)).average().orElseThrow(),mean(m,x->x.items.values().stream().mapToDouble(y->y.horizonRmse(z)).average().orElseThrow())));}var map=GlobalExperimentReport.tiers(d);tiers=new ArrayList<>();for(String tier:List.of("LOW","MEDIUM","HIGH")){var ids=map.entrySet().stream().filter(e->e.getValue().equals(tier)).map(Map.Entry::getKey).collect(Collectors.toList());tiers.add(new Group(tier,ids.size(),ids.stream().mapToDouble(i->w.items.get(i).rmse).average().orElseThrow(),mean(m,x->ids.stream().mapToDouble(i->x.items.get(i).rmse).average().orElseThrow())));}}}
    private static double mean(List<GlobalMetrics>m,java.util.function.ToDoubleFunction<GlobalMetrics>f){return m.stream().mapToDouble(f).average().orElseThrow();}private static double std(List<GlobalMetrics>m,java.util.function.ToDoubleFunction<GlobalMetrics>f,double mean){return Math.sqrt(m.stream().mapToDouble(x->{double d=f.applyAsDouble(x)-mean;return d*d;}).sum()/(m.size()-1));}
    static final class Group{final String name;final int id,count;final double weekly,global;Group(String n,int c,double w,double g){name=n;id=n.startsWith("H")?Integer.parseInt(n.substring(1)):0;count=c;weekly=w;global=g;}}
}
