import 'package:flutter/material.dart';

import '../../design/common/xnote_states.dart';
import '../../design/icons/xnote_icon.dart';
import '../../design/tokens/xnote_tokens.dart';

// -- Type Definitions

final class AgentShellPage extends StatelessWidget {
  const AgentShellPage({super.key});

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => const XNoteEmptyState(
        icon: XNoteIcon.agent,
        title: 'Agent',
        message: '智能整理功能将在准备完成后出现在这里。',
      );
}

final class ProfileShellPage extends StatelessWidget {
  const ProfileShellPage({
    required this.onOpenRecycleBin,
    required this.onOpenAppearance,
    super.key,
  });

  final VoidCallback onOpenRecycleBin;
  final VoidCallback onOpenAppearance;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        xnoteSpacingMedium,
        xnoteSpacingMedium,
        xnoteSpacingMedium,
        120,
      ),
      children: <Widget>[
        _ProfileAction(
          icon: XNoteIcon.delete,
          title: '回收站',
          onTap: onOpenRecycleBin,
        ),
        const Divider(),
        _ProfileAction(
          icon: XNoteIcon.notes,
          title: '外观与辅助功能',
          onTap: onOpenAppearance,
        ),
      ],
    );
  }
}

final class SearchShellPage extends StatelessWidget {
  const SearchShellPage({super.key});

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(xnoteSpacingMedium),
        children: const <Widget>[
          TextField(
            autofocus: true,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(hintText: '搜索标题与正文'),
          ),
          SizedBox(height: xnoteSpacingLarge),
          XNoteEmptyState(
            icon: XNoteIcon.search,
            title: '搜索笔记',
            message: '输入关键词后，这里会显示匹配的标题和正文片段。',
          ),
        ],
      );
}

final class RecycleBinShellPage extends StatelessWidget {
  const RecycleBinShellPage({super.key});

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => const XNoteEmptyState(
        icon: XNoteIcon.delete,
        title: '回收站为空',
        message: '删除的笔记会在这里保留 30 天。',
      );
}

final class AppearanceShellPage extends StatelessWidget {
  const AppearanceShellPage({
    required this.themeMode,
    required this.onThemeModeChanged,
    super.key,
  });

  final ThemeMode themeMode;
  final ValueChanged<ThemeMode> onThemeModeChanged;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(xnoteSpacingMedium),
      children: <Widget>[
        Text('主题', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: xnoteSpacingSmall),
        RadioGroup<ThemeMode>(
          groupValue: themeMode,
          onChanged: (value) {
            if (value != null) {
              onThemeModeChanged(value);
            }
          },
          child: Column(
            children: <Widget>[
              for (final entry in const <(ThemeMode, String)>[
                (ThemeMode.system, '跟随系统'),
                (ThemeMode.light, '浅色'),
                (ThemeMode.dark, '深色'),
              ])
                RadioListTile<ThemeMode>(
                  value: entry.$1,
                  title: Text(entry.$2),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

final class _ProfileAction extends StatelessWidget {
  const _ProfileAction({
    required this.icon,
    required this.title,
    required this.onTap,
  });

  final XNoteIcon icon;
  final String title;
  final VoidCallback onTap;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => ListTile(
        minTileHeight: xnoteMinimumTouchTarget,
        leading: XNoteIconView(icon: icon),
        title: Text(title),
        trailing: const XNoteIconView(
          icon: XNoteIcon.forward,
          size: xnoteIconSizeSmall,
        ),
        onTap: onTap,
      );
}
