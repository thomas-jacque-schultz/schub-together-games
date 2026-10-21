# schub-together-games

Le relais des mini-jeux de PremadeLab (Queue Killer), en Java/Spring : salons en mémoire, présence, reconnexion et diffusion des messages sur `/ws`, sans aucune règle de jeu. Les règles tournent chez un joueur, le meneur.

Les pages des jeux vivent dans le front (`schub-front-servers`, `src/games/`, Schub#139), qui les sert sur `<jeu>.premadelab.eu`. Le tunnel envoie `/ws` de ces sous-domaines ici.

Ajouter un jeu ne touche pas ce dépôt. En dev, `dev-<jeu>.premadelab.eu` demande un enregistrement DNS et une règle du tunnel.

En local : `mvn spring-boot:run`, puis `npm run dev` dans le front, avec `RELAY_URL=ws://localhost:8080`.
