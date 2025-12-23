import java.util.*;

public class CPUScheduler {

    public static SchedulerResult preemptiveSJF(List<Process> input, int contextSwitch) {
        List<Process> processes = copyList(input);
        SchedulerResult result = new SchedulerResult();
        int time = 0, completed = 0;
        Process last = null;

        while (completed < processes.size()) {
            Process cur = null;
            int minRemaining = Integer.MAX_VALUE;
            for (Process p : processes) {
                if (p.arrivalTime <= time && p.remainingTime > 0 && p.remainingTime < minRemaining) {
                    minRemaining = p.remainingTime;
                    cur = p;
                }
            }
            if (cur == null) {
                time++;
                continue;
            }

            if (last != null && last != cur) time += contextSwitch;

            // Only add to execution order when switching processes
            if (last != cur) {
                result.executionOrder.add(cur.name);
            }
            
            cur.remainingTime--;
            time++;

            if (cur.remainingTime == 0) {
                cur.completionTime = time;
                completed++;
            }
            last = cur;
        }

        calculateTimes(processes, result);
        return result;
    }

    public static SchedulerResult roundRobin(List<Process> input, int quantum, int contextSwitch) {
        List<Process> processes = copyList(input);
        SchedulerResult result = new SchedulerResult();
        Queue<Process> queue = new LinkedList<>();
        int time = 0, completed = 0;
        int n = processes.size();
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        
        int idx = 0;
        // Add processes that arrive at time 0
        while (idx < n && processes.get(idx).arrivalTime <= time) {
            queue.add(processes.get(idx));
            idx++;
        }
        
        Process last = null;
        
        while (completed < n) {
            if (queue.isEmpty()) {
                // No process ready, advance time to next arrival
                if (idx < n) {
                    time = processes.get(idx).arrivalTime;
                    while (idx < n && processes.get(idx).arrivalTime <= time) {
                        queue.add(processes.get(idx));
                        idx++;
                    }
                }
                continue;
            }
            
            Process cur = queue.poll();
            
            // Context switch if switching to a different process
            if (last != null && last != cur) {
                time += contextSwitch;
            }
            
            // Execute for min(quantum, remaining time)
            int execTime = Math.min(quantum, cur.remainingTime);
            result.executionOrder.add(cur.name);
            cur.remainingTime -= execTime;
            time += execTime;
            
            // Add newly arrived processes to queue
            while (idx < n && processes.get(idx).arrivalTime <= time) {
                queue.add(processes.get(idx));
                idx++;
            }
            
            if (cur.remainingTime == 0) {
                cur.completionTime = time;
                completed++;
            } else {
                // Process still has work, add back to queue
                queue.add(cur);
            }
            
            last = cur;
        }
        
        calculateTimes(processes, result);
        return result;
    }

    public static SchedulerResult priorityPreemptive(List<Process> input, int contextSwitch, int agingInterval) {
        List<Process> processes = copyList(input);
        SchedulerResult result = new SchedulerResult();
        int time = 0, completed = 0;
        Process last = null;
        
        // Track wait time for aging
        Map<Process, Integer> waitTime = new HashMap<>();
        for (Process p : processes) waitTime.put(p, 0);

        while (completed < processes.size()) {
            // 1. Apply aging based on ACCUMULATED wait time
            for (Process p : processes) {
                if (p.arrivalTime <= time && p.remainingTime > 0) {
                    // Check if aging interval reached
                    if (waitTime.get(p) >= agingInterval) {
                        p.priority--;
                        waitTime.put(p, 0); // Reset wait time after aging
                    }
                }
            }

            // 2. Select Process
            Process cur = null;
            int bestPriority = Integer.MAX_VALUE;
            int earliestArrival = Integer.MAX_VALUE;
            
            for (Process p : processes) {
                if (p.arrivalTime <= time && p.remainingTime > 0) {
                    // Lower Number = Higher Priority
                    // Tie-breaker: Arrival Time (FCFS)
                    if (p.priority < bestPriority || 
                        (p.priority == bestPriority && p.arrivalTime < earliestArrival)) {
                        bestPriority = p.priority;
                        earliestArrival = p.arrivalTime;
                        cur = p;
                    }
                }
            }

            // 3. Handle Idle CPU
            if (cur == null) {
                time++;
                // Increment wait time for all waiting processes
                for (Process p : processes) {
                    if (p.arrivalTime <= time && p.remainingTime > 0) {
                         waitTime.put(p, waitTime.get(p) + 1);
                    }
                }
                continue;
            }

            // 4. Handle Context Switch
            boolean switched = (last != null && last != cur);
            if (switched) {
                // Add context switch duration to time and wait times
                int oldTime = time;
                time += contextSwitch;
                
                for (Process p : processes) {
                    if (p.remainingTime > 0) {
                        int waitIncrement = 0;
                        if (p.arrivalTime <= oldTime) {
                            waitIncrement = contextSwitch;
                        } else if (p.arrivalTime <= time) {
                            waitIncrement = time - p.arrivalTime;
                        }
                        
                        if (waitIncrement > 0) {
                            waitTime.put(p, waitTime.get(p) + waitIncrement);
                        }
                    }
                }
                result.executionOrder.add(cur.name);
            } else if (last == null) {
                // First process
                result.executionOrder.add(cur.name);
            }
            
            // 5. Execute 1 Unit
            cur.remainingTime--;
            time++;
            
            // Increment wait time for OTHERS
            for (Process p : processes) {
                if (p != cur && p.arrivalTime <= time && p.remainingTime > 0) {
                    waitTime.put(p, waitTime.get(p) + 1);
                }
            }
            // Reset running process wait time (it's running, not waiting)
            waitTime.put(cur, 0);

            if (cur.remainingTime == 0) {
                cur.completionTime = time;
                completed++;
            }

            last = cur;
        }

        calculateTimes(processes, result);
        return result;
    }

    public static SchedulerResult agScheduling(List<Process> input) {
        List<Process> processes = copyList(input);
        SchedulerResult result = new SchedulerResult();
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        int n = processes.size();

        List<Process> readyQueue = new ArrayList<>();
        int time = 0;
        int completed = 0;
        int idx = 0;
        Process lastProcess = null;

        // Add processes that arrive at time 0
        while (idx < n && processes.get(idx).arrivalTime <= time) {
            readyQueue.add(processes.get(idx));
            idx++;
        }

        while (completed < n) {
            if (readyQueue.isEmpty()) {
                if (idx < n) {
                    time = processes.get(idx).arrivalTime;
                    while (idx < n && processes.get(idx).arrivalTime <= time) {
                        readyQueue.add(processes.get(idx));
                        idx++;
                    }
                }
                continue;
            }

            // Get first process from queue (FCFS ordering)
            Process current = readyQueue.remove(0);
            
            // Only add to execution order when switching to different process
            if (lastProcess != current) {
                result.executionOrder.add(current.name);
            }

            int originalQuantum = current.quantum;
            int usedQuantum = 0;
            boolean preempted = false;
            int preemptPhase = 0;
            Process preemptingProcess = null;

            // Calculate phase boundaries
            // Calculate phase boundaries
            int quarter = (int) Math.ceil(originalQuantum * 0.25);
            int phase1End = quarter;
            int phase2End = quarter + quarter;
            
            // Execute time unit by unit
            while (usedQuantum < originalQuantum && current.remainingTime > 0) {
                int currentPhase;
                if (usedQuantum < phase1End) {
                    currentPhase = 1; // FCFS - non-preemptive
                } else if (usedQuantum < phase2End) {
                    currentPhase = 2; // Priority - check for higher priority
                } else {
                    currentPhase = 3; // SJF - preemptive
                }
                
                // Add newly arrived processes before making preemption decisions
                while (idx < n && processes.get(idx).arrivalTime <= time) {
                    readyQueue.add(processes.get(idx));
                    idx++;
                }
                
                // Phase 2: Check if higher priority process is waiting
                if (currentPhase == 2) {
                    Process higherPriorityProcess = null;
                    for (Process p : readyQueue) {
                        if (p.priority < current.priority) {
                            if (higherPriorityProcess == null || p.priority < higherPriorityProcess.priority) {
                                higherPriorityProcess = p;
                            }
                        }
                    }
                    if (higherPriorityProcess != null) {
                        preempted = true;
                        preemptPhase = 2;
                        preemptingProcess = higherPriorityProcess;
                        break;
                    }
                }
                
                // Phase 3: Check if shorter job is waiting
                if (currentPhase == 3) {
                    Process shorterJob = null;
                    for (Process p : readyQueue) {
                        if (p.remainingTime < current.remainingTime) {
                            if (shorterJob == null || p.remainingTime < shorterJob.remainingTime) {
                                shorterJob = p;
                            }
                        }
                    }
                    if (shorterJob != null) {
                        preempted = true;
                        preemptPhase = 3;
                        preemptingProcess = shorterJob;
                        break;
                    }
                }
                
                // Execute 1 time unit
                current.remainingTime--;
                usedQuantum++;
                time++;
            }

            // Handle completion or requeue
            if (current.remainingTime == 0) {
                current.completionTime = time;
                current.quantumHistory.add(0);
                completed++;
            } else {
                // Calculate quantum update based on scenario
                int remainingQ = originalQuantum - usedQuantum;
                
                if (preempted) {
                    if (preemptPhase == 2) {
                        // Scenario ii: Preempted during Priority phase
                        current.quantum += (int) Math.ceil(remainingQ / 2.0);
                    } else {
                        // Scenario iii: Preempted during SJF phase
                        current.quantum += remainingQ;
                    }
                } else {
                    // Scenario i: Used all quantum time, still has work
                    current.quantum += 2;
                }
                
                current.quantumHistory.add(current.quantum);
                readyQueue.add(current); // Add to end of queue
                
                // Move preempting process to front of queue so it runs next
                if (preemptingProcess != null) {
                    readyQueue.remove(preemptingProcess);
                    readyQueue.add(0, preemptingProcess);
                }
            }
            
            lastProcess = current;
        }

        calculateTimes(processes, result);
        for (Process p : processes) result.quantumHistory.put(p.name, p.quantumHistory);
        return result;
    }

    private static List<Process> copyList(List<Process> input) {
        List<Process> list = new ArrayList<>();
        for (Process p : input) list.add(p.copy());
        return list;
    }

    private static void calculateTimes(List<Process> processes, SchedulerResult r) {
        double w = 0, t = 0;
        for (Process p : processes) {
            p.turnaroundTime = p.completionTime - p.arrivalTime;
            p.waitingTime = p.turnaroundTime - p.burstTime;
            r.waitingTimes.put(p.name, p.waitingTime);
            r.turnaroundTimes.put(p.name, p.turnaroundTime);
            w += p.waitingTime;
            t += p.turnaroundTime;
        }
        r.averageWaitingTime = w / processes.size();
        r.averageTurnaroundTime = t / processes.size();
    }
}
