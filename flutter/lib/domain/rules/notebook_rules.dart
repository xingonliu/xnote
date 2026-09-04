import '../model/note.dart';
import '../model/notebook.dart';

// -- Type Definitions

final class NotebookDeletionPatch {
  const NotebookDeletionPatch({
    required this.noteId,
    required this.originalNotebookName,
    required this.deletedAtEpochMilliseconds,
  });

  final String noteId;
  final String originalNotebookName;
  final int deletedAtEpochMilliseconds;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is NotebookDeletionPatch &&
        other.noteId == noteId &&
        other.originalNotebookName == originalNotebookName &&
        other.deletedAtEpochMilliseconds == deletedAtEpochMilliseconds;
  }

  @override
  int get hashCode => Object.hash(
        noteId,
        originalNotebookName,
        deletedAtEpochMilliseconds,
      );
}

// -- Functions

List<NotebookDeletionPatch> patchesForDeletedNotebook({
  required Notebook notebook,
  required List<Note> notesInNotebook,
  required int nowEpochMilliseconds,
}) {
  return List<NotebookDeletionPatch>.unmodifiable(
    notesInNotebook.map(
      (note) => NotebookDeletionPatch(
        noteId: note.id,
        originalNotebookName: notebook.name,
        deletedAtEpochMilliseconds:
            note.deletedAtEpochMilliseconds ?? nowEpochMilliseconds,
      ),
    ),
  );
}

String? notebookIdAfterRestore(
  Note note,
  bool Function(String notebookId) notebookExists,
) {
  final notebookId = note.notebookId;
  return notebookId != null && notebookExists(notebookId) ? notebookId : null;
}
