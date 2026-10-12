package com.dl4j.salesforecast.model;

import com.dl4j.salesforecast.analysis.TimeSeriesAnalyzer;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor;
import com.dl4j.salesforecast.preprocessing.SalesPreprocessor.*;
import java.time.LocalDate;
import java.util.*;

/** Panel of independent item windows, never a concatenated cross-item time series. */
public final class SingleStoreGlobalDataset {
    public final int storeId;
    public final List<Item> items;
    public final List<Sample> train;
    public final List<Sample> validation;

    public static SingleStoreGlobalDataset load(String path, ExperimentConfiguration config) {
        return load(path, config, new com.dl4j.salesforecast.performance.GlobalPerformanceProfile());
    }
    public static SingleStoreGlobalDataset load(String path, ExperimentConfiguration config,
            com.dl4j.salesforecast.performance.GlobalPerformanceProfile profile) {
        long started = System.nanoTime();
        if (!config.isGlobalStore()) { throw new IllegalArgumentException("Global configuration required."); }
        var series = TimeSeriesAnalyzer.analyzeStoreQuietly(path, config.getStoreId(), profile);
        var expected = new TreeSet<Integer>();
        for (int i = 1; i <= ExperimentConfiguration.ITEM_IDENTITY_COUNT; i++) { expected.add(i); }
        if (!series.keySet().equals(expected)) { throw new IllegalArgumentException("Expected all item IDs 1..50 for selected store; found " + series.keySet()); }
        List<PreprocessingResult> prepared = new ArrayList<>();
        for (int id : config.getItemIds()) {
            var s = series.get(id);
            if (!s.getStartDate().equals(LocalDate.of(2013,1,1)) || !s.getEndDate().equals(LocalDate.of(2017,12,31))) {
                throw new IllegalArgumentException("Global protocol requires complete 2013-2017 series: item " + id);
            }
            prepared.add(SalesPreprocessor.preprocess(s, LocalDate.of(2015,12,31), LocalDate.of(2016,12,31),
                    ExperimentConfiguration.INPUT_DAYS, ExperimentConfiguration.HORIZON, profile));
        }
        long panelStart = System.nanoTime();
        var data = new SingleStoreGlobalDataset(config.getStoreId(), prepared);
        profile.record("data.panel", panelStart);
        profile.record("data.total", started);
        return data;
    }

    /** Also supports small chronological synthetic fixtures; CLI enforces the full data protocol. */
    public SingleStoreGlobalDataset(int storeId, List<PreprocessingResult> prepared) {
        if (storeId <= 0 || prepared.isEmpty()) { throw new IllegalArgumentException("Nonempty single-store panel required."); }
        this.storeId = storeId;
        List<Item> list = new ArrayList<>(); Set<Integer> seen = new HashSet<>();
        for (var p : prepared) {
            if (p.getStoreId() != storeId || p.getItemId() < 1 || p.getItemId() > ExperimentConfiguration.ITEM_IDENTITY_COUNT
                    || !seen.add(p.getItemId()) || p.getInputDays() != ExperimentConfiguration.INPUT_DAYS
                    || p.getForecastDays() != ExperimentConfiguration.HORIZON) {
                throw new IllegalArgumentException("Invalid store/item/window configuration.");
            }
            list.add(new Item(p));
        }
        list.sort(Comparator.comparingInt(i -> i.id)); items = List.copyOf(list);
        List<Sample> t = new ArrayList<>(), v = new ArrayList<>();
        for (Item i : items) {
            for (Window w : i.train.getWindows()) { t.add(new Sample(i,w)); }
            for (Window w : i.validation.getWindows()) { v.add(new Sample(i,w)); }
        }
        train = List.copyOf(t); validation = List.copyOf(v);
    }

    public List<Sample> trainingOrder(long seed, int epoch) {
        List<Sample> order = new ArrayList<>(train);
        Collections.shuffle(order, new Random(seed + 1_000_003L * epoch));
        return order;
    }

    public static final class Item {
        public final int id;
        public final MinMaxScaler scaler;
        public final Split train, validation;
        public final double trainMean;
        private Item(PreprocessingResult p) {
            id = p.getItemId(); scaler = p.getScaler(); train = p.getTrain(); validation = p.getValidation();
            trainMean = train.getRawSales().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        }
    }
    public static final class Sample {
        public final Item item;
        public final Window window;
        private Sample(Item item, Window window) { this.item = item; this.window = window; }
    }
}
