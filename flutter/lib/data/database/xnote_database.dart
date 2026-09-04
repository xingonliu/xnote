import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';

import 'tables.dart';

part 'xnote_database.g.dart';

// -- Type Definitions

@DriftDatabase(
  tables: <Type>[
    Notebooks,
    Notes,
    NoteRevisions,
    Attachments,
    SearchHistoryEntries,
    AppSettingsEntries,
  ],
)
final class XNoteDatabase extends _$XNoteDatabase {
  XNoteDatabase(super.executor);

  factory XNoteDatabase.inMemory() => XNoteDatabase(
        DatabaseConnection(
          NativeDatabase.memory(),
          closeStreamsSynchronously: true,
        ),
      );

  factory XNoteDatabase.fromFile(File file) => XNoteDatabase(
        LazyDatabase(() async {
          await file.parent.create(recursive: true);
          return NativeDatabase.createInBackground(file);
        }),
      );

  // -- Derived Values

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (migrator) async {
          await migrator.createAll();
          await _createIndexes();
          await _createFullTextSearch();
          await into(appSettingsEntries).insert(
            AppSettingsEntriesCompanion.insert(
              defaultBackgroundKey: 'builtin:default',
              themeMode: 'system',
            ),
          );
        },
        beforeOpen: (_) async {
          await customStatement('PRAGMA foreign_keys = ON');
        },
      );

  // -- Functions

  Future<void> _createIndexes() async {
    await customStatement(
      'CREATE INDEX notes_notebook_id ON notes (notebook_id)',
    );
    await customStatement(
      'CREATE INDEX notes_deleted_at ON notes (deleted_at_epoch_milliseconds)',
    );
    await customStatement(
      'CREATE INDEX notes_updated_at ON notes (updated_at_epoch_milliseconds DESC)',
    );
    await customStatement(
      'CREATE INDEX notes_manual_sort ON notes (notebook_id, sort_index)',
    );
    await customStatement(
      'CREATE INDEX revisions_note_id ON note_revisions (note_id)',
    );
    await customStatement(
      'CREATE INDEX search_history_used_at '
      'ON search_history (used_at_epoch_milliseconds DESC)',
    );
  }

  Future<void> _createFullTextSearch() async {
    await customStatement(
      'CREATE VIRTUAL TABLE notes_fts USING fts5('
      'note_id UNINDEXED, title, body, tokenize = "unicode61")',
    );
    await customStatement(
      'CREATE TRIGGER notes_delete_fts AFTER DELETE ON notes BEGIN '
      'DELETE FROM notes_fts WHERE note_id = old.id; END',
    );
  }
}
