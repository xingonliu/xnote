import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import 'navigation/xnote_router.dart';
import 'theme/xnote_theme.dart';

// -- Type Definitions

class XNoteApp extends StatefulWidget {
  const XNoteApp({super.key, this.initialThemeMode = ThemeMode.system});

  final ThemeMode initialThemeMode;

  // -- Lifecycle Hooks

  @override
  State<XNoteApp> createState() => _XNoteAppState();
}

class _XNoteAppState extends State<XNoteApp> {
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
