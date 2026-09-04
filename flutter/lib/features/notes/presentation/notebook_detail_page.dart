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

final class NotebookDetailPage extends ConsumerStatefulWidget {
  const NotebookDetailPage({required this.notebookId, super.key});

  final String notebookId;

  // -- Lifecycle Hooks

  @override
  ConsumerState<NotebookDetailPage> createState() => _NotebookDetailPageState();
}

final class _NotebookDetailPageState extends ConsumerState<NotebookDetailPage> {
  // -- State and Variables

  NoteListSort _sort = NoteListSort.manual;
  List<Note>? _optimisticManualOrder;
  final Set<String> _selectedIds = <String>{};

  // -- Functions

  void _toggleSelection(String id) {
    setState(() {
      if (!_selectedIds.add(id)) {
        _selectedIds.remove(id);
      }
    });
  }

  Future<void> _createNote() async {
    final note = await ref
        .read(noteRepositoryProvider)
        .createRichNote(notebookId: widget.notebookId);
    if (mounted) {
      unawaited(context.push('/notes/entries/${note.id}'));
    }
  }

  Future<void> _renameNotebook(Notebook notebook) async {
    final name = await showNotebookNameDialog(
      context: context,
      title: '重命名笔记本',
      initialName: notebook.name,
    );
    if (!mounted || name == null || name == notebook.name) {
      return;
    }
    await ref.read(notebookRepositoryProvider).renameNotebook(
          notebook.id,
          name,
        );
    if (mounted) {
      showNoteToast(context, '已重命名为“$name”');
    }
  }

  Future<void> _deleteNotebook(Notebook notebook, int noteCount) async {
    final confirmed = await showDestructiveConfirmation(
      context: context,
      title: '删除笔记本',
      message: noteCount == 0
          ? '这个空笔记本将立即永久删除。'
          : '笔记本将立即永久删除。其中的 $noteCount 篇笔记会进入回收站，恢复后变为未归档。',
      confirmLabel: '删除笔记本',
    );
    if (!mounted || !confirmed) {
      return;
    }
    await ref.read(notebookRepositoryProvider).deleteNotebook(notebook.id);
    if (mounted) {
      showNoteToast(context, '已删除笔记本');
      context.go('/notes');
    }
  }

  Future<void> _moveSelected(List<Notebook> notebooks) async {
    final selection = await showNotebookSelectionSheet(
      context: context,
      notebooks: notebooks,
      includeAll: false,
      selectedNotebookId: widget.notebookId,
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

  Future<void> _reorder(List<Note> notes, int oldIndex, int newIndex) async {
    final reordered = List<Note>.of(notes);
    final moved = reordered.removeAt(oldIndex);
    reordered.insert(newIndex, moved);
    setState(() => _optimisticManualOrder = reordered);
    await ref
        .read(noteRepositoryProvider)
        .reorderNotes(reordered.map((note) => note.id).toList());
    if (mounted) {
      setState(() => _optimisticManualOrder = null);
    }
  }

  Widget _buildSortMenu() {
    return GlassMenu(
      key: const Key('notebook-sort-menu'),
      autoAdjustToScreen: true,
      menuAlignment: GlassMenuAlignment.bottomRight,
      menuWidth: 220,
      quality: GlassQuality.standard,
      triggerBuilder: (context, toggleMenu) => GlassIconButton(
        key: const Key('open-notebook-sort'),
        icon: const XNoteIconView(icon: XNoteIcon.reorder),
        onPressed: toggleMenu,
        semanticLabel: '排序笔记',
        quality: GlassQuality.standard,
        useOwnLayer: true,
      ),
      items: <Widget>[
        const GlassMenuLabel(title: '笔记排序'),
        for (final entry in const <(NoteListSort, String)>[
          (NoteListSort.manual, '手动排序'),
          (NoteListSort.updatedAt, '按更新时间'),
          (NoteListSort.createdAt, '按创建时间'),
          (NoteListSort.title, '按标题'),
        ])
          GlassMenuItem(
            title: entry.$2,
            isSelected: _sort == entry.$1,
            onTap: () {
              setState(() {
                _sort = entry.$1;
                _optimisticManualOrder = null;
              });
            },
          ),
      ],
    );
  }

  Widget _buildMoreMenu(Notebook notebook, int noteCount) {
    return GlassMenu(
      key: const Key('notebook-more-menu'),
      autoAdjustToScreen: true,
      menuAlignment: GlassMenuAlignment.bottomRight,
      menuWidth: 220,
      quality: GlassQuality.standard,
      triggerBuilder: (context, toggleMenu) => GlassIconButton(
        key: const Key('open-notebook-more'),
        icon: const XNoteIconView(icon: XNoteIcon.more),
        onPressed: toggleMenu,
        semanticLabel: '更多笔记本操作',
        quality: GlassQuality.standard,
        useOwnLayer: true,
      ),
      items: <Widget>[
        GlassMenuItem(
          title: '重命名',
          icon: const XNoteIconView(icon: XNoteIcon.notes),
          onTap: () => unawaited(_renameNotebook(notebook)),
        ),
        const GlassMenuDivider(),
        GlassMenuItem(
          title: '删除笔记本',
          icon: const XNoteIconView(icon: XNoteIcon.delete),
          isDestructive: true,
          onTap: () => unawaited(_deleteNotebook(notebook, noteCount)),
        ),
      ],
    );
  }

  Widget _buildPage(
    Notebook notebook,
    List<Notebook> notebooks,
    List<Note> streamedNotes,
  ) {
    final notes = _sort == NoteListSort.manual
        ? _optimisticManualOrder ?? streamedNotes
        : streamedNotes;
    final characterCount = notes.fold<int>(
      0,
      (total, note) => total + note.visibleCharacterCount,
    );
    final selectionMode = _selectedIds.isNotEmpty;
    return Column(
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(
            xnoteSpacingMedium,
            xnoteSpacingMedium,
            xnoteSpacingMedium,
            xnoteSpacingSmall,
          ),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(
                maxWidth: xnoteMaximumContentWidth,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  Text(
                    notebook.name,
                    style: Theme.of(context).textTheme.headlineLarge,
                  ),
                  const SizedBox(height: xnoteSpacingSmall),
                  Wrap(
                    spacing: xnoteSpacingSmall,
                    runSpacing: xnoteSpacingSmall,
                    children: <Widget>[
                      GlassPopover(
                        key: const Key('notebook-stats-popover'),
                        popoverWidth: 240,
                        popoverHeight: 72,
                        triggerBuilder: (context, togglePopover) =>
                            GlassIconButton(
                          icon: const XNoteIconView(icon: XNoteIcon.inbox),
                          onPressed: togglePopover,
                          semanticLabel: '查看笔记本统计',
                          quality: GlassQuality.standard,
                          useOwnLayer: true,
                        ),
                        contentBuilder: (context, close) => Padding(
                          padding: const EdgeInsets.all(xnoteSpacingMedium),
                          child: Text('${notes.length} 篇 · $characterCount 字'),
                        ),
                      ),
                      _buildSortMenu(),
                      _buildMoreMenu(notebook, notes.length),
                      GlassIconButton(
                        key: const Key('create-note-in-notebook'),
                        icon: const XNoteIconView(icon: XNoteIcon.add),
                        onPressed: () => unawaited(_createNote()),
                        semanticLabel: '在当前笔记本新建笔记',
                        quality: GlassQuality.standard,
                        useOwnLayer: true,
                      ),
                    ],
                  ),
                  if (selectionMode) ...<Widget>[
                    const SizedBox(height: xnoteSpacingSmall),
                    GlassToolbar(
                      key: const Key('notebook-selection-toolbar'),
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
                  ],
                ],
              ),
            ),
          ),
        ),
        Expanded(
          child: notes.isEmpty
              ? XNoteEmptyState(
                  icon: XNoteIcon.notes,
                  title: '这个笔记本还是空的',
                  message: '在这里写下第一篇笔记，它会属于当前笔记本。',
                  action: FilledButton.icon(
                    onPressed: () => unawaited(_createNote()),
                    icon: const XNoteIconView(icon: XNoteIcon.add),
                    label: const Text('新建笔记'),
                  ),
                )
              : _buildNoteList(notes, selectionMode),
        ),
      ],
    );
  }

  Widget _buildNoteList(List<Note> notes, bool selectionMode) {
    Widget itemBuilder(BuildContext context, int index) {
      final note = notes[index];
      return Padding(
        key: ValueKey<String>('notebook-note-${note.id}'),
        padding: const EdgeInsets.fromLTRB(
          xnoteSpacingMedium,
          xnoteSpacingExtraSmall,
          xnoteSpacingMedium,
          xnoteSpacingExtraSmall,
        ),
        child: Center(
          child: ConstrainedBox(
            constraints:
                const BoxConstraints(maxWidth: xnoteMaximumContentWidth),
            child: NoteListTile(
              note: note,
              notebookName: null,
              selected: _selectedIds.contains(note.id),
              selectionMode: selectionMode,
              onTap: () => selectionMode
                  ? _toggleSelection(note.id)
                  : unawaited(context.push('/notes/entries/${note.id}')),
              onLongPress: () => setState(() => _selectedIds.add(note.id)),
              trailing: _sort == NoteListSort.manual && !selectionMode
                  ? ReorderableDragStartListener(
                      index: index,
                      child: const SizedBox.square(
                        dimension: xnoteMinimumTouchTarget,
                        child: Center(
                          child: XNoteIconView(icon: XNoteIcon.reorder),
                        ),
                      ),
                    )
                  : null,
            ),
          ),
        ),
      );
    }

    if (_sort == NoteListSort.manual && !selectionMode) {
      return ReorderableListView.builder(
        key: const PageStorageKey<String>('notebook-manual-list'),
        padding: const EdgeInsets.only(bottom: 96),
        itemCount: notes.length,
        itemBuilder: itemBuilder,
        onReorderItem: (oldIndex, newIndex) =>
            unawaited(_reorder(notes, oldIndex, newIndex)),
      );
    }
    return ListView.builder(
      key: const PageStorageKey<String>('notebook-note-list'),
      padding: const EdgeInsets.only(bottom: 96),
      itemCount: notes.length,
      itemBuilder: itemBuilder,
    );
  }

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    final notebooks = ref.watch(notebooksProvider);
    final notes = ref.watch(notebookNotesProvider((widget.notebookId, _sort)));
    return switch ((notebooks, notes)) {
      (
        AsyncData(value: final notebookValues),
        AsyncData(value: final noteValues)
      ) =>
        switch (notebookValues
            .where((value) => value.id == widget.notebookId)
            .firstOrNull) {
          final notebook? => _buildPage(notebook, notebookValues, noteValues),
          null => XNoteEmptyState(
              icon: XNoteIcon.notes,
              title: '无法打开笔记本',
              message: '这个笔记本不存在或已被删除。',
              action: FilledButton(
                onPressed: () => context.go('/notes'),
                child: const Text('返回全部笔记'),
              ),
            ),
        },
      (AsyncError(), _) || (_, AsyncError()) => XNoteErrorState(
          message: '无法读取笔记本，请重试。',
          onRetry: () {
            ref.invalidate(notebooksProvider);
            ref.invalidate(
              notebookNotesProvider((widget.notebookId, _sort)),
            );
          },
        ),
      _ => const XNoteLoadingState(label: '正在加载笔记本'),
    };
  }
}
