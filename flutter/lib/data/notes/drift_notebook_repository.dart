import 'package:drift/drift.dart';

import '../../core/ids/id_generator.dart';
import '../../core/time/clock.dart';
import '../../domain/model/notebook.dart';
import '../../domain/repositories/notebook_repository.dart';
import '../../domain/rules/notebook_rules.dart';
import '../../domain/text/note_plain_text.dart';
import '../database/row_mappers.dart';
import '../database/xnote_database.dart';

// -- Type Definitions

final class DriftNotebookRepository implements NotebookRepository {
  const DriftNotebookRepository({
    required XNoteDatabase database,
    required IdGenerator idGenerator,
    required Clock clock,
  })  : _database = database,
        _idGenerator = idGenerator,
        _clock = clock;

  final XNoteDatabase _database;
  final IdGenerator _idGenerator;
  final Clock _clock;

  // -- Functions

  @override
  Stream<List<Notebook>> watchNotebooks() {
    final query = _database.select(_database.notebooks)
      ..orderBy(<OrderingTerm Function($NotebooksTable)>[
        (row) => OrderingTerm.asc(row.sortIndex),
        (row) => OrderingTerm.asc(row.createdAtEpochMilliseconds),
      ]);
    return query.watch().map(
          (rows) => List<Notebook>.unmodifiable(rows.map(notebookFromRow)),
        );
  }

  @override
  Future<Notebook?> getNotebook(String id) async {
    final query = _database.select(_database.notebooks)
      ..where((row) => row.id.equals(id));
    final row = await query.getSingleOrNull();
    return row == null ? null : notebookFromRow(row);
  }

  @override
  Future<Notebook> createNotebook(String name) async {
    final now = _clock.nowEpochMilliseconds();
    final notebook = Notebook(
      id: _idGenerator.nextId(),
      name: name.trim(),
      sortIndex: now,
      createdAtEpochMilliseconds: now,
      updatedAtEpochMilliseconds: now,
    );
    await _database.into(_database.notebooks).insert(notebookToRow(notebook));
    return notebook;
  }

  @override
  Future<Notebook> renameNotebook(String id, String name) async {
    final existing = await getNotebook(id);
    if (existing == null) {
      throw StateError('Notebook not found: $id');
    }
    final updated = existing.copyWith(
      name: name.trim(),
      updatedAtEpochMilliseconds: _clock.nowEpochMilliseconds(),
    );
    await _database.update(_database.notebooks).replace(notebookToRow(updated));
    return updated;
  }

  @override
  Future<void> reorderNotebooks(List<String> orderedIds) async {
    if (orderedIds.isEmpty) {
      return;
    }
    await _database.transaction(() async {
      for (var index = 0; index < orderedIds.length; index += 1) {
        await (_database.update(_database.notebooks)
              ..where((row) => row.id.equals(orderedIds[index])))
            .write(NotebooksCompanion(sortIndex: Value<int>(index)));
      }
    });
  }

  @override
  Future<void> deleteNotebook(String id) async {
    final notebook = await getNotebook(id);
    if (notebook == null) {
      return;
    }
    await _database.transaction(() async {
      final assignedRows = await (_database.select(
        _database.notes,
      )..where((row) => row.notebookId.equals(id)))
          .get();
      final assigned = assignedRows.map(noteFromRow).toList(growable: false);
      final patches = patchesForDeletedNotebook(
        notebook: notebook,
        notesInNotebook: assigned,
        nowEpochMilliseconds: _clock.nowEpochMilliseconds(),
      );
      final patchesById = <String, NotebookDeletionPatch>{
        for (final patch in patches) patch.noteId: patch,
      };
      for (final note in assigned) {
        final patch = patchesById[note.id]!;
        final trashed = withDerivedText(
          note.copyWith(
            notebookId: null,
            deletedAtEpochMilliseconds: patch.deletedAtEpochMilliseconds,
            originalNotebookName: patch.originalNotebookName,
          ),
        );
        await _database
            .into(_database.notes)
            .insertOnConflictUpdate(noteToRow(trashed));
        await _database.customStatement(
          'DELETE FROM notes_fts WHERE note_id = ?',
          <Object?>[note.id],
        );
      }
      await (_database.delete(
        _database.notebooks,
      )..where((row) => row.id.equals(id)))
          .go();
    });
  }
}
