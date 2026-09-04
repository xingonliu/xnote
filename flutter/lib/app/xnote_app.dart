import 'package:flutter/material.dart';

import 'theme/xnote_theme.dart';

// -- Type Definitions

class XNoteApp extends StatelessWidget {
  const XNoteApp({super.key});

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'XNote',
      debugShowCheckedModeBanner: false,
      theme: buildXNoteTheme(Brightness.light),
      darkTheme: buildXNoteTheme(Brightness.dark),
      themeMode: ThemeMode.system,
      home: const _FoundationScreen(),
    );
  }
}

class _FoundationScreen extends StatelessWidget {
  const _FoundationScreen();

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: SafeArea(child: Center(child: Text('XNote'))),
    );
  }
}
