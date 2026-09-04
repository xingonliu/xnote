import '../../core/ids/id_generator.dart';
import '../../core/time/clock.dart';
import '../document/note_block.dart';
import '../document/note_document.dart';
import '../markdown/rich_note_markdown.dart';
import '../model/note.dart';
import '../model/note_revision.dart';
import '../text/note_plain_text.dart';

// -- Type Definitions

enum ConversionBlocker { alreadyMarkdown, image, sticker, drawing }

final class MarkdownConversion {
  const MarkdownConversion({required this.revision, required this.note});

  final NoteRevision revision;
  final Note note;
}

// -- Functions

Set<ConversionBlocker> documentConversionBlockers(NoteDocument document) {
  final blockers = <ConversionBlocker>{};
  for (final block in document.blocks) {
    switch (block) {
      case ImageBlock():
        blockers.add(ConversionBlocker.image);
      case StickerBlock():
        blockers.add(ConversionBlocker.sticker);
      case DrawingBlock():
        blockers.add(ConversionBlocker.drawing);
      case TextBlock() || TableBlock():
        break;
    }
  }
  return Set<ConversionBlocker>.unmodifiable(blockers);
}

Set<ConversionBlocker> noteConversionBlockers(Note note) {
  if (note.kind == NoteKind.markdown) {
    return const <ConversionBlocker>{ConversionBlocker.alreadyMarkdown};
  }
  return documentConversionBlockers(note.document!);
}

bool canConvertToMarkdown(Note note) => noteConversionBlockers(note).isEmpty;

MarkdownConversion convertRichNoteToMarkdown(
  Note note, {
  required IdGenerator idGenerator,
  required Clock clock,
}) {
  final blockers = noteConversionBlockers(note);
  if (blockers.isNotEmpty) {
    throw StateError('Note cannot be converted to Markdown: $blockers');
  }
  final now = clock.nowEpochMilliseconds();
  final revision = NoteRevision(
    id: idGenerator.nextId(),
    noteId: note.id,
    reason: RevisionReason.convertToMarkdown,
    kind: note.kind,
    title: note.title,
    document: note.document,
    markdownText: note.markdownText,
    createdAtEpochMilliseconds: now,
  );
  final markdown = richNoteToMarkdown(note.title, note.document!);
  final converted = withDerivedText(
    note.copyWith(
      kind: NoteKind.markdown,
      document: null,
      markdownText: markdown,
      updatedAtEpochMilliseconds: now,
    ),
  );
  return MarkdownConversion(revision: revision, note: converted);
}
