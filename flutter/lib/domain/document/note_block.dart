import '../../core/value/equality.dart';

// -- Type Definitions

enum ParagraphStyle { body, heading, subheading, monospace }

enum TextAlignment { left, center, right }

enum ListMarker { none, bullet, dash, numbered, checklist }

enum MediaLayout { block, wrap, float }

final class InlineRun {
  const InlineRun({
    required this.text,
    this.bold = false,
    this.italic = false,
    this.underline = false,
    this.strikethrough = false,
    this.highlight = false,
    this.linkUrl,
  });

  final String text;
  final bool bold;
  final bool italic;
  final bool underline;
  final bool strikethrough;
  final bool highlight;
  final String? linkUrl;

  // -- Functions

  InlineRun copyWith({
    String? text,
    bool? bold,
    bool? italic,
    bool? underline,
    bool? strikethrough,
    bool? highlight,
    Object? linkUrl = _notProvided,
  }) {
    return InlineRun(
      text: text ?? this.text,
      bold: bold ?? this.bold,
      italic: italic ?? this.italic,
      underline: underline ?? this.underline,
      strikethrough: strikethrough ?? this.strikethrough,
      highlight: highlight ?? this.highlight,
      linkUrl:
          identical(linkUrl, _notProvided) ? this.linkUrl : linkUrl as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is InlineRun &&
        other.text == text &&
        other.bold == bold &&
        other.italic == italic &&
        other.underline == underline &&
        other.strikethrough == strikethrough &&
        other.highlight == highlight &&
        other.linkUrl == linkUrl;
  }

  @override
  int get hashCode => Object.hash(
        text,
        bold,
        italic,
        underline,
        strikethrough,
        highlight,
        linkUrl,
      );
}

sealed class NoteBlock {
  NoteBlock(this.id) {
    if (id.isEmpty) {
      throw ArgumentError.value(id, 'id', 'Block id must not be empty');
    }
  }

  final String id;
}

final class TextBlock extends NoteBlock {
  factory TextBlock({
    required String id,
    ParagraphStyle paragraphStyle = ParagraphStyle.body,
    TextAlignment alignment = TextAlignment.left,
    ListMarker listMarker = ListMarker.none,
    int indent = 0,
    bool quoted = false,
    bool collapsed = false,
    bool checked = false,
    Iterable<InlineRun> inlines = const <InlineRun>[],
  }) {
    if (indent < 0 || indent > maxTextIndent) {
      throw ArgumentError.value(
          indent, 'indent', 'Indent must be between 0 and $maxTextIndent');
    }
    return TextBlock._(
      id: id,
      paragraphStyle: paragraphStyle,
      alignment: alignment,
      listMarker: listMarker,
      indent: indent,
      quoted: quoted,
      collapsed: collapsed,
      checked: checked,
      inlines: List<InlineRun>.unmodifiable(inlines),
    );
  }

  TextBlock._({
    required String id,
    required this.paragraphStyle,
    required this.alignment,
    required this.listMarker,
    required this.indent,
    required this.quoted,
    required this.collapsed,
    required this.checked,
    required this.inlines,
  }) : super(id);

  final ParagraphStyle paragraphStyle;
  final TextAlignment alignment;
  final ListMarker listMarker;
  final int indent;
  final bool quoted;
  final bool collapsed;
  final bool checked;
  final List<InlineRun> inlines;

  // -- Functions

  TextBlock copyWith({
    ParagraphStyle? paragraphStyle,
    TextAlignment? alignment,
    ListMarker? listMarker,
    int? indent,
    bool? quoted,
    bool? collapsed,
    bool? checked,
    Iterable<InlineRun>? inlines,
  }) {
    return TextBlock(
      id: id,
      paragraphStyle: paragraphStyle ?? this.paragraphStyle,
      alignment: alignment ?? this.alignment,
      listMarker: listMarker ?? this.listMarker,
      indent: indent ?? this.indent,
      quoted: quoted ?? this.quoted,
      collapsed: collapsed ?? this.collapsed,
      checked: checked ?? this.checked,
      inlines: inlines ?? this.inlines,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is TextBlock &&
        other.id == id &&
        other.paragraphStyle == paragraphStyle &&
        other.alignment == alignment &&
        other.listMarker == listMarker &&
        other.indent == indent &&
        other.quoted == quoted &&
        other.collapsed == collapsed &&
        other.checked == checked &&
        listValueEquals(other.inlines, inlines);
  }

  @override
  int get hashCode => Object.hash(
        id,
        paragraphStyle,
        alignment,
        listMarker,
        indent,
        quoted,
        collapsed,
        checked,
        listValueHash(inlines),
      );
}

final class TableCell {
  factory TableCell({Iterable<InlineRun> inlines = const <InlineRun>[]}) {
    return TableCell._(List<InlineRun>.unmodifiable(inlines));
  }

  const TableCell._(this.inlines);

  final List<InlineRun> inlines;

  // -- Functions

  TableCell copyWith({Iterable<InlineRun>? inlines}) =>
      TableCell(inlines: inlines ?? this.inlines);

  @override
  bool operator ==(Object other) =>
      other is TableCell && listValueEquals(other.inlines, inlines);

  @override
  int get hashCode => listValueHash(inlines);
}

final class TableRow {
  factory TableRow({Iterable<TableCell> cells = const <TableCell>[]}) {
    return TableRow._(List<TableCell>.unmodifiable(cells));
  }

  const TableRow._(this.cells);

  final List<TableCell> cells;

  // -- Functions

  TableRow copyWith({Iterable<TableCell>? cells}) =>
      TableRow(cells: cells ?? this.cells);

  @override
  bool operator ==(Object other) =>
      other is TableRow && listValueEquals(other.cells, cells);

  @override
  int get hashCode => listValueHash(cells);
}

final class TableBlock extends NoteBlock {
  factory TableBlock({
    required String id,
    Iterable<TableRow> rows = const <TableRow>[],
  }) {
    return TableBlock._(id: id, rows: List<TableRow>.unmodifiable(rows));
  }

  TableBlock._({required String id, required this.rows}) : super(id);

  final List<TableRow> rows;

  // -- Derived Values

  int get columnCount => rows.fold<int>(
      0,
      (maximum, row) =>
          row.cells.length > maximum ? row.cells.length : maximum);

  // -- Functions

  TableCell? cellAt(int row, int column) {
    if (row < 0 || row >= rows.length) {
      return null;
    }
    final cells = rows[row].cells;
    return column < 0 || column >= cells.length ? null : cells[column];
  }

  TableBlock copyWith({Iterable<TableRow>? rows}) =>
      TableBlock(id: id, rows: rows ?? this.rows);

  TableBlock updateCell(int row, int column, Iterable<InlineRun> inlines) {
    if (cellAt(row, column) == null) {
      return this;
    }
    return copyWith(
      rows: <TableRow>[
        for (var rowIndex = 0; rowIndex < rows.length; rowIndex += 1)
          if (rowIndex != row)
            rows[rowIndex]
          else
            rows[rowIndex].copyWith(
              cells: <TableCell>[
                for (var columnIndex = 0;
                    columnIndex < rows[rowIndex].cells.length;
                    columnIndex += 1)
                  if (columnIndex != column)
                    rows[rowIndex].cells[columnIndex]
                  else
                    rows[rowIndex]
                        .cells[columnIndex]
                        .copyWith(inlines: inlines),
              ],
            ),
      ],
    );
  }

  @override
  bool operator ==(Object other) =>
      other is TableBlock &&
      other.id == id &&
      listValueEquals(other.rows, rows);

  @override
  int get hashCode => Object.hash(id, listValueHash(rows));
}

final class ImageBlock extends NoteBlock {
  ImageBlock({
    required String id,
    required this.attachmentId,
    this.layout = MediaLayout.block,
    this.scale = 1,
    this.rotationDegrees = 0,
    this.offsetX = 0,
    this.offsetY = 0,
    this.zIndex = 0,
  }) : super(id) {
    if (attachmentId.isEmpty || !scale.isFinite || scale <= 0) {
      throw ArgumentError('Image attachment id and scale must be valid');
    }
  }

  final String attachmentId;
  final MediaLayout layout;
  final double scale;
  final double rotationDegrees;
  final double offsetX;
  final double offsetY;
  final int zIndex;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is ImageBlock &&
        other.id == id &&
        other.attachmentId == attachmentId &&
        other.layout == layout &&
        other.scale == scale &&
        other.rotationDegrees == rotationDegrees &&
        other.offsetX == offsetX &&
        other.offsetY == offsetY &&
        other.zIndex == zIndex;
  }

  @override
  int get hashCode => Object.hash(
        id,
        attachmentId,
        layout,
        scale,
        rotationDegrees,
        offsetX,
        offsetY,
        zIndex,
      );
}

final class StickerBlock extends NoteBlock {
  StickerBlock({
    required String id,
    required this.attachmentId,
    this.libraryEntryId,
    this.scale = 1,
    this.rotationDegrees = 0,
    this.offsetX = 0,
    this.offsetY = 0,
    this.zIndex = 0,
  }) : super(id) {
    if (attachmentId.isEmpty || !scale.isFinite || scale <= 0) {
      throw ArgumentError('Sticker attachment id and scale must be valid');
    }
  }

  final String attachmentId;
  final String? libraryEntryId;
  final double scale;
  final double rotationDegrees;
  final double offsetX;
  final double offsetY;
  final int zIndex;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is StickerBlock &&
        other.id == id &&
        other.attachmentId == attachmentId &&
        other.libraryEntryId == libraryEntryId &&
        other.scale == scale &&
        other.rotationDegrees == rotationDegrees &&
        other.offsetX == offsetX &&
        other.offsetY == offsetY &&
        other.zIndex == zIndex;
  }

  @override
  int get hashCode => Object.hash(
        id,
        attachmentId,
        libraryEntryId,
        scale,
        rotationDegrees,
        offsetX,
        offsetY,
        zIndex,
      );
}

final class DrawingBlock extends NoteBlock {
  DrawingBlock({
    required String id,
    required this.attachmentId,
    required this.width,
    required this.height,
  }) : super(id) {
    if (attachmentId.isEmpty ||
        !width.isFinite ||
        !height.isFinite ||
        width <= 0 ||
        height <= 0) {
      throw ArgumentError('Drawing attachment id and dimensions must be valid');
    }
  }

  final String attachmentId;
  final double width;
  final double height;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is DrawingBlock &&
        other.id == id &&
        other.attachmentId == attachmentId &&
        other.width == width &&
        other.height == height;
  }

  @override
  int get hashCode => Object.hash(id, attachmentId, width, height);
}

// -- Constants

const maxTextIndent = 5;
const Object _notProvided = Object();

// -- Functions

String plainText(Iterable<InlineRun> inlines) =>
    inlines.map((inline) => inline.text).join();

TextBlock emptyBodyBlock(String id) => TextBlock(id: id);

TableBlock emptyTableBlock(
  String id, {
  int rowCount = 2,
  int columnCount = 2,
}) {
  final safeRows = rowCount < 1 ? 1 : rowCount;
  final safeColumns = columnCount < 1 ? 1 : columnCount;
  return TableBlock(
    id: id,
    rows: <TableRow>[
      for (var row = 0; row < safeRows; row += 1)
        TableRow(
          cells: <TableCell>[
            for (var column = 0; column < safeColumns; column += 1) TableCell(),
          ],
        ),
    ],
  );
}
