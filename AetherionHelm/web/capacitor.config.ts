import type { CapacitorConfig } from "@capacitor/cli";

// The Android app is a native shell around the live panel: the UI always comes from the server, so every deploy
// reaches every phone at once and the APK only changes when the shell itself does. When the panel cannot be
// reached, the bundled offline.html shows instead. HELM_URL is set by the release workflow; scripts/prepare-app.mjs
// uses the same default.
const url = process.env.HELM_URL || "https://panel.donnernet.de";

const config: CapacitorConfig = {
  appId: "de.aetherion.helm",
  appName: "Aetherion Helm",
  webDir: "dist",
  backgroundColor: "#04050a",
  server: {
    url,
    errorPath: "offline.html",
    androidScheme: "https",
  },
  android: {
    allowMixedContent: false,
    appendUserAgent: "AetherionHelm/1.0",
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 3000,
      launchAutoHide: true,
      launchFadeOutDuration: 250,
      backgroundColor: "#04050a",
      showSpinner: false,
    },
    StatusBar: {
      overlaysWebView: true,
      style: "DARK",
      backgroundColor: "#00000000",
    },
  },
};

export default config;
