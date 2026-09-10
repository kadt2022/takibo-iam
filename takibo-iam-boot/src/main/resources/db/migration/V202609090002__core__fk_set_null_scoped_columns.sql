-- ════════════════════════════════════════════════════════════════════════
-- Clés étrangères composites : ne nuller que la colonne facultative
-- ════════════════════════════════════════════════════════════════════════
--
-- ⚠ CONSÉQUENCE DE DÉPLOIEMENT — VERSION MINIMALE DE POSTGRESQL
--
-- Cette migration emploie ON DELETE SET NULL (<colonne>), forme par colonne de l'action
-- référentielle apparue avec PostgreSQL 15. À partir d'ici, TAKIBO exige donc
-- PostgreSQL 15 ou supérieur. Sur un serveur antérieur, l'échec survient ici même, à la
-- migration, et l'application ne démarre pas — avec une erreur de syntaxe qui n'explique
-- pas d'où vient l'exigence, d'où ce rappel en tête de fichier.
--
-- Le plancher est documenté dans readme.md (Technology Stack et Prerequisites). CI et
-- Testcontainers tournent tous deux sur PostgreSQL 16.
--
-- Cinq clés étrangères composites du schéma initial (V202601091230, V202601091233) suivent
-- le même patron : la paire (org_id, <colonne facultative>) référence la clé d'unicité
-- scopée de la table cible, avec ON DELETE SET NULL. L'intention est lisible et juste — la
-- ligne survit à la disparition de ce qu'elle désignait, en perdant seulement cette
-- désignation.
--
-- PostgreSQL ne l'entend pas ainsi. Sans liste de colonnes, ON DELETE SET NULL nulle
-- TOUTES les colonnes de la clé étrangère, donc org_id aussi :
--
--   UPDATE ONLY tas_audit_events SET org_id = NULL, space_id = NULL WHERE ...
--
-- Or org_id est NOT NULL dans les cinq tables. La suppression ne « nulle » donc jamais :
-- elle échoue, systématiquement, sur
--
--   null value in column "org_id" of relation "tas_audit_events"
--     violates not-null constraint
--
-- Constaté le 2026-09-09 en écrivant les tests de TAS-GRANTS-02B : un événement d'audit TAS
-- inséré par un test rendait impossible le DELETE FROM spaces du nettoyage, et faisait donc
-- échouer le test suivant. Le défaut n'a rien de propre à l'audit ni au TAS ; il tient au
-- patron, et les cinq clés le portent.
--
-- PostgreSQL 15 a introduit ON DELETE SET NULL (colonnes...), qui restreint la mise à NULL
-- aux colonnes nommées. La cible du dépôt est PostgreSQL 16 (CI et Testcontainers), la
-- syntaxe est donc disponible. C'est exactement ce que ces cinq contraintes voulaient dire.
--
-- Ce que la correction ne change pas : la clé étrangère reste composite et reste vérifiée à
-- l'écriture. Une ligne ne peut toujours pas désigner un space ou un compte d'une autre
-- organisation — c'est la raison d'être de la paire, et elle est préservée.
--
-- Doctrine retenue pour l'audit (RBAC-08, « audit situé ») : un événement survit à la
-- suppression de son space, avec space_id NULL et org_id intact. L'organisation est la
-- frontière de lecture d'un R_ORG_AUDITOR ; la perdre rendrait l'événement illisible pour
-- tout auditeur, ce qui reviendrait à supprimer l'audit sans le dire. Le space n'est qu'un
-- raffinement de cette frontière. C'est déjà ce que déclarent les entités JPA
-- (TasAuditEventEntity : org_id nullable = false, space_id nullable) : seul le SQL le
-- contredisait.
--
-- Réserve assumée : une fois space_id nullé, l'événement ne dit plus dans quel space il
-- s'est produit. Aucun space n'est aujourd'hui supprimé en production — le cycle de vie est
-- ACTIVE/SUSPENDED/DISABLED, sans effacement — donc le cas ne se présente pas encore.
-- Conserver la trace du space disparu (dans metadata_json, par exemple) relève d'une
-- décision de rétention, pas de la réparation de cette contrainte.

-- ── tas_audit_events : l'événement d'audit survit à son space ───────────

ALTER TABLE tas_audit_events
  DROP CONSTRAINT fk_tae_space_scope;

ALTER TABLE tas_audit_events
  ADD CONSTRAINT fk_tae_space_scope
  FOREIGN KEY (org_id, space_id)
  REFERENCES spaces(org_id, id)
  ON DELETE SET NULL (space_id);

COMMENT ON COLUMN tas_audit_events.space_id IS
  'Space ou l''evenement s''est produit. NULL = evenement de plan ORGANIZATION, ou space '
  'supprime depuis : fk_tae_space_scope ne nulle que cette colonne, jamais org_id, qui '
  'reste la frontiere de lecture de l''audit (RBAC-08).';

-- ── identity_observations : même patron, deux fois ──────────────────────

ALTER TABLE identity_observations
  DROP CONSTRAINT fk_identity_observations_space_scope;

ALTER TABLE identity_observations
  ADD CONSTRAINT fk_identity_observations_space_scope
  FOREIGN KEY (org_id, space_id)
  REFERENCES spaces(org_id, id)
  ON DELETE SET NULL (space_id);

ALTER TABLE identity_observations
  DROP CONSTRAINT fk_identity_observations_account_scope;

ALTER TABLE identity_observations
  ADD CONSTRAINT fk_identity_observations_account_scope
  FOREIGN KEY (org_id, account_id)
  REFERENCES takibo_identities(org_id, account_id)
  ON DELETE SET NULL (account_id);

-- ── spaces : le space survit à son compte propriétaire ──────────────────

ALTER TABLE spaces
  DROP CONSTRAINT fk_spaces_owner_account_scope;

ALTER TABLE spaces
  ADD CONSTRAINT fk_spaces_owner_account_scope
  FOREIGN KEY (org_id, owner_account_id)
  REFERENCES accounts(org_id, id)
  ON DELETE SET NULL (owner_account_id);

-- ── identity_keys : la clé survit à son compte de credentials ───────────

ALTER TABLE identity_keys
  DROP CONSTRAINT fk_identity_keys_cred_account_scope;

ALTER TABLE identity_keys
  ADD CONSTRAINT fk_identity_keys_cred_account_scope
  FOREIGN KEY (org_id, credential_account_id)
  REFERENCES accounts(org_id, id)
  ON DELETE SET NULL (credential_account_id);
