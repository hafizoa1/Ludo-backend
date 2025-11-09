package com.ludo.ludo_server.monitor;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MemoryMonitor {

    /**
     * Log memory usage every 60 seconds
     */
    @Scheduled(fixedRate = 60000)
    public void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory() / 1024 / 1024;  // MB
        long totalMemory = runtime.totalMemory() / 1024 / 1024;  // MB
        long freeMemory = runtime.freeMemory() / 1024 / 1024;  // MB
        long usedMemory = totalMemory - freeMemory;  // MB

        double usagePercent = (double) usedMemory / maxMemory * 100;

        System.out.println("=====================================");
        System.out.println("🧠 MEMORY USAGE:");
        System.out.println("   Used: " + usedMemory + "MB / " + maxMemory + "MB (" + String.format("%.1f", usagePercent) + "%)");
        System.out.println("   Free: " + freeMemory + "MB");
        System.out.println("   Total allocated: " + totalMemory + "MB");

        if (usagePercent > 80) {
            System.out.println("⚠️  WARNING: Memory usage above 80%!");
        }
        if (usagePercent > 90) {
            System.out.println("🔴 CRITICAL: Memory usage above 90%!");
        }

        System.out.println("=====================================");
    }
}
