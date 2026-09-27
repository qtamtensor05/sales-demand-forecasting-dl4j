package com.dl4j.salesforecast.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Reader;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class SalesDataAnalyzer {

    public static void analyze(String filePath) {
        try {
            PrintWriter output = new PrintWriter(System.out, true);
            analyze(filePath, output);
        } catch (IOException e) {
            throw new IllegalStateException("Error reading dataset: " + filePath, e);
        }
    }

    public static void analyze(String filePath, Path outputPath) throws IOException {
        StringWriter report = new StringWriter();
        try (PrintWriter output = new PrintWriter(report)) {
            analyze(filePath, output);
        }
        Path absolutePath = outputPath.toAbsolutePath();
        Files.createDirectories(absolutePath.getParent());
        Files.writeString(absolutePath, report.toString(), StandardCharsets.UTF_8);
    }

    private static void analyze(String filePath, PrintWriter output) throws IOException {

        long totalRecords = 0;
        long totalSales = 0;

        int minSales = Integer.MAX_VALUE;
        int maxSales = Integer.MIN_VALUE;

        Set<Integer> stores = new HashSet<>();
        Set<Integer> items = new HashSet<>();
        Map<Integer, Long> salesByStore = new TreeMap<>();
        Map<Integer, Long> salesByItem = new TreeMap<>();
        Map<Integer, Map<Integer, PairStatistics>> pairs = new TreeMap<>();
        Set<LocalDate> zeroSalesDates = new HashSet<>();
        long zeroSalesRecords = 0;

        LocalDate minDate = null;
        LocalDate maxDate = null;

        try (
                Reader reader = Files.newBufferedReader(Path.of(filePath), StandardCharsets.UTF_8);

                CSVParser parser = CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build()
                        .parse(reader)
        ) {

            for (CSVRecord record : parser) {

                LocalDate date = LocalDate.parse(record.get("date").trim());

                int store =
                        Integer.parseInt(record.get("store").trim());

                int item =
                        Integer.parseInt(record.get("item").trim());

                int sales =
                        Integer.parseInt(record.get("sales").trim());

                if (store <= 0 || item <= 0 || sales < 0) {
                    throw new IllegalArgumentException("Store/item IDs must be positive and sales must be non-negative (record "
                            + record.getRecordNumber() + ")");
                }

                totalRecords++;

                totalSales += sales;

                stores.add(store);
                items.add(item);
                salesByStore.merge(store, (long) sales, Long::sum);
                salesByItem.merge(item, (long) sales, Long::sum);
                PairStatistics pair = pairs.computeIfAbsent(store, key -> new TreeMap<>())
                        .computeIfAbsent(item, key -> new PairStatistics());
                if (!pair.dates.add(date)) {
                    pair.duplicateRecords++;
                }
                if (sales == 0) {
                    zeroSalesRecords++;
                    zeroSalesDates.add(date);
                    pair.zeroDates.add(date);
                }

                if (sales < minSales) {
                    minSales = sales;
                }

                if (sales > maxSales) {
                    maxSales = sales;
                }

                if (minDate == null || date.isBefore(minDate)) {
                    minDate = date;
                }

                if (maxDate == null || date.isAfter(maxDate)) {
                    maxDate = date;
                }
            }

            if (totalRecords == 0) {
                output.println("Dataset contains no records.");
                return;
            }

            double averageSales =
                    (double) totalSales / totalRecords;

            output.println(
                    "=========================================="
            );

            output.println(
                    "       SALES DATASET STATISTICS"
            );

            output.println(
                    "=========================================="
            );

            output.println(
                    "Total records      : " + totalRecords
            );

            output.println(
                    "Date range         : "
                            + minDate
                            + " -> "
                            + maxDate
            );

            output.println(
                    "Number of stores   : " + stores.size()
            );

            output.println(
                    "Number of items    : " + items.size()
            );

            output.println(
                    "Total sales        : " + totalSales
            );

            output.printf(
                    "Average sales      : %.2f%n",
                    averageSales
            );

            output.println(
                    "Minimum sales      : " + minSales
            );

            output.println(
                    "Maximum sales      : " + maxSales
            );

            output.println("\nTOTAL SALES BY STORE (units)");
            salesByStore.forEach((store, sales) ->
                    output.printf("Store %d: %d%n", store, sales));

            output.println("\nTOP 10 ITEMS BY TOTAL SALES (all stores)");
            salesByItem.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .limit(10)
                    .forEach(entry -> output.printf("Item %d: %d%n", entry.getKey(), entry.getValue()));

            output.println("\nZERO SALES");
            output.println("Records with sales = 0: " + zeroSalesRecords);
            output.println("Distinct dates with at least one sales = 0 record: " + zeroSalesDates.size());

            long expectedDays = ChronoUnit.DAYS.between(minDate, maxDate) + 1;
            long completePairs = 0;
            output.println("\nSTORE + ITEM DAILY COVERAGE");
            output.printf("Date range    : %s -> %s%n", minDate, maxDate);
            output.printf("Expected days : %d%n", expectedDays);
            output.println("Missing days: within the full dataset range.");
            output.println("Internal gaps: within each pair's own date range.");
            for (int store : new TreeSet<>(stores)) {
                output.printf("%n==========================================%nSTORE %d%n", store);
                for (int item : new TreeSet<>(items)) {
                    PairStatistics pair = pairs.get(store).get(item);
                    int uniqueDays = pair == null ? 0 : pair.dates.size();
                    long missingDays = expectedDays - uniqueDays;
                    long internalGaps = pair == null ? 0
                            : ChronoUnit.DAYS.between(pair.dates.first(), pair.dates.last()) + 1 - uniqueDays;
                    long duplicates = pair == null ? 0 : pair.duplicateRecords;
                    boolean complete = missingDays == 0 && duplicates == 0;
                    if (complete) {
                        completePairs++;
                    }
                    output.printf("%n  Item %d [%s]%n", item, complete ? "OK" : "CHECK");
                    output.printf("    Unique days       : %d%n", uniqueDays);
                    output.printf("    Zero-sales days   : %d%n", pair == null ? 0 : pair.zeroDates.size());
                    output.printf("    Missing days      : %d%n", missingDays);
                    output.printf("    Internal gaps     : %d%n", internalGaps);
                    output.printf("    Duplicate records : %d%n", duplicates);
                }
            }
            long expectedPairs = (long) stores.size() * items.size();
            output.println("\nDAILY COVERAGE SUMMARY");
            output.printf("Complete pairs : %d/%d%n", completePairs, expectedPairs);
            output.printf("Pairs to check : %d%n", expectedPairs - completePairs);
            output.println("Complete = exactly one record per day.");

            output.println(
                    "=========================================="
            );

        }
    }

    private static class PairStatistics {
        private final TreeSet<LocalDate> dates = new TreeSet<>();
        private final Set<LocalDate> zeroDates = new HashSet<>();
        private long duplicateRecords;
    }
}
