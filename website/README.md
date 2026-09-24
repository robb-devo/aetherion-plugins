# Aetherion Website

Statische Marketing- + Support-Seite für **Aetherion** (Vite, React, Tailwind, Framer Motion). Ein One-Pager, kein CMS, kein Backend.

English is the default. A header toggle switches **EN / DE**; the choice is stored in `localStorage` (`aetherion-lang`). Copy lives in `src/copy.js`.

## Lokal

```bash
cd website
npm i
npm run dev
```

Öffnet den Dev-Server (Vite, meist `http://localhost:5173`).

## Platform (accounts + servers)

`/login`, `/register`, `/servers`, `/servers/new`, `/servers/:id` talk to `/api/web` on the same origin. nginx on the Hetzner host proxies `/api/web/` to the Aetherion Control API (`aetherion-control` repo, `artifacts/api-server/src/routes/web.ts`). In dev, Vite proxies `/api` to `AETHERION_API` (default `http://127.0.0.1:5056`).

## Real world imagery

Screenshots live in `public/world/`, item icons (from the Aetherion resource pack) in `public/items/`. To add a place screenshot, drop a 16:10 JPG into `public/world/` and set `image` for that entry in `WORLD` in `src/content.js`.

## Deploy

Build, then replace `assets/`, `items/`, `world/`, `index.html`, `favicon.svg` in `/var/www/donnernet.de` — keep `launcher/` and the launcher `.exe`.

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

## Domain (donnernet.de)

The public site is **https://donnernet.de** (and **www.donnernet.de**). Players still join Minecraft at **play.donnernet.de** — do not change the `play` DNS record.

### Point Cloudflare at the static host (3 steps)

1. Deploy this folder to **Vercel**, **Netlify**, or **Cloudflare Pages**: root directory `website`, build `npm run build`, output `dist`.
2. In that host, add custom domains `donnernet.de` and `www.donnernet.de`. Copy the **A** / **CNAME** values it shows.
3. In **Cloudflare → DNS** for donnernet.de, paste those values for `@` (the apex) and `www`. Leave **`play`** exactly as it is.

Deutsch kurz: Seite = donnernet.de / www. Static-Host nehmen, A/CNAME abschreiben, in Cloudflare bei `@` und `www` einsetzen. Den Eintrag **play** nicht anfassen.

PayPal-Links und Discord sind hart im Frontend (`src/content.js`). Shard-Gutschrift bleibt **manuell** (Spieler-DM mit IGN + Beleg).

Karten-Platzhalter: [`public/maps/`](public/maps/README.md).

## Nicht anfassen

Dieser Ordner ist unabhängig von den Paper-Plugins. Maven-Module, `pom.xml` und Plugin-Jars bleiben unberührt.
