import { useState } from 'react';
import { Outlet } from 'react-router-dom';

import { Sidebar } from '@/layouts/AppShell/Sidebar';
import { TopBar } from '@/layouts/AppShell/TopBar';
import { cn } from '@/shared/utilities/cn';

/**
 * Coquille applicative de TAKIBO (récit UI 01).
 * Deux registres de navigation : un rail repliable sur desktop (icônes seules
 * une fois réduit) et un tiroir hors-champ sur tablette/mobile. Le contenu des
 * surfaces arrive par l'Outlet des routes /app/*.
 */
export function AppShell() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="min-h-dvh bg-background text-text lg:flex">
      {/* Tiroir mobile */}
      {mobileOpen && (
        <button
          type="button"
          aria-label="Fermer le menu"
          onClick={() => setMobileOpen(false)}
          className="fixed inset-0 z-30 bg-black/50 lg:hidden"
        />
      )}

      {/* z-40 conservé aussi sur desktop (flex item) : le popover du sélecteur
          de contexte doit peindre AU-DESSUS des cartes translucides du contenu. */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 border-r border-border transition-[transform,width] duration-200',
          'w-[260px]',
          'lg:static lg:shrink-0 lg:translate-x-0',
          collapsed ? 'lg:w-[76px]' : 'lg:w-[260px]',
          mobileOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
        )}
      >
        <div className="h-dvh lg:sticky lg:top-0">
          <Sidebar
            collapsed={collapsed}
            onToggleCollapsed={() => setCollapsed((value) => !value)}
            onNavigate={() => setMobileOpen(false)}
          />
        </div>
      </aside>

      <div className="flex min-h-dvh min-w-0 flex-1 flex-col">
        <TopBar onOpenMenu={() => setMobileOpen(true)} />
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-7 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
