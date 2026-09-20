import 'package:flutter/material.dart';

import 'aether_colors.dart';

abstract final class AetherTheme {
  static const manrope = 'Manrope';
  static const cinzel = 'Cinzel';

  static ThemeData dark() {
    const scheme = ColorScheme.dark(
      primary: AetherColors.cyan,
      onPrimary: AetherColors.voidBg,
      secondary: AetherColors.amethyst,
      onSecondary: AetherColors.voidBg,
      surface: AetherColors.ink,
      onSurface: AetherColors.white,
      error: AetherColors.offline,
      onError: AetherColors.white,
      outline: AetherColors.glassStroke,
    );

    final base = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: scheme,
      scaffoldBackgroundColor: AetherColors.voidBg,
      fontFamily: manrope,
      visualDensity: VisualDensity.standard,
    );

    return base.copyWith(
      textTheme: base.textTheme.apply(
        fontFamily: manrope,
        bodyColor: AetherColors.white,
        displayColor: AetherColors.white,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        foregroundColor: AetherColors.white,
        titleTextStyle: TextStyle(
          fontFamily: cinzel,
          fontSize: 18,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.4,
          color: AetherColors.white,
        ),
      ),
      cardTheme: CardThemeData(
        color: AetherColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AetherColors.glassStroke),
        ),
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: AetherColors.ink,
        modalBackgroundColor: AetherColors.ink,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22)),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 72,
        backgroundColor: AetherColors.ink.withValues(alpha: 0.94),
        indicatorColor: AetherColors.cyan.withValues(alpha: 0.2),
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return TextStyle(
            fontFamily: manrope,
            fontSize: 11,
            fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
            color: selected ? AetherColors.cyan : AetherColors.mist,
          );
        }),
        iconTheme: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return IconThemeData(
            size: 22,
            color: selected ? AetherColors.cyan : AetherColors.mist,
          );
        }),
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: Colors.transparent,
        indicatorColor: AetherColors.cyan.withValues(alpha: 0.18),
        selectedIconTheme: const IconThemeData(color: AetherColors.cyan),
        unselectedIconTheme: const IconThemeData(color: AetherColors.mist),
        selectedLabelTextStyle: const TextStyle(
          color: AetherColors.cyan,
          fontWeight: FontWeight.w700,
          fontSize: 12,
        ),
        unselectedLabelTextStyle: const TextStyle(
          color: AetherColors.mist,
          fontSize: 12,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AetherColors.glassFill,
        hintStyle: const TextStyle(color: AetherColors.mist),
        labelStyle: const TextStyle(color: AetherColors.mist),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AetherColors.glassStroke),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AetherColors.glassStroke),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AetherColors.cyan, width: 1.4),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: AetherColors.offline),
        ),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 14,
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: AetherColors.cyan,
          foregroundColor: AetherColors.voidBg,
          elevation: 0,
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          textStyle: const TextStyle(
            fontFamily: manrope,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AetherColors.mist,
          side: const BorderSide(color: AetherColors.glassStroke),
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: AetherColors.ink,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: const BorderSide(color: AetherColors.glassStroke),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AetherColors.ink,
        contentTextStyle: const TextStyle(color: AetherColors.white),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      dividerColor: AetherColors.glassStroke,
      splashFactory: InkRipple.splashFactory,
      pageTransitionsTheme: const PageTransitionsTheme(
        builders: {
          TargetPlatform.android: FadeUpwardsPageTransitionsBuilder(),
          TargetPlatform.windows: FadeUpwardsPageTransitionsBuilder(),
          TargetPlatform.linux: FadeUpwardsPageTransitionsBuilder(),
        },
      ),
    );
  }
}
