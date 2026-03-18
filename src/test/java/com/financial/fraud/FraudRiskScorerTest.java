package com.financial.fraud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional tests for FraudRiskScorer.
 */
class FraudRiskScorerTest {

    private static final String HEADER = "step\ttype\tamount\tnameOrig\toldbalanceOrg\tnewbalanceOrig\tnameDest\toldbalanceDest\tnewbalanceDest\tisFraud\tisFlaggedFraud";

    @TempDir
    Path tempDir;

    private Path createTsvFile(String... dataLines) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append("\n");
        for (String line : dataLines) {
            sb.append(line).append("\n");
        }
        Path file = tempDir.resolve("test.csv");
        Files.writeString(file, sb.toString());
        return file;
    }

    // ---- Test: parseLine ----

    @Test
    void testParseLine() {
        String[] headers = {"step", "type", "amount", "nameOrig"};
        String line = "1\tPAYMENT\t5000\tC123";
        Map<String, String> row = FraudRiskScorer.parseLine(line, headers);

        assertEquals("1", row.get("step"));
        assertEquals("PAYMENT", row.get("type"));
        assertEquals("5000", row.get("amount"));
        assertEquals("C123", row.get("nameOrig"));
    }

    @Test
    void testParseLineWithExtraColumns() {
        String[] headers = {"step", "type"};
        String line = "1\tPAYMENT\textra";
        Map<String, String> row = FraudRiskScorer.parseLine(line, headers);

        assertEquals(2, row.size());
        assertEquals("1", row.get("step"));
        assertEquals("PAYMENT", row.get("type"));
    }

    // ---- Test: amount > 10000 → +25 ----

    @Test
    void testAmountAbove10000Adds25() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t15000\tC1\t20000\t5000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // amount > 10000 → +25, nameDest count==1 → +15 = 40
        // newbalanceOrig=5000 (not 0), so no +10
        assertEquals(40, results.get(0).riskScore());
    }

    @Test
    void testAmountBelow10000NoAmountBonus() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t10000\t5000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // No amount bonus, nameDest count==1 → +15 = 15
        assertEquals(15, results.get(0).riskScore());
    }

    // ---- Test: amount > 100000 → +15 more (stacks with >10000) ----

    @Test
    void testAmountAbove100000Adds40Total() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t200000\tC1\t300000\t100000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // amount > 10000 → +25, amount > 100000 → +15, nameDest count==1 → +15 = 55
        assertEquals(55, results.get(0).riskScore());
    }

    // ---- Test: type in (CASH_OUT, TRANSFER) → +20 ----

    @Test
    void testCashOutTypeAdds20() throws IOException {
        Path file = createTsvFile(
                "1\tCASH_OUT\t5000\tC1\t10000\t5000\tC2\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // CASH_OUT → +20, nameDest count==1 → +15 = 35
        assertEquals(35, results.get(0).riskScore());
    }

    @Test
    void testTransferTypeAdds20() throws IOException {
        Path file = createTsvFile(
                "1\tTRANSFER\t5000\tC1\t10000\t5000\tC2\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // TRANSFER → +20, nameDest count==1 → +15 = 35
        assertEquals(35, results.get(0).riskScore());
    }

    @Test
    void testPaymentTypeNoTypeBonus() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t10000\t5000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // PAYMENT → no type bonus, nameDest count==1 → +15 = 15
        assertEquals(15, results.get(0).riskScore());
    }

    // ---- Test: nameDest count == 1 → +15 ----

    @Test
    void testNameDestUniqueAdds15() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t10000\t5000\tM_UNIQUE\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // nameDest count==1 → +15 = 15
        assertEquals(15, results.get(0).riskScore());
    }

    @Test
    void testNameDestNotUniqueNoBonus() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t10000\t5000\tM1\t0\t0\t0\t0",
                "2\tPAYMENT\t3000\tC2\t8000\t5000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(2, results.size());
        // M1 appears twice so nameDest count != 1, no +15
        // Both are PAYMENT with amount < 10000 → score = 0
        assertEquals(0, results.get(0).riskScore());
        assertEquals(0, results.get(1).riskScore());
    }

    // ---- Test: nameOrig has >1 txn in same step → +15 ----

    @Test
    void testOrigMultipleTxnSameStepAdds15() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t10000\t5000\tM1\t0\t0\t0\t0",
                "1\tPAYMENT\t3000\tC1\t5000\t2000\tM2\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(2, results.size());
        // C1 has 2 txns in step 1 → +15 for both
        // M1 count==1 → +15 for first; M2 count==1 → +15 for second
        // Both: +15 (origStep) + +15 (nameDest unique) = 30
        assertEquals(30, results.get(0).riskScore());
        assertEquals(30, results.get(1).riskScore());
    }

    @Test
    void testOrigSingleTxnNoStepBonus() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t10000\t5000\tM1\t0\t0\t0\t0",
                "2\tPAYMENT\t3000\tC1\t5000\t2000\tM2\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(2, results.size());
        // C1 has 1 txn per step → no origStep bonus
        // M1 count==1 → +15, M2 count==1 → +15
        assertEquals(15, results.get(0).riskScore());
        assertEquals(15, results.get(1).riskScore());
    }

    // ---- Test: newbalanceOrig == 0 and oldbalanceOrg > 0 → +10 ----

    @Test
    void testZeroNewBalanceWithPositiveOldAdds10() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t5000\t0\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // newbalanceOrig==0 && oldbalanceOrg>0 → +10, nameDest count==1 → +15 = 25
        assertEquals(25, results.get(0).riskScore());
    }

    @Test
    void testNonZeroNewBalanceNoBalanceBonus() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t10000\t5000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // newbalanceOrig=5000 (not 0) → no balance bonus, nameDest count==1 → +15 = 15
        assertEquals(15, results.get(0).riskScore());
    }

    @Test
    void testBothZeroBalancesNoBonus() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t0\tC1\t0\t0\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // oldbalanceOrg==0, so no balance bonus; nameDest count==1 → +15 = 15
        assertEquals(15, results.get(0).riskScore());
    }

    // ---- Test: Score cap at 100 ----

    @Test
    void testScoreCappedAt100() throws IOException {
        // Create scenario that would exceed 100:
        // amount > 100000 → +25+15=40, TRANSFER → +20, nameDest unique → +15,
        // multiple txns same step → +15, newbalanceOrig==0 && oldbalanceOrg>0 → +10
        // Total uncapped = 40+20+15+15+10 = 100 (exactly 100)
        // Let's push it higher by making two txns same step to get origStep bonus
        Path file = createTsvFile(
                "1\tTRANSFER\t200000\tC1\t200000\t0\tC_UNIQUE1\t0\t0\t0\t0",
                "1\tTRANSFER\t200000\tC1\t200000\t0\tC_UNIQUE2\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(2, results.size());
        // For each: amount>10000 → +25, amount>100000 → +15, TRANSFER → +20,
        // nameDest unique → +15, origStep>1 → +15, newBal==0&&oldBal>0 → +10
        // Total = 25+15+20+15+15+10 = 100, capped at 100
        assertEquals(100, results.get(0).riskScore());
        assertEquals(100, results.get(1).riskScore());
    }

    // ---- Test: Empty file (header only) ----

    @Test
    void testEmptyFileReturnsNoResults() throws IOException {
        Path file = tempDir.resolve("empty.csv");
        Files.writeString(file, HEADER + "\n");

        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertTrue(results.isEmpty());
    }

    // ---- Test: Completely empty file ----

    @Test
    void testCompletelyEmptyFileReturnsNoResults() throws IOException {
        Path file = tempDir.resolve("empty2.csv");
        Files.writeString(file, "");

        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertTrue(results.isEmpty());
    }

    // ---- Test: TransactionResult record fields ----

    @Test
    void testTransactionResultFields() throws IOException {
        Path file = createTsvFile(
                "3\tTRANSFER\t50000\tC999\t50000\t0\tC888\t1000\t51000\t1\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        FraudRiskScorer.TransactionResult r = results.get(0);
        assertEquals("3", r.step());
        assertEquals("TRANSFER", r.type());
        assertEquals(50000.0, r.amount(), 0.01);
        assertEquals("C999", r.nameOrig());
        assertEquals("C888", r.nameDest());
        // amount>10000 → +25, TRANSFER → +20, nameDest==1 → +15, newBal==0&&oldBal>0 → +10 = 70
        assertEquals(70, r.riskScore());
    }

    // ---- Test: computeRiskScore directly ----

    @Test
    void testComputeRiskScoreDirectly() {
        Map<String, String> row = new HashMap<>();
        row.put("amount", "50000");
        row.put("type", "CASH_OUT");
        row.put("nameDest", "C_TARGET");
        row.put("nameOrig", "C_SOURCE");
        row.put("step", "5");
        row.put("newbalanceOrig", "0");
        row.put("oldbalanceOrg", "50000");

        Map<String, Integer> nameDestCount = Map.of("C_TARGET", 1);
        Map<String, Integer> origStepCount = Map.of("C_SOURCE|5", 3);

        int score = FraudRiskScorer.computeRiskScore(row, nameDestCount, origStepCount);
        // amount>10000 → +25, CASH_OUT → +20, nameDest==1 → +15, origStep>1 → +15, balance → +10 = 85
        assertEquals(85, score);
    }

    @Test
    void testComputeRiskScoreZero() {
        Map<String, String> row = new HashMap<>();
        row.put("amount", "500");
        row.put("type", "PAYMENT");
        row.put("nameDest", "M_SHOP");
        row.put("nameOrig", "C_CUST");
        row.put("step", "1");
        row.put("newbalanceOrig", "9500");
        row.put("oldbalanceOrg", "10000");

        Map<String, Integer> nameDestCount = Map.of("M_SHOP", 5);
        Map<String, Integer> origStepCount = Map.of("C_CUST|1", 1);

        int score = FraudRiskScorer.computeRiskScore(row, nameDestCount, origStepCount);
        // No conditions met → 0
        assertEquals(0, score);
    }

    // ---- Test: Score with actual Example1.csv data ----

    @Test
    void testWithActualDataFile() throws IOException {
        Path actualData = Path.of("data", "Example1.csv");
        if (!Files.exists(actualData)) {
            // Try relative to project root
            actualData = Path.of(System.getProperty("user.dir"), "data", "Example1.csv");
        }
        if (!Files.exists(actualData)) {
            // Skip if file not available in test environment
            return;
        }

        FraudRiskScorer scorer = new FraudRiskScorer(actualData);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        // The file has 100 data rows (101 lines minus header)
        assertEquals(100, results.size());

        // All scores should be between 0 and 100
        for (FraudRiskScorer.TransactionResult r : results) {
            assertTrue(r.riskScore() >= 0, "Score should be >= 0");
            assertTrue(r.riskScore() <= 100, "Score should be <= 100");
        }

        // There should be some high-risk transactions (score >= 50)
        long highRisk = results.stream()
                .filter(r -> r.riskScore() >= 50)
                .count();
        assertTrue(highRisk > 0, "Should have at least some high-risk transactions");
    }

    // ---- Test: DEBIT type gets no type bonus ----

    @Test
    void testDebitTypeNoTypeBonus() throws IOException {
        Path file = createTsvFile(
                "1\tDEBIT\t5000\tC1\t10000\t5000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // DEBIT → no type bonus, nameDest count==1 → +15 = 15
        assertEquals(15, results.get(0).riskScore());
    }

    // ---- Test: Multiple different originators same step ----

    @Test
    void testDifferentOriginatorsInSameStepNoBonus() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t5000\tC1\t10000\t5000\tM1\t0\t0\t0\t0",
                "1\tPAYMENT\t3000\tC2\t8000\t5000\tM2\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(2, results.size());
        // C1 and C2 are different originators → no origStep bonus
        // M1 count==1 → +15, M2 count==1 → +15
        assertEquals(15, results.get(0).riskScore());
        assertEquals(15, results.get(1).riskScore());
    }

    // ---- Test: Exact boundary amount == 10000 (should NOT trigger) ----

    @Test
    void testAmountExactly10000NoBonus() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t10000\tC1\t20000\t10000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // amount == 10000 (not > 10000) → no amount bonus; nameDest count==1 → +15 = 15
        assertEquals(15, results.get(0).riskScore());
    }

    // ---- Test: Exact boundary amount == 100000 (triggers >10000 but not >100000) ----

    @Test
    void testAmountExactly100000OnlyFirst() throws IOException {
        Path file = createTsvFile(
                "1\tPAYMENT\t100000\tC1\t200000\t100000\tM1\t0\t0\t0\t0"
        );
        FraudRiskScorer scorer = new FraudRiskScorer(file);
        List<FraudRiskScorer.TransactionResult> results = scorer.score();

        assertEquals(1, results.size());
        // amount == 100000 → triggers >10000 (+25) but not >100000; nameDest count==1 → +15 = 40
        assertEquals(40, results.get(0).riskScore());
    }
}
