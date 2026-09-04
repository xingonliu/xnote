import '../../domain/model/note.dart';
import '../../domain/text/fts_index_text.dart';
import '../../domain/text/note_plain_text.dart';
import '../database/xnote_database.dart';

// -- Functions

Future<void> replaceNoteSearchIndex(XNoteDatabase database, Note note) async {
  await database.customStatement(
    'DELETE FROM notes_fts WHERE note_id = ?',
    <Object?>[note.id],
  );
  if (note.isTrashed) {
    return;
  }
  await database.customStatement(
    'INSERT INTO notes_fts (note_id, title, body) VALUES (?, ?, ?)',
    <Object?>[
      note.id,
      prepareFtsIndexText(note.title),
      prepareFtsIndexText(extractNotePlainText(note)),
    ],
  );
}
