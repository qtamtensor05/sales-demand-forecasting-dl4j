package com.dl4j.salesforecast.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GlobalCalibrationSelectionTest {
    @Test void choosesFastestCandidateWithinOnePercentOfBaselineRmse() {
        var baseline=new GlobalCalibrationSelection.Candidate(64,256,10.0,600);
        var fastEligible=new GlobalCalibrationSelection.Candidate(128,512,10.09,400);
        var fastestButDegraded=new GlobalCalibrationSelection.Candidate(256,1024,10.2,300);
        assertSame(fastEligible,GlobalCalibrationSelection.select(List.of(baseline,fastEligible,fastestButDegraded),1.0));
    }
    @Test void tieBreaksByRmseThenSmallerTrainBatchAndRequiresBaseline() {
        var baseline=new GlobalCalibrationSelection.Candidate(64,256,10,500);
        var a=new GlobalCalibrationSelection.Candidate(128,512,10.05,400);
        var b=new GlobalCalibrationSelection.Candidate(256,1024,10.01,400);
        assertSame(b,GlobalCalibrationSelection.select(List.of(a,baseline,b),1.0));
        assertThrows(IllegalArgumentException.class,()->GlobalCalibrationSelection.select(List.of(a,b),1.0));
    }
}
