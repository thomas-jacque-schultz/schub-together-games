package schultz.thomas.schub.games.api.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class GamePagesTest {

    private static Path fichiers;

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    static void build() throws IOException {
        fichiers = Files.createTempDirectory("jeux");
        Files.createDirectories(fichiers.resolve("scribble"));
        Files.createDirectories(fichiers.resolve("zoom"));
        Files.createDirectories(fichiers.resolve("assets"));
        Files.writeString(fichiers.resolve("scribble/index.html"), "scribble");
        Files.writeString(fichiers.resolve("zoom/index.html"), "zoom");
        Files.writeString(fichiers.resolve("assets/jeu-1a2b.js"), "//");
    }

    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) {
        registry.add("games.files", () -> fichiers.toString());
    }

    @Test
    @DisplayName("le sous-domaine choisit le jeu, en prod comme en dev ; un hôte inconnu tombe sur le jeu par défaut")
    void parSousDomaine() throws Exception {
        mvc.perform(get("/ABCD").header("Host", "zoom.premadelab.eu"))
                .andExpect(status().isOk()).andExpect(content().string("zoom"))
                .andExpect(header().string("Content-Security-Policy",
                        "frame-ancestors 'self' https://premadelab.eu https://dev.premadelab.eu"))
                .andExpect(header().string("Cache-Control", "no-cache"));
        mvc.perform(get("/").header("Host", "dev-scribble.premadelab.eu"))
                .andExpect(content().string("scribble"));
        mvc.perform(get("/").header("Host", "localhost")).andExpect(content().string("scribble"));
    }

    @Test
    @DisplayName("les assets sont communs et gardés un an ; une autre adresse répond 404")
    void assets() throws Exception {
        mvc.perform(get("/assets/jeu-1a2b.js").header("Host", "zoom.premadelab.eu"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        mvc.perform(get("/abcd/../secret").header("Host", "zoom.premadelab.eu")).andExpect(status().isNotFound());
        mvc.perform(get("/admin").header("Host", "zoom.premadelab.eu")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un nom d'hôte bizarre ne sort jamais du dossier des jeux")
    void nomDeJeu() {
        assertThat(GamePages.jeuDe("dev-scribble.premadelab.eu")).isEqualTo("scribble");
        assertThat(GamePages.jeuDe("..premadelab.eu")).isEmpty();
        assertThat(GamePages.jeuDe(null)).isEmpty();
    }
}
