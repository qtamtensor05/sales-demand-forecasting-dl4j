package com.dl4j.salesforecast.api;

interface ObjectStorage {
    void put(String objectName, byte[] content);
    byte[] get(String objectName);
}
