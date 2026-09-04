// -- Constants

final _leadingTitlePattern = RegExp(r'^#(?:[ \t]+|$)(.*)$');
final _closingHeadingPattern = RegExp(r'[ \t]+#+[ \t]*$');
final _escapedCharacterPattern = RegExp(r'\\([\\`*_{}\[\]<>()#+\-.!|~=])');
final _escapedPlaceholderPattern = RegExp('\uE000(\\d+)\uE001');

// -- Functions

String markdownDocumentTitle(String markdown) {
  final firstLine = markdown.replaceAll('\r\n', '\n').split('\n').first;
  final match = _leadingTitlePattern.firstMatch(firstLine);
  if (match == null) {
    return '';
  }
  final rawTitle = match.group(1) ?? '';
  return extractMarkdownInlineText(
    rawTitle.replaceFirst(_closingHeadingPattern, ''),
  ).trim();
}

String extractMarkdownVisibleText(String markdown) {
  final escaped = <String>[];
  var text = _protectEscapedCharacters(
    markdown.replaceAll('\r\n', '\n'),
    escaped,
  );
  text = _stripLeadingTitleHeading(text);
  text = text.replaceAll(RegExp(r'```[^\n]*\n'), '');
  text = text.replaceAll('```', '');
  text = text.replaceAll(RegExp(r'^#+\s*', multiLine: true), '');
  text = text.replaceAll(RegExp(r'^>\s*', multiLine: true), '');
  text = text.replaceAll(
    RegExp(r'^\s*-\s\[[ xX]\]\s+', multiLine: true),
    '',
  );
  text = text.replaceAll(RegExp(r'^\s*[-*+]\s+', multiLine: true), '');
  text = text.replaceAll(RegExp(r'^\s*\d+\.\s+', multiLine: true), '');
  text = text.replaceAll(
    RegExp(
      r'^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$',
      multiLine: true,
    ),
    '',
  );
  text = text.replaceAll('|', ' ');
  return _restoreEscapedCharacters(_stripInlineSyntax(text), escaped);
}

String extractMarkdownInlineText(String markdown) {
  final escaped = <String>[];
  final protected = _protectEscapedCharacters(markdown, escaped);
  return _restoreEscapedCharacters(_stripInlineSyntax(protected), escaped);
}

String _stripInlineSyntax(String markdown) {
  var text = markdown;
  text = text.replaceAllMapped(
    RegExp(r'!\[([^\]]*)\]\([^)]*\)'),
    (match) => match.group(1) ?? '',
  );
  text = text.replaceAllMapped(
    RegExp(r'\[([^\]]+)\]\([^)]*\)'),
    (match) => match.group(1) ?? '',
  );
  text = text
      .replaceAll('**', '')
      .replaceAll('__', '')
      .replaceAll('~~', '')
      .replaceAll('==', '')
      .replaceAll(RegExp(r'</?u>', caseSensitive: false), '')
      .replaceAll('`', '')
      .replaceAll('*', '')
      .replaceAll('_', '');
  return text;
}

String _protectEscapedCharacters(String markdown, List<String> escaped) {
  return markdown.replaceAllMapped(_escapedCharacterPattern, (match) {
    final index = escaped.length;
    escaped.add(match.group(1) ?? '');
    return '\uE000$index\uE001';
  });
}

String _restoreEscapedCharacters(String markdown, List<String> escaped) {
  return markdown.replaceAllMapped(_escapedPlaceholderPattern, (match) {
    final index = int.parse(match.group(1)!);
    return index < escaped.length ? escaped[index] : '';
  });
}

String _stripLeadingTitleHeading(String markdown) {
  final firstLineEnd = markdown.indexOf('\n');
  final firstLine =
      firstLineEnd < 0 ? markdown : markdown.substring(0, firstLineEnd);
  if (!firstLine.startsWith('# ') && !firstLine.startsWith('#\t')) {
    return markdown;
  }
  return firstLineEnd < 0 ? '' : markdown.substring(firstLineEnd + 1);
}
