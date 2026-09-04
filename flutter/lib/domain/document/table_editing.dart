import 'note_block.dart';
import 'note_document.dart';
import 'note_document_editing.dart';

// -- Functions

EditorChange insertTable(
  NoteDocument document, {
  required EditorSelection selection,
  required String tableId,
}) {
  final table = emptyTableBlock(tableId);
  final index = document.blockIndex(selection.blockId);
  final updated = document.blocks.toList();
  if (index < 0) {
    updated.add(table);
  } else {
    updated.insert(index + 1, table);
  }
  return EditorChange(
    document: document.copyWith(blocks: updated),
    selection: EditorSelection(
      blockId: tableId,
      tableRow: 0,
      tableColumn: 0,
    ),
  );
}

EditorChange insertTableRow(
  NoteDocument document, {
  required EditorSelection selection,
  required int afterRow,
}) {
  final table = _selectedTable(document, selection);
  if (table == null) {
    return EditorChange(document: document, selection: selection);
  }
  final columnCount = table.columnCount < 1 ? 1 : table.columnCount;
  final rows = table.rows.toList();
  final insertAt = (afterRow + 1).clamp(0, rows.length);
  rows.insert(
    insertAt,
    TableRow(
      cells: <TableCell>[
        for (var column = 0; column < columnCount; column += 1) TableCell(),
      ],
    ),
  );
  return EditorChange(
    document: _replaceBlock(document, table.copyWith(rows: rows)),
    selection: selection.copyWith(
      tableRow: insertAt,
      tableColumn: selection.tableColumn ?? 0,
      start: 0,
      end: 0,
    ),
  );
}

EditorChange insertTableColumn(
  NoteDocument document, {
  required EditorSelection selection,
  required int afterColumn,
}) {
  final table = _selectedTable(document, selection);
  if (table == null) {
    return EditorChange(document: document, selection: selection);
  }
  final insertAt = (afterColumn + 1).clamp(0, table.columnCount);
  final rows = <TableRow>[
    for (final row in table.rows)
      TableRow(
        cells: <TableCell>[
          ...row.cells.take(insertAt),
          for (var missing = row.cells.length; missing < insertAt; missing += 1)
            TableCell(),
          TableCell(),
          ...row.cells.skip(insertAt),
        ],
      ),
  ];
  return EditorChange(
    document: _replaceBlock(document, table.copyWith(rows: rows)),
    selection: selection.copyWith(
      tableRow: selection.tableRow ?? 0,
      tableColumn: insertAt,
      start: 0,
      end: 0,
    ),
  );
}

EditorChange deleteTableRow(
  NoteDocument document, {
  required EditorSelection selection,
  required int row,
  required String fallbackTextId,
}) {
  final table = _selectedTable(document, selection);
  if (table == null || row < 0 || row >= table.rows.length) {
    return EditorChange(document: document, selection: selection);
  }
  if (table.rows.length == 1) {
    return deleteBlock(
      document,
      blockId: table.id,
      fallbackTextId: fallbackTextId,
    );
  }
  final rows = <TableRow>[
    for (var index = 0; index < table.rows.length; index += 1)
      if (index != row) table.rows[index],
  ];
  final nextRow = row.clamp(0, rows.length - 1);
  return EditorChange(
    document: _replaceBlock(document, table.copyWith(rows: rows)),
    selection: selection.copyWith(tableRow: nextRow, start: 0, end: 0),
  );
}

EditorChange deleteTableColumn(
  NoteDocument document, {
  required EditorSelection selection,
  required int column,
  required String fallbackTextId,
}) {
  final table = _selectedTable(document, selection);
  if (table == null || column < 0 || column >= table.columnCount) {
    return EditorChange(document: document, selection: selection);
  }
  if (table.columnCount == 1) {
    return deleteBlock(
      document,
      blockId: table.id,
      fallbackTextId: fallbackTextId,
    );
  }
  final rows = <TableRow>[
    for (final row in table.rows)
      row.copyWith(
        cells: <TableCell>[
          for (var index = 0; index < row.cells.length; index += 1)
            if (index != column) row.cells[index],
        ],
      ),
  ];
  final nextColumn = column.clamp(0, table.columnCount - 2);
  return EditorChange(
    document: _replaceBlock(document, table.copyWith(rows: rows)),
    selection: selection.copyWith(
      tableColumn: nextColumn,
      start: 0,
      end: 0,
    ),
  );
}

NoteDocument _replaceBlock(NoteDocument document, NoteBlock replacement) {
  return document.copyWith(
    blocks: <NoteBlock>[
      for (final block in document.blocks)
        if (block.id == replacement.id) replacement else block,
    ],
  );
}

TableBlock? _selectedTable(
  NoteDocument document,
  EditorSelection selection,
) {
  final block = document.blockById(selection.blockId);
  return block is TableBlock ? block : null;
}
