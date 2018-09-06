package io.axoniq.axonhub.localstorage.file;

import io.axoniq.axonhub.KeepNames;

import java.io.File;

/**
 * Author: marc
 */
@KeepNames
public class StorageProperties {

    public static final String PATH_FORMAT = "%s/%s/%014d%s";
    private String eventsSuffix = ".events";
    private String indexSuffix = ".index";
    private String bloomIndexSuffix = ".bloom";

    private long segmentSize = 1024 * 1024 * 256L;

    private String storage;
    private float bloomIndexFpp = 0.03f;
    private long forceInterval = 1000;
    private int validationSegments = 10;
    private int numberOfSegments = 5;
    private int primaryCleanupDelay = 60;
    private int secondaryCleanupDelay = 15;

    public StorageProperties() {
    }

    public StorageProperties(String eventsSuffix, String indexSuffix, String bloomIndexSuffix) {
        this.eventsSuffix = eventsSuffix;
        this.indexSuffix = indexSuffix;
        this.bloomIndexSuffix = bloomIndexSuffix;
    }

    public String getEventsSuffix() {
        return eventsSuffix;
    }

    public void setEventsSuffix(String eventsSuffix) {
        this.eventsSuffix = eventsSuffix;
    }

    public String getIndexSuffix() {
        return indexSuffix;
    }

    public void setIndexSuffix(String indexSuffix) {
        this.indexSuffix = indexSuffix;
    }

    public String getBloomIndexSuffix() {
        return bloomIndexSuffix;
    }

    public void setBloomIndexSuffix(String bloomIndexSuffix) {
        this.bloomIndexSuffix = bloomIndexSuffix;
    }

    public long getSegmentSize() {
        return segmentSize;
    }

    public void setSegmentSize(long segmentSize) {
        this.segmentSize = segmentSize;
    }

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public float getBloomIndexFpp() {
        return bloomIndexFpp;
    }

    public void setBloomIndexFpp(float bloomIndexFpp) {
        this.bloomIndexFpp = bloomIndexFpp;
    }

    public File bloomFilter(String context, long segment) {
        return new File(String.format(PATH_FORMAT, storage, context, segment, bloomIndexSuffix));
    }

    public File index(String context, long segment) {
        return new File(String.format(PATH_FORMAT, storage, context, segment, indexSuffix));
    }

    public File indexTemp(String context, long segment) {
        return new File(String.format("%s/%s/%014d%s.temp", storage, context, segment, indexSuffix));
    }

    public File dataFile(String context, long segment) {
        return new File(String.format(PATH_FORMAT, storage, context, segment, eventsSuffix));
    }

    public long getForceInterval() {
        return forceInterval;
    }

    public void setForceInterval(long forceInterval) {
        this.forceInterval = forceInterval;
    }

    public int getFlags() {
        return 0;
    }

    public String getStorage(String context) {
        return String.format("%s/%s", storage, context);
    }

    public int getValidationSegments() {
        return validationSegments;
    }

    public void setValidationSegments(int validationSegments) {
        this.validationSegments = validationSegments;
    }

    public int getNumberOfSegments() {
        return numberOfSegments;
    }

    public void setNumberOfSegments(int numberOfSegments) {
        this.numberOfSegments = numberOfSegments;
    }

    public int getPrimaryCleanupDelay() {
        return primaryCleanupDelay;
    }

    public void setPrimaryCleanupDelay(int primaryCleanupDelay) {
        this.primaryCleanupDelay = primaryCleanupDelay;
    }

    public int getSecondaryCleanupDelay() {
        return secondaryCleanupDelay;
    }

    public void setSecondaryCleanupDelay(int secondaryCleanupDelay) {
        this.secondaryCleanupDelay = secondaryCleanupDelay;
    }
}
