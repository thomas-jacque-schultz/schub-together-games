package schultz.thomas.schub.games.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// files : le build du front (web/), un dossier par jeu et assets/ en commun.
@ConfigurationProperties("games")
public record GamesProperties(String files, String defaultGame) {
}
