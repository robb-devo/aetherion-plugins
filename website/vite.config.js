import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Served from the domain root by nginx on the Hetzner host (SPA fallback to index.html).
// In dev, /api is proxied to a Control API; default is a local tunnel on :5056.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: '/',
  server: {
    proxy: {
      '/api': {
        target: process.env.AETHERION_API || 'http://127.0.0.1:5056',
        changeOrigin: false,
      },
    },
  },
})
