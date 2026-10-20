package schultz.thomas.schub.games.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerMessage(String type, String room, String game, String you, String leader,
                            List<PlayerDto> players, List<JsonNode> history, String from, JsonNode data,
                            String code) {

    public static ServerMessage of(String type) {
        return new ServerMessage(type, null, null, null, null, null, null, null, null, null);
    }
}
