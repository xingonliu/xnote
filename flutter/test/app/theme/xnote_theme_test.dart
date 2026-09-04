import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xnote/app/theme/xnote_theme.dart';

// -- Functions

void main() {
  test('uses the documented primary color for each brightness', () {
    expect(
      buildXNoteTheme(Brightness.light).colorScheme.primary,
      const Color(0xFFE09F3E),
    );
    expect(
      buildXNoteTheme(Brightness.dark).colorScheme.primary,
      const Color(0xFFFFD60A),
    );
  });

  test('uses distinct readable surfaces for light and dark themes', () {
    final light = buildXNoteTheme(Brightness.light);
    final dark = buildXNoteTheme(Brightness.dark);

    expect(light.scaffoldBackgroundColor, const Color(0xFFF7F4EC));
    expect(dark.scaffoldBackgroundColor, const Color(0xFF151412));
    expect(light.colorScheme.onSurface, isNot(light.colorScheme.surface));
    expect(dark.colorScheme.onSurface, isNot(dark.colorScheme.surface));
  });

  test('strengthens outlines when high contrast is requested', () {
    final standard = buildXNoteTheme(Brightness.light);
    final highContrast = buildXNoteTheme(
      Brightness.light,
      highContrast: true,
    );

    expect(
      highContrast.colorScheme.outline,
      isNot(standard.colorScheme.outline),
    );
  });
}
