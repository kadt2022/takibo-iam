-- ════════════════════════════════════════════════════════════════════════
-- TAS-GRANTS-02 — Valeurs chiffrées récupérables des codes
-- ════════════════════════════════════════════════════════════════════════
--
-- V202601091233 ne portait que le hash de l'authorization code, du device code et du
-- user code ("HASH ONLY"), sur l'hypothèse qu'ils n'ont jamais besoin d'être relus tels
-- quels. C'est faux : Spring Authorization Server doit reproduire la valeur en clair pour
-- rejouer l'échange (authorization_code -> token), afficher le user_code au flux device, et
-- comparer le device_code présenté par le client à intervalles réguliers. Un hash seul ne le
-- permet pas — c'est justement la distinction que documente
-- domain.keys.port.SecretCipher : "un hachage protège une valeur qu'on ne relira jamais ;
-- ce port protège des valeurs qu'il faut restituer telles quelles".
--
-- L'access token, le refresh token et l'ID token portaient déjà value + hash : ce défaut ne
-- touchait que les trois colonnes hash-only.

ALTER TABLE oauth2_authorization
  ADD COLUMN authorization_code_value VARCHAR(500),
  ADD COLUMN device_code_value        VARCHAR(500),
  ADD COLUMN user_code_value          VARCHAR(500);

COMMENT ON COLUMN oauth2_authorization.authorization_code_value IS
  'Chiffre autoportant (EncryptedTokenValue, TAS-GRANTS-02) : jamais le code en clair. '
  'Necessaire pour que Spring Authorization Server puisse rejouer l''echange contre un '
  'access token ; authorization_code_hash reste la cle de recherche.';
COMMENT ON COLUMN oauth2_authorization.device_code_value IS
  'Chiffre autoportant. Le client presente ce code a intervalles reguliers jusqu''a '
  'l''approbation ou l''expiration ; device_code_hash reste la cle de recherche.';
COMMENT ON COLUMN oauth2_authorization.user_code_value IS
  'Chiffre autoportant. Affiche a l''utilisateur pour l''ecran d''approbation du flux '
  'device ; user_code_hash reste la cle de recherche.';
