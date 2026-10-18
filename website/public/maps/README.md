# Map screenshots

This folder is the gallery source for the marketing site.

| File | Caption on the site |
|------|---------------------|
| `eldervale.svg` | Eldervale — farming / fishing / foraging / mining islands |
| `crystal-hollows.svg` | Crystal Hollows / Amethyst mines, The Veins |
| `harbour.svg` | Anker Harbour — spawn, Egon, market, docks |

The SVG files are **placeholders**. To swap in real in-game shots:

1. Export dark, landscape screenshots (about 1600×1000, WebP or PNG).
2. Replace the files **or** drop `eldervale.webp` / `crystal-hollows.webp` / `harbour.webp` here and point `image` in `src/content.js` at the new names.
3. Set `placeholder: false` on that map in `src/content.js` to hide the “Platzhalter” badge.

Keep these three slots. The gallery is not a CMS.
