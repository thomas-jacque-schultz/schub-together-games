package schultz.thomas.schub.games.business.model;

import java.util.List;

// Ce que le relais envoie à une connexion. Le meneur est le premier joueur encore présent.
public sealed interface RoomEvent {

    record Joined(String room, String game, String you, String leader, List<Player> players, List<Payload> history)
            implements RoomEvent {
    }

    record Presence(String leader, List<Player> players) implements RoomEvent {
    }

    record Relayed(String from, Payload payload) implements RoomEvent {
    }

    record Cleared(String from) implements RoomEvent {
    }

    record Rejected(Reason reason) implements RoomEvent {
    }

    enum Reason {
        INVALID,
        ROOM_NOT_FOUND,
        ROOM_FULL,
        TOO_MANY_ROOMS,
        RATE_LIMITED,
        NOT_IN_ROOM,
        ROOM_EXPIRED,
        REPLACED,
        FORBIDDEN
    }
}
