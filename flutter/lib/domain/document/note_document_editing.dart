import '../../core/ids/id_generator.dart';
import 'inline_editing.dart';
import 'note_block.dart';
import 'note_document.dart';

// -- Type Definitions

final class EditorSelection {
  const EditorSelection({
    required this.blockId,
    this.start = 0,
    this.end = 0,
    this.tableRow,
    this.tableColumn,
  });

  final String blockId;
  final int start;
  final int end;
  final int? tableRow;
  final int? tableColumn;

  // -- Derived Values

  int get minimum => start < end ? start : end;

  int get maximum => start > end ? start : end;

  bool get isCollapsed => start == end;

  bool get isTable => tableRow != null && tableColumn != null;

  // -- Functions

  EditorSelection copyWith({
    String? blockId,
    int? start,
    int? end,
    Object? tableRow = _notProvided,
    Object? tableColumn = _notProvided,
  }) {
    return EditorSelection(
      blockId: blockId ?? this.blockId,
      start: start ?? this.start,
      end: end ?? this.end,
      tableRow:
          identical(tableRow, _notProvided) ? this.tableRow : tableRow as int?,
      tableColumn: identical(tableColumn, _notProvided)
          ? this.tableColumn
          : tableColumn as int?,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is EditorSelection &&
        other.blockId == blockId &&
        other.start == start &&
        other.end == end &&
        other.tableRow == tableRow &&
        other.tableColumn == tableColumn;
  }

  @override
  int get hashCode => Object.hash(blockId, start, end, tableRow, tableColumn);
}

final class EditorChange {
  const EditorChange({required this.document, required this.selection});

  final NoteDocument document;
  final EditorSelection selection;
}

final class InlineEditResult {
  const InlineEditResult({required this.change, required this.typingMarks});

  final EditorChange change;
  final InlineMarks typingMarks;
}

// -- Constants

const Object _notProvided = Object();

// -- Functions

Set<String> hiddenBlockIds(NoteDocument document) {
  final hidden = <String>{};
  ParagraphStyle? hidingFor;
  for (final block in document.blocks) {
    final style = block is TextBlock ? block.paragraphStyle : null;
    if (hidingFor != null) {
      final isBoundary = switch (hidingFor) {
        ParagraphStyle.heading => style == ParagraphStyle.heading,
        ParagraphStyle.subheading =>
          style == ParagraphStyle.heading || style == ParagraphStyle.subheading,
        ParagraphStyle.body || ParagraphStyle.monospace => false,
      };
      if (!isBoundary) {
        hidden.add(block.id);
        continue;
      }
      hidingFor = null;
    }
    if (block is TextBlock &&
        block.collapsed &&
        (block.paragraphStyle == ParagraphStyle.heading ||
            block.paragraphStyle == ParagraphStyle.subheading)) {
      hidingFor = block.paragraphStyle;
    }
  }
  return Set<String>.unmodifiable(hidden);
}

Map<String, int> numberedLabels(NoteDocument document) {
  final labels = <String, int>{};
  final counters = <int>[];
  for (final block in document.blocks) {
    if (block is! TextBlock || block.listMarker != ListMarker.numbered) {
      counters.clear();
      continue;
    }
    final indent = block.indent.clamp(0, maxTextIndent);
    while (counters.length > indent + 1) {
      counters.removeLast();
    }
    while (counters.length < indent + 1) {
      counters.add(0);
    }
    counters[indent] += 1;
    labels[block.id] = counters[indent];
  }
  return Map<String, int>.unmodifiable(labels);
}

EditorChange replaceSelectedText(
  NoteDocument document, {
  required EditorSelection selection,
  required String newText,
  required InlineMarks marks,
  IdGenerator? idGenerator,
}) {
  if (selection.isTable) {
    return _replaceTableCellText(document, selection, newText, marks);
  }
  final block = document.blockById(selection.blockId);
  if (block is! TextBlock) {
    return EditorChange(document: document, selection: selection);
  }
  if (newText.contains('\n')) {
    if (idGenerator == null) {
      throw ArgumentError.notNull('idGenerator');
    }
    return _replaceTextWithParagraphs(
      document,
      selection,
      newText,
      marks,
      idGenerator,
    );
  }
  final updated = block.copyWith(
    inlines: block.inlines.replacedRange(
      selection.minimum,
      selection.maximum,
      marks.toRun(newText),
    ),
  );
  final caret = selection.minimum + newText.length;
  return EditorChange(
    document: _replaceBlock(document, updated),
    selection: selection.copyWith(start: caret, end: caret),
  );
}

EditorChange splitTextBlockAt(
  NoteDocument document, {
  required EditorSelection selection,
  required String newBlockId,
}) {
  if (selection.isTable) {
    return _insertNewlineInTableCell(document, selection);
  }
  final index = document.blockIndex(selection.blockId);
  if (index < 0 || document.blocks[index] is! TextBlock) {
    return EditorChange(document: document, selection: selection);
  }
  final block = document.blocks[index] as TextBlock;
  final exitsEmptyStructure = selection.isCollapsed &&
      selection.minimum == 0 &&
      plainText(block.inlines).isEmpty &&
      (block.listMarker != ListMarker.none || block.quoted || block.indent > 0);
  if (exitsEmptyStructure) {
    return _exitStructuredBlock(document, block);
  }

  final (left, right) = block.inlines.splitAt(selection.minimum);
  final leftBlock = block.copyWith(inlines: left);
  final nextStyle = switch (block.paragraphStyle) {
    ParagraphStyle.heading || ParagraphStyle.subheading => ParagraphStyle.body,
    ParagraphStyle.body || ParagraphStyle.monospace => block.paragraphStyle,
  };
  final rightBlock = TextBlock(
    id: newBlockId,
    paragraphStyle: nextStyle,
    alignment: block.alignment,
    listMarker: block.listMarker,
    indent: block.indent,
    quoted: block.quoted,
    inlines: right,
  );
  final updatedBlocks = document.blocks.toList()
    ..[index] = leftBlock
    ..insert(index + 1, rightBlock);
  return EditorChange(
    document: document.copyWith(blocks: updatedBlocks),
    selection: EditorSelection(blockId: newBlockId),
  );
}

EditorChange deleteBackward(
  NoteDocument document, {
  required EditorSelection selection,
}) {
  if (!selection.isCollapsed) {
    return replaceSelectedText(
      document,
      selection: selection,
      newText: '',
      marks: const InlineMarks(),
    );
  }
  if (selection.isTable) {
    return _deleteBackwardInTableCell(document, selection);
  }
  if (selection.minimum > 0) {
    return replaceSelectedText(
      document,
      selection: selection.copyWith(
        start: selection.minimum - 1,
        end: selection.minimum,
      ),
      newText: '',
      marks: const InlineMarks(),
    );
  }

  final index = document.blockIndex(selection.blockId);
  if (index < 0 || document.blocks[index] is! TextBlock) {
    return EditorChange(document: document, selection: selection);
  }
  final block = document.blocks[index] as TextBlock;
  if (block.indent > 0) {
    return EditorChange(
      document:
          _replaceBlock(document, block.copyWith(indent: block.indent - 1)),
      selection: selection,
    );
  }
  if (block.listMarker != ListMarker.none) {
    return EditorChange(
      document: _replaceBlock(
        document,
        block.copyWith(listMarker: ListMarker.none, checked: false),
      ),
      selection: selection,
    );
  }
  if (block.quoted) {
    return EditorChange(
      document: _replaceBlock(document, block.copyWith(quoted: false)),
      selection: selection,
    );
  }
  if (index == 0) {
    return EditorChange(document: document, selection: selection);
  }

  final previous = document.blocks[index - 1];
  if (previous is! TextBlock) {
    if (plainText(block.inlines).isEmpty && document.blocks.length > 1) {
      final remaining = document.blocks.toList()..removeAt(index);
      return EditorChange(
        document: document.copyWith(blocks: remaining),
        selection: EditorSelection(blockId: previous.id),
      );
    }
    return EditorChange(document: document, selection: selection);
  }
  final caret = plainText(previous.inlines).length;
  final merged = previous.copyWith(
    inlines: <InlineRun>[...previous.inlines, ...block.inlines].coalesced(),
  );
  final remaining = document.blocks.toList()
    ..[index - 1] = merged
    ..removeAt(index);
  return EditorChange(
    document: document.copyWith(blocks: remaining),
    selection: EditorSelection(blockId: previous.id, start: caret, end: caret),
  );
}

EditorChange setParagraphStyle(
  NoteDocument document,
  EditorSelection selection,
  ParagraphStyle style,
) {
  final block = _selectedTextBlock(document, selection);
  if (block == null) {
    return EditorChange(document: document, selection: selection);
  }
  final isHeading =
      style == ParagraphStyle.heading || style == ParagraphStyle.subheading;
  final updated = block.copyWith(
    paragraphStyle: style,
    collapsed: isHeading ? block.collapsed : false,
    listMarker:
        style == ParagraphStyle.monospace ? ListMarker.none : block.listMarker,
    checked: style == ParagraphStyle.monospace ? false : block.checked,
  );
  return EditorChange(
    document: _replaceBlock(document, updated),
    selection: selection,
  );
}

EditorChange setListMarker(
  NoteDocument document,
  EditorSelection selection,
  ListMarker marker,
) {
  final block = _selectedTextBlock(document, selection);
  if (block == null) {
    return EditorChange(document: document, selection: selection);
  }
  final nextMarker = block.listMarker == marker ? ListMarker.none : marker;
  final updated = block.copyWith(
    listMarker: nextMarker,
    checked: nextMarker == ListMarker.checklist ? block.checked : false,
    paragraphStyle: nextMarker == ListMarker.none
        ? block.paragraphStyle
        : ParagraphStyle.body,
  );
  return EditorChange(
    document: _replaceBlock(document, updated),
    selection: selection,
  );
}

EditorChange toggleQuoted(NoteDocument document, EditorSelection selection) {
  final block = _selectedTextBlock(document, selection);
  return block == null
      ? EditorChange(document: document, selection: selection)
      : EditorChange(
          document:
              _replaceBlock(document, block.copyWith(quoted: !block.quoted)),
          selection: selection,
        );
}

EditorChange changeIndent(
  NoteDocument document,
  EditorSelection selection,
  int delta,
) {
  final block = _selectedTextBlock(document, selection);
  if (block == null) {
    return EditorChange(document: document, selection: selection);
  }
  final indent = (block.indent + delta).clamp(0, maxTextIndent);
  return EditorChange(
    document: _replaceBlock(document, block.copyWith(indent: indent)),
    selection: selection,
  );
}

EditorChange setAlignment(
  NoteDocument document,
  EditorSelection selection,
  TextAlignment alignment,
) {
  final block = _selectedTextBlock(document, selection);
  return block == null
      ? EditorChange(document: document, selection: selection)
      : EditorChange(
          document:
              _replaceBlock(document, block.copyWith(alignment: alignment)),
          selection: selection,
        );
}

EditorChange toggleChecked(NoteDocument document, EditorSelection selection) {
  final block = _selectedTextBlock(document, selection);
  if (block == null || block.listMarker != ListMarker.checklist) {
    return EditorChange(document: document, selection: selection);
  }
  return EditorChange(
    document: _replaceBlock(document, block.copyWith(checked: !block.checked)),
    selection: selection,
  );
}

EditorChange toggleCollapsed(NoteDocument document, EditorSelection selection) {
  final block = _selectedTextBlock(document, selection);
  if (block == null ||
      (block.paragraphStyle != ParagraphStyle.heading &&
          block.paragraphStyle != ParagraphStyle.subheading)) {
    return EditorChange(document: document, selection: selection);
  }
  return EditorChange(
    document: _replaceBlock(
      document,
      block.copyWith(collapsed: !block.collapsed),
    ),
    selection: selection,
  );
}

InlineEditResult applyInlineMark(
  NoteDocument document, {
  required EditorSelection selection,
  required InlineMark mark,
  required InlineMarks typingMarks,
}) {
  final inlines = _selectedInlines(document, selection);
  if (inlines == null) {
    return InlineEditResult(
      change: EditorChange(document: document, selection: selection),
      typingMarks: typingMarks,
    );
  }
  final enabled = selection.isCollapsed
      ? !typingMarks.has(mark)
      : !inlines.rangeHasMark(selection.minimum, selection.maximum, mark);
  final nextTypingMarks = typingMarks.toggle(mark, enabled: enabled);
  if (selection.isCollapsed) {
    return InlineEditResult(
      change: EditorChange(document: document, selection: selection),
      typingMarks: nextTypingMarks,
    );
  }
  final updated = inlines.mapRange(
    selection.minimum,
    selection.maximum,
    (run) => withMarks(
      run,
      marksOf(run).toggle(mark, enabled: enabled),
    ),
  );
  return InlineEditResult(
    change: EditorChange(
      document: _replaceSelectedInlines(document, selection, updated),
      selection: selection,
    ),
    typingMarks: nextTypingMarks,
  );
}

InlineEditResult setLink(
  NoteDocument document, {
  required EditorSelection selection,
  required String? url,
  required InlineMarks typingMarks,
}) {
  final inlines = _selectedInlines(document, selection);
  if (inlines == null) {
    return InlineEditResult(
      change: EditorChange(document: document, selection: selection),
      typingMarks: typingMarks,
    );
  }
  final trimmed = url?.trim();
  final normalized = trimmed == null || trimmed.isEmpty ? null : trimmed;
  final linkedMarks = typingMarks.copyWith(linkUrl: normalized);
  if (selection.isCollapsed) {
    final change = normalized == null
        ? EditorChange(document: document, selection: selection)
        : replaceSelectedText(
            document,
            selection: selection,
            newText: normalized,
            marks: linkedMarks,
          );
    return InlineEditResult(change: change, typingMarks: linkedMarks);
  }
  final updated = inlines.mapRange(
    selection.minimum,
    selection.maximum,
    (run) => run.copyWith(linkUrl: normalized),
  );
  return InlineEditResult(
    change: EditorChange(
      document: _replaceSelectedInlines(document, selection, updated),
      selection: selection,
    ),
    typingMarks: linkedMarks,
  );
}

EditorChange deleteBlock(
  NoteDocument document, {
  required String blockId,
  required String fallbackTextId,
}) {
  final removedIndex = document.blockIndex(blockId);
  if (removedIndex < 0) {
    if (document.blocks.isEmpty) {
      final fallback = emptyBodyBlock(fallbackTextId);
      return EditorChange(
        document: document.copyWith(blocks: <NoteBlock>[fallback]),
        selection: EditorSelection(blockId: fallback.id),
      );
    }
    return EditorChange(
      document: document,
      selection: EditorSelection(blockId: document.blocks.first.id),
    );
  }
  final remaining = document.blocks.toList()..removeAt(removedIndex);
  if (remaining.isEmpty) {
    final fallback = emptyBodyBlock(fallbackTextId);
    return EditorChange(
      document: document.copyWith(blocks: <NoteBlock>[fallback]),
      selection: EditorSelection(blockId: fallback.id),
    );
  }
  final nextIndex = removedIndex == 0 ? 0 : removedIndex - 1;
  return EditorChange(
    document: document.copyWith(blocks: remaining),
    selection: EditorSelection(blockId: remaining[nextIndex].id),
  );
}

NoteDocument ensureDocumentNotEmpty(
  NoteDocument document, {
  required String fallbackTextId,
}) {
  return document.blocks.isEmpty
      ? document.copyWith(blocks: <NoteBlock>[emptyBodyBlock(fallbackTextId)])
      : document;
}

NoteDocument _replaceBlock(NoteDocument document, NoteBlock replacement) {
  return document.copyWith(
    blocks: <NoteBlock>[
      for (final block in document.blocks)
        if (block.id == replacement.id) replacement else block,
    ],
  );
}

TextBlock? _selectedTextBlock(
  NoteDocument document,
  EditorSelection selection,
) {
  if (selection.isTable) {
    return null;
  }
  final block = document.blockById(selection.blockId);
  return block is TextBlock ? block : null;
}

TableBlock? _selectedTable(
  NoteDocument document,
  EditorSelection selection,
) {
  final block = document.blockById(selection.blockId);
  return block is TableBlock ? block : null;
}

List<InlineRun>? _selectedInlines(
  NoteDocument document,
  EditorSelection selection,
) {
  if (!selection.isTable) {
    return _selectedTextBlock(document, selection)?.inlines;
  }
  final table = _selectedTable(document, selection);
  return table?.cellAt(selection.tableRow!, selection.tableColumn!)?.inlines;
}

NoteDocument _replaceSelectedInlines(
  NoteDocument document,
  EditorSelection selection,
  List<InlineRun> inlines,
) {
  if (!selection.isTable) {
    final block = _selectedTextBlock(document, selection);
    return block == null
        ? document
        : _replaceBlock(document, block.copyWith(inlines: inlines));
  }
  final table = _selectedTable(document, selection);
  return table == null
      ? document
      : _replaceBlock(
          document,
          table.updateCell(
            selection.tableRow!,
            selection.tableColumn!,
            inlines,
          ),
        );
}

EditorChange _replaceTableCellText(
  NoteDocument document,
  EditorSelection selection,
  String newText,
  InlineMarks marks,
) {
  final inlines = _selectedInlines(document, selection);
  if (inlines == null) {
    return EditorChange(document: document, selection: selection);
  }
  final updated = inlines.replacedRange(
    selection.minimum,
    selection.maximum,
    marks.toRun(newText),
  );
  final caret = selection.minimum + newText.length;
  return EditorChange(
    document: _replaceSelectedInlines(document, selection, updated),
    selection: selection.copyWith(start: caret, end: caret),
  );
}

EditorChange _insertNewlineInTableCell(
  NoteDocument document,
  EditorSelection selection,
) {
  final marks =
      _selectedInlines(document, selection)?.marksAt(selection.minimum) ??
          const InlineMarks();
  return _replaceTableCellText(document, selection, '\n', marks);
}

EditorChange _deleteBackwardInTableCell(
  NoteDocument document,
  EditorSelection selection,
) {
  if (selection.minimum == 0) {
    return EditorChange(document: document, selection: selection);
  }
  return _replaceTableCellText(
    document,
    selection.copyWith(
      start: selection.minimum - 1,
      end: selection.minimum,
    ),
    '',
    const InlineMarks(),
  );
}

EditorChange _replaceTextWithParagraphs(
  NoteDocument document,
  EditorSelection selection,
  String newText,
  InlineMarks marks,
  IdGenerator idGenerator,
) {
  final lines = newText.split('\n');
  var current = replaceSelectedText(
    document,
    selection: selection,
    newText: lines.first,
    marks: marks,
  );
  for (final line in lines.skip(1)) {
    current = splitTextBlockAt(
      current.document,
      selection: current.selection,
      newBlockId: idGenerator.nextId(),
    );
    if (line.isNotEmpty) {
      current = replaceSelectedText(
        current.document,
        selection: current.selection,
        newText: line,
        marks: marks,
      );
    }
  }
  return current;
}

EditorChange _exitStructuredBlock(NoteDocument document, TextBlock block) {
  final TextBlock updated;
  if (block.indent > 0) {
    updated = block.copyWith(indent: block.indent - 1);
  } else if (block.listMarker != ListMarker.none) {
    updated = block.copyWith(listMarker: ListMarker.none, checked: false);
  } else {
    updated = block.copyWith(quoted: false);
  }
  return EditorChange(
    document: _replaceBlock(document, updated),
    selection: EditorSelection(blockId: block.id),
  );
}
