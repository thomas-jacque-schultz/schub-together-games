package schultz.thomas.schub.games.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import schultz.thomas.schub.games.business.service.TeamPassVerifierTest;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"relay.allowed-origins=https://scribble.premadelab.eu,https://premadelab.eu",
                "relay.pass-secret=secret-de-test"})
class RelayEndToEndTest {

    private static final String ORIGINE = "https://scribble.premadelab.eu";
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("deux navigateurs dans un salon : le trait de l'un arrive à l'autre, et revient après rechargement")
    void deuxJoueurs() throws Exception {
        Client a = connecte(ORIGINE);
        a.envoie("{\"type\":\"join\",\"game\":\"scribble\",\"player\":{\"id\":\"joueur-aaaa\",\"name\":\"A\"}}");
        String code = a.attend("joined").get("room").asText();

        Client b = connecte(ORIGINE);
        b.envoie("{\"type\":\"join\",\"room\":\"" + code + "\",\"game\":\"scribble\","
                + "\"player\":{\"id\":\"joueur-bbbb\",\"name\":\"B\"}}");
        assertThat(b.attend("joined").get("players")).hasSize(2);

        a.envoie("{\"type\":\"send\",\"keep\":true,\"data\":{\"trait\":[0.1,0.2]}}");
        JsonNode recu = b.attend("message");
        assertThat(recu.get("from").asText()).isEqualTo("joueur-aaaa");
        assertThat(recu.get("data").get("trait").get(1).asDouble()).isEqualTo(0.2);

        b.session.close();
        Client b2 = connecte(ORIGINE);
        b2.envoie("{\"type\":\"join\",\"room\":\"" + code + "\",\"game\":\"scribble\","
                + "\"player\":{\"id\":\"joueur-bbbb\",\"name\":\"B\"}}");
        assertThat(b2.attend("joined").get("history").get(0).get("trait")).hasSize(2);
    }

    @Test
    @DisplayName("un salon d'équipe ne s'ouvre qu'avec le laissez-passer de l'équipe")
    void salonDEquipe() throws Exception {
        String salon = TeamPassVerifierTest.EQUIPE + "." + TeamPassVerifierTest.PLAN;
        String join = "{\"type\":\"join\",\"game\":\"macro\",\"room\":\"" + salon + "\",\"pass\":\"%s\","
                + "\"player\":{\"id\":\"joueur-%s\",\"name\":\"%s\"}}";

        Client intrus = connecte("https://premadelab.eu");
        intrus.envoie(join.formatted("faux.faux", "cccc", "C"));
        assertThat(intrus.attend("error").get("code").asText()).isEqualTo("FORBIDDEN");

        String pass = TeamPassVerifierTest.signe("secret-de-test", TeamPassVerifierTest.EQUIPE,
                Instant.now().plusSeconds(600));
        Client a = connecte("https://premadelab.eu");
        a.envoie(join.formatted(pass, "aaaa", "A"));
        assertThat(a.attend("joined").get("room").asText()).isEqualTo(salon);
        Client b = connecte("https://premadelab.eu");
        b.envoie(join.formatted(pass, "bbbb", "B"));
        assertThat(b.attend("joined").get("players")).hasSize(2);
    }

    @Test
    @DisplayName("une page d'un autre site ne peut pas ouvrir le WebSocket")
    void origineRefusee() {
        assertThatThrownBy(() -> connecte("https://ailleurs.example")).isNotNull();
    }

    private Client connecte(String origine) throws Exception {
        Client client = new Client();
        WebSocketHttpHeaders entetes = new WebSocketHttpHeaders();
        entetes.setOrigin(origine);
        client.session = new StandardWebSocketClient()
                .execute(client, entetes, URI.create("ws://localhost:" + port + "/ws")).get(5, TimeUnit.SECONDS);
        return client;
    }

    private class Client extends TextWebSocketHandler {
        private final BlockingQueue<JsonNode> recus = new LinkedBlockingQueue<>();
        private WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            recus.add(json.readTree(message.getPayload()));
        }

        void envoie(String texte) throws Exception {
            session.sendMessage(new TextMessage(texte));
        }

        JsonNode attend(String type) throws InterruptedException {
            for (JsonNode lu = recus.poll(5, TimeUnit.SECONDS); lu != null; lu = recus.poll(5, TimeUnit.SECONDS)) {
                if (type.equals(lu.get("type").asText())) {
                    return lu;
                }
            }
            throw new AssertionError("aucun message " + type);
        }
    }
}
