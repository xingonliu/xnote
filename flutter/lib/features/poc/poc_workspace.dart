import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import 'editor_poc_view.dart';
import 'glass_poc_view.dart';

// -- Type Definitions

class PocWorkspace extends StatefulWidget {
  const PocWorkspace({
    required this.themeMode,
    required this.onThemeModeChanged,
    super.key,
  });

  final ThemeMode themeMode;
  final ValueChanged<ThemeMode> onThemeModeChanged;

  // -- Lifecycle Hooks

  @override
  State<PocWorkspace> createState() => _PocWorkspaceState();
}

class _PocWorkspaceState extends State<PocWorkspace> {
  // -- Constants

  static const _tabs = <GlassTab>[
    GlassTab(
      icon: Icon(CupertinoIcons.drop),
      activeIcon: Icon(CupertinoIcons.drop_fill),
      label: 'Glass',
      semanticLabel: 'Liquid Glass 验证',
    ),
    GlassTab(
      icon: Icon(CupertinoIcons.pencil_outline),
      activeIcon: Icon(CupertinoIcons.pencil),
      label: '编辑器',
      semanticLabel: '富文本编辑器验证',
    ),
  ];

  // -- State and Variables

  int _selectedIndex = 0;

  // -- Derived Values

  String get _pageTitle =>
      _selectedIndex == 0 ? 'XNote · Glass PoC' : 'XNote · 编辑器 PoC';

  // -- Functions

  void _selectPage(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  Widget _buildThemeMenu() {
    return GlassMenu(
      key: const Key('theme-glass-menu'),
      autoAdjustToScreen: true,
      menuAlignment: GlassMenuAlignment.topRight,
      menuWidth: 220,
      quality: GlassQuality.standard,
      triggerBuilder: (context, toggleMenu) => GlassIconButton(
        key: const Key('open-theme-menu'),
        icon: const Icon(CupertinoIcons.circle_lefthalf_fill),
        onPressed: toggleMenu,
        quality: GlassQuality.standard,
        semanticLabel: '切换主题',
        useOwnLayer: true,
      ),
      items: <Widget>[
        const GlassMenuLabel(title: '应用主题'),
        GlassMenuItem(
          title: '跟随系统',
          icon: const Icon(CupertinoIcons.device_phone_portrait),
          isSelected: widget.themeMode == ThemeMode.system,
          onTap: () => widget.onThemeModeChanged(ThemeMode.system),
        ),
        GlassMenuItem(
          title: '浅色',
          icon: const Icon(CupertinoIcons.sun_max),
          isSelected: widget.themeMode == ThemeMode.light,
          onTap: () => widget.onThemeModeChanged(ThemeMode.light),
        ),
        GlassMenuItem(
          title: '深色',
          icon: const Icon(CupertinoIcons.moon),
          isSelected: widget.themeMode == ThemeMode.dark,
          onTap: () => widget.onThemeModeChanged(ThemeMode.dark),
        ),
      ],
    );
  }

  Widget _buildBackground(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: <Color>[
            colorScheme.primaryContainer,
            colorScheme.surface,
            colorScheme.secondaryContainer,
          ],
        ),
      ),
    );
  }

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    return GlassScaffold(
      key: const Key('xnote-glass-scaffold'),
      background: _buildBackground(context),
      backgroundColor: Theme.of(context).colorScheme.surface,
      statusBarStyle: GlassStatusBarStyle.none,
      contentAwareBrightness: true,
      appBar: GlassAppBar(
        title: Text(_pageTitle),
        actions: <Widget>[_buildThemeMenu()],
      ),
      bottomBar: GlassTabBar.bottom(
        key: const Key('xnote-glass-tab-bar'),
        tabs: _tabs,
        selectedIndex: _selectedIndex,
        onTabSelected: _selectPage,
        quality: GlassQuality.standard,
        adaptiveBrightness: true,
      ),
      body: IndexedStack(
        index: _selectedIndex,
        children: const <Widget>[
          GlassPocView(),
          EditorPocView(),
        ],
      ),
    );
  }
}
