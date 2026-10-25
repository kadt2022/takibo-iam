import { Compass } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

import { Button } from '@/design-system/components/Button';
import { EmptyState } from '@/design-system/components/EmptyState';

/** Route inconnue. Ramène l'utilisateur vers une surface sûre. */
export function NotFound() {
  const navigate = useNavigate();
  return (
    <div className="grid min-h-dvh place-items-center bg-background px-6">
      <EmptyState
        icon={Compass}
        title="Page introuvable"
        description="Cette adresse ne correspond à aucune surface de TAKIBO."
        action={
          <Button onClick={() => navigate('/app/dashboard')}>Retour au tableau de bord</Button>
        }
      />
    </div>
  );
}
