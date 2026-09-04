import 'package:flutter/material.dart';

import '../features/poc/poc_workspace.dart';
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

  // -- Listeners

  void _handleThemeModeChanged(ThemeMode themeMode) {
    setState(() {
      _themeMode = themeMode;
    });
  }

  // -- Lifecycle Hooks

  @override
  void initState() {
    super.initState();
    _themeMode = widget.initialThemeMode;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'XNote',
      debugShowCheckedModeBanner: false,
      theme: buildXNoteTheme(Brightness.light),
      darkTheme: buildXNoteTheme(Brightness.dark),
      themeMode: _themeMode,
      home: PocWorkspace(
        themeMode: _themeMode,
        onThemeModeChanged: _handleThemeModeChanged,
      ),
    );
  }
}
