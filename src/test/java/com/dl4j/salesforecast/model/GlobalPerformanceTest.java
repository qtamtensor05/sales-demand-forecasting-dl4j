package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class GlobalPerformanceTest {
    @TempDir Path directory;

    private SingleStoreGlobalDataset fixture(String name,int future) throws Exception {
        var fixture=new GlobalStoreTest();fixture.directory=directory;
        return fixture.fixture(name,future,20000);
    }

    private static Function<INDArray,INDArray> predictor(List<float[]> inputs, boolean fortran) {
        return input -> {
            inputs.add(input.data().asFloat().clone());
            // Nonconstant and history-dependent, with distinct values at every timestep.
            INDArray output=Nd4j.create(new long[]{input.size(0),1,30},fortran?'f':'c');
            for(int row=0;row<input.size(0);row++) for(int day=0;day<30;day++) {
                output.putScalar(new int[]{row,0,day}, .07*input.getDouble(row,0,day)+.03*input.getDouble(row,2,day)-.11);
            }
            return output;
        };
    }

    private static void assertMetricsEqual(GlobalMetrics expected,GlobalMetrics actual) {
        assertEquals(expected.count,actual.count);
        assertEquals(expected.macroMae,actual.macroMae,0);
        assertEquals(expected.macroRmse,actual.macroRmse,0);
        assertEquals(expected.pooledMae,actual.pooledMae,0);
        assertEquals(expected.pooledRmse,actual.pooledRmse,0);
        assertEquals(expected.items.keySet(),actual.items.keySet());
        for(int id:expected.items.keySet()) {
            var left=expected.items.get(id);var right=actual.items.get(id);
            assertEquals(left.count,right.count);assertEquals(left.mae,right.mae,0);assertEquals(left.rmse,right.rmse,0);
            for(int h=1;h<=7;h++) { assertEquals(left.horizonMae(h),right.horizonMae(h),0);assertEquals(left.horizonRmse(h),right.horizonRmse(h),0); }
            var a=expected.predictions.get(id);var b=actual.predictions.get(id);
            assertEquals(a.size(),b.size());
            for(int i=0;i<a.size();i++) {
                assertEquals(a.get(i).getOriginDate(),b.get(i).getOriginDate());
                assertEquals(a.get(i).getTargetDate(),b.get(i).getTargetDate());
                assertEquals(a.get(i).getHorizon(),b.get(i).getHorizon());
                assertEquals(a.get(i).getActual(),b.get(i).getActual(),0);
                assertEquals(a.get(i).getWeeklyNaive(),b.get(i).getWeeklyNaive(),0);
                assertEquals(a.get(i).getPrediction(),b.get(i).getPrediction(),0);
            }
        }
    }

    @Test void rolloutMatchesFrozenOracleIncludingPartialBatchAndFortranOutput() throws Exception {
        var data=fixture("reference",0);
        for(boolean fortran:List.of(false,true)) {
            List<float[]> oldInputs=new ArrayList<>(),newInputs=new ArrayList<>();
            var expected=ReferenceGlobalValidation.evaluate(predictor(oldInputs,fortran),data.validation,7);
            var actual=GlobalResidualLstmForecaster.evaluate(predictor(newInputs,fortran),data.validation,7);
            assertMetricsEqual(expected,actual);assertEquals(24*7,actual.count);
            assertEquals(4*7,newInputs.size()); // batches 7,7,7,3: last batch must survive all horizons.
            for(int i=0;i<oldInputs.size();i++) { assertArrayEquals(oldInputs.get(i),newInputs.get(i)); }
            assertEquals(3*56*30,newInputs.get(27).length);
        }
    }

    @Test void historyDependentForecastIgnoresFutureActualAndPreservesTrainingFingerprint() throws Exception {
        var original=fixture("original",0);var mutated=fixture("mutated",10000);
        assertEquals(GlobalDataFingerprint.of(original.train),GlobalDataFingerprint.of(mutated.train));
        var a=original.validation.stream().filter(s->s.window==s.item.validation.getWindows().get(0)).collect(java.util.stream.Collectors.toList());
        var b=mutated.validation.stream().filter(s->s.window==s.item.validation.getWindows().get(0)).collect(java.util.stream.Collectors.toList());
        List<float[]> oldInputs=new ArrayList<>(),newInputs=new ArrayList<>();
        var expected=GlobalResidualLstmForecaster.evaluate(predictor(oldInputs,false),a,2);
        var actual=GlobalResidualLstmForecaster.evaluate(predictor(newInputs,false),b,2);
        for(int i=0;i<oldInputs.size();i++) { assertArrayEquals(oldInputs.get(i),newInputs.get(i)); }
        for(int item:expected.items.keySet()) for(int h=0;h<7;h++) {
            var x=expected.predictions.get(item).get(h);var y=actual.predictions.get(item).get(h);
            assertEquals(x.getPrediction(),y.getPrediction(),0);assertNotEquals(x.getActual(),y.getActual());
        }
        assertEquals(original.trainingOrder(42,2),original.trainingOrder(42,2));
    }
    @Test void profileAccumulatesCountsAndReportsNestedTimingScope() {
        var profile=new com.dl4j.salesforecast.performance.GlobalPerformanceProfile();
        profile.count("batches",2);profile.count("batches",3);
        profile.record("train",System.nanoTime());
        assertEquals(5,profile.count("batches"));
        assertTrue(profile.snapshot().get("scope").toString().contains("Nested phases overlap"));
        assertTrue(((java.util.Map<?,?>)profile.snapshot().get("seconds")).containsKey("train"));
    }
    @Test void benchmarkKeepsFullPopulationAndOnlyLimitsSeedsAndEpochs() {
        var benchmark = assertDoesNotThrow(() -> ExperimentConfiguration.fromArgs(new String[]{"--global-store-benchmark"}));
        var full = ExperimentConfiguration.fromArgs(new String[]{"--global-store"});
        assertFalse(benchmark.isSmoke());
        assertEquals(full.getItemIds(), benchmark.getItemIds());
        assertEquals(50, benchmark.getSeriesCount());
        assertEquals(List.of(42L), benchmark.getSeeds());
        assertEquals(2, benchmark.getMaxEpochs());
        assertEquals(64, benchmark.getBatchSize());
        assertEquals(5, benchmark.getPatience());
        assertEquals(1, benchmark.getFitCount());
        assertEquals(30, full.getMaxEpochs());
        assertEquals(5, full.getFitCount());
    }
}
