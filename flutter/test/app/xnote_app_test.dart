import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import 'package:xnote/app/xnote_app.dart';

// -- Functions

void main() {
  testWidgets('starts with the XNote PoC workspace', (tester) async {
    await tester.pumpWidget(
      LiquidGlassWidgets.wrap(
        child: const XNoteApp(),
        brightnessResolver: Theme.maybeBrightnessOf,
      ),
    );

    expect(find.text('XNote · Glass PoC'), findsOneWidget);
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}
