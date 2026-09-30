package com.ludo.ludo_server.monitor;

import com.ludo.ludo_server.game.connection.GameManager;
import com.ludo.ludo_server.game.connection.SessionMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain, unauthenticated snapshot of current load - separate from Actuator's
 * /actuator/health, which only reports UP/DOWN. This is deliberately a
 * normal REST endpoint, not a custom Actuator endpoint, so it doesn't widen
 * what's exposed under /actuator.
 *
 * Counts are a point-in-time snapshot, not a time series - for "how many
 * players were online at 3pm yesterday" you'd need to actually record this
 * somewhere (e.g. scrape it periodically), not just read it live.
 */
@RestController
public class StatsController {

    private final SessionMapper sessionMapper;
    private final GameManager gameManager;

    public StatsController(SessionMapper sessionMapper, GameManager gameManager) {
        this.sessionMapper = sessionMapper;
        this.gameManager = gameManager;
    }

    @GetMapping("/monitor/stats")
    public StatsResponse stats() {
        return new StatsResponse(
                sessionMapper.getActivePlayerCount(),
                gameManager.getActiveGameCount()
        );
    }

    public record StatsResponse(long activePlayers, int activeGames) {
    }
}
