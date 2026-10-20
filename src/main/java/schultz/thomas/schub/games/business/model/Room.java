package schultz.thomas.schub.games.business.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Un salon en mémoire. Les joueurs gardent leur ordre d'arrivée : le premier encore là mène la partie.
public class Room {

    private final String code;
    private final String game;
    private final Map<String, Seat> seats = new LinkedHashMap<>();
    private final Deque<Payload> history = new ArrayDeque<>();
    private long historyBytes;
    private Instant lastActivity;

    public Room(String code, String game, Instant now) {
        this.code = code;
        this.game = game;
        this.lastActivity = now;
    }

    // connection : null quand le joueur est parti ; il garde sa place jusqu'à l'expiration du salon.
    private record Seat(Player player, String connection) {
    }

    public String code() {
        return code;
    }

    public String game() {
        return game;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    public void touch(Instant now) {
        lastActivity = now;
    }

    public long connectedCount() {
        return seats.values().stream().filter(seat -> seat.connection() != null).count();
    }

    public boolean hasSeat(String playerId) {
        return seats.containsKey(playerId);
    }

    // La connexion qu'avait ce joueur, s'il était déjà assis : elle est remplacée.
    public Optional<String> seat(Player player, String connection) {
        Seat ancien = seats.get(player.id());
        if (ancien != null) {
            seats.put(player.id(), new Seat(player, connection));
            return Optional.ofNullable(ancien.connection()).filter(c -> !c.equals(connection));
        }
        seats.put(player.id(), new Seat(player, connection));
        return Optional.empty();
    }

    public Optional<String> playerOf(String connection) {
        return seats.values().stream().filter(seat -> connection.equals(seat.connection()))
                .map(seat -> seat.player().id()).findFirst();
    }

    public void disconnect(String connection) {
        seats.replaceAll((id, seat) -> connection.equals(seat.connection()) ? new Seat(seat.player(), null) : seat);
    }

    public List<Player> players() {
        return seats.values().stream().filter(seat -> seat.connection() != null).map(Seat::player).toList();
    }

    public String leader() {
        return players().stream().findFirst().map(Player::id).orElse(null);
    }

    public List<String> connections() {
        return seats.values().stream().map(Seat::connection).filter(Objects::nonNull).toList();
    }

    public List<String> connectionsExcept(String connection) {
        return connections().stream().filter(c -> !c.equals(connection)).toList();
    }

    // Les plus anciens messages tombent quand l'historique dépasse sa limite.
    public void remember(Payload payload, long limitBytes) {
        history.addLast(payload);
        historyBytes += payload.bytes();
        while (historyBytes > limitBytes && !history.isEmpty()) {
            historyBytes -= history.removeFirst().bytes();
        }
    }

    public void forget() {
        history.clear();
        historyBytes = 0;
    }

    public List<Payload> history() {
        return new ArrayList<>(history);
    }
}
