import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Static one-pager. Keep `base: '/'` for domain-root hosts (Cloudflare Pages,
// Netlify, nginx). For a GitHub Pages project site, set e.g. `base: '/REPO/'`.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: '/',
})
