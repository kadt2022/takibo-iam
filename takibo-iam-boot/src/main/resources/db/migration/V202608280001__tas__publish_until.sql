-- ════════════════════════════════════════════════════════════════════════
-- TAS-GRANTS-02A — Séparer la fin de publication de l'échéance de la clé
-- ════════════════════════════════════════════════════════════════════════
--
-- Décision. `expires_at` portait deux significations distinctes : jusqu'à quand une clé
-- ACTIVE reste dans sa période de validité, et jusqu'à quand une clé RETIRED reste publiée
-- dans le JWKS pour que les JWT qu'elle a signés restent vérifiables. Ces deux échéances ne
-- coïncident pas nécessairement — la seconde dépend de la durée de vie du dernier token émis,
-- la première d'une politique de cryptopériode qui n'existe pas encore. Réutiliser la même
-- colonne pour les deux aurait fini par allonger ou raccourcir l'une en modifiant l'autre.
--
-- `publish_until` porte désormais seule la fin de publication, écrite uniquement par la
-- rotation au retrait d'une émettrice. `expires_at` reste disponible pour une future
-- cryptopériode ; rien ne l'écrit aujourd'hui, et `findPublishable` ne le lit plus.

ALTER TABLE tas_signing_keys
  ADD COLUMN publish_until timestamptz;

-- Aucune ligne existante à raison de ce récit non encore fusionné, mais idempotent si l'ordre
-- de fusion venait à changer : une clé déjà retirée par l'ancien schéma garde sa fin de
-- publication au lieu de redevenir publiable sans limite.
UPDATE tas_signing_keys
   SET publish_until = expires_at
 WHERE status = 'RETIRED'
   AND expires_at IS NOT NULL
   AND publish_until IS NULL;

CREATE INDEX idx_tas_sk_org_publish_until
  ON tas_signing_keys (org_id, publish_until);

COMMENT ON COLUMN tas_signing_keys.publish_until IS
  'Fin de publication dans le JWKS pour une cle RETIRED : passe ce delai, findPublishable '
  'cesse de la servir. Ecrite uniquement par la rotation, jamais par la creation d''une '
  'emettrice. NULL = aucune borne (cas normal d''une cle ACTIVE).';

COMMENT ON COLUMN tas_signing_keys.expires_at IS
  'Fin de la periode de validite de la cle (cryptoperiode), distincte de publish_until qui '
  'gouverne la publication d''une cle retiree. Non utilisee tant qu''aucune politique de '
  'cryptoperiode active n''est en place (TAS-GRANTS-02A) ; reservee a cet usage futur.';
