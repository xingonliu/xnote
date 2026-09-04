import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import '../../app/navigation/xnote_destination.dart';
import '../../design/icons/xnote_icon.dart';
import '../../design/tokens/xnote_tokens.dart';

// -- Type Definitions

final class XNoteShell extends StatelessWidget {
  const XNoteShell({
    required this.body,
    required this.title,
    required this.selectedDestination,
    required this.showPrimaryChrome,
    required this.showBack,
    required this.onBack,
    required this.onOpenSearch,
    required this.onDestinationSelected,
    super.key,
  });

  final Widget body;
  final String title;
  final XNoteDestination selectedDestination;
  final bool showPrimaryChrome;
  final bool showBack;
  final VoidCallback onBack;
  final VoidCallback onOpenSearch;
  final ValueChanged<XNoteDestination> onDestinationSelected;

  // -- Functions

  Widget _background(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: <Color>[
            colors.primaryContainer.withValues(alpha: 0.42),
            Theme.of(context).scaffoldBackgroundColor,
            colors.surfaceContainerHighest.withValues(alpha: 0.55),
          ],
        ),
      ),
    );
  }

  GlassAppBar _appBar(BuildContext context) {
    return GlassAppBar(
      toolbarHeight: xnoteHeaderHeight,
      title: title.isEmpty ? null : Text(title),
      leading: showBack
          ? GlassIconButton(
              icon: const XNoteIconView(icon: XNoteIcon.back),
              onPressed: onBack,
              quality: GlassQuality.standard,
              semanticLabel: '返回',
              useOwnLayer: true,
            )
          : null,
      actions: showPrimaryChrome
          ? <Widget>[
              GlassIconButton(
                key: const Key('open-search'),
                icon: const XNoteIconView(icon: XNoteIcon.search),
                onPressed: onOpenSearch,
                quality: GlassQuality.standard,
                semanticLabel: '搜索',
                useOwnLayer: true,
              ),
            ]
          : const <Widget>[],
    );
  }

  Widget _bottomNavigation() {
    return GlassTabBar.bottom(
      key: const Key('xnote-primary-bottom-navigation'),
      tabs: const <GlassTab>[
        GlassTab(
          icon: XNoteIconView(
            icon: XNoteIcon.notesFill,
            size: xnoteBottomTabIconSize,
          ),
          activeIcon: XNoteIconView(
            icon: XNoteIcon.notesFill,
            size: xnoteBottomTabIconSize,
          ),
          label: '笔记',
          semanticLabel: '笔记',
        ),
        GlassTab(
          icon: XNoteIconView(
            icon: XNoteIcon.agentFill,
            size: xnoteBottomTabIconSize,
          ),
          activeIcon: XNoteIconView(
            icon: XNoteIcon.agentFill,
            size: xnoteBottomTabIconSize,
          ),
          label: 'Agent',
          semanticLabel: 'Agent',
        ),
        GlassTab(
          icon: XNoteIconView(
            icon: XNoteIcon.profileFill,
            size: xnoteBottomTabIconSize,
          ),
          activeIcon: XNoteIconView(
            icon: XNoteIcon.profileFill,
            size: xnoteBottomTabIconSize,
          ),
          label: '我的',
          semanticLabel: '我的',
        ),
      ],
      selectedIndex: selectedDestination.index,
      onTabSelected: (index) {
        onDestinationSelected(XNoteDestination.values[index]);
      },
      iconSize: xnoteBottomTabIconSize,
      barHeight: xnoteBottomNavigationBarHeight,
      verticalPadding: xnoteBottomNavigationVerticalPadding,
      quality: GlassQuality.standard,
      adaptiveBrightness: true,
    );
  }

  Widget _navigationRail() {
    return SafeArea(
      child: NavigationRail(
        key: const Key('xnote-primary-navigation-rail'),
        selectedIndex: selectedDestination.index,
        onDestinationSelected: (index) {
          onDestinationSelected(XNoteDestination.values[index]);
        },
        groupAlignment: -0.7,
        labelType: NavigationRailLabelType.all,
        destinations: const <NavigationRailDestination>[
          NavigationRailDestination(
            icon: XNoteIconView(
              icon: XNoteIcon.notes,
              size: xnoteNavigationRailIconSize,
            ),
            selectedIcon: XNoteIconView(
              icon: XNoteIcon.notes,
              size: xnoteNavigationRailIconSize,
            ),
            label: Text('笔记'),
          ),
          NavigationRailDestination(
            icon: XNoteIconView(
              icon: XNoteIcon.agent,
              size: xnoteNavigationRailIconSize,
            ),
            selectedIcon: XNoteIconView(
              icon: XNoteIcon.agent,
              size: xnoteNavigationRailIconSize,
            ),
            label: Text('Agent'),
          ),
          NavigationRailDestination(
            icon: XNoteIconView(
              icon: XNoteIcon.profile,
              size: xnoteNavigationRailIconSize,
            ),
            selectedIcon: XNoteIconView(
              icon: XNoteIcon.profile,
              size: xnoteNavigationRailIconSize,
            ),
            label: Text('我的'),
          ),
        ],
      ),
    );
  }

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    final wide =
        MediaQuery.sizeOf(context).width >= xnoteAdaptiveNavigationBreakpoint;

    return PopScope<void>(
      canPop: !showBack,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && showBack) {
          onBack();
        }
      },
      child: GlassScaffold(
        key: const Key('xnote-application-shell'),
        background: _background(context),
        backgroundColor: Theme.of(context).scaffoldBackgroundColor,
        statusBarStyle: GlassStatusBarStyle.none,
        contentAwareBrightness: true,
        extendBody: false,
        resizeToAvoidBottomInset: true,
        appBar: _appBar(context),
        bottomBar: !wide && showPrimaryChrome ? _bottomNavigation() : null,
        body: wide
            ? Row(
                children: <Widget>[
                  _navigationRail(),
                  const VerticalDivider(width: 1),
                  Expanded(child: body),
                ],
              )
            : body,
      ),
    );
  }
}
