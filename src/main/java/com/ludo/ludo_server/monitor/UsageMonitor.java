package com.ludo.ludo_server.monitor;

import com.ludo.ludo_server.game.connection.GameManager;
import com.ludo.ludo_server.game.connection.SessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Turns the live /monitor/stats snapshot into history: logs it periodically
 * so "who was playing around 8pm" is answerable via `fly logs` / grep,
 * instead of only ever knowing the current instant.
 *
 * Only logs when there's actually something to report (players or games
 * > 0) - an idle server logging "0 players, 0 games" every 5 minutes for
 * days is pure noise, not signal.
 */
@Component
@EnableScheduling
public class UsageMonitor {

    private static final Logger logger = LoggerFactory.getLogger(UsageMonitor.class);

    private final SessionMapper sessionMapper;
    private final GameManager gameManager;

    public UsageMonitor(SessionMapper sessionMapper, GameManager gameManager) {
        this.sessionMapper = sessionMapper;
        this.gameManager = gameManager;
    }

    /**
     * Every 5 minutes - frequent enough to reconstruct "who was on and
     * when" with reasonable granularity, infrequent enough to not flood
     * the logs on top of everything else already logged per-move.
     */
    @Scheduled(fixedRate = 300000)
    public void logUsage() {
        long activePlayers = sessionMapper.getActivePlayerCount();
        int activeGames = gameManager.getActiveGameCount();

        if (activePlayers > 0 || activeGames > 0) {
            logger.info("Usage: {} active players, {} active games", activePlayers, activeGames);
        }
    }
}
