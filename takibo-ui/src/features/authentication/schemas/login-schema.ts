import { z } from 'zod';

/**
 * IAM 31 — l'organisation identifie le compte : trois champs suffisent.
 * Le space n'est pas un élément d'identification, c'est un contexte de
 * travail choisi après connexion.
 */
export const loginSchema = z.object({
  email: z.email('Veuillez saisir une adresse courriel valide.'),
  password: z.string().min(1, 'Veuillez saisir votre mot de passe.'),
  orgCode: z.string().trim().min(1, "Veuillez saisir le code de l'organisation."),
});

export type LoginCredentialsValues = z.infer<typeof loginSchema>;
