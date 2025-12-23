import java.util.*;

public class Process {
    public String name;
    public int arrivalTime;
    public int burstTime;
    public int remainingTime;
    public int priority;
    public int quantum;

    public int waitingTime;
    public int turnaroundTime;
    public int completionTime;

    public List<Integer> quantumHistory = new ArrayList<>();

    public Process(String name, int arrivalTime, int burstTime, int priority, int quantum) {
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.remainingTime = burstTime;
        this.priority = priority;
        this.quantum = quantum;
        this.quantumHistory.add(quantum);
    }

    public Process copy() {
        Process p = new Process(name, arrivalTime, burstTime, priority, quantum);
        p.remainingTime = remainingTime;
        return p;
    }
}
