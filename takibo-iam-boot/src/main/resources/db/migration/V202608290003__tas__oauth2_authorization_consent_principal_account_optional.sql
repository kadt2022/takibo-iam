-- ════════════════════════════════════════════════════════════════════════
-- TAS-GRANTS-02 — principal_account_id de oauth2_authorization_consent devient optionnel
-- ════════════════════════════════════════════════════════════════════════
--
-- V202608290001 gardait principal_account_id NOT NULL sur cette table, en miroir de la
-- lecture initiale du récit ("un écran de consentement suppose un compte déjà authentifié").
-- Écrire l'adaptateur qui appelle réellement OAuth2AuthorizationConsentService.save(...)
-- révèle que cette lecture ne tient pas encore : ce service ne reçoit de Spring Authorization
-- Server qu'un OAuth2AuthorizationConsent — registeredClientId, principalName, authorities —
-- sans le moindre identifiant de compte. Résoudre principal_account_id à partir du seul
-- principal_name suppose un port de résolution de compte par identifiant humain, que ce récit
-- ne construit pas : aucune page de connexion ni écran de consentement n'y est branché
-- (voir "Hors périmètre"), TAS-GRANTS-03 est le récit qui les introduit.
--
-- Même situation, même traitement que oauth2_authorization.principal_account_id pour un
-- device code pas encore approuvé (V202608290001) : NULL en attendant qu'un port de
-- résolution existe, plutôt qu'une valeur inventée pour satisfaire la contrainte.
-- principal_name, ajouté par V202608290001, reste la clé de lecture fiable dans l'intervalle.

ALTER TABLE oauth2_authorization_consent
  ALTER COLUMN principal_account_id DROP NOT NULL;

COMMENT ON COLUMN oauth2_authorization_consent.principal_account_id IS
  'NULL tant qu''aucun port de resolution de compte par principal_name n''existe '
  '(TAS-GRANTS-03) : ce recit ne branche ni page de connexion ni ecran de consentement, '
  'OAuth2AuthorizationConsentService ne recoit donc jamais d''identifiant de compte a '
  'sauvegarder. principal_name reste la cle de lecture fiable dans l''intervalle.';
