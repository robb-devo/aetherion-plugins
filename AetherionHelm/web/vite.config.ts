import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// `--mode demo` builds a self-contained preview that runs on made-up data (no gateway needed).
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  base: mode === "demo" ? "./" : "/",
  define: { __DEMO__: JSON.stringify(mode === "demo") },
  build: {
    target: "es2022",
    cssMinify: true,
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks: { react: ["react", "react-dom"] },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api/live": { target: "ws://127.0.0.1:18787", ws: true },
      "/api": { target: "http://127.0.0.1:18787", changeOrigin: false },
    },
  },
}));
