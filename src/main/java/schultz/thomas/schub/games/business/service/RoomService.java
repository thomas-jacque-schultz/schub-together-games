package schultz.thomas.schub.games.business.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.games.business.model.Limits;
import schultz.thomas.schub.games.business.model.Payload;
import schultz.thomas.schub.games.business.model.Player;
import schultz.thomas.schub.games.business.model.Room;
import schultz.thomas.schub.games.business.model.RoomEvent;
import schultz.thomas.schub.games.business.model.RoomEvent.Reason;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

// Les salons du relais. Peu de joueurs à la fois : un seul verrou pour tout, plutôt que des courses à démêler.
@Slf4j
@Service
public class RoomService {

    private static final String LETTRES = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int LONGUEUR_CODE = 4;

    private final Delivery delivery;
    private final Limits limits;
    private final Clock clock;
    private final CreationLimiter creations;
    private final SecureRandom hasard = new SecureRandom();
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<String, Room> parConnexion = new HashMap<>();

    public RoomService(Delivery delivery, Limits limits, Clock clock) {
        this.delivery = delivery;
        this.limits = limits;
        this.clock = clock;
        this.creations = new CreationLimiter(limits.roomsPerAddressPerHour());
    }

    // code absent : un salon neuf.
    public synchronized void join(String connection, String address, String code, String game, Player player) {
        Room room;
        if (code == null) {
            if (rooms.size() >= limits.maxRooms()) {
                reject(connection, Reason.TOO_MANY_ROOMS);
                return;
            }
            if (!creations.permet(address, clock.instant())) {
                reject(connection, Reason.RATE_LIMITED);
                return;
            }
            room = new Room(nouveauCode(), game, clock.instant());
            rooms.put(room.code(), room);
            log.info("Salon {} ({}) créé, {} salons ouverts", room.code(), game, rooms.size());
        } else {
            room = rooms.get(code);
            if (room == null || !room.game().equals(game)) {
                reject(connection, Reason.ROOM_NOT_FOUND);
                return;
            }
            if (!room.hasSeat(player.id()) && room.connectedCount() >= limits.maxPlayers()) {
                reject(connection, Reason.ROOM_FULL);
                return;
            }
        }
        assoit(connection, room, player);
    }

    // Un salon d'équipe : son nom vient de l'équipe et du plan, il s'ouvre au premier membre arrivé. Le laissez-passer
    // a été vérifié avant.
    public synchronized void joinTeam(String connection, String code, String game, Player player) {
        Room room = rooms.get(code);
        if (room == null) {
            if (rooms.size() >= limits.maxRooms()) {
                reject(connection, Reason.TOO_MANY_ROOMS);
                return;
            }
            room = new Room(code, game, clock.instant());
            rooms.put(code, room);
            log.info("Salon d'équipe ({}) ouvert, {} salons ouverts", game, rooms.size());
        } else if (!room.game().equals(game)) {
            reject(connection, Reason.ROOM_NOT_FOUND);
            return;
        } else if (!room.hasSeat(player.id()) && room.connectedCount() >= limits.maxPlayers()) {
            reject(connection, Reason.ROOM_FULL);
            return;
        }
        assoit(connection, room, player);
    }

    private void assoit(String connection, Room room, Player player) {
        quitte(connection);
        room.seat(player, connection).ifPresent(remplacee -> {
            parConnexion.remove(remplacee);
            delivery.deliver(remplacee, new RoomEvent.Rejected(Reason.REPLACED));
            delivery.close(remplacee);
        });
        parConnexion.put(connection, room);
        room.touch(clock.instant());
        delivery.deliver(connection, new RoomEvent.Joined(room.code(), room.game(), player.id(), room.leader(),
                room.players(), room.history()));
        presence(room, connection);
    }

    // keep : le message entre dans l'historique, rejoué à qui arrive ou revient.
    public synchronized void send(String connection, Payload payload, boolean keep) {
        Optional<Room> room = salonDe(connection);
        if (room.isEmpty()) {
            return;
        }
        if (keep) {
            room.get().remember(payload, limits.historyLimitBytes());
        }
        room.get().touch(clock.instant());
        String from = room.get().playerOf(connection).orElse(null);
        room.get().connectionsExcept(connection)
                .forEach(c -> delivery.deliver(c, new RoomEvent.Relayed(from, payload)));
    }

    public synchronized void clear(String connection) {
        salonDe(connection).ifPresent(room -> {
            room.forget();
            room.touch(clock.instant());
            String from = room.playerOf(connection).orElse(null);
            room.connectionsExcept(connection).forEach(c -> delivery.deliver(c, new RoomEvent.Cleared(from)));
        });
    }

    public synchronized void leave(String connection) {
        quitte(connection);
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void expire() {
        Iterator<Room> it = rooms.values().iterator();
        while (it.hasNext()) {
            Room room = it.next();
            if (room.lastActivity().plus(limits.idleTimeout()).isBefore(clock.instant())) {
                room.connections().forEach(c -> {
                    parConnexion.remove(c);
                    delivery.deliver(c, new RoomEvent.Rejected(Reason.ROOM_EXPIRED));
                    delivery.close(c);
                });
                it.remove();
                log.info("Salon {} fermé après inactivité, {} salons ouverts", room.code(), rooms.size());
            }
        }
        creations.purge(clock.instant());
    }

    synchronized int openRooms() {
        return rooms.size();
    }

    private Optional<Room> salonDe(String connection) {
        Room room = parConnexion.get(connection);
        if (room == null) {
            delivery.deliver(connection, new RoomEvent.Rejected(Reason.NOT_IN_ROOM));
        }
        return Optional.ofNullable(room);
    }

    private void quitte(String connection) {
        Room room = parConnexion.remove(connection);
        if (room != null) {
            room.disconnect(connection);
            room.touch(clock.instant());
            presence(room, null);
        }
    }

    private void presence(Room room, String sauf) {
        RoomEvent.Presence presence = new RoomEvent.Presence(room.leader(), room.players());
        room.connections().stream().filter(c -> !c.equals(sauf)).forEach(c -> delivery.deliver(c, presence));
    }

    private void reject(String connection, Reason reason) {
        delivery.deliver(connection, new RoomEvent.Rejected(reason));
    }

    private String nouveauCode() {
        String code;
        do {
            StringBuilder lettres = new StringBuilder(LONGUEUR_CODE);
            for (int i = 0; i < LONGUEUR_CODE; i++) {
                lettres.append(LETTRES.charAt(hasard.nextInt(LETTRES.length())));
            }
            code = lettres.toString();
        } while (rooms.containsKey(code));
        return code;
    }
}
