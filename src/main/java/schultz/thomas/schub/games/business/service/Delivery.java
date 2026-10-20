package schultz.thomas.schub.games.business.service;

import schultz.thomas.schub.games.business.model.RoomEvent;

// L'envoi vers une connexion, que la couche api implémente sur le WebSocket.
public interface Delivery {

    void deliver(String connection, RoomEvent event);

    void close(String connection);
}
