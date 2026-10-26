import { Eye, EyeOff } from 'lucide-react';
import { useState } from 'react';
import type { ComponentPropsWithRef } from 'react';

import { Input } from '@/design-system/components/Input';
import { cn } from '@/shared/utilities/cn';

type PasswordInputProps = Omit<ComponentPropsWithRef<'input'>, 'type'>;

/**
 * Champ mot de passe avec bascule afficher/masquer.
 * La valeur saisie est préservée lors de la bascule (AC-08).
 */
export function PasswordInput({ className, ...props }: PasswordInputProps) {
  const [isVisible, setIsVisible] = useState(false);

  return (
    <div className="relative">
      <Input
        type={isVisible ? 'text' : 'password'}
        autoComplete="current-password"
        className={cn('pr-11', className)}
        {...props}
      />
      <button
        type="button"
        onClick={() => setIsVisible((visible) => !visible)}
        aria-label={isVisible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
        aria-pressed={isVisible}
        className={cn(
          'absolute inset-y-0 right-0 flex w-11 items-center justify-center rounded-md',
          'text-text-muted transition-colors duration-150 hover:text-text',
        )}
      >
        {isVisible ? (
          <EyeOff className="size-4.5" aria-hidden="true" />
        ) : (
          <Eye className="size-4.5" aria-hidden="true" />
        )}
      </button>
    </div>
  );
}
