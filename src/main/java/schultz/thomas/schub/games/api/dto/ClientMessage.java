package schultz.thomas.schub.games.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

// join : room absent pour créer un salon ; pass pour un salon d'équipe (Schub#171). send : data est relayé tel quel ;
// keep le garde pour qui arrive ensuite.
public record ClientMessage(String type, String room, String game, PlayerDto player, JsonNode data, boolean keep,
                            String pass) {
}
