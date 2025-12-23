import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

public class Main {

    static int passCount = 0;
    static int failCount = 0;

    // ANSI Color Codes
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_CYAN = "\u001B[36m";

    public static void main(String[] args) throws IOException {
        String baseFolder = ".";
        
        System.out.println(ANSI_CYAN + "\n==========================================" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "      CPU SCHEDULER VERIFICATION RUN      " + ANSI_RESET);
        System.out.println(ANSI_CYAN + "==========================================\n" + ANSI_RESET);

        // Run AG tests
        runAGTests(new File(baseFolder + "/AG"));
        
        // Run Other Scheduler tests
        runOtherSchedulerTests(new File(baseFolder + "/Other_Schedulers"));

        // Print summary
        System.out.println("\n" + ANSI_CYAN + "==========================================" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "              FINAL SUMMARY               " + ANSI_RESET);
        System.out.println(ANSI_CYAN + "==========================================" + ANSI_RESET);
        System.out.println(ANSI_GREEN + "  PASSED: " + passCount + ANSI_RESET);
        System.out.println(ANSI_RED   + "  FAILED: " + failCount + ANSI_RESET);
        System.out.println("  TOTAL:  " + (passCount + failCount));
        System.out.println(ANSI_CYAN + "==========================================" + ANSI_RESET);
    }

    private static void runAGTests(File folder) throws IOException {
        System.out.println(ANSI_BLUE + ">>> RUNNING AG SCHEDULING TESTS..." + ANSI_RESET);
        
        File[] files = folder.listFiles();
        if (files == null) return;
        Arrays.sort(files);

        for (File file : files) {
            if (!file.getName().endsWith(".json")) continue;
            
            JSONObject root = new JSONObject(new String(Files.readAllBytes(file.toPath())));
            JSONArray processesJson = root.getJSONObject("input").getJSONArray("processes");
            JSONObject expected = root.getJSONObject("expectedOutput");

            List<Process> processes = new ArrayList<>();
            for (int i = 0; i < processesJson.length(); i++) {
                JSONObject obj = processesJson.getJSONObject(i);
                processes.add(new Process(
                        obj.getString("name"),
                        obj.getInt("arrival"),
                        obj.getInt("burst"),
                        obj.getInt("priority"),
                        obj.getInt("quantum")
                ));
            }

            SchedulerResult result = CPUScheduler.agScheduling(processes);
            validateAGResult(file.getName(), result, expected);
        }
        System.out.println();
    }

    private static void runOtherSchedulerTests(File folder) throws IOException {
        System.out.println(ANSI_BLUE + ">>> RUNNING GENERAL SCHEDULER TESTS..." + ANSI_RESET);
        
        File[] files = folder.listFiles();
        if (files == null) return;
        Arrays.sort(files);

        for (File file : files) {
            if (!file.getName().endsWith(".json")) continue;
            
            JSONObject root = new JSONObject(new String(Files.readAllBytes(file.toPath())));
            JSONObject input = root.getJSONObject("input");
            JSONArray processesJson = input.getJSONArray("processes");
            JSONObject expected = root.getJSONObject("expectedOutput");

            int contextSwitch = input.optInt("contextSwitch", 0);
            int rrQuantum = input.optInt("rrQuantum", 2);
            int agingInterval = input.optInt("agingInterval", 5);

            List<Process> processes = new ArrayList<>();
            for (int i = 0; i < processesJson.length(); i++) {
                JSONObject obj = processesJson.getJSONObject(i);
                processes.add(new Process(
                        obj.getString("name"),
                        obj.getInt("arrival"),
                        obj.getInt("burst"),
                        obj.getInt("priority"),
                        4 // Default quantum for non-AG tests
                ));
            }
            
            String testName = file.getName();

            // SJF Test
            SchedulerResult sjfResult = CPUScheduler.preemptiveSJF(processes, contextSwitch);
            validateResult(testName + " [SJF]", sjfResult, expected.getJSONObject("SJF"));

            // Round Robin Test
            SchedulerResult rrResult = CPUScheduler.roundRobin(processes, rrQuantum, contextSwitch);
            validateResult(testName + " [RR]", rrResult, expected.getJSONObject("RR"));

            // Priority Test
            SchedulerResult priorityResult = CPUScheduler.priorityPreemptive(processes, contextSwitch, agingInterval);
            validateResult(testName + " [Priority]", priorityResult, expected.getJSONObject("Priority"));
        }
        System.out.println();
    }

    private static void validateResult(String testName, SchedulerResult result, JSONObject expected) {
        boolean passed = true;
        StringBuilder errors = new StringBuilder();

        // Check execution order
        JSONArray expectedOrder = expected.getJSONArray("executionOrder");
        List<String> expectedList = new ArrayList<>();
        for (int i = 0; i < expectedOrder.length(); i++) expectedList.add(expectedOrder.getString(i));
        
        if (!result.executionOrder.equals(expectedList)) {
            passed = false;
            errors.append("    Execution Order mismatch!\n");
            errors.append("      Expected: " + expectedList + "\n");
            errors.append("      Actual:   " + result.executionOrder + "\n");
        }

        double expectedAvgWait = expected.getDouble("averageWaitingTime");
        if (Math.abs(result.averageWaitingTime - expectedAvgWait) > 0.01) {
            passed = false;
            errors.append("    Avg Wait Time mismatch! Expected: " + expectedAvgWait + ", Got: " + result.averageWaitingTime + "\n");
        }

        double expectedAvgTurn = expected.getDouble("averageTurnaroundTime");
        if (Math.abs(result.averageTurnaroundTime - expectedAvgTurn) > 0.01) {
            passed = false;
            errors.append("    Avg Turnaround mismatch! Expected: " + expectedAvgTurn + ", Got: " + result.averageTurnaroundTime + "\n");
        }

        printStatus(testName, passed, errors.toString());
    }

    private static void validateAGResult(String fileName, SchedulerResult result, JSONObject expected) {
        boolean passed = true;
        StringBuilder errors = new StringBuilder();

        JSONArray expectedOrder = expected.getJSONArray("executionOrder");
        List<String> expectedList = new ArrayList<>();
        for (int i = 0; i < expectedOrder.length(); i++) expectedList.add(expectedOrder.getString(i));
        
        if (!result.executionOrder.equals(expectedList)) {
            passed = false;
            errors.append("    Execution Order mismatch!\n");
            errors.append("      Expected: " + expectedList + "\n");
            errors.append("      Actual:   " + result.executionOrder + "\n");
        }

        // Check quantum histories
        JSONArray processResults = expected.getJSONArray("processResults");
        for (int i = 0; i < processResults.length(); i++) {
            JSONObject pr = processResults.getJSONObject(i);
            String pName = pr.getString("name");
            JSONArray expectedQH = pr.getJSONArray("quantumHistory");
            List<Integer> expectedQHList = new ArrayList<>();
            for (int j = 0; j < expectedQH.length(); j++) expectedQHList.add(expectedQH.getInt(j));
            
            List<Integer> actualQH = result.quantumHistory.get(pName);
            // Null check
            if (actualQH == null) actualQH = new ArrayList<>();

            if (!expectedQHList.equals(actualQH)) {
                passed = false;
                errors.append("    " + pName + " Quantum History mismatch!\n");
                errors.append("      Expected: " + expectedQHList + "\n");
                errors.append("      Actual:   " + actualQH + "\n");
            }
        }

        double expectedAvgWait = expected.getDouble("averageWaitingTime");
        if (Math.abs(result.averageWaitingTime - expectedAvgWait) > 0.01) {
            passed = false;
            errors.append("    Avg Wait Time mismatch! Expected: " + expectedAvgWait + ", Got: " + result.averageWaitingTime + "\n");
        }

        double expectedAvgTurn = expected.getDouble("averageTurnaroundTime");
        if (Math.abs(result.averageTurnaroundTime - expectedAvgTurn) > 0.01) {
            passed = false;
            errors.append("    Avg Turnaround mismatch! Expected: " + expectedAvgTurn + ", Got: " + result.averageTurnaroundTime + "\n");
        }

        printStatus(fileName + " [AG]", passed, errors.toString());
    }

    private static void printStatus(String testName, boolean passed, String errors) {
        if (passed) {
            System.out.println(ANSI_GREEN + "  [PASS] " + ANSI_RESET + testName);
            passCount++;
        } else {
            System.out.println(ANSI_RED + "  [FAIL] " + ANSI_RESET + testName);
            System.out.println(ANSI_YELLOW + errors + ANSI_RESET);
            failCount++;
        }
    }
}
