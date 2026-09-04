import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import '../../../app/providers/xnote_providers.dart';
import '../../../design/common/xnote_states.dart';
import '../../../design/icons/xnote_icon.dart';
import '../../../design/tokens/xnote_tokens.dart';
import '../../../domain/model/note.dart';
import '../../../domain/model/notebook.dart';
import 'note_list_tile.dart';
import 'note_overlays.dart';

// -- Type Definitions

enum NotesHomeScope { all, unfiled }

final class NotesHomePage extends ConsumerStatefulWidget {
  const NotesHomePage({super.key});

  // -- Lifecycle Hooks

  @override
  ConsumerState<NotesHomePage> createState() => _NotesHomePageState();
}

final class _NotesHomePageState extends ConsumerState<NotesHomePage> {
  // -- State and Variables

  NotesHomeScope _scope = NotesHomeScope.all;
  NoteListSort _sort = NoteListSort.updatedAt;
  final Set<String> _selectedIds = <String>{};

  // -- Functions

  void _invalidateNotes() {
    if (_scope == NotesHomeScope.all) {
      ref.invalidate(activeNotesProvider(_sort));
      return;
    }
    ref.invalidate(unfiledNotesProvider(_sort));
  }

  void _toggleSelection(String id) {
    setState(() {
      if (!_selectedIds.add(id)) {
        _selectedIds.remove(id);
      }
    });
  }

  void _enterSelection(String id) {
    setState(() {
      _selectedIds.add(id);
    });
  }

  Future<void> _chooseScope(List<Notebook> notebooks) async {
    final selection = await showNotebookSelectionSheet(
      context: context,
      notebooks: notebooks,
      includeAll: true,
      unfiledSelected: _scope == NotesHomeScope.unfiled,
    );
    if (!mounted || selection == null) {
      return;
    }
    switch (selection.kind) {
      case NotebookSelectionKind.all:
        setState(() => _scope = NotesHomeScope.all);
      case NotebookSelectionKind.unfiled:
        setState(() => _scope = NotesHomeScope.unfiled);
      case NotebookSelectionKind.notebook:
        unawaited(
          context.push('/notes/notebooks/${selection.notebookId}'),
        );
    }
  }

  Future<void> _createNotebook() async {
    final name = await showNotebookNameDialog(
      context: context,
      title: '新建笔记本',
    );
    if (!mounted || name == null) {
      return;
    }
    await ref.read(notebookRepositoryProvider).createNotebook(name);
    if (mounted) {
      showNoteToast(context, '已创建“$name”');
    }
  }

  Future<void> _createNote() async {
    final note = await ref.read(noteRepositoryProvider).createRichNote();
    if (mounted) {
      unawaited(context.push('/notes/entries/${note.id}'));
    }
  }

  Future<void> _moveSelected(List<Notebook> notebooks) async {
    final selection = await showNotebookSelectionSheet(
      context: context,
      notebooks: notebooks,
      includeAll: false,
    );
    if (!mounted || selection == null) {
      return;
    }
    final notebookId = selection.kind == NotebookSelectionKind.notebook
        ? selection.notebookId
        : null;
    await ref.read(noteRepositoryProvider).moveNotes(_selectedIds, notebookId);
    if (mounted) {
      setState(_selectedIds.clear);
      showNoteToast(context, '已移动所选笔记');
    }
  }

  Future<void> _trashSelected() async {
    final confirmed = await showDestructiveConfirmation(
      context: context,
      title: '移入回收站',
      message: '所选笔记将在回收站保留 30 天。',
      confirmLabel: '移入回收站',
    );
    if (!mounted || !confirmed) {
      return;
    }
    await ref.read(noteRepositoryProvider).trashNotes(_selectedIds);
    if (mounted) {
      setState(_selectedIds.clear);
      showNoteToast(context, '已移入回收站');
    }
  }

  Widget _buildSortMenu() {
    return GlassMenu(
      key: const Key('notes-sort-menu'),
      autoAdjustToScreen: true,
      menuAlignment: GlassMenuAlignment.bottomRight,
      menuWidth: 220,
      quality: GlassQuality.standard,
      triggerBuilder: (context, toggleMenu) => GlassIconButton(
        key: const Key('open-notes-sort'),
        icon: const XNoteIconView(icon: XNoteIcon.reorder),
        onPressed: toggleMenu,
        semanticLabel: '排序',
        quality: GlassQuality.standard,
        useOwnLayer: true,
      ),
      items: <Widget>[
        const GlassMenuLabel(title: '笔记排序'),
        for (final entry in const <(NoteListSort, String)>[
          (NoteListSort.updatedAt, '按更新时间'),
          (NoteListSort.createdAt, '按创建时间'),
          (NoteListSort.title, '按标题'),
        ])
          GlassMenuItem(
            title: entry.$2,
            isSelected: _sort == entry.$1,
            onTap: () => setState(() => _sort = entry.$1),
          ),
      ],
    );
  }

  Widget _buildPage(List<Notebook> notebooks, List<Note> notes) {
    final notebookNames = <String, String>{
      for (final notebook in notebooks) notebook.id: notebook.name,
    };
    return Column(
      children: <Widget>[
        if (_selectedIds.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(
              xnoteSpacingMedium,
              xnoteSpacingSmall,
              xnoteSpacingMedium,
              0,
            ),
            child: GlassToolbar(
              key: const Key('notes-selection-toolbar'),
              quality: GlassQuality.standard,
              children: <Widget>[
                Text('已选择 ${_selectedIds.length} 项'),
                GlassIconButton(
                  icon: const XNoteIconView(icon: XNoteIcon.notes),
                  onPressed: () => unawaited(_moveSelected(notebooks)),
                  semanticLabel: '移动所选笔记',
                  quality: GlassQuality.standard,
                ),
                GlassIconButton(
                  icon: const XNoteIconView(icon: XNoteIcon.delete),
                  onPressed: () => unawaited(_trashSelected()),
                  semanticLabel: '删除所选笔记',
                  quality: GlassQuality.standard,
                ),
                GlassIconButton(
                  icon: const XNoteIconView(icon: XNoteIcon.check),
                  onPressed: () => setState(_selectedIds.clear),
                  semanticLabel: '退出多选',
                  quality: GlassQuality.standard,
                ),
              ],
            ),
          ),
        Expanded(
          child: ListView(
            key: const PageStorageKey<String>('notes-home-list'),
            padding: const EdgeInsets.fromLTRB(
              xnoteSpacingMedium,
              xnoteSpacingMedium,
              xnoteSpacingMedium,
              128,
            ),
            children: <Widget>[
              Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: xnoteMaximumContentWidth,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      Text(
                        _scope == NotesHomeScope.all ? '全部笔记' : '未归档',
                        style: Theme.of(context).textTheme.headlineLarge,
                      ),
                      const SizedBox(height: xnoteSpacingSmall),
                      Wrap(
                        spacing: xnoteSpacingSmall,
                        runSpacing: xnoteSpacingSmall,
                        children: <Widget>[
                          GlassIconButton(
                            key: const Key('open-notebook-picker'),
                            icon: const XNoteIconView(icon: XNoteIcon.notes),
                            onPressed: () => unawaited(_chooseScope(notebooks)),
                            semanticLabel: '选择笔记本',
                            quality: GlassQuality.standard,
                            useOwnLayer: true,
                          ),
                          _buildSortMenu(),
                          GlassIconButton(
                            key: const Key('create-notebook'),
                            icon: const XNoteIconView(icon: XNoteIcon.add),
                            onPressed: () => unawaited(_createNotebook()),
                            semanticLabel: '新建笔记本',
                            quality: GlassQuality.standard,
                            useOwnLayer: true,
                          ),
                          GlassIconButton(
                            key: const Key('create-note'),
                            icon: const XNoteIconView(icon: XNoteIcon.notes),
                            onPressed: () => unawaited(_createNote()),
                            semanticLabel: '新建笔记',
                            quality: GlassQuality.standard,
                            useOwnLayer: true,
                          ),
                        ],
                      ),
                      if (_scope == NotesHomeScope.all &&
                          notebooks.isNotEmpty) ...<Widget>[
                        const SizedBox(height: xnoteSpacingLarge),
                        Text(
                          '笔记本',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: xnoteSpacingSmall),
                        Wrap(
                          spacing: xnoteSpacingSmall,
                          runSpacing: xnoteSpacingSmall,
                          children: <Widget>[
                            for (final notebook in notebooks)
                              _NotebookCard(
                                notebook: notebook,
                                noteCount: notes
                                    .where(
                                      (note) => note.notebookId == notebook.id,
                                    )
                                    .length,
                                onTap: () => unawaited(
                                  context.push(
                                    '/notes/notebooks/${notebook.id}',
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ],
                      const SizedBox(height: xnoteSpacingLarge),
                      if (notes.isEmpty)
                        XNoteEmptyState(
                          icon: _scope == NotesHomeScope.unfiled
                              ? XNoteIcon.inbox
                              : XNoteIcon.notes,
                          title: _scope == NotesHomeScope.unfiled
                              ? '没有未归档笔记'
                              : '还没有笔记',
                          message: _scope == NotesHomeScope.unfiled
                              ? '不属于任何笔记本的笔记会出现在这里。'
                              : '写下第一条想法，之后可以在这里整理、搜索和阅读。',
                          action: FilledButton.icon(
                            onPressed: () => unawaited(_createNote()),
                            icon: const XNoteIconView(icon: XNoteIcon.add),
                            label: const Text('新建笔记'),
                          ),
                        )
                      else ...<Widget>[
                        if (_sort == NoteListSort.updatedAt && notes.length > 3)
                          _NoteSection(
                            title: '最近编辑',
                            notes: notes.take(3).toList(growable: false),
                            notebookNames: notebookNames,
                            selectedIds: _selectedIds,
                            onTap: (id) => _selectedIds.isEmpty
                                ? unawaited(
                                    context.push('/notes/entries/$id'),
                                  )
                                : _toggleSelection(id),
                            onLongPress: _enterSelection,
                          ),
                        _NoteSection(
                          title: '所有笔记',
                          notes: notes,
                          notebookNames: notebookNames,
                          selectedIds: _selectedIds,
                          onTap: (id) => _selectedIds.isEmpty
                              ? unawaited(context.push('/notes/entries/$id'))
                              : _toggleSelection(id),
                          onLongPress: _enterSelection,
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    final notebooks = ref.watch(notebooksProvider);
    final AsyncValue<List<Note>> notes = _scope == NotesHomeScope.all
        ? ref.watch(activeNotesProvider(_sort))
        : ref.watch(unfiledNotesProvider(_sort));
    return switch ((notebooks, notes)) {
      (
        AsyncData(value: final notebookValues),
        AsyncData(value: final noteValues)
      ) =>
        _buildPage(notebookValues, noteValues),
      (AsyncError(), _) || (_, AsyncError()) => XNoteErrorState(
          message: '无法读取笔记，请重试。',
          onRetry: () {
            ref.invalidate(notebooksProvider);
            _invalidateNotes();
          },
        ),
      _ => const XNoteLoadingState(label: '正在加载笔记'),
    };
  }
}

final class _NotebookCard extends StatelessWidget {
  const _NotebookCard({
    required this.notebook,
    required this.noteCount,
    required this.onTap,
  });

  final Notebook notebook;
  final int noteCount;
  final VoidCallback onTap;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => Material(
        color: Theme.of(context).colorScheme.surface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(xnoteCardRadius),
        child: InkWell(
          key: ValueKey<String>('notebook-card-${notebook.id}'),
          borderRadius: BorderRadius.circular(xnoteCardRadius),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(xnoteSpacingMedium),
            child: SizedBox(
              width: 150,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  const XNoteIconView(icon: XNoteIcon.notes),
                  const SizedBox(height: xnoteSpacingSmall),
                  Text(
                    notebook.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  Text('$noteCount 篇笔记'),
                ],
              ),
            ),
          ),
        ),
      );
}

final class _NoteSection extends StatelessWidget {
  const _NoteSection({
    required this.title,
    required this.notes,
    required this.notebookNames,
    required this.selectedIds,
    required this.onTap,
    required this.onLongPress,
  });

  final String title;
  final List<Note> notes;
  final Map<String, String> notebookNames;
  final Set<String> selectedIds;
  final ValueChanged<String> onTap;
  final ValueChanged<String> onLongPress;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: xnoteSpacingLarge),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: xnoteSpacingSmall),
            for (var index = 0; index < notes.length; index += 1) ...<Widget>[
              NoteListTile(
                note: notes[index],
                notebookName: notebookNames[notes[index].notebookId],
                selected: selectedIds.contains(notes[index].id),
                selectionMode: selectedIds.isNotEmpty,
                onTap: () => onTap(notes[index].id),
                onLongPress: () => onLongPress(notes[index].id),
              ),
              if (index != notes.length - 1)
                const SizedBox(height: xnoteSpacingSmall),
            ],
          ],
        ),
      );
}
