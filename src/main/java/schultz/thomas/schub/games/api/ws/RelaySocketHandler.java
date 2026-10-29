package schultz.thomas.schub.games.api.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import schultz.thomas.schub.games.api.dto.ClientMessage;
import schultz.thomas.schub.games.api.dto.ServerMessage;
import schultz.thomas.schub.games.business.model.Payload;
import schultz.thomas.schub.games.business.model.Player;
import schultz.thomas.schub.games.business.model.RoomEvent;
import schultz.thomas.schub.games.business.service.RoomService;
import schultz.thomas.schub.games.business.service.TeamPassVerifier;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RelaySocketHandler extends TextWebSocketHandler {

    private static final Pattern CODE = Pattern.compile("[A-Z]{4}");
    private static final Pattern SALON_EQUIPE = Pattern.compile("[0-9a-f]{24}\\.[0-9a-f]{24}");
    private static final Pattern JEU = Pattern.compile("[a-z]{2,20}");
    private static final Pattern ID_JOUEUR = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final int NOM_MAX = 24;

    private final RoomService rooms;
    private final SessionRegistry registry;
    private final TeamPassVerifier passes;
    private final ObjectMapper json;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.open(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        ClientMessage lu;
        try {
            lu = json.readValue(message.getPayload(), ClientMessage.class);
        } catch (IOException illisible) {
            refuse(session.getId());
            return;
        }
        switch (lu.type() == null ? "" : lu.type()) {
            case "join" -> rejoint(session, lu);
            case "send" -> {
                if (lu.data() == null) {
                    refuse(session.getId());
                    return;
                }
                String data = json.writeValueAsString(lu.data());
                rooms.send(session.getId(), new Payload(data, data.getBytes(StandardCharsets.UTF_8).length), lu.keep());
            }
            case "clear" -> rooms.clear(session.getId());
            case "ping" -> session.sendMessage(new TextMessage(json.writeValueAsString(ServerMessage.of("pong"))));
            default -> refuse(session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        rooms.leave(session.getId());
        registry.closed(session.getId());
    }

    private void rejoint(WebSocketSession session, ClientMessage lu) {
        String nom = lu.player() == null || lu.player().name() == null ? "" : lu.player().name().strip();
        boolean valide = lu.player() != null && lu.player().id() != null
                && ID_JOUEUR.matcher(lu.player().id()).matches()
                && !nom.isEmpty() && nom.length() <= NOM_MAX
                && lu.game() != null && JEU.matcher(lu.game()).matches();
        if (valide && lu.pass() != null) {
            rejointLEquipe(session, lu, nom);
            return;
        }
        if (!valide || (lu.room() != null && !CODE.matcher(lu.room()).matches())) {
            refuse(session.getId());
            return;
        }
        rooms.join(session.getId(), adresse(session), lu.room(), lu.game(), new Player(lu.player().id(), nom));
    }

    private void rejointLEquipe(WebSocketSession session, ClientMessage lu, String nom) {
        if (lu.room() == null || !SALON_EQUIPE.matcher(lu.room()).matches()) {
            refuse(session.getId());
            return;
        }
        if (!passes.permits(lu.pass(), lu.room())) {
            registry.deliver(session.getId(), new RoomEvent.Rejected(RoomEvent.Reason.FORBIDDEN));
            return;
        }
        rooms.joinTeam(session.getId(), lu.room(), lu.game(), new Player(lu.player().id(), nom));
    }

    private void refuse(String connection) {
        registry.deliver(connection, new RoomEvent.Rejected(RoomEvent.Reason.INVALID));
    }

    // Derrière le tunnel Cloudflare, l'adresse du joueur est dans CF-Connecting-IP.
    private static String adresse(WebSocketSession session) {
        String cloudflare = session.getHandshakeHeaders().getFirst("CF-Connecting-IP");
        if (cloudflare != null && !cloudflare.isBlank()) {
            return cloudflare;
        }
        InetSocketAddress distante = session.getRemoteAddress();
        return distante == null ? "inconnue" : distante.getAddress().getHostAddress();
    }
}
