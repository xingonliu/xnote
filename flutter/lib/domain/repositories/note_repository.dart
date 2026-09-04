import '../model/background_key.dart';
import '../model/note.dart';
import '../model/note_revision.dart';
import '../model/note_search_result.dart';
import '../model/notebook.dart';

// -- Type Definitions

abstract interface class NoteRepository {
  Stream<List<Note>> watchActiveNotes({
    NoteListSort sort = NoteListSort.updatedAt,
  });

  Stream<List<Note>> watchUnfiledNotes({
    NoteListSort sort = NoteListSort.updatedAt,
  });

  Stream<List<Note>> watchNotesInNotebook(
    String notebookId, {
    NoteListSort sort = NoteListSort.manual,
  });

  Stream<List<Note>> watchTrashedNotes();

  Stream<Note?> watchNote(String id);

  Future<Note?> getNote(String id);

  Future<Note> createRichNote({String? notebookId});

  Future<Note> saveNote(Note note);

  Future<Note> setNoteBackground(String noteId, BackgroundKey? background);

  Future<Note> convertToMarkdown(String noteId);

  Future<NoteRevision> saveRevision(String noteId, RevisionReason reason);

  Future<List<NoteRevision>> getNoteRevisions(String noteId);

  Future<void> reorderNotes(List<String> orderedIds);

  Future<void> moveNotes(Iterable<String> ids, String? notebookId);

  Future<void> trashNotes(Iterable<String> ids);

  Future<void> restoreNotes(Iterable<String> ids);

  Future<void> permanentlyDeleteNotes(Iterable<String> ids);

  Future<void> emptyTrash();

  Future<void> purgeExpiredTrash();

  Future<List<NoteSearchResult>> searchNotes(
    String query, {
    String? notebookId,
  });

  Future<Map<String, NotebookStats>> notebookStats();

  Future<NotebookStats> unfiledStats();
}
