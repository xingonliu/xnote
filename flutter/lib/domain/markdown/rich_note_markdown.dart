import '../document/note_block.dart';
import '../document/note_document.dart';

// -- Constants

const _markdownEscapableCharacters = <String>{
  r'\',
  '`',
  '*',
  '_',
  '{',
  '}',
  '[',
  ']',
  '<',
  '>',
  '(',
  ')',
  '#',
  '+',
  '-',
  '.',
  '!',
  '|',
  '~',
  '=',
};

// -- Functions

String richNoteToMarkdown(String title, NoteDocument document) {
  final parts = <String>[];
  final trimmedTitle = title.trim();
  if (trimmedTitle.isNotEmpty) {
    parts.add('# ${_escapeMarkdownText(trimmedTitle)}');
  }
  for (final block in document.blocks) {
    switch (block) {
      case TextBlock():
        parts.add(_textBlockToMarkdown(block));
      case TableBlock():
        parts.add(_tableBlockToMarkdown(block));
      case ImageBlock() || StickerBlock() || DrawingBlock():
        throw StateError(
            'Media blocks must be removed before Markdown conversion');
    }
  }
  return parts.join('\n\n').trimRight();
}

String _textBlockToMarkdown(TextBlock block) {
  if (block.paragraphStyle == ParagraphStyle.monospace) {
    return _monospaceMarkdown(
      plainText(block.inlines),
      block.indent,
      block.quoted,
    );
  }

  final marker = switch (block.listMarker) {
    ListMarker.none => '',
    ListMarker.bullet || ListMarker.dash => '- ',
    ListMarker.numbered => '1. ',
    ListMarker.checklist => block.checked ? '- [x] ' : '- [ ] ',
  };
  final heading = switch (block.paragraphStyle) {
    ParagraphStyle.heading => '## ',
    ParagraphStyle.subheading => '### ',
    ParagraphStyle.body || ParagraphStyle.monospace => '',
  };
  final content =
      '$marker$heading${block.inlines.map(_inlineToMarkdown).join()}';
  final prefix = '${_repeat('  ', block.indent)}${block.quoted ? '> ' : ''}';
  return content.split('\n').map((line) => '$prefix$line').join('\n');
}

String _tableBlockToMarkdown(TableBlock block) {
  final columnCount = block.columnCount < 1 ? 1 : block.columnCount;
  final rows = block.rows.isEmpty ? <TableRow>[TableRow()] : block.rows;
  final lines = <String>[
    _tableRowToMarkdown(rows.first, columnCount),
    '| ${List<String>.filled(columnCount, '---').join(' | ')} |',
    for (final row in rows.skip(1)) _tableRowToMarkdown(row, columnCount),
  ];
  return lines.join('\n');
}

String _tableRowToMarkdown(TableRow row, int columnCount) {
  final values = <String>[
    for (var index = 0; index < columnCount; index += 1)
      if (index < row.cells.length)
        row.cells[index].inlines
            .map(_inlineToMarkdown)
            .join()
            .replaceAll('\n', '<br>')
      else
        '',
  ];
  return '| ${values.join(' | ')} |';
}

String _inlineToMarkdown(InlineRun inline) {
  var value = _escapeMarkdownText(inline.text);
  if (value.isEmpty) {
    return value;
  }
  if (inline.bold) {
    value = '**$value**';
  }
  if (inline.italic) {
    value = '*$value*';
  }
  if (inline.strikethrough) {
    value = '~~$value~~';
  }
  if (inline.underline) {
    value = '<u>$value</u>';
  }
  if (inline.highlight) {
    value = '==$value==';
  }
  if (inline.linkUrl?.trim().isNotEmpty ?? false) {
    value = '[$value](${_escapeMarkdownUrl(inline.linkUrl!)})';
  }
  return value;
}

String _monospaceMarkdown(String text, int indent, bool quoted) {
  final prefix = '${_repeat('  ', indent)}${quoted ? '> ' : ''}';
  if (!text.contains('\n')) {
    final delimiters = RegExp(r'`+').allMatches(text);
    var longestDelimiter = 0;
    for (final match in delimiters) {
      if (match.group(0)!.length > longestDelimiter) {
        longestDelimiter = match.group(0)!.length;
      }
    }
    final delimiter = _repeat('`', longestDelimiter + 1);
    final padding = text.startsWith('`') || text.endsWith('`') ? ' ' : '';
    return '$prefix$delimiter$padding$text$padding$delimiter';
  }
  final lines = text.split('\n').map((line) => '$prefix$line').join('\n');
  return '$prefix```\n$lines\n$prefix```';
}

String _escapeMarkdownText(String text) {
  final output = StringBuffer();
  for (final codePoint in text.runes) {
    final character = String.fromCharCode(codePoint);
    if (_markdownEscapableCharacters.contains(character)) {
      output.write(r'\');
    }
    output.write(character);
  }
  return output.toString();
}

String _escapeMarkdownUrl(String url) {
  final output = StringBuffer();
  for (final codePoint in url.trim().runes) {
    final character = String.fromCharCode(codePoint);
    if (character == r'\' || character == ')') {
      output.write(r'\');
    }
    output.write(character);
  }
  return output.toString();
}

String _repeat(String value, int count) =>
    List<String>.filled(count, value).join();
