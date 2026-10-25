// Bakes the panel address into the app's offline page, which has to work without the network.
// Same default as capacitor.config.ts.
import { readFileSync, writeFileSync } from "node:fs";

const url = process.env.HELM_URL || "https://panel.donnernet.de";
const file = new URL("../dist/offline.html", import.meta.url);
writeFileSync(file, readFileSync(file, "utf8").replace("__HELM_URL__", url));
console.log(`offline.html -> ${url}`);
