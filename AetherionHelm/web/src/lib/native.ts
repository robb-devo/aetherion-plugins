// Small bridge to the Android shell. Everything no-ops in a normal browser.
import { Capacitor } from "@capacitor/core";

export const isNative = Capacitor.isNativePlatform();

export async function tap(kind: "light" | "medium" | "heavy" | "success" | "warning" | "error" = "light") {
  if (!isNative) return;
  try {
    const { Haptics, ImpactStyle, NotificationType } = await import("@capacitor/haptics");
    if (kind === "success" || kind === "warning" || kind === "error") {
      await Haptics.notification({ type: kind === "success" ? NotificationType.Success : kind === "warning" ? NotificationType.Warning : NotificationType.Error });
    } else {
      await Haptics.impact({ style: kind === "light" ? ImpactStyle.Light : kind === "medium" ? ImpactStyle.Medium : ImpactStyle.Heavy });
    }
  } catch {
    // haptics unavailable
  }
}

export async function initNative(dark: boolean) {
  if (!isNative) return;
  document.documentElement.classList.add("native");
  try {
    const { StatusBar, Style } = await import("@capacitor/status-bar");
    await StatusBar.setOverlaysWebView({ overlay: true });
    await StatusBar.setStyle({ style: dark ? Style.Dark : Style.Light });
  } catch {
    // no status bar plugin
  }
  try {
    const { SplashScreen } = await import("@capacitor/splash-screen");
    await SplashScreen.hide({ fadeOutDuration: 250 });
  } catch {
    // no splash plugin
  }
  try {
    const { App } = await import("@capacitor/app");
    App.addListener("backButton", ({ canGoBack }) => {
      const open = document.querySelector("[data-dismiss]") as HTMLElement | null;
      if (open) open.click();
      else if (canGoBack && location.hash && location.hash !== "#/") history.back();
      else App.minimizeApp();
    });
  } catch {
    // no app plugin
  }
}
