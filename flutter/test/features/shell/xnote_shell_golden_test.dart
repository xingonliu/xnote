import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import 'package:xnote/app/dependencies/xnote_dependencies.dart';
import 'package:xnote/app/navigation/xnote_router.dart';
import 'package:xnote/app/providers/xnote_providers.dart';
import 'package:xnote/app/theme/xnote_theme.dart';

import '../../support/test_dependencies.dart';

// -- Constants

const _goldenSurfaceKey = Key('xnote-golden-surface');

// -- Functions

void _setSurfaceSize(WidgetTester tester, Size size) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
}

Widget _buildSubject(
  GoRouter router, {
  required XNoteDependencies dependencies,
  required ThemeMode themeMode,
  TextScaler textScaler = TextScaler.noScaling,
}) {
  return ProviderScope(
    overrides: [
      noteRepositoryProvider.overrideWithValue(dependencies.notes),
      notebookRepositoryProvider.overrideWithValue(dependencies.notebooks),
    ],
    child: RepaintBoundary(
      key: _goldenSurfaceKey,
      child: LiquidGlassWidgets.wrap(
        child: MaterialApp.router(
          theme: buildXNoteTheme(Brightness.light),
          darkTheme: buildXNoteTheme(Brightness.dark),
          themeMode: themeMode,
          routerConfig: router,
          builder: (context, child) {
            return MediaQuery(
              data: MediaQuery.of(context).copyWith(textScaler: textScaler),
              child: GlassNavigationShell(child: child!),
            );
          },
        ),
        brightnessResolver: Theme.maybeBrightnessOf,
      ),
    ),
  );
}

GoRouter _createRouter() {
  return buildXNoteRouter(
    readThemeMode: () => ThemeMode.light,
    onThemeModeChanged: (_) {},
  );
}

void main() {
  late TestDependencies testDependencies;

  setUp(() async {
    testDependencies = await TestDependencies.create();
  });

  tearDown(() => testDependencies.close());

  testWidgets('matches the narrow light application shell', (tester) async {
    _setSurfaceSize(tester, const Size(390, 844));
    final router = _createRouter();
    addTearDown(router.dispose);
    await tester.pumpWidget(
      _buildSubject(
        router,
        dependencies: testDependencies.dependencies,
        themeMode: ThemeMode.light,
      ),
    );
    await tester.pump(const Duration(milliseconds: 200));

    await expectLater(
      find.byKey(_goldenSurfaceKey),
      matchesGoldenFile('goldens/xnote_shell_mobile_light.png'),
    );
  });

  testWidgets('matches the wide dark shell at 200 percent text', (
    tester,
  ) async {
    _setSurfaceSize(tester, const Size(800, 900));
    final router = _createRouter();
    addTearDown(router.dispose);
    await tester.pumpWidget(
      _buildSubject(
        router,
        dependencies: testDependencies.dependencies,
        themeMode: ThemeMode.dark,
        textScaler: const TextScaler.linear(2),
      ),
    );
    await tester.pump(const Duration(milliseconds: 200));

    await expectLater(
      find.byKey(_goldenSurfaceKey),
      matchesGoldenFile('goldens/xnote_shell_tablet_dark_large_text.png'),
    );
  });
}
