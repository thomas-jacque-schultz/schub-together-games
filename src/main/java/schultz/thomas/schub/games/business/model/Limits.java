package schultz.thomas.schub.games.business.model;

import java.time.Duration;

public record Limits(int maxPlayers, Duration idleTimeout, int maxRooms, long historyLimitBytes,
                     int roomsPerAddressPerHour) {
}
