/**
 * Concatène des classes CSS en ignorant les valeurs falsy.
 * Suffisant pour TAKIBO UI sans dépendance supplémentaire.
 */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}
