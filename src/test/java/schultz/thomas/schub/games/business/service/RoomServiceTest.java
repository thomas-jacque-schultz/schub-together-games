package schultz.thomas.schub.games.business.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import schultz.thomas.schub.games.business.model.Limits;
import schultz.thomas.schub.games.business.model.Payload;
import schultz.thomas.schub.games.business.model.Player;
import schultz.thomas.schub.games.business.model.RoomEvent;
import schultz.thomas.schub.games.business.model.RoomEvent.Reason;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoomServiceTest {

    private final Map<String, List<RoomEvent>> recus = new HashMap<>();
    private final List<String> fermees = new ArrayList<>();
    private Instant maintenant = Instant.parse("2026-10-20T10:00:00Z");
    private RoomService rooms;

    @BeforeEach
    void setUp() {
        Delivery delivery = new Delivery() {
            @Override
            public void deliver(String connection, RoomEvent event) {
                recus.computeIfAbsent(connection, c -> new ArrayList<>()).add(event);
            }

            @Override
            public void close(String connection) {
                fermees.add(connection);
            }
        };
        Clock horloge = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return maintenant;
            }
        };
        rooms = new RoomService(delivery, new Limits(3, Duration.ofMinutes(30), 2, 20, 2), horloge);
    }

    @Test
    @DisplayName("créer un salon donne son code ; un autre joueur le rejoint et chacun voit l'autre")
    void creeEtRejoint() {
        String code = cree("c1", "a");
        rooms.join("c2", "ip2", code, "scribble", new Player("joueur-b1", "B"));

        RoomEvent.Joined b = (RoomEvent.Joined) dernier("c2");
        assertThat(b.room()).isEqualTo(code);
        assertThat(b.players()).extracting(Player::name).containsExactly("A", "B");
        assertThat(b.leader()).isEqualTo("joueur-a1");
        assertThat(dernier("c1")).isInstanceOf(RoomEvent.Presence.class);
    }

    @Test
    @DisplayName("le meneur parti, le suivant mène ; l'ancien meneur qui revient ne reprend pas la main")
    void meneurGardeLaMain() {
        String code = cree("c1", "a");
        rooms.join("c2", "ip2", code, "scribble", new Player("joueur-b1", "B"));

        rooms.leave("c1");
        assertThat(((RoomEvent.Presence) dernier("c2")).leader()).isEqualTo("joueur-b1");

        rooms.join("c3", "ip1", code, "scribble", new Player("joueur-a1", "A"));
        assertThat(((RoomEvent.Joined) dernier("c3")).leader()).isEqualTo("joueur-b1");
        assertThat(((RoomEvent.Presence) dernier("c2")).leader()).isEqualTo("joueur-b1");
    }

    @Test
    @DisplayName("un message gardé est relayé aux autres et rejoué à qui revient après un rechargement")
    void historiqueRejoue() {
        String code = cree("c1", "a");
        rooms.join("c2", "ip2", code, "scribble", new Player("joueur-b1", "B"));
        rooms.send("c1", new Payload("{\"trait\":1}", 11), true);
        assertThat(dernier("c2")).isEqualTo(new RoomEvent.Relayed("joueur-a1", new Payload("{\"trait\":1}", 11)));

        rooms.leave("c2");
        rooms.join("c3", "ip2", code, "scribble", new Player("joueur-b1", "B"));
        assertThat(((RoomEvent.Joined) dernier("c3")).history()).containsExactly(new Payload("{\"trait\":1}", 11));
    }

    @Test
    @DisplayName("l'historique garde les derniers messages sous sa limite, et s'efface sur demande")
    void historiqueBorne() {
        String code = cree("c1", "a");
        for (int i = 0; i < 5; i++) {
            rooms.send("c1", new Payload("m" + i, 8), true);
        }
        rooms.join("c2", "ip2", code, "scribble", new Player("joueur-b1", "B"));
        assertThat(((RoomEvent.Joined) dernier("c2")).history()).extracting(Payload::json).containsExactly("m3", "m4");

        rooms.clear("c1");
        assertThat(dernier("c2")).isEqualTo(new RoomEvent.Cleared("joueur-a1"));
        rooms.join("c3", "ip3", code, "scribble", new Player("joueur-c1", "C"));
        assertThat(((RoomEvent.Joined) dernier("c3")).history()).isEmpty();
    }

    @Test
    @DisplayName("au-delà du plafond de joueurs, le salon refuse ; un joueur déjà assis revient quand même")
    void salonPlein() {
        String code = cree("c1", "a");
        rooms.join("c2", "ip", code, "scribble", new Player("joueur-b1", "B"));
        rooms.join("c3", "ip", code, "scribble", new Player("joueur-c1", "C"));
        rooms.join("c4", "ip", code, "scribble", new Player("joueur-d1", "D"));
        assertThat(dernier("c4")).isEqualTo(new RoomEvent.Rejected(Reason.ROOM_FULL));

        rooms.join("c5", "ip", code, "scribble", new Player("joueur-c1", "C"));
        assertThat(dernier("c5")).isInstanceOf(RoomEvent.Joined.class);
        assertThat(dernier("c3")).isEqualTo(new RoomEvent.Rejected(Reason.REPLACED));
        assertThat(fermees).containsExactly("c3");
    }

    @Test
    @DisplayName("les créations sont limitées par adresse et par heure, et le nombre de salons est plafonné")
    void creationsLimitees() {
        cree("c1", "a");
        cree("c2", "b");
        rooms.join("c3", "ip-a", null, "scribble", new Player("joueur-z1", "Z"));
        assertThat(dernier("c3")).isEqualTo(new RoomEvent.Rejected(Reason.TOO_MANY_ROOMS));

        maintenant = maintenant.plus(Duration.ofMinutes(31));
        rooms.expire();
        rooms.join("c4", "ip-a", null, "scribble", new Player("joueur-z1", "Z"));
        rooms.join("c5", "ip-a", null, "scribble", new Player("joueur-y1", "Y"));
        assertThat(dernier("c5")).isEqualTo(new RoomEvent.Rejected(Reason.RATE_LIMITED));
    }

    @Test
    @DisplayName("un salon sans activité depuis trente minutes est fermé, ses joueurs prévenus")
    void expiration() {
        String code = cree("c1", "a");
        maintenant = maintenant.plus(Duration.ofMinutes(29));
        rooms.expire();
        assertThat(rooms.openRooms()).isEqualTo(1);

        maintenant = maintenant.plus(Duration.ofMinutes(2));
        rooms.expire();
        assertThat(rooms.openRooms()).isZero();
        assertThat(dernier("c1")).isEqualTo(new RoomEvent.Rejected(Reason.ROOM_EXPIRED));
        rooms.join("c2", "ip2", code, "scribble", new Player("joueur-b1", "B"));
        assertThat(dernier("c2")).isEqualTo(new RoomEvent.Rejected(Reason.ROOM_NOT_FOUND));
    }

    private String cree(String connection, String lettre) {
        rooms.join(connection, "ip-" + lettre, null, "scribble",
                new Player("joueur-" + lettre + "1", lettre.toUpperCase()));
        return ((RoomEvent.Joined) dernier(connection)).room();
    }

    private RoomEvent dernier(String connection) {
        List<RoomEvent> liste = recus.get(connection);
        return liste.get(liste.size() - 1);
    }
}
