import { useCallback, useState } from 'react';

import { useNavigate } from 'react-router-dom';

import type {
  CurrentContext,
  SpaceSelectionState,
} from '@/features/context-selector/model/context';
import { useSession } from '@/shared/security/session-context';

/**
 * Couture UNIQUE de la sélection de contexte (récit UI 06A).
 *
 * Le contexte réel reste `ORGANIZATION` dans ce récit. `selectSpace` ne
 * fabrique NI token, NI rôle, NI contexte local : l'échange sécurisé
 * ORGANIZATION → SPACE n'existe pas encore côté backend, et son implémentation
 * actuelle se contente de signaler `unsupported`. La future logique d'échange
 * remplacera le corps de cette fonction — pas les composants qui l'appellent.
 * Aucun token n'est écrit dans localStorage, sessionStorage, l'URL ou un
 * cookie : la preuve vit en mémoire React uniquement (SessionProvider).
 */
export function useContextSelection() {
  const { session } = useSession();
  const navigate = useNavigate();
  const [spaceSelection, setSpaceSelection] = useState<SpaceSelectionState>({ status: 'idle' });

  if (!session) {
    throw new Error('useContextSelection requiert une session ORGANIZATION active.');
  }

  const current: CurrentContext = {
    type: 'ORGANIZATION',
    organizationId: session.organizationId,
  };

  const selectOrganization = useCallback((): void => {
    setSpaceSelection({ status: 'idle' });
    navigate('/app/dashboard');
  }, [navigate]);

  const selectSpace = useCallback(async (spaceId: string): Promise<void> => {
    setSpaceSelection({ status: 'unsupported', spaceId });
  }, []);

  const resetSpaceSelection = useCallback(() => {
    setSpaceSelection({ status: 'idle' });
  }, []);

  return { current, selectOrganization, selectSpace, spaceSelection, resetSpaceSelection };
}
