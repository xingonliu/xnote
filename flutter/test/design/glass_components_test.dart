import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import 'package:xnote/app/xnote_app.dart';

// -- Functions

void main() {
  testWidgets('composes the required Liquid Glass components directly', (
    tester,
  ) async {
    await tester.pumpWidget(
      LiquidGlassWidgets.wrap(
        child: const XNoteApp(),
        brightnessResolver: Theme.maybeBrightnessOf,
      ),
    );

    expect(find.byType(GlassScaffold), findsOneWidget);
    expect(find.byType(GlassAppBar), findsOneWidget);
    expect(find.byType(GlassTabBar), findsOneWidget);
    expect(find.byType(GlassToolbar), findsOneWidget);
    expect(find.byType(GlassMenu), findsOneWidget);
    expect(find.byType(GlassIconButton), findsWidgets);
    expect(find.byType(ProgressiveBlur), findsWidgets);
  });
}
