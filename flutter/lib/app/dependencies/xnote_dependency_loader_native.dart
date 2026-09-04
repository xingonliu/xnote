import 'dart:io';

import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

import '../../core/ids/id_generator.dart';
import '../../core/ids/uuid_id_generator.dart';
import '../../core/time/clock.dart';
import '../../data/attachments/drift_attachment_repository.dart';
import '../../data/database/xnote_database.dart';
import '../../data/files/attachment_file_store.dart';
import '../../data/maintenance/recycle_bin_maintenance.dart';
import '../../data/notes/drift_note_repository.dart';
import '../../data/notes/drift_notebook_repository.dart';
import '../../data/search/drift_search_history_repository.dart';
import '../../data/settings/drift_settings_repository.dart';
import '../../domain/model/app_settings.dart';
import '../../domain/repositories/attachment_repository.dart';
import '../../domain/repositories/note_repository.dart';
import '../../domain/repositories/notebook_repository.dart';
import '../../domain/repositories/search_history_repository.dart';
import '../../domain/repositories/settings_repository.dart';
import 'xnote_dependencies.dart';

// -- Type Definitions

final class NativeXNoteDependencies implements XNoteDependencies {
  const NativeXNoteDependencies._({
    required XNoteDatabase database,
    required this.initialSettings,
    required this.notes,
    required this.notebooks,
    required this.attachments,
    required this.searchHistory,
    required this.settings,
  }) : _database = database;

  final XNoteDatabase _database;

  @override
  final AppSettings initialSettings;

  @override
  final NoteRepository notes;

  @override
  final NotebookRepository notebooks;

  @override
  final AttachmentRepository attachments;

  @override
  final SearchHistoryRepository searchHistory;

  @override
  final SettingsRepository settings;

  // -- Functions

  static Future<NativeXNoteDependencies> openAt(
    Directory rootDirectory, {
    IdGenerator idGenerator = const UuidIdGenerator(),
    Clock clock = const SystemClock(),
  }) async {
    final database = XNoteDatabase.fromFile(
      File(path.join(rootDirectory.path, 'xnote.sqlite')),
    );
    return _create(
      database: database,
      rootDirectory: rootDirectory,
      idGenerator: idGenerator,
      clock: clock,
    );
  }

  static Future<NativeXNoteDependencies> inMemory({
    required Directory rootDirectory,
    IdGenerator idGenerator = const UuidIdGenerator(),
    Clock clock = const SystemClock(),
  }) {
    return _create(
      database: XNoteDatabase.inMemory(),
      rootDirectory: rootDirectory,
      idGenerator: idGenerator,
      clock: clock,
    );
  }

  static Future<NativeXNoteDependencies> _create({
    required XNoteDatabase database,
    required Directory rootDirectory,
    required IdGenerator idGenerator,
    required Clock clock,
  }) async {
    try {
      final files = AttachmentFileStore(rootDirectory);
      final notes = DriftNoteRepository(
        database: database,
        attachmentFiles: files,
        idGenerator: idGenerator,
        clock: clock,
      );
      final settings = DriftSettingsRepository(database);
      final dependencies = NativeXNoteDependencies._(
        database: database,
        initialSettings: await settings.getSettings(),
        notes: notes,
        notebooks: DriftNotebookRepository(
          database: database,
          idGenerator: idGenerator,
          clock: clock,
        ),
        attachments: DriftAttachmentRepository(
          database: database,
          files: files,
          idGenerator: idGenerator,
          clock: clock,
        ),
        searchHistory: DriftSearchHistoryRepository(
          database: database,
          clock: clock,
        ),
        settings: settings,
      );
      await RecycleBinMaintenance(notes).runStartupSweep();
      return dependencies;
    } catch (_) {
      await database.close();
      rethrow;
    }
  }

  @override
  Future<void> close() => _database.close();
}

// -- Functions

Future<XNoteDependencies> openPlatformXNoteDependencies() async {
  final directory = await getApplicationDocumentsDirectory();
  return NativeXNoteDependencies.openAt(directory);
}
