import 'package:flutter/material.dart';

// -- Constants

const _lightPrimary = Color(0xFFE09F3E);
const _darkPrimary = Color(0xFFFFD60A);

// -- Functions

ThemeData buildXNoteTheme(Brightness brightness) {
  final primary = brightness == Brightness.light ? _lightPrimary : _darkPrimary;

  return ThemeData(
    brightness: brightness,
    colorScheme: ColorScheme.fromSeed(
      seedColor: primary,
      brightness: brightness,
    ).copyWith(primary: primary),
    useMaterial3: true,
  );
}
