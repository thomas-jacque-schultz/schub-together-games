package schultz.thomas.schub.games.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
public class GamesWebConfiguration implements WebMvcConfigurer {

    private final GamesProperties properties;

    public GamesWebConfiguration(GamesProperties properties) {
        this.properties = properties;
    }

    // Les noms des fichiers de assets/ portent leur empreinte : ils ne changent jamais.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String racine = "file:" + properties.files() + "/";
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(racine + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/favicon.svg").addResourceLocations(racine);
    }
}
