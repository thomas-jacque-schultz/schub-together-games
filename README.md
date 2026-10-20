# schub-together-games

Les mini-jeux de PremadeLab (Queue Killer) : un dépôt, une image, un service.

- `src/` : le relais (Java/Spring). Salons en mémoire, présence, reconnexion et diffusion des messages sur `/ws`, sans aucune règle de jeu. Il sert aussi les pages des jeux, choisies par le sous-domaine : `scribble.premadelab.eu`, `zoom.premadelab.eu` (en dev, `dev-scribble…`, `dev-zoom…`).
- `web/` : les pages des jeux (React, Vite). `src/commun/` porte le salon, le relais côté navigateur, le pont avec PremadeLab et le moteur du meneur (les règles tournent chez un joueur) ; `src/<jeu>/` les règles et l'écran d'un jeu, `<jeu>/index.html` sa page.

Ajouter un jeu : un dossier `web/src/<jeu>/`, sa page `web/<jeu>/index.html` et son entrée dans `web/vite.config.ts`. Côté relais et côté infra, rien : le sous-domaine suffit en prod (`*.premadelab.eu`). En dev, `dev-<jeu>.premadelab.eu` demande un enregistrement DNS et une règle du tunnel.

En local : `mvn spring-boot:run` à la racine (sert `web/dist`, à construire avec `npm run build` dans `web/`), ou `npm run dev` dans `web/` avec un relais lancé à côté.
