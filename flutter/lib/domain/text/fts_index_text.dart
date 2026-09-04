// -- Functions

String prepareFtsIndexText(String text) {
  if (text.isEmpty) {
    return text;
  }
  final output = StringBuffer();
  int? previousCodePoint;
  var previousWasCjk = false;
  for (final codePoint in text.runes) {
    final character = String.fromCharCode(codePoint);
    final isWhitespace = character.trim().isEmpty;
    final isCjk = _isCjkCodePoint(codePoint);
    if (output.length > 0 &&
        !isWhitespace &&
        previousCodePoint != null &&
        String.fromCharCode(previousCodePoint).trim().isNotEmpty &&
        (previousWasCjk || isCjk)) {
      output.write(' ');
    }
    output.write(character);
    previousCodePoint = codePoint;
    previousWasCjk = isCjk;
  }
  return output.toString().trim().replaceAll(RegExp(r'\s+'), ' ');
}

String? ftsMatchQuery(String rawQuery) {
  final prepared = prepareFtsIndexText(rawQuery.trim());
  if (prepared.isEmpty) {
    return null;
  }
  final sanitized = prepared
      .replaceAll('"', ' ')
      .replaceAll(RegExp(r'[{}()*?:^]'), ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
  return sanitized.isEmpty ? null : '"$sanitized"';
}

bool _isCjkCodePoint(int codePoint) {
  return (codePoint >= 0x3400 && codePoint <= 0x4DBF) ||
      (codePoint >= 0x4E00 && codePoint <= 0x9FFF) ||
      (codePoint >= 0xF900 && codePoint <= 0xFAFF) ||
      (codePoint >= 0x20000 && codePoint <= 0x2FA1F) ||
      (codePoint >= 0x3040 && codePoint <= 0x30FF) ||
      (codePoint >= 0xAC00 && codePoint <= 0xD7AF);
}
