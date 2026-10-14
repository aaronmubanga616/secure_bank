import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    dedupe: ['react', 'react-dom', '@tanstack/react-query', 'react-router', 'react-router-dom'],
  },
  server: { proxy: { '/api': 'http://localhost:8180', '/actuator': 'http://localhost:8180' } },
});
