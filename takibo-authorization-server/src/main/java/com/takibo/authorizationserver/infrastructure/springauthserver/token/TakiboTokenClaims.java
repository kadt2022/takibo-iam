package com.takibo.authorizationserver.infrastructure.springauthserver.token;

/**
 * Noms canoniques des claims/settings « situés » TAKIBO.
 * <p>
 * Source de vérité partagée entre les producteurs (les {@code RegisteredClientRepository} qui
 * posent ces valeurs dans les {@code ClientSettings}) et le {@code TakiboOAuth2TokenCustomizer}
 * qui les transcrit en claims de token. Aucun de ces noms ne doit diverger entre les deux côtés.
 */
public final class TakiboTokenClaims {

    private TakiboTokenClaims() {
    }

    // Clés (claims ET settings)
    public static final String SCOPE_LEVEL = "takibo_scope_level";
    public static final String TENANT_SOURCE = "takibo_tenant_source";
    public static final String ORG_ID = "org_id";
    public static final String SPACE_ID = "space_id";
    public static final String ACCOUNT_ID = "account_id";
    public static final String USER_ID = "user_id";
    public static final String ROLES = "roles";
    public static final String GROUPS = "groups";
    public static final String PERMISSIONS = "permissions";
    public static final String SUBJECT_TYPE = "subject_type";
    public static final String AUTH_METHOD = "auth_method";

    /**
     * Clé {@code TokenSettings} personnalisée pour la durée de vie de l'ID token, en secondes.
     * Spring Authorization Server n'a pas d'équivalent natif à
     * {@code accessTokenTimeToLive}/{@code refreshTokenTimeToLive} pour l'ID token — il fixe
     * 30 minutes en dur dans {@code JwtGenerator}. {@code TakiboOAuth2TokenCustomizer} lit ce
     * réglage quand il est présent et réécrit la réclamation {@code exp} de l'ID token en
     * conséquence, avant l'émission.
     */
    public static final String ID_TOKEN_TTL_SECONDS = "takibo_id_token_ttl_seconds";

    // Valeurs de scope
    public static final String SCOPE_SPACE = "SPACE";
    public static final String SCOPE_ORGANIZATION = "ORGANIZATION";
    public static final String SCOPE_PLATFORM = "PLATFORM";

    // Valeurs de source de tenant
    public static final String SOURCE_OAUTH2_CLIENT = "oauth2_client";
    public static final String SOURCE_PLATFORM = "platform_client";
    public static final String SOURCE_HUMAN_LOGIN = "human_login";
    public static final String SOURCE_HUMAN_SPACE_SELECTION = "human_space_selection";

    // Valeurs sujet / auth (flux client_credentials)
    public static final String SUBJECT_CLIENT_APP = "CLIENT_APP";
    public static final String AUTH_CLIENT_CREDENTIALS = "OAUTH2_CLIENT_CREDENTIALS";

    // Valeurs sujet / auth (login humain)
    public static final String SUBJECT_HUMAN = "HUMAN";
    public static final String AUTH_PASSWORD = "PASSWORD";
}
