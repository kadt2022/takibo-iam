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
  },
});
