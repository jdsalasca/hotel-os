import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// El frontend se sirve detrás del proxy en producción; en desarrollo apunta al backend local.
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      '/api': { target: process.env.API_URL ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: { outDir: 'dist', sourcemap: false },
  css: { preprocessorOptions: { scss: { api: 'modern-compiler' } } },
});