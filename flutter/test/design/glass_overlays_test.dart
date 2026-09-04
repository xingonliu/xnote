import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import 'package:xnote/app/xnote_app.dart';

// -- Functions

Widget _buildSubject() {
  return LiquidGlassWidgets.wrap(
    child: const XNoteApp(),
    brightnessResolver: Theme.maybeBrightnessOf,
  );
}

void main() {
  testWidgets('shows library toast feedback from the Glass PoC',
      (tester) async {
    await tester.pumpWidget(_buildSubject());

    await tester.tap(find.byKey(const Key('show-glass-toast')));
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.byType(GlassToast), findsOneWidget);
    expect(find.text('Liquid Glass 反馈层工作正常'), findsOneWidget);
  });

  testWidgets('opens and closes the library modal sheet', (tester) async {
    await tester.pumpWidget(_buildSubject());

    await tester.tap(find.byKey(const Key('show-glass-sheet')));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('GlassModalSheet'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('关闭弹层'));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('GlassModalSheet'), findsNothing);
  });
}
