import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Sans les globals Vitest, Testing Library n'enregistre pas son cleanup
// automatique : on le fait explicitement.
afterEach(() => {
  cleanup();
});
