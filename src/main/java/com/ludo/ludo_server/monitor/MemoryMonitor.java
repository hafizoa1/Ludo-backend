package com.ludo.ludo_server.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MemoryMonitor {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitor.class);

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

        logger.info("Memory usage: {}MB / {}MB ({}%), free: {}MB, total allocated: {}MB",
                usedMemory, maxMemory, String.format("%.1f", usagePercent), freeMemory, totalMemory);

        if (usagePercent > 80) {
            logger.warn("Memory usage above 80%!");
        }
        if (usagePercent > 90) {
            logger.error("CRITICAL: Memory usage above 90%!");
        }
    }
}
