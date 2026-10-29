package com.takibo.iamboot.tas;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Socle des tests de reference TAS sur PostgreSQL reel (TAS-GRANTS-00).
 * <p>
 * Le profil {@code test} du module tourne sur H2 avec Flyway desactive et
 * {@code ddl-auto: none} : dans cette configuration, aucune table TAS n'existe. Or le
 * schema TAS repose sur {@code jsonb} et sur des index uniques partiels, que H2 ne porte
 * pas. Un filet de securite pose sur H2 ne prouverait donc rien.
 * <p>
 * Ce socle demarre un PostgreSQL reel, applique les migrations Flyway du depot et bascule
 * Hibernate en {@code validate} : les entites JPA sont confrontees au schema tel qu'il sera
 * en production. Le conteneur est unique pour la JVM et partage par toutes les classes qui
 * heritent de ce socle ; Testcontainers le supprime a la sortie.
 * <p>
 * Les classes concretes portent {@code @EnabledIf("dockerIsAvailable")} : sans Docker, elles
 * sont ignorees plutot qu'en echec, sauf en CI ({@code CI=true}) ou l'absence de Docker fait
 * echouer la suite : une suite verte n'y cache jamais des tests PostgreSQL ignores.
 */
abstract class TasPostgresBaseline {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";

    private static PostgreSQLContainer<?> postgres;

    /**
     * Condition d'activation JUnit. Volontairement sans effet de bord : elle ne demarre pas
     * le conteneur, sans quoi une machine sans Docker paierait le demarrage avant d'etre
     * declaree inapte.
     */
    static boolean dockerIsAvailable() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError ignored) {
            // Docker absent ou mal configure : l'initialisation de Testcontainers echoue
            // soit par une exception, soit par une erreur de chargement de classe.
            // Volontairement plus etroit que Throwable : une erreur de la JVM, une
            // interruption ou un depassement de pile doivent remonter.
            available = false;
        }
        return requiredInCi(available, System.getenv("CI"));
    }

    /** En CI, Docker est obligatoire : son absence fait echouer la classe au lieu de l'ignorer. */
    static boolean requiredInCi(boolean dockerAvailable, String ciEnvironment) {
        if (!dockerAvailable && "true".equalsIgnoreCase(ciEnvironment)) {
            throw new IllegalStateException(
                    "Docker indisponible en CI : les tests PostgreSQL ne peuvent pas etre ignores");
        }
        return dockerAvailable;
    }

    private static synchronized PostgreSQLContainer<?> container() {
        if (postgres == null) {
            PostgreSQLContainer<?> instance = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                    .withDatabaseName("takibo_iam")
                    .withUsername("takibo")
                    .withPassword("takibo");
            instance.start();
            postgres = instance;
        }
        return postgres;
    }

    @DynamicPropertySource
    static void tasBaselineDatasource(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> pg = container();
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Le schema reel, pas une approximation : Flyway rejoue toutes les migrations.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        // validate confronte les entites JPA au schema migre ; il ne cree ni ne modifie rien.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
