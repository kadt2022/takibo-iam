import { ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import { Alert } from '@/design-system/components/Alert';
import { Button } from '@/design-system/components/Button';
import { Card } from '@/design-system/components/Card';
import { FormField } from '@/design-system/components/FormField';
import { Input } from '@/design-system/components/Input';
import { PasswordInput } from '@/design-system/components/PasswordInput';
import { LoginError } from '@/features/authentication/api/login-api';
import { useLogin } from '@/features/authentication/hooks/use-login';
import { loginSchema } from '@/features/authentication/schemas/login-schema';
import {
  buildOrganizationSession,
  SessionRejectedError,
} from '@/shared/security/organization-session';
import { useSession } from '@/shared/security/session-context';
import { fieldErrorId } from '@/shared/utilities/field-error-id';

type FieldKey = 'orgCode' | 'email' | 'password';
type FieldErrors = Partial<Record<FieldKey, string>>;

/**
 * Écran de connexion — récit UI 02 : connexion réelle et session organisationnelle.
 * Trois champs (orgCode, email, password), jamais de spaceCode : l'organisation
 * identifie le compte. La preuve reçue n'est acceptée que si elle est de portée
 * HUMAN / ORGANIZATION ; la session vit en mémoire uniquement.
 */
export function LoginScreen() {
  const navigate = useNavigate();
  const { openSession } = useSession();
  const login = useLogin();

  const [orgCode, setOrgCode] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (login.isPending) return; // bloque les soumissions multiples

    setFormError(null);
    const parsed = loginSchema.safeParse({ orgCode, email, password });
    if (!parsed.success) {
      const next: FieldErrors = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as FieldKey;
        if (key && !next[key]) next[key] = issue.message;
      }
      setFieldErrors(next);
      return;
    }
    setFieldErrors({});

    const origin = { orgCode: parsed.data.orgCode, email: parsed.data.email };
    try {
      // Corps strict : orgCode + email + password. Jamais spaceCode.
      const response = await login.mutateAsync({
        orgCode: parsed.data.orgCode,
        email: parsed.data.email,
        password: parsed.data.password,
      });
      const session = buildOrganizationSession(response, origin);
      openSession(session);
      navigate('/app/dashboard', { replace: true });
    } catch (error) {
      if (error instanceof LoginError || error instanceof SessionRejectedError) {
        setFormError(error.message);
      } else {
        setFormError('Connexion impossible pour le moment. Veuillez réessayer.');
      }
    }
  };

  return (
    <div className="w-full max-w-md">
      <Card className="px-6 py-8 sm:px-10 sm:py-10">
        <header className="mb-6">
          <h1 className="text-2xl font-bold text-text">Bienvenue dans TAKIBO</h1>
          <p className="mt-2 text-sm text-text-muted">
            Connectez-vous à votre organisation pour accéder à la console.
          </p>
        </header>

        {formError && (
          <Alert variant="danger" className="mb-6">
            {formError}
          </Alert>
        )}

        <form onSubmit={submit} className="flex flex-col gap-1" noValidate>
          <FormField fieldId="orgCode" label="Organisation" error={fieldErrors.orgCode}>
            <Input
              id="orgCode"
              type="text"
              autoComplete="organization"
              value={orgCode}
              onChange={(event) => setOrgCode(event.target.value)}
              placeholder="Exemple : acme"
              aria-invalid={fieldErrors.orgCode ? true : undefined}
              aria-describedby={fieldErrors.orgCode ? fieldErrorId('orgCode') : undefined}
            />
          </FormField>
          <FormField fieldId="email" label="Adresse courriel" error={fieldErrors.email}>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="prenom.nom@organisation.com"
              aria-invalid={fieldErrors.email ? true : undefined}
              aria-describedby={fieldErrors.email ? fieldErrorId('email') : undefined}
            />
          </FormField>
          <FormField fieldId="password" label="Mot de passe" error={fieldErrors.password}>
            <PasswordInput
              id="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••••••"
              aria-invalid={fieldErrors.password ? true : undefined}
              aria-describedby={fieldErrors.password ? fieldErrorId('password') : undefined}
            />
          </FormField>

          <Button
            type="submit"
            className="mt-2 w-full"
            isLoading={login.isPending}
            loadingLabel="Connexion…"
          >
            Se connecter
          </Button>

          <p className="mt-6 flex items-center justify-center gap-2 text-xs text-text-muted">
            <ShieldCheck className="size-3.5 shrink-0" aria-hidden="true" />
            Connexion protégée. Vos identifiants ne sont jamais conservés par ce navigateur.
          </p>
        </form>
      </Card>
    </div>
  );
}
