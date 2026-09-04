import 'dart:convert';

import 'note_block.dart';
import 'note_document.dart';

// -- Constants

const _documentKeys = <String>{'schemaVersion', 'blocks'};
const _inlineKeys = <String>{
  'text',
  'bold',
  'italic',
  'underline',
  'strikethrough',
  'highlight',
  'linkUrl',
};
const _textBlockKeys = <String>{
  'type',
  'id',
  'paragraphStyle',
  'alignment',
  'listMarker',
  'indent',
  'quoted',
  'collapsed',
  'checked',
  'inlines',
};
const _tableBlockKeys = <String>{'type', 'id', 'rows'};
const _tableRowKeys = <String>{'cells'};
const _tableCellKeys = <String>{'inlines'};
const _imageBlockKeys = <String>{
  'type',
  'id',
  'attachmentId',
  'layout',
  'scale',
  'rotationDegrees',
  'offsetX',
  'offsetY',
  'zIndex',
};
const _stickerBlockKeys = <String>{
  'type',
  'id',
  'attachmentId',
  'libraryEntryId',
  'scale',
  'rotationDegrees',
  'offsetX',
  'offsetY',
  'zIndex',
};
const _drawingBlockKeys = <String>{
  'type',
  'id',
  'attachmentId',
  'width',
  'height',
};

// -- Functions

String encodeNoteDocument(NoteDocument document) => jsonEncode(
      <String, Object?>{
        'schemaVersion': document.schemaVersion,
        'blocks': <Object?>[
          for (final block in document.blocks) _blockToJson(block),
        ],
      },
    );

NoteDocument decodeNoteDocument(String source) {
  final Object? decoded;
  try {
    decoded = jsonDecode(source);
  } on FormatException catch (error) {
    throw FormatException('Invalid note document JSON: ${error.message}');
  }
  final root = _asMap(decoded, r'$');
  _expectExactKeys(root, _documentKeys, r'$');
  final schemaVersion = _asInt(root['schemaVersion'], r'$.schemaVersion');
  if (schemaVersion != currentDocumentSchemaVersion) {
    throw FormatException(
        'Unsupported document schema version: $schemaVersion');
  }
  final blocks = _asList(root['blocks'], r'$.blocks');
  try {
    return NoteDocument(
      schemaVersion: schemaVersion,
      blocks: <NoteBlock>[
        for (var index = 0; index < blocks.length; index += 1)
          _blockFromJson(blocks[index], r'$.blocks[' '$index]'),
      ],
    );
  } on ArgumentError catch (error) {
    throw FormatException('Invalid note document value: ${error.message}');
  }
}

Map<String, Object?> _blockToJson(NoteBlock block) {
  return switch (block) {
    TextBlock() => <String, Object?>{
        'type': 'text',
        'id': block.id,
        'paragraphStyle': block.paragraphStyle.name,
        'alignment': block.alignment.name,
        'listMarker': block.listMarker.name,
        'indent': block.indent,
        'quoted': block.quoted,
        'collapsed': block.collapsed,
        'checked': block.checked,
        'inlines': <Object?>[
          for (final inline in block.inlines) _inlineToJson(inline),
        ],
      },
    TableBlock() => <String, Object?>{
        'type': 'table',
        'id': block.id,
        'rows': <Object?>[
          for (final row in block.rows)
            <String, Object?>{
              'cells': <Object?>[
                for (final cell in row.cells)
                  <String, Object?>{
                    'inlines': <Object?>[
                      for (final inline in cell.inlines) _inlineToJson(inline),
                    ],
                  },
              ],
            },
        ],
      },
    ImageBlock() => <String, Object?>{
        'type': 'image',
        'id': block.id,
        'attachmentId': block.attachmentId,
        'layout': block.layout.name,
        'scale': block.scale,
        'rotationDegrees': block.rotationDegrees,
        'offsetX': block.offsetX,
        'offsetY': block.offsetY,
        'zIndex': block.zIndex,
      },
    StickerBlock() => <String, Object?>{
        'type': 'sticker',
        'id': block.id,
        'attachmentId': block.attachmentId,
        'libraryEntryId': block.libraryEntryId,
        'scale': block.scale,
        'rotationDegrees': block.rotationDegrees,
        'offsetX': block.offsetX,
        'offsetY': block.offsetY,
        'zIndex': block.zIndex,
      },
    DrawingBlock() => <String, Object?>{
        'type': 'drawing',
        'id': block.id,
        'attachmentId': block.attachmentId,
        'width': block.width,
        'height': block.height,
      },
  };
}

Map<String, Object?> _inlineToJson(InlineRun inline) => <String, Object?>{
      'text': inline.text,
      'bold': inline.bold,
      'italic': inline.italic,
      'underline': inline.underline,
      'strikethrough': inline.strikethrough,
      'highlight': inline.highlight,
      'linkUrl': inline.linkUrl,
    };

NoteBlock _blockFromJson(Object? value, String path) {
  final map = _asMap(value, path);
  final type = _asString(map['type'], '$path.type');
  return switch (type) {
    'text' => _textBlockFromJson(map, path),
    'table' => _tableBlockFromJson(map, path),
    'image' => _imageBlockFromJson(map, path),
    'sticker' => _stickerBlockFromJson(map, path),
    'drawing' => _drawingBlockFromJson(map, path),
    _ => throw FormatException('Unknown block type at $path.type: $type'),
  };
}

TextBlock _textBlockFromJson(Map<String, Object?> map, String path) {
  _expectExactKeys(map, _textBlockKeys, path);
  return TextBlock(
    id: _asString(map['id'], '$path.id'),
    paragraphStyle: _enumValue(
      ParagraphStyle.values,
      _asString(map['paragraphStyle'], '$path.paragraphStyle'),
      '$path.paragraphStyle',
    ),
    alignment: _enumValue(
      TextAlignment.values,
      _asString(map['alignment'], '$path.alignment'),
      '$path.alignment',
    ),
    listMarker: _enumValue(
      ListMarker.values,
      _asString(map['listMarker'], '$path.listMarker'),
      '$path.listMarker',
    ),
    indent: _asInt(map['indent'], '$path.indent'),
    quoted: _asBool(map['quoted'], '$path.quoted'),
    collapsed: _asBool(map['collapsed'], '$path.collapsed'),
    checked: _asBool(map['checked'], '$path.checked'),
    inlines: _inlineList(map['inlines'], '$path.inlines'),
  );
}

TableBlock _tableBlockFromJson(Map<String, Object?> map, String path) {
  _expectExactKeys(map, _tableBlockKeys, path);
  final rows = _asList(map['rows'], '$path.rows');
  return TableBlock(
    id: _asString(map['id'], '$path.id'),
    rows: <TableRow>[
      for (var rowIndex = 0; rowIndex < rows.length; rowIndex += 1)
        _tableRowFromJson(rows[rowIndex], '$path.rows[$rowIndex]'),
    ],
  );
}

TableRow _tableRowFromJson(Object? value, String path) {
  final map = _asMap(value, path);
  _expectExactKeys(map, _tableRowKeys, path);
  final cells = _asList(map['cells'], '$path.cells');
  return TableRow(
    cells: <TableCell>[
      for (var cellIndex = 0; cellIndex < cells.length; cellIndex += 1)
        _tableCellFromJson(cells[cellIndex], '$path.cells[$cellIndex]'),
    ],
  );
}

TableCell _tableCellFromJson(Object? value, String path) {
  final map = _asMap(value, path);
  _expectExactKeys(map, _tableCellKeys, path);
  return TableCell(inlines: _inlineList(map['inlines'], '$path.inlines'));
}

ImageBlock _imageBlockFromJson(Map<String, Object?> map, String path) {
  _expectExactKeys(map, _imageBlockKeys, path);
  return ImageBlock(
    id: _asString(map['id'], '$path.id'),
    attachmentId: _asString(map['attachmentId'], '$path.attachmentId'),
    layout: _enumValue(
      MediaLayout.values,
      _asString(map['layout'], '$path.layout'),
      '$path.layout',
    ),
    scale: _asDouble(map['scale'], '$path.scale'),
    rotationDegrees: _asDouble(
      map['rotationDegrees'],
      '$path.rotationDegrees',
    ),
    offsetX: _asDouble(map['offsetX'], '$path.offsetX'),
    offsetY: _asDouble(map['offsetY'], '$path.offsetY'),
    zIndex: _asInt(map['zIndex'], '$path.zIndex'),
  );
}

StickerBlock _stickerBlockFromJson(Map<String, Object?> map, String path) {
  _expectExactKeys(map, _stickerBlockKeys, path);
  return StickerBlock(
    id: _asString(map['id'], '$path.id'),
    attachmentId: _asString(map['attachmentId'], '$path.attachmentId'),
    libraryEntryId: _asNullableString(
      map['libraryEntryId'],
      '$path.libraryEntryId',
    ),
    scale: _asDouble(map['scale'], '$path.scale'),
    rotationDegrees: _asDouble(
      map['rotationDegrees'],
      '$path.rotationDegrees',
    ),
    offsetX: _asDouble(map['offsetX'], '$path.offsetX'),
    offsetY: _asDouble(map['offsetY'], '$path.offsetY'),
    zIndex: _asInt(map['zIndex'], '$path.zIndex'),
  );
}

DrawingBlock _drawingBlockFromJson(Map<String, Object?> map, String path) {
  _expectExactKeys(map, _drawingBlockKeys, path);
  return DrawingBlock(
    id: _asString(map['id'], '$path.id'),
    attachmentId: _asString(map['attachmentId'], '$path.attachmentId'),
    width: _asDouble(map['width'], '$path.width'),
    height: _asDouble(map['height'], '$path.height'),
  );
}

List<InlineRun> _inlineList(Object? value, String path) {
  final list = _asList(value, path);
  return <InlineRun>[
    for (var index = 0; index < list.length; index += 1)
      _inlineFromJson(list[index], '$path[$index]'),
  ];
}

InlineRun _inlineFromJson(Object? value, String path) {
  final map = _asMap(value, path);
  _expectExactKeys(map, _inlineKeys, path);
  return InlineRun(
    text: _asString(map['text'], '$path.text'),
    bold: _asBool(map['bold'], '$path.bold'),
    italic: _asBool(map['italic'], '$path.italic'),
    underline: _asBool(map['underline'], '$path.underline'),
    strikethrough: _asBool(map['strikethrough'], '$path.strikethrough'),
    highlight: _asBool(map['highlight'], '$path.highlight'),
    linkUrl: _asNullableString(map['linkUrl'], '$path.linkUrl'),
  );
}

Map<String, Object?> _asMap(Object? value, String path) {
  if (value is! Map<Object?, Object?>) {
    throw FormatException('Expected object at $path');
  }
  final result = <String, Object?>{};
  for (final entry in value.entries) {
    if (entry.key is! String) {
      throw FormatException('Expected string key at $path');
    }
    result[entry.key! as String] = entry.value;
  }
  return result;
}

List<Object?> _asList(Object? value, String path) {
  if (value is! List<Object?>) {
    throw FormatException('Expected array at $path');
  }
  return value;
}

String _asString(Object? value, String path) {
  if (value is! String) {
    throw FormatException('Expected string at $path');
  }
  return value;
}

String? _asNullableString(Object? value, String path) {
  if (value == null || value is String) {
    return value as String?;
  }
  throw FormatException('Expected string or null at $path');
}

int _asInt(Object? value, String path) {
  if (value is! int) {
    throw FormatException('Expected integer at $path');
  }
  return value;
}

double _asDouble(Object? value, String path) {
  if (value is! num || !value.isFinite) {
    throw FormatException('Expected finite number at $path');
  }
  return value.toDouble();
}

bool _asBool(Object? value, String path) {
  if (value is! bool) {
    throw FormatException('Expected boolean at $path');
  }
  return value;
}

T _enumValue<T extends Enum>(List<T> values, String name, String path) {
  for (final value in values) {
    if (value.name == name) {
      return value;
    }
  }
  throw FormatException('Unknown enum value at $path: $name');
}

void _expectExactKeys(
  Map<String, Object?> value,
  Set<String> expected,
  String path,
) {
  final actual = value.keys.toSet();
  final missing = expected.difference(actual);
  final unknown = actual.difference(expected);
  if (missing.isNotEmpty || unknown.isNotEmpty) {
    throw FormatException(
      'Invalid keys at $path; missing: ${missing.join(', ')}, '
      'unknown: ${unknown.join(', ')}',
    );
  }
}
