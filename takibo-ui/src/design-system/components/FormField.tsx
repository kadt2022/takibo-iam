import type { ReactNode } from 'react';

import { ErrorMessage } from '@/design-system/components/ErrorMessage';
import { Label } from '@/design-system/components/Label';
import { fieldErrorId } from '@/shared/utilities/field-error-id';

interface FormFieldProps {
  /** Identifiant du contrôle associé (htmlFor / aria-describedby). */
  fieldId: string;
  label: ReactNode;
  error?: string;
  /** Contenu additionnel aligné à droite du label (ex. lien d'aide). */
  labelAside?: ReactNode;
  children: ReactNode;
}

export function FormField({ fieldId, label, error, labelAside, children }: FormFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between">
        <Label htmlFor={fieldId}>{label}</Label>
        {labelAside}
      </div>
      {children}
      <ErrorMessage id={fieldErrorId(fieldId)}>{error}</ErrorMessage>
    </div>
  );
}
