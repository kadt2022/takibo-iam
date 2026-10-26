/** Convention TAKIBO : l'erreur d'un champ `x` porte l'identifiant `x-error`. */
export function fieldErrorId(fieldId: string): string {
  return `${fieldId}-error`;
}
