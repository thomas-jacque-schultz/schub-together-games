package schultz.thomas.schub.games.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.games.config.GamesProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

// Un sous-domaine par jeu : scribble.premadelab.eu et dev-scribble.premadelab.eu servent la page du scribble.
@RestController
public class GamePages {

    private static final Pattern NOM = Pattern.compile("[a-z]+");

    private final GamesProperties properties;

    public GamePages(GamesProperties properties) {
        this.properties = properties;
    }

    // /ABCD : le code du salon est lu par la page.
    @GetMapping(value = {"/", "/{salon:[A-Z]{4}}"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<FileSystemResource> page(HttpServletRequest request) {
        Path page = pageDe(jeuDe(request.getServerName()));
        if (!Files.isRegularFile(page)) {
            page = pageDe(properties.defaultGame());
        }
        if (!Files.isRegularFile(page)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new FileSystemResource(page));
    }

    static String jeuDe(String hote) {
        String premier = hote == null ? "" : hote.split("\\.")[0].toLowerCase();
        String nom = premier.startsWith("dev-") ? premier.substring(4) : premier;
        return NOM.matcher(nom).matches() ? nom : "";
    }

    private Path pageDe(String jeu) {
        return Path.of(properties.files(), jeu, "index.html");
    }
}
