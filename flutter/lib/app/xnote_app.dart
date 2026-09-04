import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import '../design/common/xnote_states.dart';
import '../domain/model/app_settings.dart';
import '../domain/repositories/settings_repository.dart';
import 'navigation/xnote_router.dart';
import 'providers/xnote_providers.dart';
import 'theme/xnote_theme.dart';

// -- Type Definitions

final class XNoteApp extends ConsumerWidget {
  const XNoteApp({super.key, this.initialThemeMode});

  final ThemeMode? initialThemeMode;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dependencies = ref.watch(xnoteDependenciesProvider);
    return switch (dependencies) {
      AsyncData(:final value) => _RoutedXNoteApp(
          initialThemeMode: initialThemeMode ??
              _themeModeFromDomain(value.initialSettings.themeMode),
          settings: value.settings,
        ),
      AsyncError() => _StartupFrame(
          child: XNoteErrorState(
            message: '无法打开本地笔记库，请重试。',
            onRetry: () => ref.invalidate(xnoteDependenciesProvider),
          ),
        ),
      _ => const _StartupFrame(child: XNoteLoadingState(label: '正在打开笔记库')),
    };
  }
}

final class _RoutedXNoteApp extends StatefulWidget {
  const _RoutedXNoteApp({
    required this.initialThemeMode,
    required this.settings,
  });

  final ThemeMode initialThemeMode;
  final SettingsRepository settings;

  // -- Lifecycle Hooks

  @override
  State<_RoutedXNoteApp> createState() => _RoutedXNoteAppState();
}

final class _RoutedXNoteAppState extends State<_RoutedXNoteApp> {
  // -- State and Variables

  late ThemeMode _themeMode;
  late final _router = buildXNoteRouter(
    readThemeMode: () => _themeMode,
    onThemeModeChanged: _handleThemeModeChanged,
  );

  // -- Listeners

  void _handleThemeModeChanged(ThemeMode themeMode) {
    setState(() {
      _themeMode = themeMode;
    });
    _router.refresh();
    unawaited(widget.settings.setThemeMode(_themeModeToDomain(themeMode)));
  }

  // -- Lifecycle Hooks

  @override
  void initState() {
    super.initState();
    _themeMode = widget.initialThemeMode;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'XNote',
      debugShowCheckedModeBanner: false,
      theme: buildXNoteTheme(Brightness.light),
      darkTheme: buildXNoteTheme(Brightness.dark),
      highContrastTheme: buildXNoteTheme(
        Brightness.light,
        highContrast: true,
      ),
      highContrastDarkTheme: buildXNoteTheme(
        Brightness.dark,
        highContrast: true,
      ),
      themeMode: _themeMode,
      builder: (context, child) => GlassNavigationShell(child: child!),
      routerConfig: _router,
    );
  }

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }
}

final class _StartupFrame extends StatelessWidget {
  const _StartupFrame({required this.child});

  final Widget child;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'XNote',
      debugShowCheckedModeBanner: false,
      theme: buildXNoteTheme(Brightness.light),
      darkTheme: buildXNoteTheme(Brightness.dark),
      home: Scaffold(body: child),
    );
  }
}

// -- Functions

ThemeMode _themeModeFromDomain(AppThemeMode mode) => switch (mode) {
      AppThemeMode.system => ThemeMode.system,
      AppThemeMode.light => ThemeMode.light,
      AppThemeMode.dark => ThemeMode.dark,
    };

AppThemeMode _themeModeToDomain(ThemeMode mode) => switch (mode) {
      ThemeMode.system => AppThemeMode.system,
      ThemeMode.light => AppThemeMode.light,
      ThemeMode.dark => AppThemeMode.dark,
    };
