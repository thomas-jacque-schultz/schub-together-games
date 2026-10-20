package schultz.thomas.schub.games.business.model;

// Un message de jeu, opaque pour le relais : il le garde et le diffuse sans le lire.
public record Payload(String json, int bytes) {
}
