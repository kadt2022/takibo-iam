import { PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { NavLink } from 'react-router-dom';

import { Logo } from '@/design-system/components/Logo';
import { ContextSelector } from '@/features/context-selector/components/ContextSelector';
import { organizationNav } from '@/layouts/AppShell/menu';
import { isOrgAdmin } from '@/shared/identity/roles';
import { useIdentity } from '@/shared/identity/useIdentity';
import { cn } from '@/shared/utilities/cn';

interface SidebarProps {
  collapsed: boolean;
  onToggleCollapsed: () => void;
  onNavigate?: () => void;
}

export function Sidebar({ collapsed, onToggleCollapsed, onNavigate }: SidebarProps) {
  const { email, avatarInitial, roleLabel, roles } = useIdentity();
  const visibleNav = organizationNav.filter((item) => !item.orgAdminOnly || isOrgAdmin(roles));

  return (
    <div className="flex h-full flex-col gap-5 bg-surface p-3">
      {/* Marque */}
      <div
        className={cn(
          'flex items-center gap-2 px-2 pt-1',
          collapsed && 'lg:justify-center lg:gap-0',
        )}
      >
        {collapsed ? (
          <>
            <Logo size={30} withWordmark className="lg:hidden" />
            <Logo size={30} withWordmark={false} className="hidden lg:inline-flex" />
          </>
        ) : (
          <Logo size={30} withWordmark />
        )}
      </div>

      {/* Sélecteur de contexte Organisation / Mes Spaces (récit UI 06A) —
          l'ancienne carte Organisation, devenue déclencheur façon GitHub. */}
      <ContextSelector collapsed={collapsed} />

      {/* Navigation du contexte ORGANISATION */}
      <nav className="flex flex-1 flex-col gap-1">
        <span
          className={cn(
            'px-2 pb-1 text-[10px] uppercase tracking-[0.16em] text-text-muted',
            collapsed && 'lg:hidden',
          )}
        >
          Organisation
        </span>
        {visibleNav.map(({ label, to, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onNavigate}
            title={collapsed ? label : undefined}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-md px-2.5 py-2 text-sm transition-colors duration-150',
                collapsed && 'lg:justify-center',
                isActive
                  ? 'bg-primary/12 font-medium text-primary'
                  : 'text-text-muted hover:bg-background/60 hover:text-text',
              )
            }
          >
            <Icon className="size-[18px] shrink-0" aria-hidden="true" />
            <span className={cn('truncate', collapsed && 'lg:hidden')}>{label}</span>
          </NavLink>
        ))}
      </nav>

      {/* Pied : identité + repli */}
      <div className="flex flex-col gap-1 border-t border-border pt-3">
        <div
          className={cn(
            'flex items-center gap-3 rounded-md px-2 py-1.5',
            collapsed && 'lg:justify-center',
          )}
        >
          <span className="grid size-8 shrink-0 place-items-center rounded-full bg-primary text-xs font-semibold text-on-primary">
            {avatarInitial}
          </span>
          <span className={cn('min-w-0 leading-tight', collapsed && 'lg:hidden')}>
            <span className="block truncate text-sm font-medium text-text">{email}</span>
            <span className="block truncate text-xs text-text-muted">{roleLabel}</span>
          </span>
        </div>
        <button
          type="button"
          onClick={onToggleCollapsed}
          className={cn(
            'hidden items-center gap-3 rounded-md px-2.5 py-2 text-sm text-text-muted transition-colors duration-150 hover:bg-background/60 hover:text-text lg:flex',
            collapsed && 'justify-center',
          )}
          aria-label={collapsed ? 'Déployer le menu' : 'Réduire le menu'}
        >
          {collapsed ? (
            <PanelLeftOpen className="size-[18px]" aria-hidden="true" />
          ) : (
            <PanelLeftClose className="size-[18px]" aria-hidden="true" />
          )}
          {!collapsed && <span>Réduire le menu</span>}
        </button>
      </div>
    </div>
  );
}
