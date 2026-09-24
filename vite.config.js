import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: 'app/src/main/assets/web',
    emptyOutDir: true,
    target: 'es2020',
    sourcemap: false
  }
});
