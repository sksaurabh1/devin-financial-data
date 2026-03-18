package com.financial.fraud;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fraud Risk Scorer
 *
 * Reads data/Example1.csv (tab-delimited TSV) and computes an additive risk score
 * for each transaction using a two-pass approach. Uses only Java stdlib.
 */
public class FraudRiskScorer {

    /**
     * Represents a scored transaction result.
     */
    public record TransactionResult(
            String step,
            String type,
            double amount,
            String nameOrig,
            String nameDest,
            int riskScore
    ) {}

    private static final Set<String> HIGH_RISK_TYPES = Set.of("CASH_OUT", "TRANSFER");

    private final Path dataPath;

    public FraudRiskScorer(Path dataPath) {
        this.dataPath = dataPath;
    }

    /**
     * Parses a single TSV line into a map of column name to value.
     */
    static Map<String, String> parseLine(String line, String[] headers) {
        String[] values = line.split("\t", -1);
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < headers.length && i < values.length; i++) {
            row.put(headers[i], values[i]);
        }
        return row;
    }

    /**
     * Computes the risk score for a single transaction given pre-scan counts.
     */
    static int computeRiskScore(Map<String, String> row,
                                Map<String, Integer> nameDestCount,
                                Map<String, Integer> origStepCount) {
        int score = 0;

        double amount = Double.parseDouble(row.get("amount"));
        String txnType = row.get("type");
        String nameDest = row.get("nameDest");
        String nameOrig = row.get("nameOrig");
        String step = row.get("step");
        double newBalanceOrig = Double.parseDouble(row.get("newbalanceOrig"));
        double oldBalanceOrg = Double.parseDouble(row.get("oldbalanceOrg"));

        // amount > 10000 → +25
        if (amount > 10000) {
            score += 25;
        }

        // amount > 100000 → +15 more
        if (amount > 100000) {
            score += 15;
        }

        // type in (CASH_OUT, TRANSFER) → +20
        if (HIGH_RISK_TYPES.contains(txnType)) {
            score += 20;
        }

        // nameDest count == 1 → +15
        if (nameDestCount.getOrDefault(nameDest, 0) == 1) {
            score += 15;
        }

        // nameOrig has >1 txn in same step → +15
        String origStepKey = nameOrig + "|" + step;
        if (origStepCount.getOrDefault(origStepKey, 0) > 1) {
            score += 15;
        }

        // newbalanceOrig == 0 and oldbalanceOrg > 0 → +10
        if (newBalanceOrig == 0 && oldBalanceOrg > 0) {
            score += 10;
        }

        // Cap at 100
        return Math.min(score, 100);
    }

    /**
     * Runs both passes and returns scored transaction results.
     */
    public List<TransactionResult> score() throws IOException {
        // ---- Pass 1: Pre-scan ----
        Map<String, Integer> nameDestCount = new HashMap<>();
        Map<String, Integer> origStepCount = new HashMap<>();

        String[] headers;
        try (BufferedReader reader = Files.newBufferedReader(dataPath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            headers = headerLine.split("\t");

            String line;
            while ((line = reader.readLine()) != null) {
                Map<String, String> row = parseLine(line, headers);
                String nameDest = row.get("nameDest");
                String nameOrig = row.get("nameOrig");
                String step = row.get("step");

                nameDestCount.merge(nameDest, 1, Integer::sum);
                origStepCount.merge(nameOrig + "|" + step, 1, Integer::sum);
            }
        }

        // ---- Pass 2: Score each transaction ----
        List<TransactionResult> results = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(dataPath)) {
            // Skip header
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                Map<String, String> row = parseLine(line, headers);

                int riskScore = computeRiskScore(row, nameDestCount, origStepCount);

                results.add(new TransactionResult(
                        row.get("step"),
                        row.get("type"),
                        Double.parseDouble(row.get("amount")),
                        row.get("nameOrig"),
                        row.get("nameDest"),
                        riskScore
                ));
            }
        }

        return results;
    }

    /**
     * Prints scored results to stdout in a formatted table.
     */
    public static void printResults(List<TransactionResult> results) {
        System.out.printf("%-6s %-12s %14s %-16s %-16s %10s%n",
                "step", "type", "amount", "nameOrig", "nameDest", "risk_score");
        System.out.println("-".repeat(80));

        for (TransactionResult r : results) {
            System.out.printf("%-6s %-12s %14.2f %-16s %-16s %10d%n",
                    r.step(), r.type(), r.amount(), r.nameOrig(), r.nameDest(), r.riskScore());
        }

        System.out.printf("%nTotal transactions scored: %d%n", results.size());

        long highRiskCount = results.stream()
                .filter(r -> r.riskScore() >= 50)
                .count();
        System.out.printf("High-risk transactions (score >= 50): %d%n", highRiskCount);
    }

    public static void main(String[] args) throws IOException {
        // Resolve path relative to current working directory (repo root)
        Path dataPath;
        if (args.length > 0) {
            dataPath = Paths.get(args[0]);
        } else {
            dataPath = Paths.get("data", "Example1.csv");
        }

        FraudRiskScorer scorer = new FraudRiskScorer(dataPath);
        List<TransactionResult> results = scorer.score();
        printResults(results);
    }
}
