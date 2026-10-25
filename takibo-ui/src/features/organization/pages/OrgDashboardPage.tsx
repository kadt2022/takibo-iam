import {
  ArrowRight,
  ChevronDown,
  ChevronRight,
  KeyRound,
  Layers,
  Plus,
  ScrollText,
  ShieldCheck,
  Trash2,
  TriangleAlert,
  UserPlus,
  Users,
} from 'lucide-react';
import type { ComponentType, ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { Card } from '@/design-system/components/Card';
import { ApiForbiddenError } from '@/shared/api/http';
import { DemoTag } from '@/design-system/components/DemoTag';
import { ActivityChart } from '@/features/organization/components/ActivityChart';
import { DonutChart } from '@/features/organization/components/DonutChart';
import { SpaceStatusPill } from '@/features/organization/components/SpaceStatusPill';
import {
  demoActivity,
  demoAccessibleSpaces,
  demoNotifications,
  demoRecentActivities,
  demoRoleDistribution,
  demoSpaceStatusDistribution,
} from '@/shared/demo/demo';
import type { ActivitySeverity, NotificationSeverity } from '@/shared/demo/demo';
import { useOrgDashboardSummary } from '@/features/dashboard/hooks/use-org-dashboard-summary';
import { useOrganizationSpaces } from '@/features/spaces/hooks/use-organization-spaces';
import { isOrgAdmin } from '@/shared/identity/roles';
import { useIdentity } from '@/shared/identity/useIdentity';
import { cn } from '@/shared/utilities/cn';

type IconType = ComponentType<{ className?: string; 'aria-hidden'?: boolean | 'true' | 'false' }>;

const activityIcons: Record<string, IconType> = {
  'user-plus': UserPlus,
  shield: ShieldCheck,
  key: KeyRound,
  alert: TriangleAlert,
  trash: Trash2,
};

const severityBadge: Record<ActivitySeverity, { label: string; className: string }> = {
  success: { label: 'Succès', className: 'border-success/40 bg-success/10 text-success' },
  warning: { label: 'Avertissement', className: 'border-warning/40 bg-warning/10 text-warning' },
  error: { label: 'Erreur', className: 'border-danger/40 bg-danger/10 text-danger' },
};

const notificationDot: Record<NotificationSeverity, string> = {
  high: 'bg-danger',
  medium: 'bg-warning',
  low: 'bg-primary',
};

function PanelHeader({
  title,
  action,
  demo = false,
}: {
  title: string;
  action?: ReactNode;
  demo?: boolean;
}) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <div className="flex min-w-0 items-center gap-2">
        <h2 className="text-base font-semibold text-text">{title}</h2>
        {demo && <DemoTag />}
      </div>
      {action}
    </div>
  );
}

/**
 * Cartouche d'un compteur RÉEL de TAKIBO (aucun badge « Démonstration », aucune
 * tendance inventée). Un tiret honnête quand la valeur n'est pas disponible.
 * Le tableau de bord résume, le clic explique : chaque carte ouvre l'écran
 * de détail correspondant.
 */
function RealKpiTile({
  icon: Icon,
  label,
  hint,
  value,
  loading,
  to,
}: {
  icon: IconType;
  label: string;
  hint: string;
  value: number | undefined;
  loading: boolean;
  to: string;
}) {
  return (
    <Link
      to={to}
      className="block h-full rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <Card className="flex h-full items-start gap-4 p-5 transition-colors duration-150 hover:border-primary/50 hover:bg-primary/5">
        <span className="grid size-12 shrink-0 place-items-center rounded-xl bg-primary/12 text-primary">
          <Icon className="size-6" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <p className="text-sm text-text-muted">{label}</p>
          <p className="mt-0.5 font-mono text-2xl font-bold tabular-nums text-text">
            {loading ? '…' : (value ?? '—')}
          </p>
          <p className="mt-0.5 text-xs text-text-muted">{hint}</p>
        </div>
      </Card>
    </Link>
  );
}

/** Compteur d'attente explicite : valeur locale, aucun appel ni navigation. */
function PlaceholderKpiTile({ icon: Icon, label }: { icon: IconType; label: string }) {
  return (
    <Card className="flex h-full items-start gap-4 p-5">
      <span className="grid size-12 shrink-0 place-items-center rounded-xl bg-primary/12 text-primary">
        <Icon className="size-6" aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <p className="text-sm text-text-muted">{label}</p>
        <p className="mt-0.5 font-mono text-2xl font-bold tabular-nums text-text">0</p>
        <p className="mt-0.5 text-xs text-text-muted">bientôt disponible</p>
      </div>
    </Card>
  );
}

function QuickAction({
  icon: Icon,
  label,
  description,
  onClick,
  disabled = false,
}: {
  icon: IconType;
  label: string;
  description: string;
  onClick?: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="flex w-full items-center gap-3 rounded-lg border border-border px-3.5 py-3 text-left transition-colors duration-150 hover:border-primary/50 hover:bg-primary/5 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:border-border disabled:hover:bg-transparent"
    >
      <span className="grid size-9 shrink-0 place-items-center rounded-md bg-primary/12 text-primary">
        <Icon className="size-[18px]" aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-text">{label}</span>
        <span className="block truncate text-xs text-text-muted">{description}</span>
      </span>
      <ArrowRight className="size-4 shrink-0 text-text-muted" aria-hidden="true" />
    </button>
  );
}

export function OrgDashboardPage() {
  const { roles } = useIdentity();
  const admin = isOrgAdmin(roles);
  // Chaque compteur réel a SA source, pour que les cartes se dégradent
  // indépendamment : si une surface backend est absente, elle seule affiche « — ».
  //  - users / clients OAuth2 : read-side dashboard (Dashboard 01 et 02) ;
  //  - spaces : inventaire administratif, déjà en place depuis UI 03.
  const summary = useOrgDashboardSummary({ enabled: admin });
  const orgSpaces = useOrganizationSpaces({ page: 0, size: 1 }, { enabled: admin });
  const navigate = useNavigate();
  // Si le backend refuse le résumé (403) malgré un rôle R_ORG_* décodé, la
  // frontière fait autorité : la surface se masque, elle n'affiche pas des « — ».
  const summaryForbidden = summary.isError && summary.error instanceof ApiForbiddenError;

  return (
    <div className="flex flex-col gap-6">
      {/* Pas d'en-tête de bienvenue : l'identité et le contexte sont déjà
          portés par la TopBar et la Sidebar — le dashboard va droit aux
          indicateurs. */}
      {/* Indicateurs d'autorité ORG : trois compteurs réels et deux emplacements
          statiques non branchés pour Rôles et Groupes. */}
      {admin && !summaryForbidden && (
        <section className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-text-muted">
            Indicateurs réels
          </h2>
          {/* Les trois compteurs réels ouvrent leur détail. Rôles et Groupes
              restent volontairement non cliquables tant que leurs sources
              backend ne sont pas disponibles. */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <RealKpiTile
              icon={Users}
              label="Utilisateurs"
              hint="comptes distincts de l’organisation"
              value={summary.data?.usersTotal}
              loading={summary.isPending}
              to="/app/organization/users"
            />
            <RealKpiTile
              icon={Layers}
              label="Spaces"
              hint="dans l’organisation"
              value={orgSpaces.data?.totalElements}
              loading={orgSpaces.isPending}
              to="/app/spaces"
            />
            <RealKpiTile
              icon={KeyRound}
              label="Clients OAuth2"
              hint="dans l’organisation"
              value={summary.data?.oauthClientsTotal}
              loading={summary.isPending}
              to="/app/organization/clients"
            />
            <PlaceholderKpiTile icon={ShieldCheck} label="Rôles" />
            <PlaceholderKpiTile icon={Users} label="Groupes" />
          </div>
        </section>
      )}

      {/* Graphiques */}
      <section className="grid grid-cols-1 items-start gap-5 xl:grid-cols-[1.5fr_1fr_1fr]">
        <Card className="p-5">
          <PanelHeader
            title="Activité de l’organisation"
            demo
            action={
              <span className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1 text-xs text-text-muted">
                7 derniers jours
                <ChevronDown className="size-3.5" aria-hidden="true" />
              </span>
            }
          />
          <ActivityChart
            labels={demoActivity.labels}
            series={demoActivity.series}
            maxY={demoActivity.maxY}
          />
        </Card>

        <Card className="p-4">
          <PanelHeader title="Répartition des utilisateurs par rôle" demo />
          <DonutChart
            segments={demoRoleDistribution}
            centerValue={342}
            centerLabel="Total"
            size={128}
          />
        </Card>

        <Card className="p-4">
          <PanelHeader title="Statut des Spaces" demo />
          <DonutChart
            segments={demoSpaceStatusDistribution}
            centerValue={7}
            centerLabel="Total"
            size={128}
          />
        </Card>
      </section>

      {/* Rangée basse */}
      <section className="grid grid-cols-1 gap-5 xl:grid-cols-3">
        {/* Mes Spaces accessibles */}
        <Card className="flex flex-col p-5">
          <PanelHeader title="Mes Spaces accessibles" demo />
          <div className="-mx-2 overflow-x-auto">
            <table className="w-full min-w-[380px] text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-text-muted">
                  <th className="px-2 pb-2 font-medium">Space</th>
                  <th className="px-2 pb-2 font-medium">Statut</th>
                  <th className="px-2 pb-2 font-medium">Rôle</th>
                  <th className="px-2 pb-2 text-right font-medium">Users</th>
                  <th className="px-2 pb-2" />
                </tr>
              </thead>
              <tbody>
                {demoAccessibleSpaces.map((space) => (
                  <tr key={space.id} className="border-t border-border">
                    <td className="px-2 py-3">
                      <span className="flex items-center gap-2.5">
                        <span className="grid size-8 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
                          <Layers className="size-4" aria-hidden="true" />
                        </span>
                        <span className="min-w-0">
                          <span className="block truncate font-medium text-text">{space.name}</span>
                          <span className="block truncate text-xs text-text-muted">
                            {space.code}
                          </span>
                        </span>
                      </span>
                    </td>
                    <td className="px-2 py-3">
                      <SpaceStatusPill status={space.status} />
                    </td>
                    <td className="px-2 py-3">
                      <span
                        className={cn(
                          'text-xs font-medium',
                          space.role === 'Space Admin' ? 'text-primary' : 'text-text-muted',
                        )}
                      >
                        {space.role}
                      </span>
                    </td>
                    <td className="px-2 py-3 text-right font-mono tabular-nums text-text-muted">
                      {space.users}
                    </td>
                    <td className="px-2 py-3 text-right">
                      <ChevronRight className="ml-auto size-4 text-text-muted" aria-hidden="true" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Link
            to="/app/my-spaces"
            className="mt-3 inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"
          >
            Voir tous mes Spaces
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </Card>

        {/* Activités récentes */}
        <Card className="p-5">
          <PanelHeader
            title="Activités récentes"
            demo
            action={
              <button type="button" className="text-xs font-medium text-primary hover:underline">
                Voir tout
              </button>
            }
          />
          <ul className="flex flex-col divide-y divide-border">
            {demoRecentActivities.map((item) => {
              const Icon = activityIcons[item.icon] ?? ShieldCheck;
              const badge = severityBadge[item.severity];
              return (
                <li key={item.id} className="flex items-start gap-3 py-3">
                  <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-md border border-border bg-background/40 text-text-muted">
                    <Icon className="size-4" aria-hidden="true" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center justify-between gap-2">
                      <span className="truncate text-sm font-medium text-text">{item.title}</span>
                      <span
                        className={cn(
                          'shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-medium',
                          badge.className,
                        )}
                      >
                        {badge.label}
                      </span>
                    </span>
                    <span className="mt-0.5 flex items-center justify-between gap-2">
                      <span className="truncate text-xs text-text-muted">{item.detail}</span>
                      <span className="shrink-0 text-[11px] text-text-muted">{item.time}</span>
                    </span>
                  </span>
                </li>
              );
            })}
          </ul>
        </Card>

        {/* Accès rapides + Notifications */}
        <div className="flex flex-col gap-5">
          <Card className="p-5">
            <PanelHeader title="Accès rapides" />
            <div className="flex flex-col gap-2.5">
              <QuickAction
                icon={Plus}
                label="Créer un Space"
                description="Ajouter un nouvel espace à l’organisation"
                disabled
              />
              <QuickAction
                icon={UserPlus}
                label="Inviter un utilisateur"
                description="Inviter dans un espace de l’organisation"
                onClick={() => navigate('/app/invitations')}
              />
              <QuickAction
                icon={ShieldCheck}
                label="Gérer les rôles"
                description="Rôles et autorités de l’organisation"
                onClick={() => navigate('/app/roles')}
              />
              <QuickAction
                icon={ScrollText}
                label="Voir l’audit"
                description="Journaux d’activité de l’organisation"
                onClick={() => navigate('/app/audit')}
              />
            </div>
          </Card>

          <Card className="p-5">
            <PanelHeader
              title="Notifications"
              demo
              action={
                <button type="button" className="text-xs font-medium text-primary hover:underline">
                  Voir tout
                </button>
              }
            />
            <ul className="flex flex-col divide-y divide-border">
              {demoNotifications.map((notif) => (
                <li key={notif.id} className="flex items-center gap-3 py-2.5">
                  <span
                    className={cn('size-2 shrink-0 rounded-full', notificationDot[notif.severity])}
                  />
                  <span className="min-w-0 flex-1 truncate text-sm text-text">{notif.title}</span>
                  <span className="shrink-0 text-[11px] text-text-muted">{notif.time}</span>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </section>

      <p className="text-center text-xs text-text-muted">
        Votre identité et votre session sont réelles. Les indicateurs, graphiques et listes marqués
        « Démonstration » ne sont pas encore issus de TAKIBO — ils arrivent aux récits UI 03 et
        suivants.
      </p>
    </div>
  );
}
