package com.gnjake.ardaclient.debug;

import com.gnjake.ardaclient.ArdaClient;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Temporary performance tester: {@code /arda-client perf [seconds]} records frame times and how long each part of Arda
 * Client takes, then writes a report to chat and the log. Remove before a normal release.
 */
public final class PerfMonitor {
    private static final int MAX_FRAMES = 100_000;

    private static final class Section {
        long calls;
        long totalNs;
        long maxNs;
    }

    private static boolean recording;
    private static long startedNs;
    private static long untilNs;
    private static long lastFrameNs;
    private static long[] frames = new long[0];
    private static int frameCount;
    private static int unfocusedFrames;
    private static final Map<String, Section> sections = new LinkedHashMap<>();

    private PerfMonitor() {
    }

    public static int start(int seconds) {
        sections.clear();
        frames = new long[MAX_FRAMES];
        frameCount = 0;
        unfocusedFrames = 0;
        lastFrameNs = 0L;
        startedNs = System.nanoTime();
        untilNs = startedNs + seconds * 1_000_000_000L;
        recording = true;
        say("Recording performance for " + seconds + " seconds. Play normally.");
        return 1;
    }

    /** Times one part of the mod while recording. */
    public static void time(String name, Runnable work) {
        if (!recording) {
            work.run();
            return;
        }

        long start = System.nanoTime();
        try {
            work.run();
        } finally {
            add(name, System.nanoTime() - start);
        }
    }

    public static <T> T time(String name, Supplier<T> work) {
        if (!recording) {
            return work.get();
        }

        long start = System.nanoTime();
        try {
            return work.get();
        } finally {
            add(name, System.nanoTime() - start);
        }
    }

    private static void add(String name, long ns) {
        Section section = sections.computeIfAbsent(name, key -> new Section());
        section.calls++;
        section.totalNs += ns;
        section.maxNs = Math.max(section.maxNs, ns);
    }

    /** Called once a frame, from a HUD layer. */
    public static void frame() {
        if (!recording) {
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNs != 0L && frameCount < MAX_FRAMES) {
            frames[frameCount++] = now - lastFrameNs;
            if (!Minecraft.getInstance().isWindowActive()) {
                unfocusedFrames++;
            }
        }

        lastFrameNs = now;
        if (now >= untilNs) {
            recording = false;
            report(now - startedNs);
        }
    }

    private static void report(long elapsedNs) {
        Minecraft minecraft = Minecraft.getInstance();
        long[] sorted = Arrays.copyOf(frames, frameCount);
        Arrays.sort(sorted);
        double seconds = elapsedNs / 1e9;
        long frameTotal = 0L;
        for (long frame : sorted) {
            frameTotal += frame;
        }

        double fps = frameCount / seconds;
        double avgMs = frameCount == 0 ? 0.0 : frameTotal / 1e6 / frameCount;
        double p99Ms = frameCount == 0 ? 0.0 : sorted[Math.min(frameCount - 1, (int)(frameCount * 0.99))] / 1e6;
        double maxMs = frameCount == 0 ? 0.0 : sorted[frameCount - 1] / 1e6;
        double lowFps = 0.0;
        int lowCount = Math.max(1, frameCount / 100);
        if (frameCount > 0) {
            long slowest = 0L;
            for (int index = frameCount - lowCount; index < frameCount; index++) {
                slowest += sorted[index];
            }

            lowFps = lowCount / (slowest / 1e9);
        }

        long modNs = 0L;
        for (Section section : sections.values()) {
            modNs += section.totalNs;
        }

        String summary = String.format(Locale.ROOT, "%.0f FPS avg, 1%% low %.0f FPS, frame %.2f ms avg, %.2f ms 99th, %.1f ms worst. "
            + "Arda Client used %.3f ms a frame (%.1f%% of frame time).", fps, lowFps, avgMs, p99Ms, maxMs,
            frameCount == 0 ? 0.0 : modNs / 1e6 / frameCount, frameTotal == 0 ? 0.0 : modNs * 100.0 / frameTotal);
        ArdaClient.LOG.info("Performance: {} frames over {}s, {} unfocused. {}", frameCount, String.format(Locale.ROOT, "%.1f", seconds), unfocusedFrames, summary);
        ArdaClient.LOG.info("Performance: max FPS setting {}, render distance {}, window {}x{}, GPU {}", minecraft.options.framerateLimit().get(),
            minecraft.options.renderDistance().get(), minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(),
            com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().name());
        sections.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().totalNs, a.getValue().totalNs))
            .forEach(entry -> {
                Section section = entry.getValue();
                ArdaClient.LOG.info(String.format(Locale.ROOT, "Performance: %-24s %8d calls %9.2f ms total %7.4f ms/frame %7.4f ms avg %7.3f ms max",
                    entry.getKey(), section.calls, section.totalNs / 1e6, frameCount == 0 ? 0.0 : section.totalNs / 1e6 / frameCount,
                    section.totalNs / 1e6 / section.calls, section.maxNs / 1e6));
            });
        say(summary + (unfocusedFrames > 0 ? " " + unfocusedFrames + " frames were with the window unfocused." : "") + " Details are in the log.");
        frames = new long[0];
    }

    private static void say(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("[Arda Perf] " + text));
        }
    }
}
