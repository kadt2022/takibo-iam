-- ============================================================================
-- IAM-REVOKE-01 — Rattrapage des identités TAKIBO manquantes
--
-- Depuis IAM-REVOKE-01, le login refuse un Account sans identité ACTIVE. Tout Account
-- humain reçoit désormais son identité à la création ; cette migration crée celles qui
-- manqueraient pour des Accounts antérieurs.
--
-- Garanties :
--   - additive : uniquement des INSERT, jamais d'UPDATE ni de DELETE ; une identité existante,
--     quel que soit son statut (SUSPENDED, DISABLED), n'est ni modifiée ni réactivée ;
--   - idempotente : rejouée, elle n'insère rien (NOT EXISTS + ON CONFLICT DO NOTHING) ;
--   - bornée par la frontière Organization : l'identité porte l'org_id de son Account ;
--   - même convention que le code (TakiboIdentity.createHuman) : identity_id = account_id,
--     type HUMAN, statut ACTIVE. Aucun chemin de TAKIBO ne crée d'Account machine : les
--     machines sont des clients OAuth.
-- ============================================================================

INSERT INTO takibo_identities (
    identity_id, org_id, account_id, identity_type, identity_status, created_by)
SELECT a.id, a.org_id, a.id, 'HUMAN', 'ACTIVE', 'migration:IAM-REVOKE-01'
FROM accounts a
WHERE NOT EXISTS (
    SELECT 1
    FROM takibo_identities ti
    WHERE ti.org_id = a.org_id
      AND ti.account_id = a.id
)
ON CONFLICT DO NOTHING;
