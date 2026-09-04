import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../design/tokens/xnote_tokens.dart';
import '../../features/shell/shell_pages.dart';
import '../../features/shell/xnote_shell.dart';
import 'xnote_destination.dart';

// -- Constants

const _primaryPaths = <String>{'/notes', '/agent', '/profile'};

// -- Functions

GoRouter buildXNoteRouter({
  required ThemeMode Function() readThemeMode,
  required ValueChanged<ThemeMode> onThemeModeChanged,
}) {
  return GoRouter(
    initialLocation: XNoteDestination.notes.rootPath,
    routes: <RouteBase>[
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          final path = state.uri.path;
          final destination =
              XNoteDestination.values[navigationShell.currentIndex];
          return XNoteShell(
            body: navigationShell,
            title: _titleFor(path),
            selectedDestination: destination,
            showPrimaryChrome: _primaryPaths.contains(path),
            showBack: !_primaryPaths.contains(path),
            onBack: () => _goBack(context, destination),
            onOpenSearch: () {
              unawaited(context.push('${destination.rootPath}/search'));
            },
            onDestinationSelected: (next) {
              navigationShell.goBranch(
                next.index,
                initialLocation: next == destination,
              );
            },
          );
        },
        branches: <StatefulShellBranch>[
          StatefulShellBranch(
            initialLocation: XNoteDestination.notes.rootPath,
            routes: <RouteBase>[
              GoRoute(
                path: XNoteDestination.notes.rootPath,
                pageBuilder: (context, state) => _page(
                  context,
                  state,
                  const NotesShellPage(key: ValueKey<String>('notes')),
                ),
                routes: <RouteBase>[
                  GoRoute(
                    path: 'search',
                    pageBuilder: (context, state) => _page(
                      context,
                      state,
                      const SearchShellPage(key: ValueKey<String>('search')),
                    ),
                  ),
                  GoRoute(
                    path: 'notebooks/:notebookId',
                    pageBuilder: (context, state) => _page(
                      context,
                      state,
                      NotesRouteShellPage(
                        key: ValueKey<String>(
                          'notebook:${state.pathParameters['notebookId']}',
                        ),
                        label: '笔记本',
                      ),
                    ),
                  ),
                  GoRoute(
                    path: 'entries/:noteId',
                    pageBuilder: (context, state) => _page(
                      context,
                      state,
                      NotesRouteShellPage(
                        key: ValueKey<String>(
                          'editor:${state.pathParameters['noteId']}',
                        ),
                        label: '编辑笔记',
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            initialLocation: XNoteDestination.agent.rootPath,
            routes: <RouteBase>[
              GoRoute(
                path: XNoteDestination.agent.rootPath,
                pageBuilder: (context, state) => _page(
                  context,
                  state,
                  const AgentShellPage(key: ValueKey<String>('agent')),
                ),
                routes: <RouteBase>[
                  GoRoute(
                    path: 'search',
                    pageBuilder: (context, state) => _page(
                      context,
                      state,
                      const SearchShellPage(key: ValueKey<String>('search')),
                    ),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            initialLocation: XNoteDestination.profile.rootPath,
            routes: <RouteBase>[
              GoRoute(
                path: XNoteDestination.profile.rootPath,
                pageBuilder: (context, state) => _page(
                  context,
                  state,
                  ProfileShellPage(
                    key: const ValueKey<String>('profile'),
                    onOpenRecycleBin: () {
                      unawaited(context.push('/profile/recycle-bin'));
                    },
                    onOpenAppearance: () {
                      unawaited(context.push('/profile/appearance'));
                    },
                  ),
                ),
                routes: <RouteBase>[
                  GoRoute(
                    path: 'search',
                    pageBuilder: (context, state) => _page(
                      context,
                      state,
                      const SearchShellPage(key: ValueKey<String>('search')),
                    ),
                  ),
                  GoRoute(
                    path: 'recycle-bin',
                    pageBuilder: (context, state) => _page(
                      context,
                      state,
                      const RecycleBinShellPage(
                        key: ValueKey<String>('recycle-bin'),
                      ),
                    ),
                  ),
                  GoRoute(
                    path: 'appearance',
                    pageBuilder: (context, state) => _page(
                      context,
                      state,
                      AppearanceShellPage(
                        key: const ValueKey<String>('appearance'),
                        themeMode: readThemeMode(),
                        onThemeModeChanged: onThemeModeChanged,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    ],
  );
}

Page<void> _page(BuildContext context, GoRouterState state, Widget child) {
  final reduceMotion = MediaQuery.disableAnimationsOf(context);
  return CustomTransitionPage<void>(
    key: state.pageKey,
    transitionDuration:
        reduceMotion ? Duration.zero : xnoteShortAnimationDuration,
    reverseTransitionDuration:
        reduceMotion ? Duration.zero : xnoteShortAnimationDuration,
    child: Material(type: MaterialType.transparency, child: child),
    transitionsBuilder: (context, animation, secondaryAnimation, child) {
      return FadeTransition(opacity: animation, child: child);
    },
  );
}

void _goBack(BuildContext context, XNoteDestination destination) {
  if (context.canPop()) {
    context.pop();
    return;
  }
  context.go(destination.rootPath);
}

String _titleFor(String path) {
  if (path.endsWith('/search')) {
    return '搜索';
  }
  if (path == '/profile/recycle-bin') {
    return '回收站';
  }
  if (path == '/profile/appearance') {
    return '外观与辅助功能';
  }
  if (path.startsWith('/notes/notebooks/')) {
    return '笔记本';
  }
  if (path.startsWith('/notes/entries/')) {
    return '编辑笔记';
  }
  if (path == XNoteDestination.agent.rootPath) {
    return 'Agent';
  }
  if (path == XNoteDestination.profile.rootPath) {
    return '我的';
  }
  return '笔记';
}
