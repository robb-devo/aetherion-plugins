import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/geist";
import "@fontsource-variable/geist-mono";
import "./styles/app.css";
import "./lib/theme";
import { resolved } from "./lib/theme";
import { initNative, isNative } from "./lib/native";
import { App } from "./App";

initNative(resolved() === "dark");

if (!__DEMO__ && !isNative && "serviceWorker" in navigator && location.protocol === "https:") {
  window.addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
