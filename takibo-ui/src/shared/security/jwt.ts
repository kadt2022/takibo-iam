import type { TakiboTokenClaims } from '@/features/authentication/model/login';

/**
 * Décode le payload d'un JWT pour affichage (base64url → JSON).
 * Aucune vérification de signature : la vérification appartient au backend ;
 * ici le token sert uniquement à montrer le pouvoir effectif reçu.
 */
export function decodeTokenClaims(token: string): TakiboTokenClaims | null {
  const payload = token.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(base64);
    return JSON.parse(json) as TakiboTokenClaims;
  } catch {
    return null;
  }
}
