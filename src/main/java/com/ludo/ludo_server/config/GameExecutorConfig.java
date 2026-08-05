package com.ludo.ludo_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A dedicated, bounded thread pool for turn processing - waiting on a
 * player's choice can block a thread for up to CHOICE_TIMEOUT_SECONDS, so
 * this must never share the JVM-wide common pool (sized to CPU count, meant
 * for short CPU-bound work, not long blocking waits on a human).
 *
 * Bounded rather than unbounded: threads blocked waiting are cheap, but not
 * free, and a hard cap means a spike in concurrent games degrades (queued
 * tasks) instead of crashing the server (unbounded thread creation).
 */
@Configuration
public class GameExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService gameTurnExecutor(@Value("${game.turn-executor.pool-size:30}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize);
    }
}
