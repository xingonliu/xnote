import 'package:flutter/material.dart';

import '../../design/tokens/xnote_tokens.dart';

// -- Constants

const _lightBackground = Color(0xFFF7F4EC);
const _darkBackground = Color(0xFF151412);

const _xnoteTypography = TextTheme(
  headlineLarge: TextStyle(
    fontWeight: FontWeight.bold,
    fontSize: 32,
    height: 38 / 32,
  ),
  headlineSmall: TextStyle(
    fontWeight: FontWeight.w600,
    fontSize: 22,
    height: 28 / 22,
  ),
  titleMedium: TextStyle(
    fontWeight: FontWeight.w600,
    fontSize: 16,
    height: 22 / 16,
  ),
  bodyLarge: TextStyle(fontSize: 17, height: 25 / 17),
  bodyMedium: TextStyle(fontSize: 15, height: 22 / 15),
  labelMedium: TextStyle(
    fontWeight: FontWeight.w500,
    fontSize: 12,
    height: 16 / 12,
  ),
);

// -- Functions

ThemeData buildXNoteTheme(
  Brightness brightness, {
  bool highContrast = false,
}) {
  final isLight = brightness == Brightness.light;
  final primary = isLight ? xnoteLightPrimary : xnoteDarkPrimary;
  final base = ColorScheme.fromSeed(
    seedColor: primary,
    brightness: brightness,
  );
  final colors = base.copyWith(
    primary: primary,
    onPrimary: isLight ? const Color(0xFF2D1B00) : const Color(0xFF463700),
    primaryContainer:
        isLight ? const Color(0xFFFFE08A) : const Color(0xFF645000),
    onPrimaryContainer:
        isLight ? const Color(0xFF2C2100) : const Color(0xFFFFE08A),
    secondary: isLight ? const Color(0xFF625B4D) : const Color(0xFFCCC3B3),
    onSecondary: isLight ? const Color(0xFFFFFFFF) : const Color(0xFF343027),
    surface: isLight ? const Color(0xFFFFFCF4) : const Color(0xFF1D1C19),
    onSurface: isLight ? const Color(0xFF211F1A) : const Color(0xFFE9E2D8),
    surfaceContainerHighest:
        isLight ? const Color(0xFFE9E3D7) : const Color(0xFF4C473F),
    onSurfaceVariant:
        isLight ? const Color(0xFF4C473F) : const Color(0xFFD0C7BA),
    outline: highContrast
        ? (isLight ? const Color(0xFF302C25) : const Color(0xFFE8DFD2))
        : (isLight ? const Color(0xFF7D766A) : const Color(0xFF978F82)),
    error: isLight ? const Color(0xFFBA1A1A) : const Color(0xFFFFB4AB),
  );

  return ThemeData(
    brightness: brightness,
    colorScheme: colors,
    scaffoldBackgroundColor: isLight ? _lightBackground : _darkBackground,
    textTheme: _xnoteTypography.apply(
      bodyColor: colors.onSurface,
      displayColor: colors.onSurface,
    ),
    dividerTheme: DividerThemeData(color: colors.outlineVariant),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: colors.surfaceContainerHighest.withValues(alpha: 0.5),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(xnoteRadiusSmall),
        borderSide: BorderSide.none,
      ),
    ),
    visualDensity: VisualDensity.standard,
    useMaterial3: true,
  );
}
