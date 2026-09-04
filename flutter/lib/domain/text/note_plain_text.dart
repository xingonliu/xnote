import '../document/note_block.dart';
import '../document/note_document.dart';
import '../markdown/markdown_visible_text.dart';
import '../model/note.dart';

// -- Type Definitions

final class VisibleTextStats {
  const VisibleTextStats({
    required this.characterCount,
    required this.latinWordCount,
  });

  final int characterCount;
  final int latinWordCount;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is VisibleTextStats &&
        other.characterCount == characterCount &&
        other.latinWordCount == latinWordCount;
  }

  @override
  int get hashCode => Object.hash(characterCount, latinWordCount);
}

// -- Constants

const defaultSummaryLength = 80;
final _latinWordPattern = RegExp(r"[A-Za-z]+(?:['’][A-Za-z]+)*");

// -- Functions

String extractDocumentPlainText(NoteDocument document) {
  final parts = <String>[];
  for (final block in document.blocks) {
    switch (block) {
      case TextBlock():
        final text = plainText(block.inlines);
        if (text.isNotEmpty) {
          parts.add(text);
        }
      case TableBlock():
        for (final row in block.rows) {
          for (final cell in row.cells) {
            final text = plainText(cell.inlines);
            if (text.isNotEmpty) {
              parts.add(text);
            }
          }
        }
      case ImageBlock() || StickerBlock() || DrawingBlock():
        break;
    }
  }
  return parts.join('\n');
}

String extractNotePlainText(Note note) => switch (note.kind) {
      NoteKind.rich => extractDocumentPlainText(note.document!),
      NoteKind.markdown => extractMarkdownVisibleText(note.markdownText!),
    };

String summarizePlainText(
  String plainText, {
  int maximumLength = defaultSummaryLength,
}) {
  if (maximumLength < 0) {
    throw ArgumentError.value(maximumLength, 'maximumLength');
  }
  final collapsed = plainText.replaceAll(RegExp(r'\s+'), ' ').trim();
  if (collapsed.length <= maximumLength) {
    return collapsed;
  }
  return collapsed.substring(0, maximumLength).trimRight();
}

VisibleTextStats visibleTextStats(String plainText) => VisibleTextStats(
      characterCount: countVisibleCharacters(plainText),
      latinWordCount: _latinWordPattern.allMatches(plainText).length,
    );

VisibleTextStats noteVisibleTextStats(Note note) =>
    visibleTextStats(extractNotePlainText(note));

int countVisibleCharacters(String text) {
  var count = 0;
  for (final codePoint in text.runes) {
    final character = String.fromCharCode(codePoint);
    if (character.trim().isNotEmpty) {
      count += 1;
    }
  }
  return count;
}

Note withDerivedText(Note note) {
  final plainText = extractNotePlainText(note);
  final stats = visibleTextStats(plainText);
  return note.copyWith(
    visibleCharacterCount: stats.characterCount,
    latinWordCount: stats.latinWordCount,
    summary: summarizePlainText(plainText),
  );
}
