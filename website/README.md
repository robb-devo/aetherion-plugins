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
