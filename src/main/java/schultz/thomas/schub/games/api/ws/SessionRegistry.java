package schultz.thomas.schub.games.api.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import schultz.thomas.schub.games.api.dto.PlayerDto;
import schultz.thomas.schub.games.api.dto.ServerMessage;
import schultz.thomas.schub.games.business.model.Payload;
import schultz.thomas.schub.games.business.model.Player;
import schultz.thomas.schub.games.business.model.RoomEvent;
import schultz.thomas.schub.games.business.service.Delivery;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Les connexions ouvertes. Un client trop lent pour suivre (tampon de 512 Ko plein) est fermé plutôt qu'attendu.
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRegistry implements Delivery {

    private static final int ENVOI_MAX_MS = 5_000;
    private static final int TAMPON_MAX = 512 * 1024;

    private final ObjectMapper json;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    void open(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, ENVOI_MAX_MS, TAMPON_MAX,
                ConcurrentWebSocketSessionDecorator.OverflowStrategy.TERMINATE));
    }

    void closed(String connection) {
        sessions.remove(connection);
    }

    @Override
    public void deliver(String connection, RoomEvent event) {
        WebSocketSession session = sessions.get(connection);
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(message(event))));
        } catch (IOException | RuntimeException echec) {
            log.debug("Envoi impossible à {} : {}", connection, echec.getMessage());
        }
    }

    @Override
    public void close(String connection) {
        WebSocketSession session = sessions.remove(connection);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException deja) {
                log.debug("Fermeture de {} : {}", connection, deja.getMessage());
            }
        }
    }

    private ServerMessage message(RoomEvent event) {
        return switch (event) {
            case RoomEvent.Joined j -> new ServerMessage("joined", j.room(), j.game(), j.you(), j.leader(),
                    joueurs(j.players()), j.history().stream().map(this::lu).toList(), null, null, null);
            case RoomEvent.Presence p -> new ServerMessage("presence", null, null, null, p.leader(),
                    joueurs(p.players()), null, null, null, null);
            case RoomEvent.Relayed r -> new ServerMessage("message", null, null, null, null, null, null, r.from(),
                    lu(r.payload()), null);
            case RoomEvent.Cleared c -> new ServerMessage("cleared", null, null, null, null, null, null, c.from(),
                    null, null);
            case RoomEvent.Rejected r -> new ServerMessage("error", null, null, null, null, null, null, null, null,
                    r.reason().name());
        };
    }

    private static List<PlayerDto> joueurs(List<Player> players) {
        return players.stream().map(p -> new PlayerDto(p.id(), p.name())).toList();
    }

    private JsonNode lu(Payload payload) {
        try {
            return json.readTree(payload.json());
        } catch (JsonProcessingException illisible) {
            throw new IllegalStateException(illisible);
        }
    }
}
