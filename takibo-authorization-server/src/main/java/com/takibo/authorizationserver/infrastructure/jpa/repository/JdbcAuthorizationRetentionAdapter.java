package com.takibo.authorizationserver.infrastructure.jpa.repository;

import com.takibo.authorizationserver.domain.authorization.port.AuthorizationRetentionPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

/**
 * Adaptateur JDBC de la retention (TAS-GRANTS-02B).
 * <p>
 * Une seule instruction fait tout : elle choisit un lot borne, le verrouille, ignore ce
 * qu'une autre instance tient deja, puis supprime. Il n'existe donc aucune fenetre entre la
 * selection et la suppression pendant laquelle une ligne pourrait redevenir active ou etre
 * prise par un concurrent.
 * <p>
 * JDBC plutot que JPA : la clause {@code FOR UPDATE SKIP LOCKED} combinee a un
 * {@code DELETE ... USING} n'a pas d'equivalent portable en JPQL, et la traduction
 * entite-domaine n'apporte rien a une suppression qui ne lit aucune colonne.
 */
public class JdbcAuthorizationRetentionAdapter implements AuthorizationRetentionPort {

    /**
     * {@code CURRENT_TIMESTAMP} est l'horloge de PostgreSQL, jamais celle d'une JVM : c'est
     * la seule horloge que toutes les instances partagent.
     * <p>
     * L'ordre {@code (purge_after, id)} epouse l'index partiel : les lots sont deterministes
     * et deux executions concurrentes progressent dans le meme sens plutot que de se croiser.
     * <p>
     * {@code purge_after IS NOT NULL} est redondant avec la clause de comparaison, qui
     * ecarterait deja les nulls. Il est ecrit quand meme parce qu'il rend la requete
     * compatible avec l'index partiel, et parce qu'il dit a la lecture ce que la purge refuse
     * de toucher.
     */
    private static final String PURGE_BATCH = """
            WITH candidates AS (
                SELECT id
                FROM oauth2_authorization
                WHERE purge_after IS NOT NULL
                  AND purge_after < CURRENT_TIMESTAMP - CAST(? AS INTERVAL)
                ORDER BY purge_after, id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            DELETE FROM oauth2_authorization a
            USING candidates c
            WHERE a.id = c.id
            """;

    private static final String COUNT_UNPURGEABLE = """
            SELECT COUNT(*) FROM oauth2_authorization WHERE purge_after IS NULL
            """;

    private final JdbcTemplate jdbc;

    public JdbcAuthorizationRetentionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int purgeOneBatch(Duration gracePeriod, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("RETENTION_BATCH_SIZE_MUST_BE_POSITIVE");
        }
        if (gracePeriod == null || gracePeriod.isNegative()) {
            throw new IllegalArgumentException("RETENTION_GRACE_PERIOD_MUST_NOT_BE_NEGATIVE");
        }

        return jdbc.update(PURGE_BATCH, toInterval(gracePeriod), batchSize);
    }

    @Override
    public long countUnpurgeable() {
        Long count = jdbc.queryForObject(COUNT_UNPURGEABLE, Long.class);
        return count == null ? 0L : count;
    }

    /**
     * Duree passee en unites explicites plutot qu'en texte ISO-8601 : PostgreSQL accepte les
     * deux, mais {@code PT2H} depend du reglage {@code IntervalStyle} de la session, alors
     * que {@code 7200 seconds} est interprete de la meme facon partout.
     * <p>
     * La partie sous la seconde est ecrite separement, et ce n'est pas de la coquetterie :
     * {@code toSeconds()} seul tronque vers le bas, donc {@code 1500ms} deviendrait une
     * seconde et {@code 500ms} deviendrait zero. La purge s'executerait alors <b>plus tot</b>
     * que la retention configuree — une troncature qui detruit plus vite que demande, dans le
     * mauvais sens pour une operation irreversible.
     * <p>
     * La microseconde est la precision reelle du type {@code interval} de PostgreSQL :
     * descendre plus bas ne servirait qu'a faire croire a une exactitude que la base ne
     * garde pas.
     */
    static String toInterval(Duration gracePeriod) {
        long seconds = gracePeriod.toSeconds();
        int microseconds = gracePeriod.toNanosPart() / 1_000;
        return seconds + " seconds " + microseconds + " microseconds";
    }
}
