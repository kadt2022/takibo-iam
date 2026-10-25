/*
 * Données de DÉMONSTRATION du shell TAKIBO (récits UI 01+).
 * ---------------------------------------------------------
 * Les métriques et listes du tableau de bord viennent d'ici et sont présentées
 * comme démonstration (badge « Démonstration » sur chaque surface concernée).
 * Le login et la session sont désormais réels (récit UI 02) ; la vraie liste des
 * spaces arrive au récit UI 03. Ces sources remplaceront cet adapter, pas le shell.
 */

import type { SpaceStatus } from '@/features/spaces/model/space';

/** Palette catégorielle des graphiques (distincte des tokens sémantiques). */
export const chartPalette = {
  blue: '#3b82f6',
  green: '#22c55e',
  amber: '#f59e0b',
  red: '#ef4444',
  sky: '#38bdf8',
  violet: '#8b5cf6',
} as const;

export interface DemoOrganizationSpace {
  id: string;
  code: string;
  name: string;
  status: SpaceStatus;
  users: number;
}

export interface DemoAccessibleSpace extends DemoOrganizationSpace {
  membershipStatus: 'ACTIVE' | 'SUSPENDED';
  role: string;
  selectable: boolean;
}

/**
 * Future source GET /api/v1/me/spaces : uniquement les spaces accessibles au
 * compte courant, avec appartenance et possibilite d'ouverture de contexte.
 */
export const demoAccessibleSpaces: DemoAccessibleSpace[] = [
  {
    id: 's-finance',
    code: 'finance',
    name: 'Finance',
    status: 'ACTIVE',
    users: 128,
    membershipStatus: 'ACTIVE',
    role: 'Space Admin',
    selectable: true,
  },
  {
    id: 's-marketing',
    code: 'marketing',
    name: 'Marketing',
    status: 'ACTIVE',
    users: 87,
    membershipStatus: 'ACTIVE',
    role: 'Space Admin',
    selectable: true,
  },
  {
    id: 's-rnd',
    code: 'rnd',
    name: 'R&D',
    status: 'ACTIVE',
    users: 42,
    membershipStatus: 'ACTIVE',
    role: 'Membre',
    selectable: true,
  },
  {
    id: 's-support',
    code: 'support',
    name: 'Support',
    status: 'SUSPENDED',
    users: 22,
    membershipStatus: 'ACTIVE',
    role: 'Membre',
    selectable: false,
  },
];

/**
 * Future source GET /api/v1/orgs/{orgId}/spaces : catalogue organisationnel,
 * independant de l'appartenance du compte courant.
 */
export const demoOrganizationSpaces: DemoOrganizationSpace[] = [
  { id: 's-finance', code: 'finance', name: 'Finance', status: 'ACTIVE', users: 128 },
  { id: 's-marketing', code: 'marketing', name: 'Marketing', status: 'ACTIVE', users: 87 },
  { id: 's-rnd', code: 'rnd', name: 'R&D', status: 'ACTIVE', users: 42 },
  { id: 's-support', code: 'support', name: 'Support', status: 'SUSPENDED', users: 22 },
  { id: 's-legal', code: 'legal', name: 'Juridique', status: 'ACTIVE', users: 19 },
  { id: 's-ops', code: 'ops', name: 'Operations', status: 'ACTIVE', users: 31 },
  { id: 's-archive', code: 'archive', name: 'Archives', status: 'CREATING', users: 0 },
];

/** Courbe d'activité sur 7 jours (3 séries). */
export interface DemoSeries {
  name: string;
  color: string;
  points: number[];
}

export const demoActivity: { labels: string[]; maxY: number; series: DemoSeries[] } = {
  labels: ['13 mai', '14 mai', '15 mai', '16 mai', '17 mai', '18 mai', '19 mai'],
  maxY: 500,
  series: [
    { name: 'Connexions', color: chartPalette.blue, points: [305, 325, 395, 415, 375, 405, 430] },
    {
      name: 'Actions sensibles',
      color: chartPalette.amber,
      points: [180, 165, 180, 175, 205, 195, 235],
    },
    {
      name: "Échecs d'authentification",
      color: chartPalette.green,
      points: [85, 70, 110, 100, 90, 110, 120],
    },
  ],
};

export interface DemoSegment {
  label: string;
  value: number;
  pct: number;
  color: string;
}

/** Répartition des utilisateurs par rôle (total 342). */
export const demoRoleDistribution: DemoSegment[] = [
  { label: 'Org Admins', value: 62, pct: 18, color: chartPalette.blue },
  { label: 'Space Admins', value: 96, pct: 28, color: chartPalette.violet },
  { label: 'Utilisateurs', value: 133, pct: 39, color: chartPalette.green },
  { label: 'Invités', value: 27, pct: 8, color: chartPalette.red },
  { label: 'Autres', value: 24, pct: 7, color: chartPalette.sky },
];

/** Statut des spaces (total 7). */
export const demoSpaceStatusDistribution: DemoSegment[] = [
  { label: 'Actifs', value: 5, pct: 71, color: chartPalette.green },
  { label: 'Suspendus', value: 1, pct: 14, color: chartPalette.amber },
  { label: 'En création', value: 1, pct: 14, color: chartPalette.blue },
  { label: 'Désactivés', value: 0, pct: 0, color: chartPalette.red },
];

export type ActivitySeverity = 'success' | 'warning' | 'error';

export interface DemoActivityItem {
  id: string;
  icon: 'user-plus' | 'shield' | 'key' | 'alert' | 'trash';
  title: string;
  detail: string;
  time: string;
  severity: ActivitySeverity;
}

export const demoRecentActivities: DemoActivityItem[] = [
  {
    id: 'a1',
    icon: 'user-plus',
    title: 'Nouvel utilisateur créé',
    detail: 'maria.dubois@acme.com dans Marketing',
    time: 'Il y a 2 min',
    severity: 'success',
  },
  {
    id: 'a2',
    icon: 'shield',
    title: 'Rôle assigné',
    detail: 'R_SPACE_ADMIN assigné à paul.martin@acme.com',
    time: 'Il y a 8 min',
    severity: 'success',
  },
  {
    id: 'a3',
    icon: 'key',
    title: 'Nouveau client OAuth2',
    detail: 'Application « Acme Mobile » créée',
    time: 'Il y a 15 min',
    severity: 'success',
  },
  {
    id: 'a4',
    icon: 'alert',
    title: "Échec d'authentification",
    detail: 'Tentative échouée pour john.doe@acme.com',
    time: 'Il y a 18 min',
    severity: 'warning',
  },
  {
    id: 'a5',
    icon: 'trash',
    title: 'Action sensible : suppression de groupe',
    detail: 'Groupe « Legacy Users » supprimé par jane.smith@acme.com',
    time: 'Il y a 45 min',
    severity: 'error',
  },
];

export type NotificationSeverity = 'high' | 'medium' | 'low';

export interface DemoNotification {
  id: string;
  title: string;
  time: string;
  severity: NotificationSeverity;
}

export const demoNotifications: DemoNotification[] = [
  {
    id: 'n1',
    title: '5 échecs d’authentification détectés',
    time: 'Il y a 3 min',
    severity: 'high',
  },
  { id: 'n2', title: 'Rôles expirant dans 7 jours', time: 'Il y a 1 h', severity: 'medium' },
  { id: 'n3', title: 'Space « R&D » en cours de création', time: 'Il y a 2 h', severity: 'low' },
];
