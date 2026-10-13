package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import static org.junit.jupiter.api.Assertions.*;

class GlobalTestEvaluationRunnerTest {
    @TempDir Path directory;

    @Test void datasetExposesChronologicalTestUsingArtifactScaler() throws Exception {
        LocalDate start=LocalDate.of(2020,1,1); Path csv=directory.resolve("panel.csv");
        StringBuilder text=new StringBuilder("date,store,item,sales\n");
        for(int item:List.of(1,25,50)) for(int day=0;day<100;day++)
            text.append(start.plusDays(day)).append(",1,").append(item).append(',').append(item+day%9).append('\n');
        Files.writeString(csv,text);
        var series=TimeSeriesAnalyzer.analyzeStoreQuietly(csv.toString(),1);
        List<SalesPreprocessor.PreprocessingResult> prepared=new ArrayList<>();
        for(var entry:series.entrySet()) {
            var scaler=SalesPreprocessor.MinMaxScaler.fromTrainBounds(entry.getKey(),entry.getKey()+8);
            prepared.add(SalesPreprocessor.preprocessWithScaler(entry.getValue(),start.plusDays(70),start.plusDays(84),30,7,scaler));
        }
        var data=new SingleStoreGlobalDataset(1,prepared);
        assertEquals(27,data.test.size());
        assertTrue(data.test.stream().allMatch(s->s.window.getTargetDates().stream().allMatch(d->!d.isBefore(start.plusDays(85)))));
        assertSame(prepared.get(0).getScaler(),data.items.get(0).scaler);
    }

    @Test void dedicatedEvaluationTypesExist() {
        assertDoesNotThrow(()->Class.forName("com.dl4j.salesforecast.model.GlobalTestEvaluationRunner"));
        assertDoesNotThrow(()->Class.forName("com.dl4j.salesforecast.GlobalTestEvaluationMain"));
    }

    @Test void inferencePreservesWeightsCheckpointAndUsesIdenticalWeeklyKeys() throws Exception {
        var data=smallDataset();
        MultiLayerNetwork network=new MultiLayerNetwork(new NeuralNetConfiguration.Builder().seed(7).updater(new Adam(.001)).list()
                .layer(new LSTM.Builder().nIn(GlobalBatch.CHANNELS).nOut(2).activation(Activation.TANH).build())
                .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE).nIn(2).nOut(1).activation(Activation.IDENTITY).build()).build());
        network.init(); Path checkpoint=directory.resolve("model.zip"); ModelSerializer.writeModel(network,checkpoint.toFile(),true);
        String hashBefore=hash(checkpoint); var params=network.params().dup();
        var model=GlobalResidualLstmForecaster.evaluate(network,data.test,64);
        var weekly=GlobalTestEvaluationRunner.weekly(data.test);
        assertTrue(params.equalsWithEps(network.params(),0)); assertEquals(hashBefore,hash(checkpoint));
        assertEquals(weekly.count,model.count); assertEquals(27L*7,model.count);
        for(int item:List.of(1,25,50)) {
            var weeklyKeys=weekly.predictions.get(item).stream().map(p->p.getOriginDate()+"|"+p.getTargetDate()+"|"+p.getHorizon()).collect(java.util.stream.Collectors.toSet());
            var modelKeys=model.predictions.get(item).stream().map(p->p.getOriginDate()+"|"+p.getTargetDate()+"|"+p.getHorizon()).collect(java.util.stream.Collectors.toSet());
            assertEquals(weekly.predictions.get(item).size(),weeklyKeys.size());assertEquals(weeklyKeys,modelKeys);
        }
        params.close();network.close();
    }

    @Test void runnerSourceHasNoTrainingOrCheckpointMutationCalls() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/dl4j/salesforecast/model/GlobalTestEvaluationRunner.java"));
        assertFalse(source.contains(".fit("));assertFalse(source.contains("setParams("));
        assertFalse(source.contains("saveCheckpoint("));assertFalse(source.contains("writeModel("));
        assertFalse(source.contains("finalizeArtifacts("));
    }

    @Test void launcherOverridesPomMainAndBypassesTrainingConfiguration() throws Exception {
        String pom=Files.readString(Path.of("pom.xml"));
        String script=Files.readString(Path.of("scripts/Invoke-GlobalTestEvaluation.ps1"));
        String runner=Files.readString(Path.of("src/main/java/com/dl4j/salesforecast/model/GlobalTestEvaluationRunner.java"));
        assertTrue(pom.contains("<exec.mainClass>com.dl4j.salesforecast.Main</exec.mainClass>"));
        assertTrue(pom.contains("<mainClass>${exec.mainClass}</mainClass>"));
        assertTrue(script.contains("-Dexec.mainClass=com.dl4j.salesforecast.GlobalTestEvaluationMain"));
        assertFalse(runner.contains("ExperimentConfiguration.fromArgs"));
    }

    @Test void highDemandTierIsNotParsedAsAHorizon() {
        var high=assertDoesNotThrow(()->new GlobalTestEvaluationRunner.Group("HIGH",17,10,9));
        assertEquals(0,high.id);assertEquals(17,high.count);
        var horizon=new GlobalTestEvaluationRunner.Group("H7",7,10,9);
        assertEquals(7,horizon.id);
    }

    private SingleStoreGlobalDataset smallDataset() throws Exception {
        LocalDate start=LocalDate.of(2020,1,1); Path csv=directory.resolve("small.csv");StringBuilder text=new StringBuilder("date,store,item,sales\n");
        for(int item:List.of(1,25,50))for(int day=0;day<100;day++)text.append(start.plusDays(day)).append(",1,").append(item).append(',').append(item+day%9).append('\n');Files.writeString(csv,text);
        var series=TimeSeriesAnalyzer.analyzeStoreQuietly(csv.toString(),1);List<SalesPreprocessor.PreprocessingResult> prepared=new ArrayList<>();
        for(var entry:series.entrySet())prepared.add(SalesPreprocessor.preprocess(entry.getValue(),start.plusDays(70),start.plusDays(84),30,7));return new SingleStoreGlobalDataset(1,prepared);
    }
    private static String hash(Path path)throws Exception{byte[]bytes=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format("%02x",b));return value.toString();}
}
