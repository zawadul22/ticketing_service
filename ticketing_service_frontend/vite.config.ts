import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] })
  ],
  // The browser only ever talks to Vite, so the backend needs no CORS setup
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
