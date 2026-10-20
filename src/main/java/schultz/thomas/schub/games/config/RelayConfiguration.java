package schultz.thomas.schub.games.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import schultz.thomas.schub.games.api.ws.RelaySocketHandler;

@Configuration
@EnableWebSocket
public class RelayConfiguration implements WebSocketConfigurer {

    private final RelayProperties properties;
    private final RelaySocketHandler handler;

    public RelayConfiguration(RelayProperties properties, RelaySocketHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    // Seules les pages des jeux ouvrent un WebSocket : un autre site ne peut pas parler au relais au nom du joueur.
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }

    @Bean
    public ServletServerContainerFactoryBean conteneurWebSocket() {
        ServletServerContainerFactoryBean conteneur = new ServletServerContainerFactoryBean();
        conteneur.setMaxTextMessageBufferSize(properties.messageMaxBytes());
        return conteneur;
    }
}
