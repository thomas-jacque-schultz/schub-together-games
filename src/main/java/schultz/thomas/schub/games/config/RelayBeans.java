package schultz.thomas.schub.games.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import schultz.thomas.schub.games.business.model.Limits;
import schultz.thomas.schub.games.business.service.TeamPassVerifier;

import java.time.Clock;

@Configuration
public class RelayBeans {

    @Bean
    public Limits limits(RelayProperties properties) {
        return new Limits(properties.maxPlayers(), properties.idleTimeout(), properties.maxRooms(),
                properties.historyLimitBytes(), properties.roomsPerAddressPerHour());
    }

    @Bean
    public TeamPassVerifier teamPassVerifier(RelayProperties properties, Clock clock) {
        return new TeamPassVerifier(properties.passSecret(), clock);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
