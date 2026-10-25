import { SlidersHorizontal } from 'lucide-react';

import { EmptyState } from '@/design-system/components/EmptyState';
import { useIdentity } from '@/shared/identity/useIdentity';

/**
 * « Paramètres de l'organisation » (récit UI 01 — structure sans contenu).
 * La surface existe et est navigable ; son câblage arrivera dans un récit
 * ultérieur, une fois les endpoints de paramètres d'organisation disponibles.
 * L'en-tête situe l'organisation RÉELLE de la session (orgCode du login),
 * jamais un nom commercial fictif.
 */
export function OrgSettingsPage() {
  const { orgCode } = useIdentity();

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-bold text-text">Paramètres de l’organisation</h1>
        <p className="mt-1 text-sm text-text-muted">Configuration de {orgCode}.</p>
      </header>

      <EmptyState
        icon={SlidersHorizontal}
        title="Bientôt"
        description="Les paramètres de l’organisation seront branchés dans un récit ultérieur, lorsque leur surface backend sera disponible."
      />
    </div>
  );
}
