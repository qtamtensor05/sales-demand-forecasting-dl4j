package com.dl4j.salesforecast;

import com.dl4j.salesforecast.model.GlobalTestEvaluationRunner;
import java.nio.file.Path;
import com.dl4j.salesforecast.output.ProjectPaths;

/** Dedicated entry point: checkpoint inference only, never delegates to the training Main. */
public final class GlobalTestEvaluationMain {
    private GlobalTestEvaluationMain() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2||!"--source-run".equals(args[0])) throw new IllegalArgumentException("Usage: --source-run <run-directory>");
        ProjectPaths paths=ProjectPaths.fromEnvironment();
        var result=GlobalTestEvaluationRunner.run(Path.of(args[1]),paths.trainingDataset(),paths.evaluationRoot());
        System.out.println("Evaluation completed: "+result.output.toAbsolutePath());
    }
}
