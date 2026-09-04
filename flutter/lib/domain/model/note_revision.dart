import '../document/note_document.dart';
import 'note.dart';

// -- Type Definitions

enum RevisionReason { convertToMarkdown, agentPolish }

final class NoteRevision {
  NoteRevision({
    required this.id,
    required this.noteId,
    required this.reason,
    required this.kind,
    required this.title,
    required this.document,
    required this.markdownText,
    required this.createdAtEpochMilliseconds,
  }) {
    if (id.isEmpty || noteId.isEmpty) {
      throw ArgumentError('Revision ids must not be empty');
    }
    if (kind == NoteKind.rich && (document == null || markdownText != null)) {
      throw ArgumentError('Rich revisions require a document');
    }
    if (kind == NoteKind.markdown &&
        (document != null || markdownText == null)) {
      throw ArgumentError('Markdown revisions require Markdown text');
    }
  }

  final String id;
  final String noteId;
  final RevisionReason reason;
  final NoteKind kind;
  final String title;
  final NoteDocument? document;
  final String? markdownText;
  final int createdAtEpochMilliseconds;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is NoteRevision &&
        other.id == id &&
        other.noteId == noteId &&
        other.reason == reason &&
        other.kind == kind &&
        other.title == title &&
        other.document == document &&
        other.markdownText == markdownText &&
        other.createdAtEpochMilliseconds == createdAtEpochMilliseconds;
  }

  @override
  int get hashCode => Object.hash(
        id,
        noteId,
        reason,
        kind,
        title,
        document,
        markdownText,
        createdAtEpochMilliseconds,
      );
}
