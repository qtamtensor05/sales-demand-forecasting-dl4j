package com.dl4j.salesforecast.output;

import com.dl4j.salesforecast.model.*;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Atomic, load-verified global checkpoints and the self-contained deployment package. */
public final class GlobalModelArtifacts {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private GlobalModelArtifacts() { }

    public static Path saveCheckpoint(MultiLayerNetwork network, Path seedDirectory) throws IOException {
        Files.createDirectories(seedDirectory);
        Path target=seedDirectory.resolve("model.zip"), temporary=seedDirectory.resolve("model.zip.tmp");
        Files.deleteIfExists(temporary);
        try {
            ModelSerializer.writeModel(network,temporary.toFile(),true);
            MultiLayerNetwork restored=ModelSerializer.restoreMultiLayerNetwork(temporary.toFile(),true);
            try {
                if(!network.params().equalsWithEps(restored.params(),1e-12)) throw new IOException("Checkpoint parameter verification failed");
                try(var input=Nd4j.zeros(1,GlobalBatch.CHANNELS,ExperimentConfiguration.INPUT_DAYS);
                    var before=network.output(input,false);var after=restored.output(input,false)) {
                    if(!before.equalsWithEps(after,1e-7)) throw new IOException("Checkpoint prediction verification failed");
                }
            } finally { restored.close(); }
            move(temporary,target);
            return target;
        } catch(Throwable failure) {
            Files.deleteIfExists(temporary);
            if(failure instanceof IOException) throw (IOException)failure;
            throw new IOException("Cannot save verified checkpoint",failure);
        }
    }

    public static MultiLayerNetwork loadCheckpoint(Path model) throws IOException {
        if(!Files.isRegularFile(model) || Files.size(model)==0) throw new IOException("Missing or empty checkpoint: "+model);
        try { return ModelSerializer.restoreMultiLayerNetwork(model.toFile(),true); }
        catch(Exception failure) { throw new IOException("Corrupt checkpoint: "+model,failure); }
    }

    public static GlobalResidualLstmForecaster.Fit select(List<GlobalResidualLstmForecaster.Fit> fits) {
        return fits.stream().min(Comparator
                .comparingDouble((GlobalResidualLstmForecaster.Fit f)->f.validation.macroRmse)
                .thenComparingDouble(f->f.validation.macroMae).thenComparingLong(f->f.seed)).orElseThrow();
    }

    public static void finalizeArtifacts(GlobalExperimentRunner.Result result, ExperimentConfiguration config,
                                         Path runDirectory, String runId, String datasetHash) throws IOException {
        for(var fit:result.fits) {
            if(fit.checkpoint==null || !Files.isRegularFile(fit.checkpoint)) throw new IOException("Missing seed checkpoint: "+fit.seed);
            MultiLayerNetwork verified=loadCheckpoint(fit.checkpoint);verified.close();
            Map<String,Object> metadata=modelMetadata(result,config,fit,runId,datasetHash);
            writeJsonAtomic(fit.checkpoint.getParent().resolve("metadata.json"),metadata);
        }
        var selected=select(result.fits); Path deployment=runDirectory.resolve("deployment");Files.createDirectories(deployment);
        copyAtomic(selected.checkpoint,deployment.resolve("model.zip"));
        copyAtomic(selected.checkpoint.getParent().resolve("metadata.json"),deployment.resolve("model-metadata.json"));
        writeScalers(result,deployment.resolve("item-scalers.csv"));
        writeJsonAtomic(deployment.resolve("item-mapping.json"),itemMapping(config));
        writeJsonAtomic(deployment.resolve("feature-schema.json"),featureSchema(config));
        Map<String,Object> selection=new LinkedHashMap<>();
        selection.put("selectedSeed",selected.seed);selection.put("selectedEpoch",selected.bestEpoch);
        selection.put("selectionMetric","validationMacroRMSE");selection.put("validationMacroRMSE",selected.validation.macroRmse);
        selection.put("sourceCheckpoint",runDirectory.relativize(selected.checkpoint).toString().replace('\\','/'));
        selection.put("runId",runId);selection.put("datasetSha256",datasetHash);
        selection.put("trainBatchSize",config.getBatchSize());selection.put("validationBatchSize",config.getValidationBatchSize());
        selection.put("tieBreak","lower validation macro RMSE, then lower validation macro MAE, then lower seed ID");
        writeJsonAtomic(deployment.resolve("selection.json"),selection);
    }

    private static Map<String,Object> modelMetadata(GlobalExperimentRunner.Result result,ExperimentConfiguration config,
                                                     GlobalResidualLstmForecaster.Fit fit,String runId,String datasetHash) {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("usable",true);values.put("runId",runId);values.put("storeId",result.data.storeId);values.put("seed",fit.seed);
        values.put("bestEpoch",fit.bestEpoch);values.put("epochsRun",fit.history.size());
        values.put("validationMacroMAE",fit.validation.macroMae);values.put("validationMacroRMSE",fit.validation.macroRmse);
        values.put("validationPooledMAE",fit.validation.pooledMae);values.put("validationPooledRMSE",fit.validation.pooledRmse);
        Map<String,Double> horizons=new LinkedHashMap<>();
        for(int h=1;h<=ExperimentConfiguration.HORIZON;h++) { final int horizon=h; horizons.put("H"+h+"RMSE",fit.validation.items.values().stream().mapToDouble(m->m.horizonRmse(horizon)).average().orElseThrow()); }
        values.put("horizonRMSE",horizons);values.put("datasetSha256",datasetHash);
        values.put("trainPeriod","2013-01-01..2015-12-31");values.put("validationPeriod","2016-01-01..2016-12-31");
        values.put("inputDays",ExperimentConfiguration.INPUT_DAYS);values.put("horizon",ExperimentConfiguration.HORIZON);
        values.put("featureSchema",featureSchema(config));values.put("scalingPolicy","PER_ITEM_MIN_MAX_FIT_ON_TRAIN_ONLY_NO_REFIT");
        values.put("targetPolicy","(actual-observedSales(t-7))/trainSalesScale; reconstruct baseline+output*scale");
        values.put("loss","MSE");values.put("optimizer","Adam");values.put("learningRate",ExperimentRunner.DEFAULT_LEARNING_RATE);
        values.put("batchSize",config.getBatchSize());values.put("hiddenUnits",ExperimentRunner.DEFAULT_HIDDEN_UNITS);
        values.put("validationBatchSize",config.getValidationBatchSize());
        String dl4jVersion=MultiLayerNetwork.class.getPackage().getImplementationVersion();
        values.put("javaVersion",System.getProperty("java.version"));values.put("dl4jVersion",dl4jVersion==null?"unknown":dl4jVersion);
        values.put("nd4jBackend",Nd4j.getBackend().getClass().getName());values.put("createdAt",Instant.now().toString());
        values.put("testUsedForSelection",false);return values;
    }

    private static Map<String,Object> featureSchema(ExperimentConfiguration config) {
        Map<String,Object> schema=new LinkedHashMap<>();
        schema.put("inputDays",ExperimentConfiguration.INPUT_DAYS);schema.put("horizon",ExperimentConfiguration.HORIZON);
        schema.put("dynamicFeatureCount",GlobalBatch.DYNAMIC);schema.put("identityFeatureCount",ExperimentConfiguration.ITEM_IDENTITY_COUNT);
        schema.put("totalFeatureCount",GlobalBatch.CHANNELS);schema.put("dynamicFeatures",SalesPreprocessor.getInputFeatureNames());
        List<String> order=new ArrayList<>(SalesPreprocessor.getInputFeatureNames());
        for(int item=1;item<=ExperimentConfiguration.ITEM_IDENTITY_COUNT;item++) order.add("item_"+item);schema.put("featureOrder",order);return schema;
    }
    private static List<Map<String,Integer>> itemMapping(ExperimentConfiguration config) {
        List<Map<String,Integer>> rows=new ArrayList<>();
        for(int item=1;item<=ExperimentConfiguration.ITEM_IDENTITY_COUNT;item++) { Map<String,Integer> row=new LinkedHashMap<>();row.put("itemId",item);row.put("identityIndex",item-1);row.put("channel",GlobalBatch.DYNAMIC+item-1);rows.add(row); }
        return rows;
    }
    private static void writeScalers(GlobalExperimentRunner.Result result,Path target) throws IOException {
        StringBuilder out=new StringBuilder("item_id,train_min,train_max,train_scale\n");
        for(var item:result.data.items) out.append(String.format(Locale.ROOT,"%d,%.17g,%.17g,%.17g%n",item.id,item.scaler.getMin(),item.scaler.getMax(),item.scaler.getScale()));
        writeStringAtomic(target,out.toString());
    }
    static void writeJsonAtomic(Path target,Object value) throws IOException { writeStringAtomic(target,JSON.toJson(value)+"\n"); }
    private static void writeStringAtomic(Path target,String value) throws IOException { Files.createDirectories(target.getParent());Path tmp=target.resolveSibling(target.getFileName()+".tmp");Files.writeString(tmp,value,StandardCharsets.UTF_8);move(tmp,target); }
    private static void copyAtomic(Path source,Path target) throws IOException { Path tmp=target.resolveSibling(target.getFileName()+".tmp");Files.copy(source,tmp,StandardCopyOption.REPLACE_EXISTING);move(tmp,target); }
    private static void move(Path source,Path target) throws IOException { try { Files.move(source,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); } catch(AtomicMoveNotSupportedException e) { Files.move(source,target,StandardCopyOption.REPLACE_EXISTING); } }
}
