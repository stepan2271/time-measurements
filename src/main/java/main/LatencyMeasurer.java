package main;

import net.openhft.affinity.AffinityLock;
import net.openhft.affinity.AffinitySupport;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class LatencyMeasurer
{
    @CommandLine.Option(names = {"--warmupCycles"}, description = "Number of warmup cycles")
    private int warmupCycles = 20_000;

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
        latencyMeasurer.run();
    }

    private void run()
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
        System.out.println(checkCtxSwitches(threadId));
        for (int i = 0; i < warmupCycles; i++) {
            measureLatency(Long.MAX_VALUE, threadId);
        }
        System.out.println("Warmup finished");
        System.out.println(checkCtxSwitches(threadId));
        while (true) {
            measureLatency(thresholdNs, threadId);
        }
    }

    private static void measureLatency(final long thresholdNs, final int threadId)
    {
        long start = System.nanoTime();
        long end = System.nanoTime();
        long latency = end - start;
        if (latency > thresholdNs) {
            final String ctxtSwitches = checkCtxSwitches(threadId);
            System.out.println(String.format("\n\n Latency is greater than treshold: %d", latency));
            System.out.println(ctxtSwitches);
        }
    }

    private static String checkCtxSwitches(final int threadId)
    {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("/bin/bash", "-c", String.format("cat /proc/%d/status | grep ctxt", threadId));
        String result = "";
        try {

            final Process process = processBuilder.start();

            final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                result = String.join("\n", result, line);
            }
            process.waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
