import { useCallback, useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';

import { ContextSelectorMenu } from '@/features/context-selector/components/ContextSelectorMenu';
import { ContextSelectorTrigger } from '@/features/context-selector/components/ContextSelectorTrigger';
import { useAccessibleSpaces } from '@/features/context-selector/hooks/use-accessible-spaces';
import { useContextSelection } from '@/features/context-selector/hooks/use-context-selection';
import { useIdentity } from '@/shared/identity/useIdentity';

/**
 * Sélecteur de contexte Organisation / Mes Spaces (récit UI 06A), inspiré du
 * sélecteur de repository GitHub. Les Spaces viennent de GET /api/v1/me/spaces ;
 * l'Organisation reste le seul contexte réellement actif — choisir un Space
 * passe par la couture unique `selectSpace`, qui signale `unsupported` tant que
 * l'échange sécurisé ORGANIZATION → SPACE n'existe pas côté backend.
 *
 * Clavier : flèches pour circuler entre les options, Escape ferme et restaure
 * le focus sur le bouton. Les options désactivées restent focusables et lues
 * par les technologies d'assistance (aria-disabled).
 */
export function ContextSelector({ collapsed = false }: { collapsed?: boolean }) {
  const { orgCode, organizationId } = useIdentity();
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const { selectSpace, spaceSelection, resetSpaceSelection } = useContextSelection();
  // Chargé seulement quand le menu est ouvert : le sélecteur n'interroge pas
  // /me/spaces en arrière-plan à chaque rendu du shell.
  const spaces = useAccessibleSpaces({ enabled: open });

  const close = useCallback(
    (restoreFocus: boolean) => {
      setOpen(false);
      resetSpaceSelection();
      if (restoreFocus) {
        triggerRef.current?.focus();
      }
    },
    [resetSpaceSelection],
  );

  // Fermeture au clic extérieur — sans voler le focus.
  useEffect(() => {
    if (!open) {
      return;
    }
    function onPointerDown(event: PointerEvent) {
      const target = event.target as Node;
      if (!menuRef.current?.contains(target) && !triggerRef.current?.contains(target)) {
        close(false);
      }
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open, close]);

  // À l'ouverture : focus sur la recherche (visible immédiatement, façon
  // GitHub) — à défaut la première option, à défaut le panneau.
  useEffect(() => {
    if (!open) {
      return;
    }
    const search = menuRef.current?.querySelector<HTMLElement>('input');
    const first = menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]');
    (search ?? first ?? menuRef.current)?.focus();
  }, [open]);

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape' && open) {
      event.stopPropagation();
      close(true);
      return;
    }
    if (!open || !['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
      return;
    }
    // Dans le champ de recherche, Home/End gardent leur sens texte ; seules les
    // flèches basculent vers la liste d'options.
    if (
      document.activeElement instanceof HTMLInputElement &&
      (event.key === 'Home' || event.key === 'End')
    ) {
      return;
    }
    const items = Array.from(
      menuRef.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [],
    );
    if (items.length === 0) {
      return;
    }
    event.preventDefault();
    const activeIndex = items.findIndex((item) => item === document.activeElement);
    let next = 0;
    if (event.key === 'ArrowDown') {
      next = activeIndex < 0 ? 0 : (activeIndex + 1) % items.length;
    } else if (event.key === 'ArrowUp') {
      next = activeIndex <= 0 ? items.length - 1 : activeIndex - 1;
    } else if (event.key === 'End') {
      next = items.length - 1;
    }
    items[next]?.focus();
  }

  return (
    <div className="relative" onKeyDown={onKeyDown}>
      <ContextSelectorTrigger
        orgCode={orgCode}
        organizationId={organizationId}
        open={open}
        collapsed={collapsed}
        onToggle={() => (open ? close(true) : setOpen(true))}
        buttonRef={triggerRef}
      />
      {open && (
        <ContextSelectorMenu
          spaces={spaces}
          spaceSelection={spaceSelection}
          onSelectSpace={(spaceId) => void selectSpace(spaceId)}
          menuRef={menuRef}
        />
      )}
    </div>
  );
}
