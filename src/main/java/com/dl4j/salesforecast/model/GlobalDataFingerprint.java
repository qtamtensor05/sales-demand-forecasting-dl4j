package com.dl4j.salesforecast.model;

import java.io.*;
import java.security.*;
import java.util.List;

/** Audit of ordered windows, exact doubles, dates and per-item scalers; never consumes model RNG. */
final class GlobalDataFingerprint {
    private GlobalDataFingerprint() { }
    static String of(List<SingleStoreGlobalDataset.Sample> samples) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var out=new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest)))) {
                out.writeInt(samples.size());
                for(var sample:samples) {
                    var w=sample.window;
                    out.writeInt(sample.item.id);
                    out.writeDouble(sample.item.scaler.getMin());out.writeDouble(sample.item.scaler.getMax());out.writeDouble(sample.item.scaler.getScale());
                    for(var date:w.getInputDates()) { out.writeLong(date.toEpochDay()); }
                    for(var date:w.getTargetDates()) { out.writeLong(date.toEpochDay()); }
                    for(double v:w.getRawInput()) { out.writeDouble(v); }
                    for(double v:w.getRawTarget()) { out.writeDouble(v); }
                    for(double v:w.getInput()) { out.writeDouble(v); }
                    for(double v:w.getTarget()) { out.writeDouble(v); }
                    for(var vector:w.getInputFeatures()) for(double v:vector) { out.writeDouble(v); }
                }
            }
            StringBuilder hex=new StringBuilder();
            for(byte b:digest.digest()) { hex.append(String.format("%02x",b)); }
            return hex.toString();
        } catch(IOException | NoSuchAlgorithmException e) { throw new IllegalStateException("Cannot fingerprint global windows",e); }
    }
}
