import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import '../../../app/providers/xnote_providers.dart';
import '../../../design/common/xnote_states.dart';
import '../../../design/icons/xnote_icon.dart';
import '../../../design/tokens/xnote_tokens.dart';
import '../../../domain/document/note_block.dart';
import '../../../domain/model/note.dart';
import '../../../domain/model/notebook.dart';
import '../../../domain/repositories/note_repository.dart';
import 'note_overlays.dart';

// -- Type Definitions

enum BasicEditorSaveStatus { idle, saving, saved, error }

final class BasicNoteEditorPage extends ConsumerStatefulWidget {
  const BasicNoteEditorPage({required this.noteId, super.key});

  final String noteId;

  // -- Lifecycle Hooks

  @override
  ConsumerState<BasicNoteEditorPage> createState() =>
      _BasicNoteEditorPageState();
}

final class _BasicNoteEditorPageState extends ConsumerState<BasicNoteEditorPage>
    with WidgetsBindingObserver {
  // -- Constants

  static const _autoSaveDelay = Duration(milliseconds: 450);

  // -- State and Variables

  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _bodyController = TextEditingController();
  late final NoteRepository _repository;
  Note? _note;
  Object? _loadError;
  Timer? _autoSaveTimer;
  Future<bool>? _activeSave;
  BasicEditorSaveStatus _saveStatus = BasicEditorSaveStatus.idle;
  bool _loading = true;
  bool _dirty = false;
  bool _disposed = false;
  int _editVersion = 0;

  // -- Derived Values

  bool get _canLeaveImmediately => !_dirty && _activeSave == null;

  // -- Functions

  Future<void> _load() async {
    try {
      final note = await _repository.getNote(widget.noteId);
      if (_disposed) {
        return;
      }
      if (note == null || note.isTrashed || note.kind != NoteKind.rich) {
        setState(() {
          _note = null;
          _loading = false;
        });
        return;
      }
      _titleController.text = note.title;
      _bodyController.text = _bodyText(note);
      setState(() {
        _note = note;
        _loading = false;
      });
    } catch (error) {
      if (!_disposed) {
        setState(() {
          _loadError = error;
          _loading = false;
        });
      }
    }
  }

  void _handleTextChanged() {
    if (_loading || _disposed || _note == null) {
      return;
    }
    _editVersion += 1;
    _dirty = true;
    if (_saveStatus != BasicEditorSaveStatus.saving) {
      _setSaveStatus(BasicEditorSaveStatus.idle);
    }
    _scheduleAutoSave();
  }

  void _scheduleAutoSave() {
    _autoSaveTimer?.cancel();
    _autoSaveTimer = Timer(_autoSaveDelay, () {
      unawaited(_saveNow());
    });
  }

  Future<bool> _saveNow() {
    _autoSaveTimer?.cancel();
    final active = _activeSave;
    if (active != null) {
      return active;
    }
    late final Future<bool> operation;
    operation = _performSave().whenComplete(() {
      if (identical(_activeSave, operation)) {
        _activeSave = null;
      }
    });
    _activeSave = operation;
    return operation;
  }

  Future<bool> _performSave() async {
    while (_dirty && !_disposed) {
      final version = _editVersion;
      final draft = _draftNote();
      if (draft == null) {
        return false;
      }
      _setSaveStatus(BasicEditorSaveStatus.saving);
      try {
        final saved = await _repository.saveNote(draft);
        _note = saved;
      } catch (_) {
        _setSaveStatus(BasicEditorSaveStatus.error);
        return false;
      }
      if (version == _editVersion) {
        _dirty = false;
        _setSaveStatus(BasicEditorSaveStatus.saved);
      }
    }
    return !_dirty;
  }

  Note? _draftNote() {
    final note = _note;
    final document = note?.document;
    if (note == null || document == null) {
      return null;
    }
    var didReplaceBody = false;
    final blocks = <NoteBlock>[];
    for (final block in document.blocks) {
      if (!didReplaceBody && block is TextBlock) {
        blocks.add(
          block.copyWith(
            inlines: <InlineRun>[
              if (_bodyController.text.isNotEmpty)
                InlineRun(text: _bodyController.text),
            ],
          ),
        );
        didReplaceBody = true;
      } else {
        blocks.add(block);
      }
    }
    if (!didReplaceBody) {
      return note.copyWith(title: _titleController.text);
    }
    return note.copyWith(
      title: _titleController.text,
      document: document.copyWith(blocks: blocks),
    );
  }

  void _setSaveStatus(BasicEditorSaveStatus status) {
    if (_disposed || !mounted) {
      return;
    }
    setState(() => _saveStatus = status);
  }

  Future<void> _handleBlockedBack() async {
    if (await _saveNow() && mounted) {
      context.pop();
    }
  }

  Future<void> _moveNote(List<Notebook> notebooks) async {
    if (!await _saveNow() || !mounted) {
      return;
    }
    final selection = await showNotebookSelectionSheet(
      context: context,
      notebooks: notebooks,
      includeAll: false,
      selectedNotebookId: _note?.notebookId,
      unfiledSelected: _note?.notebookId == null,
    );
    if (!mounted || selection == null) {
      return;
    }
    final notebookId = selection.kind == NotebookSelectionKind.notebook
        ? selection.notebookId
        : null;
    await _repository.moveNotes(<String>[widget.noteId], notebookId);
    _note = await _repository.getNote(widget.noteId);
    if (mounted) {
      showNoteToast(context, '已移动笔记');
      setState(() {});
    }
  }

  Future<void> _trashNote() async {
    FocusManager.instance.primaryFocus?.unfocus();
    final confirmed = await showDestructiveConfirmation(
      context: context,
      title: '移入回收站',
      message: '这篇笔记将在回收站保留 30 天。',
      confirmLabel: '移入回收站',
    );
    if (!mounted || !confirmed || !await _saveNow()) {
      return;
    }
    await _repository.trashNotes(<String>[widget.noteId]);
    if (mounted) {
      showNoteToast(context, '已移入回收站');
      context.go('/notes');
    }
  }

  Widget _buildSaveStatusPopover() {
    return GlassPopover(
      key: const Key('editor-save-status-popover'),
      popoverWidth: 220,
      triggerBuilder: (context, togglePopover) => GlassIconButton(
        key: const Key('editor-save-status'),
        icon: XNoteIconView(
          icon: _saveStatus == BasicEditorSaveStatus.error
              ? XNoteIcon.more
              : XNoteIcon.check,
        ),
        onPressed: togglePopover,
        semanticLabel: _saveStatusLabel(),
        quality: GlassQuality.standard,
      ),
      contentBuilder: (context, close) => Padding(
        padding: const EdgeInsets.all(xnoteSpacingMedium),
        child: Text(_saveStatusDescription()),
      ),
    );
  }

  String _saveStatusLabel() => switch (_saveStatus) {
        BasicEditorSaveStatus.idle => _dirty ? '尚未保存' : '已保存',
        BasicEditorSaveStatus.saving => '保存中',
        BasicEditorSaveStatus.saved => '已保存',
        BasicEditorSaveStatus.error => '保存失败',
      };

  String _saveStatusDescription() => switch (_saveStatus) {
        BasicEditorSaveStatus.idle => _dirty ? '更改将在片刻后自动保存。' : '所有更改均已保存。',
        BasicEditorSaveStatus.saving => '正在写入本地笔记库。',
        BasicEditorSaveStatus.saved => '更改已写入本地笔记库。',
        BasicEditorSaveStatus.error => '未能保存笔记，请检查后重试。',
      };

  Widget _buildEditor(List<Notebook> notebooks) {
    return PopScope<void>(
      canPop: _canLeaveImmediately,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) {
          unawaited(_handleBlockedBack());
        }
      },
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          xnoteSpacingMedium,
          xnoteSpacingSmall,
          xnoteSpacingMedium,
          xnoteSpacingSmall,
        ),
        child: Center(
          child: ConstrainedBox(
            constraints:
                const BoxConstraints(maxWidth: xnoteMaximumContentWidth),
            child: Column(
              children: <Widget>[
                TextField(
                  key: const Key('basic-editor-title'),
                  controller: _titleController,
                  maxLines: 2,
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    hintText: '标题',
                    filled: false,
                    border: InputBorder.none,
                  ),
                  style: Theme.of(context).textTheme.headlineLarge,
                ),
                Expanded(
                  child: TextField(
                    key: const Key('basic-editor-body'),
                    controller: _bodyController,
                    expands: true,
                    minLines: null,
                    maxLines: null,
                    keyboardType: TextInputType.multiline,
                    textInputAction: TextInputAction.newline,
                    decoration: const InputDecoration(
                      hintText: '开始书写',
                      filled: false,
                      border: InputBorder.none,
                    ),
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                ),
                SafeArea(
                  top: false,
                  child: GlassToolbar(
                    key: const Key('basic-editor-toolbar'),
                    quality: GlassQuality.standard,
                    children: <Widget>[
                      _buildSaveStatusPopover(),
                      GlassIconButton(
                        key: const Key('move-current-note'),
                        icon: const XNoteIconView(icon: XNoteIcon.notes),
                        onPressed: () => unawaited(_moveNote(notebooks)),
                        semanticLabel: '移动笔记',
                        quality: GlassQuality.standard,
                      ),
                      GlassIconButton(
                        key: const Key('trash-current-note'),
                        icon: const XNoteIconView(icon: XNoteIcon.delete),
                        onPressed: () => unawaited(_trashNote()),
                        semanticLabel: '移入回收站',
                        quality: GlassQuality.standard,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  String _bodyText(Note note) {
    final document = note.document;
    if (document == null) {
      return '';
    }
    for (final block in document.blocks) {
      if (block is TextBlock) {
        return plainText(block.inlines);
      }
    }
    return '';
  }

  // -- Lifecycle Hooks

  @override
  void initState() {
    super.initState();
    _repository = ref.read(noteRepositoryProvider);
    _titleController.addListener(_handleTextChanged);
    _bodyController.addListener(_handleTextChanged);
    WidgetsBinding.instance.addObserver(this);
    unawaited(_load());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      unawaited(_saveNow());
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const XNoteLoadingState(label: '正在打开笔记');
    }
    if (_loadError != null) {
      return XNoteErrorState(
        message: '无法打开笔记，请重试。',
        onRetry: () {
          setState(() {
            _loading = true;
            _loadError = null;
          });
          unawaited(_load());
        },
      );
    }
    if (_note == null) {
      return XNoteEmptyState(
        icon: XNoteIcon.notes,
        title: '无法打开笔记',
        message: '这篇笔记不存在或已进入回收站。',
        action: FilledButton(
          onPressed: () => context.go('/notes'),
          child: const Text('返回全部笔记'),
        ),
      );
    }
    final notebooks = ref.watch(notebooksProvider);
    return switch (notebooks) {
      AsyncData(value: final values) => _buildEditor(values),
      AsyncError() => XNoteErrorState(
          message: '无法读取笔记本，请重试。',
          onRetry: () => ref.invalidate(notebooksProvider),
        ),
      _ => const XNoteLoadingState(label: '正在加载笔记本'),
    };
  }

  @override
  void dispose() {
    _disposed = true;
    WidgetsBinding.instance.removeObserver(this);
    _autoSaveTimer?.cancel();
    if (_dirty && _activeSave == null) {
      final draft = _draftNote();
      if (draft != null) {
        unawaited(_repository.saveNote(draft));
      }
    }
    _titleController
      ..removeListener(_handleTextChanged)
      ..dispose();
    _bodyController
      ..removeListener(_handleTextChanged)
      ..dispose();
    super.dispose();
  }
}
