import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/** A preview build lives at one fixed URL: no language redirect, no absolute canonical links. */
function previewHtml() {
  return {
    name: 'preview-html',
    transformIndexHtml(html) {
      if (process.env.VITE_PREVIEW !== '1') return html
      return html
        .replace(/<script>[\s\S]*?<\/script>/g, '')
        .replace(/<link rel="canonical"[^>]*>\s*/g, '')
        .replace(/<link rel="alternate"[^>]*>\s*/g, '')
        .replace(/<link rel="preload" as="fetch"[^>]*>\s*/g, '')
    },
  }
}

// Served from the domain root by nginx (SPA fallback to index.html, prerendered pages win).
// In dev, set AETHERION_API to proxy /api to a Control API. Without it the account pages show their offline state.
export default defineConfig({
  plugins: [react(), tailwindcss(), previewHtml()],
  base: '/',
  worker: { format: 'es' },
  build: {
    target: 'es2022',
    chunkSizeWarningLimit: 900,
  },
  server: {
    proxy: process.env.AETHERION_API ? { '/api': { target: process.env.AETHERION_API, changeOrigin: false } } : undefined,
  },
})
