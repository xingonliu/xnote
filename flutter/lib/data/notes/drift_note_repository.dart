import 'package:drift/drift.dart';

import '../../core/ids/id_generator.dart';
import '../../core/time/clock.dart';
import '../../domain/document/note_document.dart';
import '../../domain/model/background_key.dart';
import '../../domain/model/note.dart';
import '../../domain/model/note_revision.dart';
import '../../domain/model/note_search_result.dart';
import '../../domain/model/notebook.dart';
import '../../domain/repositories/note_repository.dart';
import '../../domain/rules/markdown_conversion_rules.dart';
import '../../domain/rules/notebook_rules.dart';
import '../../domain/rules/recycle_bin_policy.dart';
import '../../domain/text/fts_index_text.dart';
import '../../domain/text/note_plain_text.dart';
import '../../domain/text/search_text.dart';
import '../attachments/orphan_attachment_cleanup.dart';
import '../database/row_mappers.dart';
import '../database/xnote_database.dart';
import '../files/attachment_file_store.dart';
import 'note_index.dart';

// -- Type Definitions

final class DriftNoteRepository implements NoteRepository {
  const DriftNoteRepository({
    required XNoteDatabase database,
    required AttachmentFileStore attachmentFiles,
    required IdGenerator idGenerator,
    required Clock clock,
  })  : _database = database,
        _attachmentFiles = attachmentFiles,
        _idGenerator = idGenerator,
        _clock = clock;

  final XNoteDatabase _database;
  final AttachmentFileStore _attachmentFiles;
  final IdGenerator _idGenerator;
  final Clock _clock;

  // -- Functions

  @override
  Stream<List<Note>> watchActiveNotes({
    NoteListSort sort = NoteListSort.updatedAt,
  }) {
    final query = _database.select(_database.notes)
      ..where((row) => row.deletedAtEpochMilliseconds.isNull());
    return _mapNoteStream(query.watch(), sort);
  }

  @override
  Stream<List<Note>> watchUnfiledNotes({
    NoteListSort sort = NoteListSort.updatedAt,
  }) {
    final query = _database.select(_database.notes)
      ..where(
        (row) =>
            row.deletedAtEpochMilliseconds.isNull() & row.notebookId.isNull(),
      );
    return _mapNoteStream(query.watch(), sort);
  }

  @override
  Stream<List<Note>> watchNotesInNotebook(
    String notebookId, {
    NoteListSort sort = NoteListSort.manual,
  }) {
    final query = _database.select(_database.notes)
      ..where(
        (row) =>
            row.deletedAtEpochMilliseconds.isNull() &
            row.notebookId.equals(notebookId),
      );
    return _mapNoteStream(query.watch(), sort);
  }

  @override
  Stream<List<Note>> watchTrashedNotes() {
    final query = _database.select(_database.notes)
      ..where((row) => row.deletedAtEpochMilliseconds.isNotNull());
    return query.watch().map((rows) {
      final notes = rows.map(noteFromRow).toList()
        ..sort(
          (left, right) => (right.deletedAtEpochMilliseconds ?? 0).compareTo(
            left.deletedAtEpochMilliseconds ?? 0,
          ),
        );
      return List<Note>.unmodifiable(notes);
    });
  }

  @override
  Stream<Note?> watchNote(String id) {
    final query = _database.select(_database.notes)
      ..where((row) => row.id.equals(id));
    return query.watchSingleOrNull().map(
          (row) => row == null ? null : noteFromRow(row),
        );
  }

  @override
  Future<Note?> getNote(String id) async {
    final query = _database.select(_database.notes)
      ..where((row) => row.id.equals(id));
    final row = await query.getSingleOrNull();
    return row == null ? null : noteFromRow(row);
  }

  @override
  Future<Note> createRichNote({String? notebookId}) async {
    await _requireNotebookIfPresent(notebookId);
    final now = _clock.nowEpochMilliseconds();
    final note = withDerivedText(
      Note(
        id: _idGenerator.nextId(),
        notebookId: notebookId,
        title: '',
        kind: NoteKind.rich,
        document: emptyNoteDocument(_idGenerator),
        markdownText: null,
        backgroundKey: null,
        sortIndex: now,
        visibleCharacterCount: 0,
        latinWordCount: 0,
        summary: '',
        createdAtEpochMilliseconds: now,
        updatedAtEpochMilliseconds: now,
        deletedAtEpochMilliseconds: null,
        originalNotebookName: null,
      ),
    );
    await _database.transaction(() async {
      await _database.into(_database.notes).insert(noteToRow(note));
      await replaceNoteSearchIndex(_database, note);
    });
    return note;
  }

  @override
  Future<Note> saveNote(Note note) async {
    return _database.transaction(() async {
      final existing = await getNote(note.id);
      if (existing == null) {
        throw StateError('Note not found: ${note.id}');
      }
      if (note.kind != existing.kind) {
        throw StateError('Note kind can only change through conversion');
      }
      await _requireNotebookIfPresent(note.notebookId);
      final saved = withDerivedText(
        note.copyWith(
          createdAtEpochMilliseconds: existing.createdAtEpochMilliseconds,
          updatedAtEpochMilliseconds: _clock.nowEpochMilliseconds(),
          deletedAtEpochMilliseconds: existing.deletedAtEpochMilliseconds,
          originalNotebookName: existing.originalNotebookName,
        ),
      );
      await _database.update(_database.notes).replace(noteToRow(saved));
      await replaceNoteSearchIndex(_database, saved);
      return saved;
    });
  }

  @override
  Future<Note> setNoteBackground(
    String noteId,
    BackgroundKey? background,
  ) async {
    final existing = await _requireNote(noteId);
    if (existing.isTrashed) {
      throw StateError('Trashed notes cannot change background');
    }
    final updated = existing.copyWith(
      backgroundKey: background,
      updatedAtEpochMilliseconds: _clock.nowEpochMilliseconds(),
    );
    await _database.update(_database.notes).replace(noteToRow(updated));
    return updated;
  }

  @override
  Future<Note> convertToMarkdown(String noteId) async {
    return _database.transaction(() async {
      final existing = await _requireNote(noteId);
      if (existing.isTrashed) {
        throw StateError('Trashed notes cannot be converted');
      }
      final conversion = convertRichNoteToMarkdown(
        existing,
        idGenerator: _idGenerator,
        clock: _clock,
      );
      await _database
          .into(_database.noteRevisions)
          .insert(noteRevisionToRow(conversion.revision));
      await _database
          .update(_database.notes)
          .replace(noteToRow(conversion.note));
      await replaceNoteSearchIndex(_database, conversion.note);
      return conversion.note;
    });
  }

  @override
  Future<NoteRevision> saveRevision(
    String noteId,
    RevisionReason reason,
  ) async {
    final note = await _requireNote(noteId);
    final revision = NoteRevision(
      id: _idGenerator.nextId(),
      noteId: note.id,
      reason: reason,
      kind: note.kind,
      title: note.title,
      document: note.document,
      markdownText: note.markdownText,
      createdAtEpochMilliseconds: _clock.nowEpochMilliseconds(),
    );
    await _database
        .into(_database.noteRevisions)
        .insert(noteRevisionToRow(revision));
    return revision;
  }

  @override
  Future<List<NoteRevision>> getNoteRevisions(String noteId) async {
    final query = _database.select(_database.noteRevisions)
      ..where((row) => row.noteId.equals(noteId))
      ..orderBy(<OrderingTerm Function($NoteRevisionsTable)>[
        (row) => OrderingTerm.desc(row.createdAtEpochMilliseconds),
      ]);
    final rows = await query.get();
    return List<NoteRevision>.unmodifiable(rows.map(noteRevisionFromRow));
  }

  @override
  Future<void> reorderNotes(List<String> orderedIds) async {
    if (orderedIds.isEmpty) {
      return;
    }
    await _database.transaction(() async {
      for (var index = 0; index < orderedIds.length; index += 1) {
        await (_database.update(_database.notes)
              ..where((row) => row.id.equals(orderedIds[index])))
            .write(NotesCompanion(sortIndex: Value<int>(index)));
      }
    });
  }

  @override
  Future<void> moveNotes(Iterable<String> ids, String? notebookId) async {
    final idList = ids.toSet().toList(growable: false);
    if (idList.isEmpty) {
      return;
    }
    await _requireNotebookIfPresent(notebookId);
    final now = _clock.nowEpochMilliseconds();
    await _database.transaction(() async {
      final current = await _notesByIds(idList);
      for (final note in current) {
        final updated = withDerivedText(
          note.copyWith(
            notebookId: notebookId,
            sortIndex: now,
            updatedAtEpochMilliseconds: now,
          ),
        );
        await _database.update(_database.notes).replace(noteToRow(updated));
        await replaceNoteSearchIndex(_database, updated);
      }
    });
  }

  @override
  Future<void> trashNotes(Iterable<String> ids) async {
    final idList = ids.toSet().toList(growable: false);
    if (idList.isEmpty) {
      return;
    }
    final now = _clock.nowEpochMilliseconds();
    await _database.transaction(() async {
      for (final note in await _notesByIds(idList)) {
        final updated = note.isTrashed
            ? note
            : withDerivedText(
                note.copyWith(deletedAtEpochMilliseconds: now),
              );
        await _database.update(_database.notes).replace(noteToRow(updated));
        await replaceNoteSearchIndex(_database, updated);
      }
    });
  }

  @override
  Future<void> restoreNotes(Iterable<String> ids) async {
    final idList = ids.toSet().toList(growable: false);
    if (idList.isEmpty) {
      return;
    }
    final notebookIds = (await _database.select(_database.notebooks).get())
        .map((row) => row.id)
        .toSet();
    final now = _clock.nowEpochMilliseconds();
    await _database.transaction(() async {
      for (final note in await _notesByIds(idList)) {
        final restored = withDerivedText(
          note.copyWith(
            notebookId: notebookIdAfterRestore(
              note,
              notebookIds.contains,
            ),
            deletedAtEpochMilliseconds: null,
            originalNotebookName: null,
            updatedAtEpochMilliseconds: now,
          ),
        );
        await _database.update(_database.notes).replace(noteToRow(restored));
        await replaceNoteSearchIndex(_database, restored);
      }
    });
  }

  @override
  Future<void> permanentlyDeleteNotes(Iterable<String> ids) async {
    final idList = ids.toSet().toList(growable: false);
    if (idList.isEmpty) {
      return;
    }
    final orphaned = await _database.transaction(() async {
      await (_database.delete(
        _database.notes,
      )..where((row) => row.id.isIn(idList)))
          .go();
      return removeOrphanAttachmentRows(_database);
    });
    for (final attachment in orphaned) {
      await _attachmentFiles.delete(attachment.relativePath);
    }
  }

  @override
  Future<void> emptyTrash() async {
    final rows = await (_database.select(
      _database.notes,
    )..where((row) => row.deletedAtEpochMilliseconds.isNotNull()))
        .get();
    await permanentlyDeleteNotes(rows.map((row) => row.id));
  }

  @override
  Future<void> purgeExpiredTrash() async {
    final cutoff = _clock.nowEpochMilliseconds() -
        recycleBinRetentionDays * Duration.millisecondsPerDay;
    final rows = await (_database.select(_database.notes)
          ..where(
            (row) =>
                row.deletedAtEpochMilliseconds.isSmallerOrEqualValue(cutoff),
          ))
        .get();
    await permanentlyDeleteNotes(rows.map((row) => row.id));
  }

  @override
  Future<List<NoteSearchResult>> searchNotes(
    String query, {
    String? notebookId,
  }) async {
    final matchQuery = ftsMatchQuery(query);
    if (matchQuery == null) {
      return <NoteSearchResult>[];
    }
    final variables = <Variable<Object>>[Variable<String>(matchQuery)];
    var sql = 'SELECT notes.* FROM notes_fts '
        'JOIN notes ON notes.id = notes_fts.note_id '
        'WHERE notes_fts MATCH ? AND notes.deleted_at_epoch_milliseconds IS NULL';
    if (notebookId != null) {
      sql += ' AND notes.notebook_id = ?';
      variables.add(Variable<String>(notebookId));
    }
    final rows = await _database.customSelect(
      sql,
      variables: variables,
      readsFrom: {_database.notes},
    ).get();
    final results = <NoteSearchResult>[
      for (final row in rows)
        if (_database.notes.map(row.data) case final noteRow)
          NoteSearchResult(
            note: noteFromRow(noteRow),
            matchedText: searchSnippet(
              extractNotePlainText(noteFromRow(noteRow)),
              query,
            ),
          ),
    ];
    results.sort((left, right) {
      final leftTitle = searchMatchRanges(left.note.title, query).isNotEmpty;
      final rightTitle = searchMatchRanges(right.note.title, query).isNotEmpty;
      if (leftTitle != rightTitle) {
        return leftTitle ? -1 : 1;
      }
      return right.note.updatedAtEpochMilliseconds.compareTo(
        left.note.updatedAtEpochMilliseconds,
      );
    });
    return List<NoteSearchResult>.unmodifiable(results);
  }

  @override
  Future<Map<String, NotebookStats>> notebookStats() async {
    final active = await (_database.select(
      _database.notes,
    )..where((row) => row.deletedAtEpochMilliseconds.isNull()))
        .get();
    final counts = <String, (int, int)>{};
    for (final row in active) {
      final notebookId = row.notebookId;
      if (notebookId == null) {
        continue;
      }
      final previous = counts[notebookId] ?? (0, 0);
      counts[notebookId] = (
        previous.$1 + 1,
        previous.$2 + row.visibleCharacterCount,
      );
    }
    return Map<String, NotebookStats>.unmodifiable(<String, NotebookStats>{
      for (final entry in counts.entries)
        entry.key: NotebookStats(
          noteCount: entry.value.$1,
          characterCount: entry.value.$2,
        ),
    });
  }

  @override
  Future<NotebookStats> unfiledStats() async {
    final rows = await (_database.select(_database.notes)
          ..where(
            (row) =>
                row.deletedAtEpochMilliseconds.isNull() &
                row.notebookId.isNull(),
          ))
        .get();
    return NotebookStats(
      noteCount: rows.length,
      characterCount: rows.fold<int>(
        0,
        (total, row) => total + row.visibleCharacterCount,
      ),
    );
  }

  Stream<List<Note>> _mapNoteStream(
    Stream<List<NoteRow>> rows,
    NoteListSort sort,
  ) {
    return rows.map((values) {
      final notes = values.map(noteFromRow).toList()..sort(_comparator(sort));
      return List<Note>.unmodifiable(notes);
    });
  }

  Future<Note> _requireNote(String id) async {
    final note = await getNote(id);
    if (note == null) {
      throw StateError('Note not found: $id');
    }
    return note;
  }

  Future<void> _requireNotebookIfPresent(String? id) async {
    if (id == null) {
      return;
    }
    final query = _database.select(_database.notebooks)
      ..where((row) => row.id.equals(id));
    if (await query.getSingleOrNull() == null) {
      throw StateError('Notebook not found: $id');
    }
  }

  Future<List<Note>> _notesByIds(List<String> ids) async {
    final rows = await (_database.select(
      _database.notes,
    )..where((row) => row.id.isIn(ids)))
        .get();
    return rows.map(noteFromRow).toList(growable: false);
  }
}

// -- Functions

Comparator<Note> _comparator(NoteListSort sort) => switch (sort) {
      NoteListSort.updatedAt => (left, right) => right
          .updatedAtEpochMilliseconds
          .compareTo(left.updatedAtEpochMilliseconds),
      NoteListSort.createdAt => (left, right) => right
          .createdAtEpochMilliseconds
          .compareTo(left.createdAtEpochMilliseconds),
      NoteListSort.title => (left, right) =>
          left.title.toLowerCase().compareTo(right.title.toLowerCase()),
      NoteListSort.manual => (left, right) =>
          left.sortIndex.compareTo(right.sortIndex),
    };
