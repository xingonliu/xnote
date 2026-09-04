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
}
