package com.takibo.authorizationserver.infrastructure.springauthserver.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

/**
 * {@code ObjectMapper} qui (dé)sérialise {@code attributes} et chaque {@code *_metadata} d'une
 * {@link org.springframework.security.oauth2.server.authorization.OAuth2Authorization}
 * (TAS-GRANTS-02).
 * <p>
 * Ces cartes portent des types que Jackson ne sait pas reconstruire sans indication — une
 * {@link java.time.Instant}, un {@code Authentication} de sécurité côté {@code attributes}.
 * Spring Authorization Server fournit ses propres modules Jackson précisément pour ça, avec
 * une liste d'autorisation de types déserialisables ({@code SecurityJackson2Modules}) plutôt
 * qu'une désérialisation polymorphe ouverte : {@link JpaOAuth2AuthorizationService} doit
 * utiliser cet {@code ObjectMapper}, jamais celui, générique, de l'application, qui n'a ni les
 * modules ni la liste d'autorisation.
 * <p>
 * Bean dédié et nommé — jamais {@code @Primary} — pour ne jamais remplacer l'{@code
 * ObjectMapper} par défaut de Spring MVC ailleurs dans l'application.
 * <p>
 * {@code SecurityJackson2Modules}/{@code OAuth2AuthorizationServerJackson2Module} sont
 * annoncés {@code @Deprecated(forRemoval = true)} au profit de Jackson 3
 * ({@code tools.jackson}) : Spring Authorization Server 7.0.3 porte les deux générations en
 * parallèle. Le reste de cette application reste sur Jackson 2 ({@code
 * com.fasterxml.jackson}, y compris {@code OAuth2HttpErrorWriter}) ; migrer vers Jackson 3
 * est hors périmètre de ce récit et concernerait l'application entière, pas cette seule
 * classe.
 */
@Configuration
public class OAuth2AuthorizationJacksonConfig {

    public static final String OAUTH2_AUTHORIZATION_OBJECT_MAPPER = "oauth2AuthorizationObjectMapper";

    // Deprecation assumee, justifiee dans la javadoc de classe : la generation Jackson 3 de
    // ces modules (tools.jackson) n'a pas d'equivalent pour un ObjectMapper Jackson 2, celui
    // que tout le reste de l'application utilise. Migrer l'un sans l'autre romprait cette
    // classe ; migrer les deux est hors perimetre de ce recit.
    // "removal", pas seulement "deprecation" : @Deprecated(forRemoval = true) declenche la
    // categorie d'avertissement javac distincte -Xlint:removal.
    @SuppressWarnings({"deprecation", "removal"})
    @Bean(OAUTH2_AUTHORIZATION_OBJECT_MAPPER)
    public ObjectMapper oauth2AuthorizationObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        ClassLoader classLoader = OAuth2AuthorizationJacksonConfig.class.getClassLoader();
        objectMapper.registerModules(SecurityJackson2Modules.getModules(classLoader));
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        return objectMapper;
    }
}
