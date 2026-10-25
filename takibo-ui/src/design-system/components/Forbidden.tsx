import { ShieldX } from 'lucide-react';
import type { ReactNode } from 'react';

import { EmptyState } from '@/design-system/components/EmptyState';

interface ForbiddenProps {
  title?: string;
  description?: ReactNode;
  action?: ReactNode;
}

/**
 * Accès refusé — la frontière tient. Le frontend n'est jamais la sécurité
 * finale : cet écran explique un refus déjà décidé (ou à décider) côté backend.
 */
export function Forbidden({
  title = 'Accès refusé',
  description = "Votre contexte actuel ne permet pas d'ouvrir cette surface.",
  action,
}: ForbiddenProps) {
  return <EmptyState icon={ShieldX} title={title} description={description} action={action} />;
}
