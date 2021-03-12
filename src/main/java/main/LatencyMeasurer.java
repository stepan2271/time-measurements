package main;

import net.openhft.affinity.AffinityLock;
import net.openhft.affinity.AffinitySupport;
import picocli.CommandLine;

public class LatencyMeasurer
{
    @CommandLine.Option(names = {"--warmupCycles"}, description = "Number of warmup cycles")
    private int warmupCycles = 20_000;

    @CommandLine.Option(names = {"--thresholdNs"}, description = "Threshold in nanoseconds. When latency is above " +
            "this threshold we will print this event")
    private long thresholdNs = 500_000_000L;

    @CommandLine.Option(names = {"--bindingCpu"}, description = "Binding cpu")
    private int bindingCpu = -1;

    public static void main(String[] args)
    {
        final LatencyMeasurer latencyMeasurer = new LatencyMeasurer();
        final CommandLine commandLine = new CommandLine(latencyMeasurer);
        commandLine.parse(args);
        latencyMeasurer.run();
    }

    private void run()
    {
        final Thread thread = Thread.currentThread();
        if (bindingCpu != -1) {
            System.out.printf(
                    "\nAttempt to bind thread %d (%d) to cpu %d",
                    thread.getId(),
                    AffinitySupport.getThreadId(),
                    bindingCpu);
            final AffinityLock affinityLock = AffinityLock.acquireLock(bindingCpu);
            System.out.printf(
                    "\nSuccessfully bound thread %d (%d) to cpu %d",
                    thread.getId(),
                    AffinitySupport.getThreadId(),
                    affinityLock.cpuId());
        }
        for (int i = 0; i < warmupCycles; i++)
        {
            measureLatency(Long.MAX_VALUE);
        }
        while (true)
        {
            measureLatency(thresholdNs);
        }
    }

    private static void measureLatency(final long thresholdNs)
    {
        long start = System.nanoTime();
        long end = System.nanoTime();
        long latency = end - start;
        if (latency > thresholdNs)
        {
            System.out.println(String.format("Latency is greater than treshold: %d", latency));
        }
    }
}
