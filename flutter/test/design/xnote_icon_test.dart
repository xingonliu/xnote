import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xnote/design/icons/xnote_icon.dart';

// -- Functions

void main() {
  test('all fixed Keyline assets keep a 24 by 24 current-color SVG', () {
    expect(XNoteIcon.values, hasLength(20));

    for (final icon in XNoteIcon.values) {
      final source = File(icon.assetPath).readAsStringSync();
      expect(source, contains('viewBox="0 0 24 24"'), reason: icon.name);
      expect(source, contains('currentColor'), reason: icon.name);
    }
  });

  test('only the three mobile destinations use fill assets', () {
    final fillIcons = XNoteIcon.values
        .where((icon) => icon.relativeAssetPath.startsWith('fill/'))
        .toList();

    expect(
      fillIcons,
      <XNoteIcon>[
        XNoteIcon.notesFill,
        XNoteIcon.agentFill,
        XNoteIcon.profileFill,
      ],
    );
  });

  testWidgets('directional icons mirror in RTL while neutral icons do not', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Directionality(
          textDirection: TextDirection.rtl,
          child: Row(
            children: <Widget>[
              XNoteIconView(icon: XNoteIcon.back),
              XNoteIconView(icon: XNoteIcon.search),
            ],
          ),
        ),
      ),
    );

    final pictures = tester.widgetList<SvgPicture>(find.byType(SvgPicture));
    expect(pictures.first.matchTextDirection, isTrue);
    expect(pictures.last.matchTextDirection, isFalse);
  });
}
