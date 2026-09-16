-- ════════════════════════════════════════════════════════════════════════
-- TMS-OAUTH-CLIENT-BOUNDARY-01 — Aligner le registre OAuth sur les trois
-- frontières TAKIBO : PLATFORM / ORGANIZATION / SPACE
-- ════════════════════════════════════════════════════════════════════════
--
-- Le domaine TAS modélise déjà trois plans de client et vérifie leurs invariants à la
-- construction (ClientPlan, ResolvedOAuthClient). Le registre TMS, lui, imposait org_id et
-- space_id NOT NULL : il ne savait persister qu'un client SPACE.
--
-- La preuve de cette dette était déjà dans le code : le client PLATFORM est déclaré par
-- InMemoryPlatformOAuthClientResolver, précisément parce qu'aucune ligne ne pouvait le
-- représenter.
--
-- ── Ce que cette migration change, et dans quel ordre ──────────────────
--
-- Rendre les deux colonnes nullables sur le parent ne suffit pas. Six tables de
-- configuration dupliquent la frontière du parent, avec org_id et space_id NOT NULL et une
-- FK composite vers oauth2_clients(org_id, space_id, id) :
--
--   oauth2_client_scopes
--   oauth2_client_grant_types
--   oauth2_client_redirect_uris
--   oauth2_client_post_logout_redirect_uris
--   oauth2_client_cors_origins
--   oauth2_client_secret_history
--
-- Sans elles, un client PLATFORM ou ORGANIZATION serait représentable mais INUTILISABLE :
-- il ne pourrait porter aucun grant type, et JpaResolvedOAuthClientResolver traite un client
-- sans grant type comme introuvable.
--
-- Ces colonnes sont supprimées plutôt que rendues nullables. Aucune requête de production ne
-- les lit — TAS interroge ces tables par le seul UUID technique du client, TMS les mappe par
-- un @ManyToOne composite qui ne fait que recopier la frontière du parent. Et surtout, une
-- frontière dupliquée et NON VÉRIFIÉE est pire qu'absente : la FK composite cesse d'être
-- appliquée dès qu'une de ses colonnes est nulle (MATCH SIMPLE, voir plus bas), si bien
-- qu'une ligne de configuration pourrait déclarer une organisation différente de celle de son
-- client sans que rien ne s'y oppose.
--
-- L'ordre ci-dessous n'est pas négociable. Chaque étape est couverte par la précédente, et
-- l'intégrité n'est jamais assurée zéro fois :
--
--   1. FK simple client_id -> oauth2_clients(id) sur les six filles
--   2. nouvelles unicités et index par (client_id, …)
--   3. anciennes unicités et index supprimés
--   4. FK composites supprimées  → la FK simple prend seule le relais
--   5. colonnes org_id / space_id supprimées des six filles
--   6. parent rendu nullable + CHECK des frontières valides
--   7. vérification d'absence d'orphelins
--
-- L'inversion des étapes 4 et 6 est la seule erreur réellement dangereuse : rendre le parent
-- nullable tant que les FK composites existent ferait cesser leur vérification en silence,
-- pendant la migration elle-même.
--
-- ── Hors périmètre, volontairement ─────────────────────────────────────
--
-- user_client_associations et client_role_permissions référencent aussi
-- oauth2_clients(org_id, space_id, id). Elles ne sont PAS touchées : ce sont des associations
-- utilisateur/RBAC, qui restent SPACE-only. Leur FK garde tout son sens après cette migration
-- — la clé d'unicité (org_id, space_id, id) du parent subsiste, et une ligne parente sans
-- tenant ne peut tout simplement pas être référencée. C'est exactement la propriété voulue :
-- une association d'utilisateur n'existe que pour un client situé dans un Space.
--
-- oauth2_authorization et oauth2_authorization_consent n'ont plus aucune FK vers
-- oauth2_clients depuis V202608290001, où elle a été supprimée délibérément. Elle n'est pas
-- réintroduite ici.

-- ────────────────────────────────────────────────────────────────────────
-- ÉTAPE 1 — FK simple sur chaque fille, AVANT tout retrait
-- ────────────────────────────────────────────────────────────────────────
-- À partir d'ici, l'intégrité référentielle de chaque fille est assurée deux fois : par
-- l'ancienne FK composite et par la nouvelle. Jamais zéro.
--
-- Cette FK-ci, contrairement à la composite, est vérifiée dans TOUS les cas, y compris pour
-- un client sans organisation ni space. C'est elle qui rend l'intégrité des filles possible
-- pour les trois frontières.

ALTER TABLE oauth2_client_scopes
  ADD CONSTRAINT fk_ocs_client
  FOREIGN KEY (client_id) REFERENCES oauth2_clients(id) ON DELETE CASCADE;

ALTER TABLE oauth2_client_grant_types
  ADD CONSTRAINT fk_ocg_client
  FOREIGN KEY (client_id) REFERENCES oauth2_clients(id) ON DELETE CASCADE;

ALTER TABLE oauth2_client_redirect_uris
  ADD CONSTRAINT fk_ocr_client
  FOREIGN KEY (client_id) REFERENCES oauth2_clients(id) ON DELETE CASCADE;

ALTER TABLE oauth2_client_post_logout_redirect_uris
  ADD CONSTRAINT fk_ocplr_client
  FOREIGN KEY (client_id) REFERENCES oauth2_clients(id) ON DELETE CASCADE;

ALTER TABLE oauth2_client_cors_origins
  ADD CONSTRAINT fk_occo_client
  FOREIGN KEY (client_id) REFERENCES oauth2_clients(id) ON DELETE CASCADE;

ALTER TABLE oauth2_client_secret_history
  ADD CONSTRAINT fk_ocsh_client
  FOREIGN KEY (client_id) REFERENCES oauth2_clients(id) ON DELETE CASCADE;

-- ────────────────────────────────────────────────────────────────────────
-- ÉTAPE 2 — Unicités et index par client_id
-- ────────────────────────────────────────────────────────────────────────
-- Les unicités portaient (org_id, space_id, client_id, valeur). Le client_id étant
-- globalement unique et identifiant à lui seul son client, (client_id, valeur) exprime
-- exactement la même règle sans dépendre de la frontière.

ALTER TABLE oauth2_client_scopes
  ADD CONSTRAINT uk_ocs_client_scope_v2 UNIQUE (client_id, scope);

ALTER TABLE oauth2_client_grant_types
  ADD CONSTRAINT uk_ocg_client_grant_v2 UNIQUE (client_id, grant_type);

ALTER TABLE oauth2_client_redirect_uris
  ADD CONSTRAINT uk_ocr_client_redirect_v2 UNIQUE (client_id, uri);

ALTER TABLE oauth2_client_post_logout_redirect_uris
  ADD CONSTRAINT uk_ocplr_client_post_logout_v2 UNIQUE (client_id, uri);

ALTER TABLE oauth2_client_cors_origins
  ADD CONSTRAINT uk_occo_client_origin_v2 UNIQUE (client_id, origin);

-- oauth2_client_secret_history n'a jamais porté d'unicité : un même secret peut
-- légitimement réapparaître dans l'historique. Seul son index de lecture est refait.

CREATE INDEX idx_ocsh_client_v2 ON oauth2_client_secret_history(client_id);

-- ────────────────────────────────────────────────────────────────────────
-- ÉTAPE 3 — Anciennes unicités et index de lecture
-- ────────────────────────────────────────────────────────────────────────
-- Les index idx_*_client portaient (org_id, space_id, client_id). Les nouvelles contraintes
-- UNIQUE de l'étape 2 créent leur propre index commençant par client_id, qui sert désormais
-- les lectures findByClientId. Seul l'historique des secrets, sans unicité, garde un index
-- dédié.

ALTER TABLE oauth2_client_scopes                    DROP CONSTRAINT uk_ocs_client_scope;
ALTER TABLE oauth2_client_grant_types               DROP CONSTRAINT uk_ocg_client_grant;
ALTER TABLE oauth2_client_redirect_uris             DROP CONSTRAINT uk_ocr_client_redirect;
ALTER TABLE oauth2_client_post_logout_redirect_uris DROP CONSTRAINT uk_ocplr_client_post_logout;
ALTER TABLE oauth2_client_cors_origins              DROP CONSTRAINT uk_occo_client_origin;

DROP INDEX idx_ocs_client;
DROP INDEX idx_ocg_client;
DROP INDEX idx_ocr_client;
DROP INDEX idx_ocplr_client;
DROP INDEX idx_occo_client;
DROP INDEX idx_ocsh_client;

-- ────────────────────────────────────────────────────────────────────────
-- ÉTAPE 4 — FK composites retirées ; la FK simple prend seule le relais
-- ────────────────────────────────────────────────────────────────────────
-- Les FK vers organizations partent avec elles : elles ne portaient que la colonne org_id
-- dupliquée, dont la frontière appartient désormais au seul client.

ALTER TABLE oauth2_client_scopes                    DROP CONSTRAINT fk_ocs_client_scope;
ALTER TABLE oauth2_client_grant_types               DROP CONSTRAINT fk_ocg_client_scope;
ALTER TABLE oauth2_client_redirect_uris             DROP CONSTRAINT fk_ocr_client_scope;
ALTER TABLE oauth2_client_post_logout_redirect_uris DROP CONSTRAINT fk_ocplr_client_scope;
ALTER TABLE oauth2_client_cors_origins              DROP CONSTRAINT fk_occo_client_scope;
ALTER TABLE oauth2_client_secret_history            DROP CONSTRAINT fk_ocsh_client_scope;

ALTER TABLE oauth2_client_scopes                    DROP CONSTRAINT fk_ocs_org;
ALTER TABLE oauth2_client_grant_types               DROP CONSTRAINT fk_ocg_org;
ALTER TABLE oauth2_client_redirect_uris             DROP CONSTRAINT fk_ocr_org;
ALTER TABLE oauth2_client_post_logout_redirect_uris DROP CONSTRAINT fk_ocplr_org;
ALTER TABLE oauth2_client_cors_origins              DROP CONSTRAINT fk_occo_org;
ALTER TABLE oauth2_client_secret_history            DROP CONSTRAINT fk_ocsh_org;

-- ────────────────────────────────────────────────────────────────────────
-- ÉTAPE 5 — Colonnes de frontière supprimées des six filles
-- ────────────────────────────────────────────────────────────────────────
-- La frontière d'une configuration de client est celle de son client, une seule fois, au
-- seul endroit qui l'applique.

ALTER TABLE oauth2_client_scopes                    DROP COLUMN org_id, DROP COLUMN space_id;
ALTER TABLE oauth2_client_grant_types               DROP COLUMN org_id, DROP COLUMN space_id;
ALTER TABLE oauth2_client_redirect_uris             DROP COLUMN org_id, DROP COLUMN space_id;
ALTER TABLE oauth2_client_post_logout_redirect_uris DROP COLUMN org_id, DROP COLUMN space_id;
ALTER TABLE oauth2_client_cors_origins              DROP COLUMN org_id, DROP COLUMN space_id;
ALTER TABLE oauth2_client_secret_history            DROP COLUMN org_id, DROP COLUMN space_id;

-- ────────────────────────────────────────────────────────────────────────
-- ÉTAPE 6 — Le parent accueille les trois frontières
-- ────────────────────────────────────────────────────────────────────────
-- Jamais avant l'étape 4 : tant que les FK composites des filles existaient, un NULL dans le
-- parent aurait fait cesser leur vérification en silence.
--
-- ⚠ La FK fk_oauth2_clients_space_scope (org_id, space_id) -> spaces(org_id, id) est
-- conservée telle quelle, et c'est son comportement PAR DÉFAUT qui rend le client
-- ORGANIZATION possible : une FK composite PostgreSQL est vérifiée en MATCH SIMPLE, donc
-- IGNORÉE dès qu'une de ses colonnes est nulle.
--
--   (org_id = A, space_id = S1)   → vérifiée, S1 doit appartenir à A
--   (org_id = A, space_id = NULL) → non vérifiée : c'est le client ORGANIZATION
--   (NULL, NULL)                  → non vérifiée : c'est le client PLATFORM
--
-- La passer en MATCH FULL paraîtrait un durcissement légitime lors d'une revue future. Ce
-- serait une régression totale : MATCH FULL exige que toutes les colonnes soient nulles ou
-- toutes non nulles, donc TOUT CLIENT ORGANIZATION deviendrait impossible à écrire.
-- fk_oauth2_clients_org, elle, porte une seule colonne et reste vérifiée dès qu'org_id est
-- présent.

ALTER TABLE oauth2_clients
  ALTER COLUMN org_id   DROP NOT NULL,
  ALTER COLUMN space_id DROP NOT NULL;

ALTER TABLE oauth2_clients
  ADD CONSTRAINT ck_oauth2_clients_boundary
  CHECK (org_id IS NOT NULL OR space_id IS NULL);

COMMENT ON COLUMN oauth2_clients.org_id IS
  'Frontiere du client, avec space_id. (NULL, NULL) = PLATFORM, (UUID, NULL) = ORGANIZATION, '
  '(UUID, UUID) = SPACE. La combinaison (NULL, UUID) est interdite par '
  'ck_oauth2_clients_boundary. Le ClientPlan se deduit de cette paire, il n''est pas stocke.';

COMMENT ON COLUMN oauth2_clients.space_id IS
  'NULL pour un client PLATFORM ou ORGANIZATION. Quand il est present, '
  'fk_oauth2_clients_space_scope garantit que le space appartient bien a org_id.';

-- ────────────────────────────────────────────────────────────────────────
-- ÉTAPE 7 — Aucun orphelin
-- ────────────────────────────────────────────────────────────────────────
-- Fail-closed : la migration échoue plutôt que de livrer un schéma dont l'intégrité n'a pas
-- été constatée. Les FK de l'étape 1 l'interdisent déjà pour l'avenir ; cette vérification
-- porte sur les données existantes au moment du passage.

DO $$
DECLARE
    orphan_count BIGINT;
BEGIN
    SELECT (
        (SELECT COUNT(*) FROM oauth2_client_scopes s
          WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients c WHERE c.id = s.client_id))
      + (SELECT COUNT(*) FROM oauth2_client_grant_types g
          WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients c WHERE c.id = g.client_id))
      + (SELECT COUNT(*) FROM oauth2_client_redirect_uris r
          WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients c WHERE c.id = r.client_id))
      + (SELECT COUNT(*) FROM oauth2_client_post_logout_redirect_uris p
          WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients c WHERE c.id = p.client_id))
      + (SELECT COUNT(*) FROM oauth2_client_cors_origins o
          WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients c WHERE c.id = o.client_id))
      + (SELECT COUNT(*) FROM oauth2_client_secret_history h
          WHERE NOT EXISTS (SELECT 1 FROM oauth2_clients c WHERE c.id = h.client_id))
    ) INTO orphan_count;

    IF orphan_count > 0 THEN
        RAISE EXCEPTION
          'TMS-OAUTH-CLIENT-BOUNDARY-01: % ligne(s) de configuration orpheline(s) detectee(s)',
          orphan_count;
    END IF;
END $$;
