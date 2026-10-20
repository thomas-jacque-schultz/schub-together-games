package schultz.thomas.schub.games.business.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// Créations de salons par adresse, sur une heure glissante.
class CreationLimiter {

    private static final Duration FENETRE = Duration.ofHours(1);

    private final int parHeure;
    private final Map<String, Deque<Instant>> parAdresse = new HashMap<>();

    CreationLimiter(int parHeure) {
        this.parHeure = parHeure;
    }

    boolean permet(String adresse, Instant maintenant) {
        Deque<Instant> creations = parAdresse.computeIfAbsent(adresse, a -> new ArrayDeque<>());
        oublie(creations, maintenant);
        if (creations.size() >= parHeure) {
            return false;
        }
        creations.addLast(maintenant);
        return true;
    }

    void purge(Instant maintenant) {
        parAdresse.values().forEach(creations -> oublie(creations, maintenant));
        parAdresse.values().removeIf(Deque::isEmpty);
    }

    private static void oublie(Deque<Instant> creations, Instant maintenant) {
        while (!creations.isEmpty() && creations.peekFirst().isBefore(maintenant.minus(FENETRE))) {
            creations.removeFirst();
        }
    }
}
