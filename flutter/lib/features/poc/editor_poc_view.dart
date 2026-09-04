import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import 'rich_text_editing_controller.dart';

// -- Type Definitions

@immutable
class EditorPocDraft {
  const EditorPocDraft({
    required this.title,
    required this.heading,
    required this.paragraph,
    required this.boldRanges,
    required this.listItem,
    required this.table,
    required this.headingCollapsed,
  });

  final String title;
  final String heading;
  final String paragraph;
  final List<TextRange> boldRanges;
  final String listItem;
  final List<List<String>> table;
  final bool headingCollapsed;
}

class EditorPocView extends StatefulWidget {
  const EditorPocView({super.key, this.onDraftSaved});

  final ValueChanged<EditorPocDraft>? onDraftSaved;

  // -- Lifecycle Hooks

  @override
  State<EditorPocView> createState() => _EditorPocViewState();
}

class _EditorPocViewState extends State<EditorPocView>
    with WidgetsBindingObserver {
  // -- Constants

  static const _autosaveDelay = Duration(milliseconds: 450);
  static const _toolbarBottomInset = 112.0;

  // -- State and Variables

  final _titleController = TextEditingController(text: '周五的产品想法');
  final _headingController = TextEditingController(text: '下一步');
  final _paragraphController = RichTextEditingController(
    text: '把零散灵感收进一个安静的页面。',
  );
  final _listController = TextEditingController(
    text: '验证中文拼音、Emoji 🙂 与换行',
  );
  final _paragraphFocusNode = FocusNode(debugLabel: 'paragraph');
  final _undoController = UndoHistoryController();
  final List<List<TextEditingController>> _tableControllers =
      <List<TextEditingController>>[];
  final List<List<FocusNode>> _tableFocusNodes = <List<FocusNode>>[];
  Timer? _autosaveTimer;
  bool _headingCollapsed = false;
  bool _isDirty = false;
  int _savedRevision = 0;

  // -- Derived Values

  int get _rowCount => _tableControllers.length;

  int get _columnCount =>
      _tableControllers.isEmpty ? 0 : _tableControllers.first.length;

  String get _saveStatus {
    if (_isDirty) {
      return '等待自动保存';
    }
    if (_savedRevision == 0) {
      return '尚未改动';
    }
    return '已保存 r$_savedRevision';
  }

  // -- Functions

  TextEditingController _createCellController(String text) {
    final controller = TextEditingController(text: text);
    controller.addListener(_handleDocumentChanged);
    return controller;
  }

  FocusNode _createCellFocusNode(int row, int column) {
    return FocusNode(debugLabel: 'table-$row-$column');
  }

  void _createInitialTable() {
    const initialValues = <List<String>>[
      <String>['风险', '验证'],
      <String>['输入法', '待验证'],
    ];
    for (var row = 0; row < initialValues.length; row += 1) {
      _tableControllers.add(<TextEditingController>[
        for (final value in initialValues[row]) _createCellController(value),
      ]);
      _tableFocusNodes.add(<FocusNode>[
        for (var column = 0; column < initialValues[row].length; column += 1)
          _createCellFocusNode(row, column),
      ]);
    }
  }

  void _scheduleAutosave() {
    _autosaveTimer?.cancel();
    _autosaveTimer = Timer(_autosaveDelay, _saveNow);
  }

  void _saveNow() {
    _autosaveTimer?.cancel();
    if (!_isDirty) {
      return;
    }
    final draft = _createDraft();
    _savedRevision += 1;
    _isDirty = false;
    widget.onDraftSaved?.call(draft);
    if (mounted) {
      setState(() {});
    }
  }

  EditorPocDraft _createDraft() {
    return EditorPocDraft(
      title: _titleController.text,
      heading: _headingController.text,
      paragraph: _paragraphController.text,
      boldRanges: List<TextRange>.unmodifiable(
        _paragraphController.boldRanges,
      ),
      listItem: _listController.text,
      table: List<List<String>>.unmodifiable(
        <List<String>>[
          for (final row in _tableControllers)
            List<String>.unmodifiable(
              <String>[for (final controller in row) controller.text],
            ),
        ],
      ),
      headingCollapsed: _headingCollapsed,
    );
  }

  void _markStructureChanged(VoidCallback mutation) {
    setState(mutation);
    _handleDocumentChanged();
  }

  void _toggleHeadingCollapsed() {
    _markStructureChanged(() {
      _headingCollapsed = !_headingCollapsed;
    });
  }

  void _toggleBold() {
    _paragraphController.toggleBold();
  }

  void _addRow() {
    final newRowIndex = _rowCount;
    _markStructureChanged(() {
      _tableControllers.add(<TextEditingController>[
        for (var column = 0; column < _columnCount; column += 1)
          _createCellController(''),
      ]);
      _tableFocusNodes.add(<FocusNode>[
        for (var column = 0; column < _columnCount; column += 1)
          _createCellFocusNode(newRowIndex, column),
      ]);
    });
    _requestCellFocus(newRowIndex, 0);
  }

  void _removeRow() {
    if (_rowCount <= 1) {
      return;
    }
    _markStructureChanged(() {
      final removedControllers = _tableControllers.removeLast();
      final removedFocusNodes = _tableFocusNodes.removeLast();
      for (final controller in removedControllers) {
        controller
          ..removeListener(_handleDocumentChanged)
          ..dispose();
      }
      for (final focusNode in removedFocusNodes) {
        focusNode.dispose();
      }
    });
    _requestCellFocus(_rowCount - 1, 0);
  }

  void _addColumn() {
    final newColumnIndex = _columnCount;
    _markStructureChanged(() {
      for (var row = 0; row < _rowCount; row += 1) {
        _tableControllers[row].add(_createCellController(''));
        _tableFocusNodes[row].add(_createCellFocusNode(row, newColumnIndex));
      }
    });
    _requestCellFocus(0, newColumnIndex);
  }

  void _removeColumn() {
    if (_columnCount <= 1) {
      return;
    }
    _markStructureChanged(() {
      for (var row = 0; row < _rowCount; row += 1) {
        _tableControllers[row].removeLast()
          ..removeListener(_handleDocumentChanged)
          ..dispose();
        _tableFocusNodes[row].removeLast().dispose();
      }
    });
    _requestCellFocus(0, _columnCount - 1);
  }

  void _requestCellFocus(int row, int column) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || row >= _rowCount || column >= _columnCount) {
        return;
      }
      _tableFocusNodes[row][column].requestFocus();
    });
  }

  Widget _buildEditor(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return SingleChildScrollView(
      key: const Key('editor-scroll-view'),
      padding: const EdgeInsets.fromLTRB(20, 80, 20, 220),
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              TextField(
                key: const Key('editor-title'),
                controller: _titleController,
                textInputAction: TextInputAction.next,
                style: textTheme.headlineMedium,
                decoration: const InputDecoration(
                  hintText: '标题',
                  border: InputBorder.none,
                ),
              ),
              const SizedBox(height: 20),
              Row(
                children: <Widget>[
                  Expanded(
                    child: TextField(
                      key: const Key('editor-heading'),
                      controller: _headingController,
                      style: textTheme.titleLarge,
                      decoration: const InputDecoration(
                        hintText: '小标题',
                        border: InputBorder.none,
                      ),
                    ),
                  ),
                  GlassIconButton(
                    key: const Key('toggle-heading-collapse'),
                    icon: Icon(
                      _headingCollapsed
                          ? CupertinoIcons.chevron_down
                          : CupertinoIcons.chevron_up,
                    ),
                    onPressed: _toggleHeadingCollapsed,
                    quality: GlassQuality.standard,
                    semanticLabel: _headingCollapsed ? '展开标题内容' : '折叠标题内容',
                    useOwnLayer: true,
                  ),
                ],
              ),
              AnimatedCrossFade(
                firstChild: _buildExpandedContent(context),
                secondChild: const SizedBox.shrink(),
                crossFadeState: _headingCollapsed
                    ? CrossFadeState.showSecond
                    : CrossFadeState.showFirst,
                duration: MediaQuery.disableAnimationsOf(context)
                    ? Duration.zero
                    : const Duration(milliseconds: 180),
              ),
              const SizedBox(height: 12),
              Text(
                _saveStatus,
                key: const Key('editor-save-status'),
                textAlign: TextAlign.end,
                style: textTheme.labelMedium,
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildExpandedContent(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        TextField(
          key: const Key('editor-paragraph'),
          controller: _paragraphController,
          focusNode: _paragraphFocusNode,
          undoController: _undoController,
          minLines: 3,
          maxLines: null,
          keyboardType: TextInputType.multiline,
          decoration: const InputDecoration(
            labelText: '正文块 1',
            alignLabelWithHint: true,
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          key: const Key('editor-list-item'),
          controller: _listController,
          minLines: 2,
          maxLines: null,
          keyboardType: TextInputType.multiline,
          decoration: const InputDecoration(
            labelText: '• 正文块 2（列表）',
            alignLabelWithHint: true,
          ),
        ),
        const SizedBox(height: 24),
        _buildTableHeader(context),
        const SizedBox(height: 8),
        _buildTable(),
      ],
    );
  }

  Widget _buildTableHeader(BuildContext context) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: <Widget>[
        Text(
          '$_rowCount×$_columnCount 表格',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        GlassIconButton(
          key: const Key('add-table-row'),
          icon: const Icon(CupertinoIcons.arrow_down_to_line),
          onPressed: _addRow,
          quality: GlassQuality.standard,
          semanticLabel: '增加表格行',
          useOwnLayer: true,
        ),
        GlassIconButton(
          key: const Key('remove-table-row'),
          icon: const Icon(CupertinoIcons.minus),
          onPressed: _rowCount > 1 ? _removeRow : null,
          quality: GlassQuality.standard,
          semanticLabel: '删除表格行',
          useOwnLayer: true,
        ),
        GlassIconButton(
          key: const Key('add-table-column'),
          icon: const Icon(CupertinoIcons.arrow_right_to_line),
          onPressed: _addColumn,
          quality: GlassQuality.standard,
          semanticLabel: '增加表格列',
          useOwnLayer: true,
        ),
        GlassIconButton(
          key: const Key('remove-table-column'),
          icon: const Icon(CupertinoIcons.minus_square),
          onPressed: _columnCount > 1 ? _removeColumn : null,
          quality: GlassQuality.standard,
          semanticLabel: '删除表格列',
          useOwnLayer: true,
        ),
      ],
    );
  }

  Widget _buildTable() {
    return Table(
      key: const Key('editor-table'),
      border: TableBorder.all(color: Theme.of(context).dividerColor),
      defaultVerticalAlignment: TableCellVerticalAlignment.middle,
      children: <TableRow>[
        for (var row = 0; row < _rowCount; row += 1)
          TableRow(
            children: <Widget>[
              for (var column = 0; column < _columnCount; column += 1)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: TextField(
                    key: Key('table-cell-$row-$column'),
                    controller: _tableControllers[row][column],
                    focusNode: _tableFocusNodes[row][column],
                    decoration: const InputDecoration(border: InputBorder.none),
                  ),
                ),
            ],
          ),
      ],
    );
  }

  Widget _buildToolbar() {
    return GlassToolbar(
      key: const Key('editor-glass-toolbar'),
      quality: GlassQuality.standard,
      height: 52,
      children: <Widget>[
        Expanded(
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: <Widget>[
                GlassIconButton(
                  key: const Key('toggle-bold'),
                  icon: const Icon(CupertinoIcons.bold),
                  onPressed: _toggleBold,
                  quality: GlassQuality.standard,
                  semanticLabel: '粗体',
                ),
                const SizedBox(width: 8),
                ValueListenableBuilder<UndoHistoryValue>(
                  valueListenable: _undoController,
                  builder: (context, history, child) => GlassIconButton(
                    key: const Key('undo-editor'),
                    icon: const Icon(CupertinoIcons.arrow_uturn_left),
                    onPressed: history.canUndo ? _undoController.undo : null,
                    quality: GlassQuality.standard,
                    semanticLabel: '撤销',
                  ),
                ),
                const SizedBox(width: 8),
                ValueListenableBuilder<UndoHistoryValue>(
                  valueListenable: _undoController,
                  builder: (context, history, child) => GlassIconButton(
                    key: const Key('redo-editor'),
                    icon: const Icon(CupertinoIcons.arrow_uturn_right),
                    onPressed: history.canRedo ? _undoController.redo : null,
                    quality: GlassQuality.standard,
                    semanticLabel: '重做',
                  ),
                ),
                const SizedBox(width: 8),
                GlassIconButton(
                  key: const Key('save-editor'),
                  icon: const Icon(CupertinoIcons.check_mark),
                  onPressed: _saveNow,
                  quality: GlassQuality.standard,
                  semanticLabel: '立即保存',
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  void _disposeTable() {
    for (final row in _tableControllers) {
      for (final controller in row) {
        controller
          ..removeListener(_handleDocumentChanged)
          ..dispose();
      }
    }
    for (final row in _tableFocusNodes) {
      for (final focusNode in row) {
        focusNode.dispose();
      }
    }
  }

  // -- Listeners

  void _handleDocumentChanged() {
    if (!mounted) {
      return;
    }
    if (!_isDirty) {
      setState(() {
        _isDirty = true;
      });
    }
    _scheduleAutosave();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        return;
      case AppLifecycleState.detached:
      case AppLifecycleState.inactive:
      case AppLifecycleState.hidden:
      case AppLifecycleState.paused:
        _saveNow();
    }
  }

  // -- Lifecycle Hooks

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _createInitialTable();
    _titleController.addListener(_handleDocumentChanged);
    _headingController.addListener(_handleDocumentChanged);
    _paragraphController.addListener(_handleDocumentChanged);
    _listController.addListener(_handleDocumentChanged);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _autosaveTimer?.cancel();
    if (_isDirty) {
      widget.onDraftSaved?.call(_createDraft());
    }
    _titleController
      ..removeListener(_handleDocumentChanged)
      ..dispose();
    _headingController
      ..removeListener(_handleDocumentChanged)
      ..dispose();
    _paragraphController
      ..removeListener(_handleDocumentChanged)
      ..dispose();
    _listController
      ..removeListener(_handleDocumentChanged)
      ..dispose();
    _paragraphFocusNode.dispose();
    _undoController.dispose();
    _disposeTable();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      type: MaterialType.transparency,
      child: PopScope<Object?>(
        onPopInvokedWithResult: (didPop, result) => _saveNow(),
        child: Stack(
          children: <Widget>[
            Positioned.fill(child: _buildEditor(context)),
            Positioned(
              left: 12,
              right: 12,
              bottom: _toolbarBottomInset,
              child: _buildToolbar(),
            ),
          ],
        ),
      ),
    );
  }
}
