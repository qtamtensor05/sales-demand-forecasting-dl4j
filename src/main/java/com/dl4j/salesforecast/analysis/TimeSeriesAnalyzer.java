package com.dl4j.salesforecast.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Analysis only: does not fill gaps, merge duplicates or transform sales. */
public final class TimeSeriesAnalyzer {
    private TimeSeriesAnalyzer() {
    }

    public static TimeSeriesResult analyze(String filePath, int storeId, int itemId) {
        List<Observation> observations = readSeries(filePath, storeId, itemId);
        if (observations.isEmpty()) {
            throw new IllegalArgumentException(
                    "No records found for store=" + storeId + ", item=" + itemId);
        }
        observations.sort(Comparator.comparing(observation -> observation.date));
        TimeSeriesResult result = new TimeSeriesResult(storeId, itemId, observations);
        printReport(result);
        return result;
    }

    private static List<Observation> readSeries(String filePath, int storeId, int itemId) {
        List<Observation> observations = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Path.of(filePath), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            for (String column : List.of("date", "store", "item", "sales")) {
                if (!parser.getHeaderMap().containsKey(column)) {
                    throw new IllegalArgumentException("Missing CSV column: " + column);
                }
            }
            for (CSVRecord record : parser) {
                try {
                    int store = Integer.parseInt(record.get("store").trim());
                    int item = Integer.parseInt(record.get("item").trim());
                    if (store != storeId || item != itemId) {
                        continue;
                    }
                    LocalDate date = LocalDate.parse(record.get("date").trim());
                    int sales = Integer.parseInt(record.get("sales").trim());
                    if (sales < 0) {
                        throw new IllegalArgumentException("Sales must be non-negative");
                    }
                    observations.add(new Observation(date, sales));
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException(
                            "Invalid CSV record " + record.getRecordNumber() + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read dataset: " + filePath, e);
        }
        return observations;
    }

    private static void printReport(TimeSeriesResult result) {
        System.out.println("\n==========================================");
        System.out.println("TIME SERIES ANALYSIS");
        System.out.println("==========================================");
        System.out.printf(Locale.ROOT,
                "Store               : %d%nItem                : %d%n"
                        + "Date range          : %s -> %s%n"
                        + "Total days          : %d%nTotal records       : %d%n"
                        + "Total sales         : %.0f%nMin sales           : %.0f%n"
                        + "Max sales           : %.0f%nMean                : %.4f%n"
                        + "Median              : %.4f%nStandard deviation  : %.4f%n"
                        + "Zero-sales days     : %d%nMissing days        : %d%n"
                        + "Gap intervals       : %d%nDuplicate dates     : %d%n"
                        + "Extra records       : %d%nTime series status  : %s%n",
                result.storeId, result.itemId, result.getStartDate(), result.getEndDate(),
                result.totalDays, result.dates.size(), result.totalSales, result.min, result.max,
                result.mean, result.median, result.standardDeviation, result.zeroSalesDays,
                result.missingDays, result.gapCount, result.duplicateDates,
                result.duplicateRecords, result.isComplete() ? "OK" : "CHECK");
        System.out.println("Standard deviation: population (divide by N).");
        System.out.println("Missing days: within this series' first/last date.");
        if (result.duplicateDates > 0) {
            System.out.println("Duplicates are retained; sales statistics include all records.");
        }

        Map<Integer, DoubleSummaryStatistics> weekdays = new TreeMap<>();
        Map<Integer, DoubleSummaryStatistics> months = new TreeMap<>();
        Map<Integer, DoubleSummaryStatistics> years = new TreeMap<>();
        for (int i = 0; i < result.dates.size(); i++) {
            LocalDate date = result.dates.get(i);
            double sales = result.sales.get(i);
            weekdays.computeIfAbsent(date.getDayOfWeek().getValue(), k -> new DoubleSummaryStatistics()).accept(sales);
            months.computeIfAbsent(date.getMonthValue(), k -> new DoubleSummaryStatistics()).accept(sales);
            years.computeIfAbsent(date.getYear(), k -> new DoubleSummaryStatistics()).accept(sales);
        }
        System.out.println("\nAVERAGE SALES BY DAY OF WEEK");
        for (DayOfWeek day : DayOfWeek.values()) {
            printAverage(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH), weekdays.get(day.getValue()));
        }
        System.out.println("\nAVERAGE SALES BY MONTH");
        for (Month month : Month.values()) {
            printAverage(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), months.get(month.getValue()));
        }
        System.out.println("\nAVERAGE SALES BY YEAR");
        for (int year = result.getStartDate().getYear(); year <= result.getEndDate().getYear(); year++) {
            printAverage(Integer.toString(year), years.get(year));
        }
        printRecords("FIRST 10 RECORDS", result, 0, Math.min(10, result.dates.size()));
        printRecords("LAST 10 RECORDS", result, Math.max(0, result.dates.size() - 10), result.dates.size());
        System.out.println("==========================================");
    }

    private static void printAverage(String label, DoubleSummaryStatistics statistics) {
        if (statistics == null) {
            System.out.printf("%-20s: N/A%n", label);
        } else {
            System.out.printf(Locale.ROOT, "%-20s: %.4f%n", label, statistics.getAverage());
        }
    }

    private static void printRecords(String title, TimeSeriesResult result, int from, int to) {
        System.out.println("\n" + title);
        System.out.printf("%-13s%s%n", "Date", "Sales");
        for (int i = from; i < to; i++) {
            System.out.printf(Locale.ROOT, "%-13s%.0f%n", result.dates.get(i), result.sales.get(i));
        }
    }

    private static final class Observation {
        private final LocalDate date;
        private final double sales;

        private Observation(LocalDate date, double sales) {
            this.date = date;
            this.sales = sales;
        }
    }

    /** Immutable, aligned date/sales lists in chronological order, including duplicates. */
    public static final class TimeSeriesResult {
        private final int storeId;
        private final int itemId;
        private final List<LocalDate> dates;
        private final List<Double> sales;
        private final double min;
        private final double max;
        private final double mean;
        private final double median;
        private final double standardDeviation;
        private final double totalSales;
        private final long totalDays;
        private final long zeroSalesDays;
        private final long missingDays;
        private final long gapCount;
        private final long duplicateDates;
        private final long duplicateRecords;

        private TimeSeriesResult(int storeId, int itemId, List<Observation> observations) {
            this.storeId = storeId;
            this.itemId = itemId;
            List<LocalDate> dateValues = new ArrayList<>();
            List<Double> saleValues = new ArrayList<>();
            Map<LocalDate, Integer> counts = new TreeMap<>();
            long zeros = 0;
            LocalDate lastZeroDate = null;
            for (Observation observation : observations) {
                dateValues.add(observation.date);
                saleValues.add(observation.sales);
                counts.merge(observation.date, 1, Integer::sum);
                if (observation.sales == 0 && !observation.date.equals(lastZeroDate)) {
                    zeros++;
                    lastZeroDate = observation.date;
                }
            }
            dates = List.copyOf(dateValues);
            sales = List.copyOf(saleValues);
            totalDays = counts.size();
            zeroSalesDays = zeros;
            duplicateDates = counts.values().stream().filter(count -> count > 1).count();
            duplicateRecords = observations.size() - totalDays;
            missingDays = ChronoUnit.DAYS.between(getStartDate(), getEndDate()) + 1 - totalDays;
            long gaps = 0;
            LocalDate previous = null;
            for (LocalDate date : counts.keySet()) {
                if (previous != null && ChronoUnit.DAYS.between(previous, date) > 1) {
                    gaps++;
                }
                previous = date;
            }
            gapCount = gaps;

            DoubleSummaryStatistics statistics = sales.stream().mapToDouble(Double::doubleValue).summaryStatistics();
            min = statistics.getMin();
            max = statistics.getMax();
            totalSales = statistics.getSum();
            mean = statistics.getAverage();
            List<Double> sortedSales = new ArrayList<>(sales);
            sortedSales.sort(Double::compare);
            int middle = sortedSales.size() / 2;
            median = sortedSales.size() % 2 == 0
                    ? (sortedSales.get(middle - 1) + sortedSales.get(middle)) / 2.0
                    : sortedSales.get(middle);
            double squaredDifferences = 0;
            for (double value : sales) {
                double difference = value - mean;
                squaredDifferences += difference * difference;
            }
            standardDeviation = Math.sqrt(squaredDifferences / sales.size());
        }

        public int getStoreId() { return storeId; }
        public int getItemId() { return itemId; }
        public List<LocalDate> getDates() { return dates; }
        public List<Double> getSales() { return sales; }
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getMean() { return mean; }
        public double getMedian() { return median; }
        public double getStandardDeviation() { return standardDeviation; }
        public double getTotalSales() { return totalSales; }
        public LocalDate getStartDate() { return dates.get(0); }
        public LocalDate getEndDate() { return dates.get(dates.size() - 1); }
        public long getTotalDays() { return totalDays; }
        public long getZeroSalesDays() { return zeroSalesDays; }
        public long getMissingDays() { return missingDays; }
        public long getGapCount() { return gapCount; }
        public long getDuplicateDates() { return duplicateDates; }
        public long getDuplicateRecords() { return duplicateRecords; }
        public boolean isComplete() { return missingDays == 0 && duplicateDates == 0; }
    }
}
