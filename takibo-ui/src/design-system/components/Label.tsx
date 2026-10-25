import * as RadixLabel from '@radix-ui/react-label';
import type { ComponentPropsWithRef } from 'react';

import { cn } from '@/shared/utilities/cn';

type LabelProps = ComponentPropsWithRef<typeof RadixLabel.Root>;

export function Label({ className, ...props }: LabelProps) {
  return <RadixLabel.Root className={cn('text-sm font-medium text-text', className)} {...props} />;
}
