import { useEffect } from 'react';
import { Navigate, Outlet } from 'react-router-dom';

import { isSessionActive } from '@/shared/security/organization-session';
import { useSession } from '@/shared/security/session-context';

/**
 * Garde des routes /app/** : sans session ORGANIZATION active, redirige vers
 * /login. Programme aussi la fermeture automatique à l'expiration de la preuve —
 * la session tombe alors à null et la garde redirige.
 */
export function RequireOrganizationSession() {
  const { session, closeSession } = useSession();
  const active = isSessionActive(session);

  useEffect(() => {
    if (!session) return;
    const delay = session.expiresAt - Date.now();
    if (delay <= 0) {
      closeSession();
      return;
    }
    const timer = window.setTimeout(closeSession, delay);
    return () => window.clearTimeout(timer);
  }, [session, closeSession]);

  if (!active) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
