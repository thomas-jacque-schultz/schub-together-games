/**
 * Trois couches, sans data (les salons vivent en mémoire) :
 * - api : le WebSocket et son JSON, et les pages des jeux (api.web), choisies par le sous-domaine ;
 * - business : salons, présence, historique et garde-fous, sans rien savoir du WebSocket ; les envois passent par
 *   l'interface Delivery, que api implémente.
 * Aucune règle de jeu ici : elles tournent chez le joueur meneur (Schub#118), dans web/. ArchitectureTest tient ces règles.
 */
package schultz.thomas.schub.games;
