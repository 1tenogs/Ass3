import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

public class Main {

    static int passCount = 0;
    static int failCount = 0;

    public static void main(String[] args) throws IOException {
        String baseFolder = ".";

        // Run AG tests
        runAGTests(new File(baseFolder + "/AG"));
        
        // Run Other Scheduler tests
        runOtherSchedulerTests(new File(baseFolder + "/Other_Schedulers"));

        // Print summary
        System.out.println("\n========== TEST SUMMARY ==========");
        System.out.println("PASSED: " + passCount);
        System.out.println("FAILED: " + failCount);
        System.out.println("TOTAL:  " + (passCount + failCount));
    }

    private static void runAGTests(File folder) throws IOException {
        System.out.println("\n========== AG SCHEDULING TESTS ==========\n");
        
        for (File file : folder.listFiles()) {
            if (!file.getName().endsWith(".json")) continue;
            
            System.out.println("===== Test: " + file.getName() + " =====");

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
            
            System.out.println("--- AG Scheduling ---");
            printResultWithQuantum(result);
            
            // Validate against expected
            validateAGResult(result, expected);
            System.out.println();
        }
    }

    private static void runOtherSchedulerTests(File folder) throws IOException {
        System.out.println("\n========== OTHER SCHEDULER TESTS ==========\n");
        
        for (File file : folder.listFiles()) {
            if (!file.getName().endsWith(".json")) continue;
            
            System.out.println("===== Test: " + file.getName() + " =====");

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

            // SJF Test
            System.out.println("--- Preemptive SJF Scheduling ---");
            SchedulerResult sjfResult = CPUScheduler.preemptiveSJF(processes, contextSwitch);
            printResult(sjfResult);
            validateResult("SJF", sjfResult, expected.getJSONObject("SJF"));

            // Round Robin Test
            System.out.println("--- Round Robin Scheduling ---");
            SchedulerResult rrResult = CPUScheduler.roundRobin(processes, rrQuantum, contextSwitch);
            printResult(rrResult);
            validateResult("RR", rrResult, expected.getJSONObject("RR"));

            // Priority Test
            System.out.println("--- Priority Preemptive Scheduling ---");
            SchedulerResult priorityResult = CPUScheduler.priorityPreemptive(processes, contextSwitch, agingInterval);
            printResult(priorityResult);
            validateResult("Priority", priorityResult, expected.getJSONObject("Priority"));

            System.out.println();
        }
    }

    private static void validateResult(String name, SchedulerResult result, JSONObject expected) {
        boolean passed = true;
        StringBuilder errors = new StringBuilder();

        // Check execution order
        JSONArray expectedOrder = expected.getJSONArray("executionOrder");
        List<String> expectedList = new ArrayList<>();
        for (int i = 0; i < expectedOrder.length(); i++) {
            expectedList.add(expectedOrder.getString(i));
        }
        
        if (!result.executionOrder.equals(expectedList)) {
            passed = false;
            errors.append("  Execution Order mismatch!\n");
            errors.append("    Expected: " + expectedList + "\n");
            errors.append("    Actual:   " + result.executionOrder + "\n");
        }

        // Check average waiting time
        double expectedAvgWait = expected.getDouble("averageWaitingTime");
        if (Math.abs(result.averageWaitingTime - expectedAvgWait) > 0.01) {
            passed = false;
            errors.append("  Avg Waiting Time mismatch! Expected: " + expectedAvgWait + ", Got: " + result.averageWaitingTime + "\n");
        }

        // Check average turnaround time
        double expectedAvgTurn = expected.getDouble("averageTurnaroundTime");
        if (Math.abs(result.averageTurnaroundTime - expectedAvgTurn) > 0.01) {
            passed = false;
            errors.append("  Avg Turnaround Time mismatch! Expected: " + expectedAvgTurn + ", Got: " + result.averageTurnaroundTime + "\n");
        }

        if (passed) {
            System.out.println("[PASS] " + name);
            passCount++;
        } else {
            System.out.println("[FAIL] " + name);
            System.out.print(errors.toString());
            failCount++;
        }
    }

    private static void validateAGResult(SchedulerResult result, JSONObject expected) {
        boolean passed = true;
        StringBuilder errors = new StringBuilder();

        // Check execution order
        JSONArray expectedOrder = expected.getJSONArray("executionOrder");
        List<String> expectedList = new ArrayList<>();
        for (int i = 0; i < expectedOrder.length(); i++) {
            expectedList.add(expectedOrder.getString(i));
        }
        
        if (!result.executionOrder.equals(expectedList)) {
            passed = false;
            errors.append("  Execution Order mismatch!\n");
            errors.append("    Expected: " + expectedList + "\n");
            errors.append("    Actual:   " + result.executionOrder + "\n");
        }

        // Check quantum histories
        JSONArray processResults = expected.getJSONArray("processResults");
        for (int i = 0; i < processResults.length(); i++) {
            JSONObject pr = processResults.getJSONObject(i);
            String pName = pr.getString("name");
            JSONArray expectedQH = pr.getJSONArray("quantumHistory");
            
            List<Integer> expectedQHList = new ArrayList<>();
            for (int j = 0; j < expectedQH.length(); j++) {
                expectedQHList.add(expectedQH.getInt(j));
            }
            
            List<Integer> actualQH = result.quantumHistory.get(pName);
            if (!expectedQHList.equals(actualQH)) {
                passed = false;
                errors.append("  " + pName + " Quantum History mismatch!\n");
                errors.append("    Expected: " + expectedQHList + "\n");
                errors.append("    Actual:   " + actualQH + "\n");
            }
        }

        // Check average waiting time
        double expectedAvgWait = expected.getDouble("averageWaitingTime");
        if (Math.abs(result.averageWaitingTime - expectedAvgWait) > 0.01) {
            passed = false;
            errors.append("  Avg Waiting Time mismatch! Expected: " + expectedAvgWait + ", Got: " + result.averageWaitingTime + "\n");
        }

        // Check average turnaround time
        double expectedAvgTurn = expected.getDouble("averageTurnaroundTime");
        if (Math.abs(result.averageTurnaroundTime - expectedAvgTurn) > 0.01) {
            passed = false;
            errors.append("  Avg Turnaround Time mismatch! Expected: " + expectedAvgTurn + ", Got: " + result.averageTurnaroundTime + "\n");
        }

        if (passed) {
            System.out.println("[PASS] AG Scheduling");
            passCount++;
        } else {
            System.out.println("[FAIL] AG Scheduling");
            System.out.print(errors.toString());
            failCount++;
        }
    }

    private static void printResult(SchedulerResult r) {
        System.out.println("Execution Order: " + r.executionOrder);
        System.out.println("Waiting Times: " + r.waitingTimes);
        System.out.println("Turnaround Times: " + r.turnaroundTimes);
        System.out.println("Average Waiting Time: " + r.averageWaitingTime);
        System.out.println("Average Turnaround Time: " + r.averageTurnaroundTime);
        System.out.println("-----------------------------");
    }

    private static void printResultWithQuantum(SchedulerResult r) {
        printResult(r);
        System.out.println("Quantum History: " + r.quantumHistory);
        System.out.println("-----------------------------");
    }
}
