-- ════════════════════════════════════════════════════════════════════════
-- TAS-GRANTS-02 — Corrections issues de la revue de sécurité de PR #57
-- ════════════════════════════════════════════════════════════════════════
--
-- Deux incohérences de schéma relevées en revue, distinctes des deux corrections de code
-- (vérification de frontière à la relecture, intégrité du hash après déchiffrement) qui les
-- accompagnent :
--
--   1. oauth2_authorization_consent.org_id restait NOT NULL alors que le modèle applicatif
--      autorise déjà un consentement sans organisation (même vocabulaire PLATFORM que
--      oauth2_authorization.org_id, V202608290001). Aucun flux ne produit aujourd'hui de
--      consentement PLATFORM — client_credentials n'affiche jamais d'écran de consentement —
--      mais rien ne l'interdisait non plus explicitement ; la colonne devient nullable par
--      symétrie avec oauth2_authorization, plutôt que de laisser une contrainte que le modèle
--      ne respecte qu'à moitié.
--
--   2. access_token_value (VARCHAR(16000)), refresh_token_value (VARCHAR(4000)) et
--      oidc_id_token_value (VARCHAR(16000)) ont été dimensionnées pour la valeur en CLAIR
--      (V202601091233, avant que TAS-GRANTS-02 ne les chiffre). Le chiffre autoportant produit
--      par SecretCipher encode le résultat en base64 (~+33 %) par-dessus l'IV et le tag GCM,
--      en plus de l'enveloppe "v1$<keyId>$" : un JWT qui tenait tout juste dans ces colonnes
--      avant chiffrement peut désormais dépasser la limite et faire échouer save() en
--      production. authorization_code_value/device_code_value/user_code_value
--      (VARCHAR(500), V202608290002) sont dimensionnées pour des codes courts et ne débordent
--      pas dans la pratique, mais passent à TEXT par cohérence : aucune des six ne doit plus
--      porter de limite arbitraire alors que Postgres ne pénalise pas TEXT face à VARCHAR(n).

ALTER TABLE oauth2_authorization_consent
  ALTER COLUMN org_id DROP NOT NULL;

COMMENT ON COLUMN oauth2_authorization_consent.org_id IS
  'NULL = PLATFORM, meme vocabulaire que oauth2_authorization.org_id (V202608290001). '
  'Aucun flux ne produit aujourd''hui de consentement PLATFORM (client_credentials n''affiche '
  'jamais d''ecran de consentement), mais rien ne doit non plus l''interdire par une '
  'contrainte que le modele ne respecte qu''a moitie.';

ALTER TABLE oauth2_authorization
  ALTER COLUMN authorization_code_value TYPE TEXT,
  ALTER COLUMN access_token_value TYPE TEXT,
  ALTER COLUMN oidc_id_token_value TYPE TEXT,
  ALTER COLUMN refresh_token_value TYPE TEXT,
  ALTER COLUMN user_code_value TYPE TEXT,
  ALTER COLUMN device_code_value TYPE TEXT;

COMMENT ON COLUMN oauth2_authorization.access_token_value IS
  'Chiffre autoportant (EncryptedTokenValue, TAS-GRANTS-02), jamais le clair. TEXT, pas '
  'VARCHAR(16000) : ce dimensionnement d''origine visait la valeur en clair, avant '
  'chiffrement -- l''enveloppe et l''encodage base64 du chiffre ajoutent environ un tiers a '
  'la longueur d''origine.';
COMMENT ON COLUMN oauth2_authorization.refresh_token_value IS
  'Chiffre autoportant. Meme raison que access_token_value : TEXT remplace VARCHAR(4000), '
  'dimensionne pour la valeur en clair.';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_value IS
  'Chiffre autoportant. Meme raison que access_token_value.';
