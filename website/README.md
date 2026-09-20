# Aetherion Website

Statische Marketing- + Support-Seite für **Aetherion** (Vite, React, Tailwind). Ein One-Pager, kein CMS, kein Backend.

## Lokal

```bash
cd website
npm i
npm run dev
```

Öffnet den Dev-Server (Vite, meist `http://localhost:5173`).

## Build

```bash
cd website
npm i
npm run build
```

Output: `website/dist/` — reines HTML/CSS/JS.

Preview des Builds:

```bash
npm run preview
```

## Deploy (static)

Beliebiger Static-Host. Build-Command `npm run build`, Publish-Directory `dist`, Root-Directory `website` wenn das Repo der Monorepo-Root ist.

| Host | Hinweis |
|------|---------|
| Cloudflare Pages / Netlify / Vercel | Framework preset Static / Vite. Output `dist`. |
| nginx / Apache / Caddy | Inhalt von `dist/` in den DocumentRoot kopieren. |
| GitHub Pages (Project-Site) | In `vite.config.js` `base: '/REPO/'` setzen, dann `dist/` als Pages-Artifact. |

`base` ist standardmäßig `'/'` (Domain-Root). Keine Server-Routes, keine API, keine Env-Secrets.

PayPal-Links und Discord sind hart im Frontend (`src/content.js`). Shard-Gutschrift bleibt **manuell** (Spieler-DM mit IGN + Beleg).

Karten-Platzhalter: [`public/maps/`](public/maps/README.md).

## Nicht anfassen

Dieser Ordner ist unabhängig von den Paper-Plugins. Maven-Module, `pom.xml` und Plugin-Jars bleiben unberührt.
