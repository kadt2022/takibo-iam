/**
 * Résumé réel du tableau de bord organisationnel (récit backend Dashboard 01).
 * Contrat de GET /api/v1/orgs/{organizationId}/dashboard/summary.
 *
 * `usersTotal` / `activeUsersTotal` comptent des COMPTES DISTINCTS de
 * l'organisation : un même compte présent dans plusieurs Spaces ne compte
 * qu'une fois. Ce ne sont donc pas des totaux « par Space ».
 *
 * Le dashboard n'affiche que `usersTotal`, `spacesTotal` et `oauthClientsTotal` ;
 * `activeUsersTotal` reste dans le contrat pour la future page dédiée
 * /app/organization/users.
 *
 * `oauthClientsTotal` (récit Dashboard 02) compte tous les clients OAuth2
 * persistés dans les Spaces de l'organisation — jamais aucun secret ici.
 */
export interface OrganizationDashboardSummary {
  organizationId: string;
  usersTotal: number;
  activeUsersTotal: number;
  spacesTotal: number;
  oauthClientsTotal: number;
  generatedAt: string;
}
