package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.output.GlobalModelArtifacts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GlobalModelArtifactsTest {
    @TempDir Path directory;

    private GlobalMetrics metrics(double itemOneError,double itemTwoError) {
        Map<Integer,List<ValidationPrediction>> rows=new TreeMap<>();
        for(int item:List.of(1,2)) { List<ValidationPrediction> values=new ArrayList<>();double error=item==1?itemOneError:itemTwoError;
            for(int h=1;h<=7;h++) values.add(new ValidationPrediction(LocalDate.of(2016,1,1),LocalDate.of(2016,1,1).plusDays(h),h,0,error,0));rows.put(item,values); }
        return GlobalMetrics.of(rows);
    }
    private GlobalMetrics metricsPattern(double... errors) {
        Map<Integer,List<ValidationPrediction>> rows=new TreeMap<>();
        for(int item:List.of(1,2)) { List<ValidationPrediction> values=new ArrayList<>();
            for(int h=1;h<=7;h++) for(int origin=0;origin<errors.length;origin++) values.add(new ValidationPrediction(
                    LocalDate.of(2016,1,1).plusDays(origin),LocalDate.of(2016,1,1).plusDays(origin+h),h,0,errors[origin],0));rows.put(item,values); }
        return GlobalMetrics.of(rows);
    }
    private GlobalResidualLstmForecaster.Fit fit(long seed,GlobalMetrics metrics) {
        return new GlobalResidualLstmForecaster.Fit(seed,1,List.of(),metrics,directory.resolve("seed-"+seed+"/model.zip"));
    }

    @Test void selectionUsesMacroRmseThenMacroMaeThenSeedAndNeverTestData() {
        var rmseWinner=fit(2026,metrics(2,2));
        var worse=fit(42,metrics(1,4));
        assertSame(rmseWinner,GlobalModelArtifacts.select(List.of(worse,rmseWinner)));
        var lowerMae=fit(123,metricsPattern(0,2));var higherMae=fit(42,metricsPattern(Math.sqrt(2),Math.sqrt(2)));
        assertEquals(lowerMae.validation.macroRmse,higherMae.validation.macroRmse,1e-12);
        assertSame(lowerMae,GlobalModelArtifacts.select(List.of(higherMae,lowerMae)));
        var tiedHighSeed=fit(123,metrics(2,2));var tiedLowSeed=fit(42,metrics(2,2));
        assertSame(tiedLowSeed,GlobalModelArtifacts.select(List.of(tiedHighSeed,tiedLowSeed)));
    }

    @Test void missingAndCorruptCheckpointAreRejected() throws Exception {
        assertThrows(java.io.IOException.class,()->GlobalModelArtifacts.loadCheckpoint(directory.resolve("missing.zip")));
        Path corrupt=directory.resolve("corrupt.zip");Files.writeString(corrupt,"partial");
        assertThrows(java.io.IOException.class,()->GlobalModelArtifacts.loadCheckpoint(corrupt));
    }
}
