package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import static org.junit.jupiter.api.Assertions.*;

class GlobalStoreTest {
    @TempDir Path directory;
    SingleStoreGlobalDataset fixture(String name, int validationOffset, int testOffset) throws Exception {
        LocalDate start = LocalDate.of(2020,1,1);
        StringBuilder csv = new StringBuilder("date,store,item,sales\n");
        for (int store : List.of(1,2)) for (int item : List.of(1,25,50)) for (int d=0;d<100;d++) {
            int value = item==50 ? 80 : item*10+(d%7)*2+(d/7%3)*3;
            value += (d>=71 ? validationOffset:0)+(d>=85 ? testOffset:0)+1000*(store-1);
            csv.append(start.plusDays(d)).append(',').append(store).append(',').append(item).append(',').append(value).append('\n');
        }
        Path file=directory.resolve(name+".csv"); Files.writeString(file,csv);
        var all=TimeSeriesAnalyzer.analyzeStoreQuietly(file.toString(),1);
        assertEquals(Set.of(1,25,50),all.keySet());
        List<SalesPreprocessor.PreprocessingResult> list=new ArrayList<>();
        for(var series:all.values()) {
            assertEquals(1,series.getStoreId());
            list.add(SalesPreprocessor.preprocess(series,start.plusDays(70),start.plusDays(84),30,7));
        }
        return new SingleStoreGlobalDataset(1,list);
    }

    @Test void panelScalingWindowsIdentityAndBatchesAreCausal() throws Exception {
        var a=fixture("original",0,0);var b=fixture("future",10000,20000);
        assertEquals(66,a.train.size());assertEquals(24,a.validation.size());
        assertEquals(3,a.items.size());
        assertEquals(1,a.items.get(2).scaler.getScale());
        assertNotEquals(a.items.get(0).scaler.getMin(),a.items.get(1).scaler.getMin());
        var left=GlobalBatch.training(a.train);var right=GlobalBatch.training(b.train);
        assertEquals(left.getFeatures(),right.getFeatures());assertEquals(left.getLabels(),right.getLabels());
        assertArrayEquals(new long[]{66,56,30},left.getFeatures().shape());
        assertArrayEquals(new long[]{66,1,30},left.getLabels().shape());
        assertEquals(66,left.getLabelsMaskArray().sumNumber().intValue());
        for(int row=0;row<a.train.size();row++) {
            var sample=a.train.get(row);
            assertTrue(sample.window.getRawInput().stream().allMatch(v->v>=sample.item.scaler.getMin()&&v<=sample.item.scaler.getMax()));
            for(int t=0;t<30;t++) for(int item=1;item<=50;item++) {
                assertEquals(item==sample.item.id?1:0,left.getFeatures().getDouble(row,GlobalBatch.identityChannel(item),t));
            }
            assertEquals(ResidualLstmForecaster.scaledTarget(sample.window,0,sample.item.scaler),left.getLabels().getDouble(row,0,29),1e-6);
        }
        assertEquals(a.trainingOrder(42,1),a.trainingOrder(42,1));
        assertNotEquals(a.trainingOrder(42,1),a.trainingOrder(99,1));
        var order=a.trainingOrder(42,2);Set<SingleStoreGlobalDataset.Sample> visited=new HashSet<>();
        for(int from=0;from<order.size();from+=64) {
            var samples=order.subList(from,Math.min(from+64,order.size()));
            var batch=GlobalBatch.training(samples);visited.addAll(samples);GlobalBatch.close(batch);
        }
        assertEquals(new HashSet<>(a.train),visited);
        for(int i=0;i<3;i++) {
            assertEquals(a.items.get(i).scaler.getMin(),b.items.get(i).scaler.getMin());
            assertEquals(a.items.get(i).scaler.getMax(),b.items.get(i).scaler.getMax());
            assertEquals(a.items.get(i).validation.getWindows().get(0).getInputFeatures(),b.items.get(i).validation.getWindows().get(0).getInputFeatures());
        }
        assertThrows(IllegalArgumentException.class,()->SingleStoreGlobalDataset.load(directory.resolve("original.csv").toString(),ExperimentConfiguration.fromArgs(new String[]{"--global-store"})));
        GlobalBatch.close(left);GlobalBatch.close(right);
    }
    @Test void globalCliIsOneFitPerSeed() {
        var full = assertDoesNotThrow(() -> ExperimentConfiguration.fromArgs(new String[]{"--global-store=1"}));
        assertEquals(List.of(42L,123L,2026L),full.getSeeds());
        assertEquals(3, full.getFitCount());
        assertEquals(10,full.getMaxEpochs());
        assertEquals(64,full.getBatchSize());
        assertEquals(256,ExperimentConfiguration.fromArgs(new String[]{"--global-store=1","--validation-batch-size=256"}).getValidationBatchSize());
        assertEquals(List.of(2026L,42L),ExperimentConfiguration.fromArgs(new String[]{"--global-store=1","--seeds=2026,42"}).getSeeds());
        assertEquals(java.util.List.of("GlobalResidualAR"), full.getModels());
        var smoke = ExperimentConfiguration.fromArgs(new String[]{"--global-store-smoke"});
        assertEquals(1, smoke.getFitCount());
        assertEquals(2, smoke.getMaxEpochs());
        assertEquals(120, ExperimentConfiguration.legacy().getFitCount());
        assertEquals(180, ExperimentConfiguration.residual().getFitCount());
        assertEquals(List.of(42L,123L,2026L,7L,99L),ExperimentConfiguration.legacy().getSeeds());
        assertThrows(IllegalArgumentException.class,()->ExperimentConfiguration.fromArgs(new String[]{"--global-store=1","--seeds=42,42"}));
        var calibration=ExperimentConfiguration.fromArgs(new String[]{"--global-store-calibration=1","--batch-size=128","--validation-batch-size=512"});
        assertTrue(calibration.isCalibration());assertEquals(List.of(42L),calibration.getSeeds());
        assertEquals(1,calibration.getMaxEpochs());assertEquals(128,calibration.getBatchSize());assertEquals(512,calibration.getValidationBatchSize());
    }

    @Test void globalDatasetFunctionalityExists() {
        assertDoesNotThrow(() -> Class.forName("com.dl4j.salesforecast.model.SingleStoreGlobalDataset"));
    }

    @Test void fullPanelLoadsAllFiftyItemsWithoutFiftyModels() throws Exception {
        var start=LocalDate.of(2013,1,1);Path file=directory.resolve("full-panel.csv");
        try(var out=Files.newBufferedWriter(file)) {
            out.write("date,store,item,sales\n");
            for(int item=1;item<=50;item++) for(int day=0;day<1826;day++) {
                out.write(start.plusDays(day)+",2,"+item+","+(item*10+day%7)+"\n");
            }
        }
        var config=ExperimentConfiguration.fromArgs(new String[]{"--global-store=2","--batch-size=32"});
        var data=SingleStoreGlobalDataset.load(file.toString(),config);
        assertEquals(50,data.items.size());assertEquals(52300,data.train.size());assertEquals(18000,data.validation.size());assertEquals(17950,data.test.size());
        assertEquals(3,config.getFitCount());assertEquals(32,config.getBatchSize());
        for(var item:data.items) {
            assertEquals(item.id*10,item.scaler.getMin());assertEquals(6,item.scaler.getScale());
            assertEquals(1046,item.train.getWindows().size());assertEquals(360,item.validation.getWindows().size());assertEquals(359,item.test.getWindows().size());
            assertTrue(item.train.getWindows().stream().allMatch(w->w.getTargetDates().get(6).getYear()<=2015));
            assertTrue(item.validation.getWindows().stream().allMatch(w->w.getTargetDates().get(0).getYear()==2016&&w.getTargetDates().get(6).getYear()==2016));
            assertTrue(item.test.getWindows().stream().allMatch(w->w.getTargetDates().get(0).getYear()==2017&&w.getTargetDates().get(6).getYear()==2017));
        }
        assertThrows(IllegalArgumentException.class,()->SingleStoreGlobalDataset.load(file.toString(),ExperimentConfiguration.fromArgs(new String[]{"--global-store=1"})));
        assertThrows(IllegalArgumentException.class,()->ExperimentConfiguration.fromArgs(new String[]{"--global-store=0"}));
        assertThrows(IllegalArgumentException.class,()->ExperimentConfiguration.fromArgs(new String[]{"--global-store","--batch-size=0"}));
    }

    @Test void rolloutKeepsIdentityAndNeverReadsFutureActuals() throws Exception {
        var a=fixture("a",0,0);var b=fixture("b",10000,20000);
        var sa=a.validation.stream().filter(s->s.window==s.item.validation.getWindows().get(0)).collect(java.util.stream.Collectors.toList());
        var sb=b.validation.stream().filter(s->s.window==s.item.validation.getWindows().get(0)).collect(java.util.stream.Collectors.toList());
        List<float[]> firstInputs=new ArrayList<>(),secondInputs=new ArrayList<>();
        java.util.function.Function<org.nd4j.linalg.api.ndarray.INDArray,org.nd4j.linalg.api.ndarray.INDArray> first = input->{
            firstInputs.add(input.data().asFloat().clone());
            for(int row=0;row<3;row++) for(int t=0;t<30;t++) {
                assertEquals(1,input.getDouble(row,GlobalBatch.identityChannel(sa.get(row).item.id),t));
            }
            return org.nd4j.linalg.factory.Nd4j.valueArrayOf(new long[]{3,1,30},.25f);
        };
        var va=GlobalResidualLstmForecaster.evaluate(first,sa,64);
        var vb=GlobalResidualLstmForecaster.evaluate(input->{secondInputs.add(input.data().asFloat().clone());return org.nd4j.linalg.factory.Nd4j.valueArrayOf(new long[]{3,1,30},.25f);},sb,64);
        assertEquals(7,firstInputs.size());
        for(int h=0;h<7;h++) assertArrayEquals(firstInputs.get(h),secondInputs.get(h));
        for(var item:a.items) {
            var pa=va.predictions.get(item.id);var pb=vb.predictions.get(item.id);
            for(int h=0;h<7;h++) {
                assertEquals(h+1,pa.get(h).getHorizon());
                assertEquals(pa.get(h).getOriginDate().plusDays(h+1),pa.get(h).getTargetDate());
                assertEquals(pa.get(h).getPrediction(),pb.get(h).getPrediction());
                assertEquals(pa.get(h).getWeeklyNaive()+.25*item.scaler.getScale(),pa.get(h).getPrediction(),1e-6);
                assertNotEquals(pa.get(h).getActual(),pb.get(h).getActual());
            }
        }
        var zero=GlobalResidualLstmForecaster.evaluate(input->org.nd4j.linalg.factory.Nd4j.zeros(3,1,30),sa,64);
        assertTrue(zero.predictions.values().stream().flatMap(List::stream).allMatch(p->p.getPrediction()==p.getWeeklyNaive()));
    }

    @Test void macroAndPooledHaveDifferentMeanings() {
        Map<Integer,List<ValidationPrediction>> predictions=new TreeMap<>();
        for(int item:List.of(1,2)) {
            var rows=new ArrayList<ValidationPrediction>();
            for(int h=1;h<=7;h++) rows.add(new ValidationPrediction(LocalDate.of(2020,1,1),LocalDate.of(2020,1,1).plusDays(h),h,0,item==1?1:9,0));
            predictions.put(item,rows);
        }
        var metrics=GlobalMetrics.of(predictions);
        assertEquals(5,metrics.macroRmse);assertEquals(Math.sqrt(41),metrics.pooledRmse);
        assertEquals(5,metrics.macroMae);assertEquals(14,metrics.count);
        assertTrue(GlobalMetrics.improves(4.9,5));assertFalse(GlobalMetrics.improves(5+1e-10,5));
    }

    @Test void syntheticSharedFitRestoresMacroCheckpointAndExports() throws Exception {
        var data=fixture("fit",0,0);
        var config=ExperimentConfiguration.fromArgs(new String[]{"--global-store-smoke"});
        GlobalExperimentRunner.Result result;Path run;
        try(var session=com.dl4j.salesforecast.output.RunArtifactSession.start(directory.resolve("runs"),directory.resolve("fit.csv"),config)) {
            run=session.getDirectory();result=GlobalExperimentRunner.run(data,config,run);session.complete(result);
        }
        assertEquals(1,result.fits.size());
        var fit=result.fits.get(0);assertEquals(3,fit.validation.items.size());
        assertEquals(168,fit.validation.count);
        double best=fit.history.stream().mapToDouble(e->e.validation.macroRmse).min().orElseThrow();
        assertEquals(best,fit.validation.macroRmse,1e-5);
        assertEquals(fit.history.get(fit.bestEpoch-1).validation.macroRmse,fit.validation.macroRmse,1e-5);
        var changed=fixture("testchanged",0,20000);
        var repeat=GlobalResidualLstmForecaster.fit(changed,config,42);
        assertEquals(fit.bestEpoch,repeat.bestEpoch);assertEquals(fit.validation.macroRmse,repeat.validation.macroRmse,1e-5);
        var json=com.google.gson.JsonParser.parseString(Files.readString(run.resolve("run.json"))).getAsJsonObject();
        assertEquals("SUCCESS",json.get("status").getAsString());assertEquals(4,json.get("schemaVersion").getAsInt());
        assertEquals(1,json.get("fits").getAsInt());assertEquals(56,json.get("totalFeatureCount").getAsInt());
        assertEquals(4,Files.readAllLines(run.resolve("global-series-scaling.csv")).size());
        var lines=Files.readAllLines(run.resolve("global-validation-predictions.csv"));assertEquals(169,lines.size());
        Set<String> keys=new HashSet<>();
        for(String line:lines.subList(1,lines.size())) {
            var f=line.split(",");assertTrue(keys.add(String.join(",",Arrays.copyOf(f,7))));
            assertEquals(Double.parseDouble(f[10]),Double.parseDouble(f[8])+Double.parseDouble(f[9]),2e-10);
        }
        assertTrue(Files.readString(run.resolve("summary.txt")).contains("SMOKE ONLY"));
        assertTrue(Files.readString(run.resolve("summary.txt")).contains("PER ITEM"));
        assertTrue(Files.isRegularFile(run.resolve("models/seed-42/model.zip")));
        assertTrue(Files.isRegularFile(run.resolve("deployment/model.zip")));
        assertTrue(Files.readString(run.resolve("deployment/selection.json")).contains("validationMacroRMSE"));
        assertEquals(50,com.google.gson.JsonParser.parseString(Files.readString(run.resolve("deployment/item-mapping.json"))).getAsJsonArray().size());
        assertEquals(56,com.google.gson.JsonParser.parseString(Files.readString(run.resolve("deployment/feature-schema.json"))).getAsJsonObject().get("totalFeatureCount").getAsInt());
        assertEquals(4,Files.readAllLines(run.resolve("deployment/item-scalers.csv")).size());
        assertEquals(64,com.google.gson.JsonParser.parseString(Files.readString(run.resolve("deployment/model-metadata.json"))).getAsJsonObject().get("datasetSha256").getAsString().length());
        var loaded=com.dl4j.salesforecast.output.GlobalModelArtifacts.loadCheckpoint(run.resolve("deployment/model.zip"));try {
            var input=org.nd4j.linalg.factory.Nd4j.zeros(1,56,30);
            var first=loaded.output(input,false);var second=loaded.output(input,false);
            assertArrayEquals(first.ravel().toDoubleVector(),second.ravel().toDoubleVector(),0);first.close();second.close();input.close();
            var restoredValidation=GlobalResidualLstmForecaster.evaluate(loaded,data.validation,64);
            assertEquals(result.fits.get(0).validation.macroRmse,restoredValidation.macroRmse,1e-7);
        } finally { loaded.close(); }
        assertEquals(1,Files.readAllLines(run.resolve("global-epoch-history.csv")).stream().filter(l->l.contains(",true,")).count());
        try(var session=com.dl4j.salesforecast.output.RunArtifactSession.start(directory.resolve("runs"),directory.resolve("fit.csv"),ExperimentConfiguration.fromArgs(new String[]{"--global-store"}))) {
            assertNotEquals(run,session.getDirectory());run=session.getDirectory();
            assertThrows(IllegalArgumentException.class,()->session.complete(result));
        }
        assertTrue(Files.readString(run.resolve("run.json")).contains("FAILED"));
    }
}
