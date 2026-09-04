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
  ThemeMode themeMode = ThemeMode.light,
  TextScaler textScaler = TextScaler.noScaling,
  TextDirection textDirection = TextDirection.ltr,
  bool disableAnimations = false,
}) {
  return ProviderScope(
    overrides: [
      noteRepositoryProvider.overrideWithValue(dependencies.notes),
      notebookRepositoryProvider.overrideWithValue(dependencies.notebooks),
    ],
    child: LiquidGlassWidgets.wrap(
      child: MaterialApp.router(
        theme: buildXNoteTheme(Brightness.light),
        darkTheme: buildXNoteTheme(Brightness.dark),
        themeMode: themeMode,
        routerConfig: router,
        builder: (context, child) {
          final mediaQuery = MediaQuery.of(context).copyWith(
            textScaler: textScaler,
            disableAnimations: disableAnimations,
          );
          return MediaQuery(
            data: mediaQuery,
            child: Directionality(
              textDirection: textDirection,
              child: GlassNavigationShell(child: child!),
            ),
          );
        },
      ),
      brightnessResolver: Theme.maybeBrightnessOf,
    ),
  );
}

GoRouter _createRouter({
  ThemeMode Function()? readThemeMode,
  ValueChanged<ThemeMode>? onThemeModeChanged,
}) {
  return buildXNoteRouter(
    readThemeMode: readThemeMode ?? () => ThemeMode.light,
    onThemeModeChanged: onThemeModeChanged ?? (_) {},
  );
}

void main() {
  late TestDependencies testDependencies;

  setUp(() async {
    testDependencies = await TestDependencies.create();
  });

  tearDown(() => testDependencies.close());

  testWidgets('uses bottom navigation below the 600 logical pixel breakpoint', (
    tester,
  ) async {
    _setSurfaceSize(tester, const Size(390, 844));
    final router = _createRouter();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _buildSubject(router, dependencies: testDependencies.dependencies),
    );
    await tester.pump();

    expect(
      find.byKey(const Key('xnote-primary-bottom-navigation')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('xnote-primary-navigation-rail')),
      findsNothing,
    );
    final scaffold = tester.widget<GlassScaffold>(find.byType(GlassScaffold));
    expect(scaffold.resizeToAvoidBottomInset, isTrue);
    expect(scaffold.statusBarStyle, GlassStatusBarStyle.none);
  });

  testWidgets('uses a persistent navigation rail from the tablet breakpoint', (
    tester,
  ) async {
    _setSurfaceSize(tester, const Size(800, 900));
    final router = _createRouter();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _buildSubject(router, dependencies: testDependencies.dependencies),
    );
    await tester.pump();

    expect(
      find.byKey(const Key('xnote-primary-navigation-rail')),
      findsOneWidget,
    );
    expect(
      find.byKey(const Key('xnote-primary-bottom-navigation')),
      findsNothing,
    );
  });

  testWidgets('uses the wide shell in a landscape phone window', (
    tester,
  ) async {
    _setSurfaceSize(tester, const Size(844, 390));
    final router = _createRouter();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _buildSubject(router, dependencies: testDependencies.dependencies),
    );
    await tester.pump();

    expect(
      find.byKey(const Key('xnote-primary-navigation-rail')),
      findsOneWidget,
    );
    expect(find.byType(SafeArea), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('opens search independently and system back restores the branch',
      (
    tester,
  ) async {
    _setSurfaceSize(tester, const Size(390, 844));
    final router = _createRouter();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _buildSubject(router, dependencies: testDependencies.dependencies),
    );
    await tester.tap(find.byKey(const Key('open-search')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.byKey(const ValueKey<String>('search')), findsOneWidget);
    expect(
      find.byKey(const Key('xnote-primary-bottom-navigation')),
      findsNothing,
    );

    await tester.binding.handlePopRoute();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(router.routerDelegate.currentConfiguration.uri.path, '/notes');
    expect(find.byKey(const ValueKey<String>('notes')), findsOneWidget);
  });

  testWidgets('keeps each primary destination navigation stack independent', (
    tester,
  ) async {
    _setSurfaceSize(tester, const Size(800, 900));
    final router = _createRouter();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _buildSubject(router, dependencies: testDependencies.dependencies),
    );
    router.go('/notes/notebooks/book-1');
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(
      find.byKey(const ValueKey<String>('notebook:book-1')),
      findsOneWidget,
    );

    final rail = find.byKey(const Key('xnote-primary-navigation-rail'));
    await tester.tap(find.descendant(of: rail, matching: find.text('Agent')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.byKey(const ValueKey<String>('agent')), findsOneWidget);

    await tester.tap(find.descendant(of: rail, matching: find.text('笔记')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(
      find.byKey(const ValueKey<String>('notebook:book-1')),
      findsOneWidget,
    );

    await tester.tap(find.descendant(of: rail, matching: find.text('笔记')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(router.routerDelegate.currentConfiguration.uri.path, '/notes');
    expect(find.byKey(const ValueKey<String>('notes')), findsOneWidget);
  });

  testWidgets('keeps the rail visible while wide search owns the content pane',
      (
    tester,
  ) async {
    _setSurfaceSize(tester, const Size(800, 900));
    final router = _createRouter();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _buildSubject(router, dependencies: testDependencies.dependencies),
    );
    await tester.tap(find.byKey(const Key('open-search')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(
      find.byKey(const Key('xnote-primary-navigation-rail')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey<String>('search')), findsOneWidget);
  });

  testWidgets('supports 200 percent text, RTL, and reduced motion together', (
    tester,
  ) async {
    _setSurfaceSize(tester, const Size(800, 900));
    final router = _createRouter();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _buildSubject(
        router,
        dependencies: testDependencies.dependencies,
        textScaler: const TextScaler.linear(2),
        textDirection: TextDirection.rtl,
        disableAnimations: true,
      ),
    );
    router.go('/profile/appearance');
    await tester.pump();

    expect(find.text('外观与辅助功能'), findsWidgets);
    expect(
        MediaQuery.disableAnimationsOf(tester.element(find.byType(ListView))),
        isTrue);
    expect(Directionality.of(tester.element(find.byType(ListView))),
        TextDirection.rtl);
    expect(tester.takeException(), isNull);
  });
}
