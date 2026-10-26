/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // Provisoire (récit 01.5) : en dev, /api est relayé vers takibo-iam-boot.
    // Le BFF (récit TAKIBO UI 02) remplacera ce relais.
    proxy: {
      '/api': {
        target: process.env['TAKIBO_API_TARGET'] ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/main.tsx'],
      reporter: ['text-summary', 'lcov', 'cobertura'],
      // Cliquet bloquant en CI : niveau réel mesuré sur tout src/ à l'introduction (fichiers
      // non chargés par les tests compris). À relever avec la couverture, jamais à abaisser
      // pour faire passer une PR. Le nouveau code est tenu à 80 % par le Quality Gate Sonar.
      thresholds: {
        lines: 86,
        statements: 85,
        functions: 84,
        branches: 79,
      },
    },
  },
});
