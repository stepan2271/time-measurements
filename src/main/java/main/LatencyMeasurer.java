package main;

import net.openhft.affinity.AffinityLock;
import net.openhft.affinity.AffinitySupport;
import org.agrona.concurrent.ShutdownSignalBarrier;
import picocli.CommandLine;

import java.time.Instant;

public class LatencyMeasurer
    implements Runnable
{
    @CommandLine.Option(names = {"--warmupCycles"}, description = "Number of warmup cycles")
    private int warmupCycles = 200_000;

    @CommandLine.Option(names = {"--thresholdNs"}, description = "Threshold in nanoseconds. When latency is above " +
            "this threshold we will print this event")
    private long thresholdNs = 500_000;

    @CommandLine.Option(names = {"--bindingCpu"}, description = "Binding cpu")
    private int bindingCpu = -1;

    public static void main(String[] args)
    {
        final LatencyMeasurer latencyMeasurer = new LatencyMeasurer();
        final CommandLine commandLine = new CommandLine(latencyMeasurer);
        commandLine.parse(args);
        final Thread thread = new Thread(latencyMeasurer, "latencyMeasurer");
        thread.setDaemon(true);
        thread.start();
        new ShutdownSignalBarrier().await();
    }

    @Override
    public void run()
    {
        final Thread thread = Thread.currentThread();
        final int threadId = AffinitySupport.getThreadId();
        System.out.printf("Thread id: %d\n", threadId);
        if (bindingCpu != -1) {
            System.out.printf(
                    "\nAttempt to bind thread %d (%d) to cpu %d",
                    thread.getId(),
                    threadId,
                    bindingCpu);
            final AffinityLock affinityLock = AffinityLock.acquireLock(bindingCpu);
            System.out.printf(
                    "\nSuccessfully bound thread %d (%d) to cpu %d",
                    thread.getId(),
                    threadId,
                    affinityLock.cpuId());
        }
        for (int i = 0; i < warmupCycles; i++) {
            measureLatency(Long.MAX_VALUE);
        }
        System.out.println("\nWarmup finished " + Instant.now());
        while (true) {
            measureLatency(thresholdNs);
        }
    }

    private static void measureLatency(final long thresholdNs)
    {
        long start = System.nanoTime();
        long end = System.nanoTime();
        long latency = end - start;
        if (latency > thresholdNs) {
            System.out.println(String.format("\n\n %s Latency is greater than treshold: %dns", Instant.now(), latency));
        }
    }
}
