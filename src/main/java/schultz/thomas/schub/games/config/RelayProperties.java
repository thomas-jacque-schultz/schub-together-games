package schultz.thomas.schub.games.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("relay")
public record RelayProperties(List<String> allowedOrigins, int maxPlayers, Duration idleTimeout, int maxRooms,
                              long historyLimitBytes, int roomsPerAddressPerHour, int messageMaxBytes) {
}
