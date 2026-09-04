import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import 'package:xnote/app/providers/xnote_providers.dart';
import 'package:xnote/app/xnote_app.dart';

import '../support/test_dependencies.dart';

// -- Functions

Widget _buildSubject(
  TestDependencies testDependencies, {
  ThemeMode themeMode = ThemeMode.system,
}) {
  return LiquidGlassWidgets.wrap(
    child: ProviderScope(
      overrides: [
        xnoteDependenciesProvider.overrideWith(
          (ref) async => testDependencies.dependencies,
        ),
      ],
      child: XNoteApp(initialThemeMode: themeMode),
    ),
    brightnessResolver: Theme.maybeBrightnessOf,
  );
}

void main() {
  late TestDependencies testDependencies;

  setUp(() async {
    testDependencies = await TestDependencies.create();
  });

  tearDown(() => testDependencies.close());

  testWidgets('starts with the production XNote application shell', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(390, 844);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    await tester.pumpWidget(_buildSubject(testDependencies));
    await tester.pumpAndSettle();

    expect(find.byType(MaterialApp), findsOneWidget);
    expect(find.byType(GlassNavigationShell), findsOneWidget);
    expect(find.byType(GlassScaffold), findsOneWidget);
    expect(find.byType(GlassAppBar), findsOneWidget);
    expect(find.byType(GlassTabBar), findsOneWidget);
    expect(find.text('还没有笔记'), findsOneWidget);
    expect(find.text('XNote · Glass PoC'), findsNothing);
  });

  testWidgets('applies the requested initial theme mode', (tester) async {
    await tester.pumpWidget(
      _buildSubject(testDependencies, themeMode: ThemeMode.dark),
    );
    await tester.pumpAndSettle();

    final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(materialApp.themeMode, ThemeMode.dark);
  });

  testWidgets('changes the application theme from the appearance page', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(390, 844);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    await tester.pumpWidget(_buildSubject(testDependencies));
    await tester.pumpAndSettle();

    final bottomNavigation = find.byKey(
      const Key('xnote-primary-bottom-navigation'),
    );
    await tester.tap(
      find.descendant(of: bottomNavigation, matching: find.text('我的')),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    await tester.tap(find.text('外观与辅助功能'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    await tester.tap(find.text('深色'));
    await tester.pump();

    final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(materialApp.themeMode, ThemeMode.dark);
  });
}
