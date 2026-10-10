package com.dl4j.salesforecast.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RunArtifactSessionTest {
    @Test
    void createsIsolatedRunAndRecordsFailureStatus(@TempDir Path directory) throws Exception {
        Path dataset = directory.resolve("train.csv");
        Files.writeString(dataset, "date,store,item,sales\n2020-01-01,1,1,1\n");

        RunArtifactSession session = RunArtifactSession.start(directory.resolve("runs"), dataset);
        assertTrue(Files.isDirectory(session.getDirectory()));
        assertTrue(Files.readString(session.getDirectory().resolve("run.json")).contains("\"RUNNING\""));
        assertTrue(Files.exists(session.getDirectory().resolve("console.log")));

        session.fail(new IllegalStateException("synthetic failure"));
        session.close();
        String manifest = Files.readString(session.getDirectory().resolve("run.json"));
        assertTrue(manifest.contains("\"FAILED\""));
        assertTrue(manifest.contains("synthetic failure"));
        assertTrue(Files.size(session.getDirectory().resolve("console.log")) >= 0);
    }
}
