import { defineConfig, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';

// El frontend se sirve detrás del proxy en producción; en desarrollo apunta al backend local.
// `proxy` lleva su tipo explícito: Vite 7 no acepta el objeto plano sin comprobación.
const proxy: Record<string, string | ProxyOptions> = {
  '/api': { target: process.env.API_URL ?? 'http://localhost:8080', changeOrigin: false },
};

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy,
  },
  build: { outDir: 'dist', sourcemap: false },
});