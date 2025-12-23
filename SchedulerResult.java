import java.util.*;

public class SchedulerResult {
    public List<String> executionOrder = new ArrayList<>();
    public Map<String, Integer> waitingTimes = new HashMap<>();
    public Map<String, Integer> turnaroundTimes = new HashMap<>();
    public double averageWaitingTime;
    public double averageTurnaroundTime;

    public Map<String, List<Integer>> quantumHistory = new HashMap<>();
}
