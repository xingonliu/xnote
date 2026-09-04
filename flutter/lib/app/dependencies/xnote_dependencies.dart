import '../../domain/model/app_settings.dart';
import '../../domain/repositories/attachment_repository.dart';
import '../../domain/repositories/note_repository.dart';
import '../../domain/repositories/notebook_repository.dart';
import '../../domain/repositories/search_history_repository.dart';
import '../../domain/repositories/settings_repository.dart';

// -- Type Definitions

abstract interface class XNoteDependencies {
  AppSettings get initialSettings;

  NoteRepository get notes;

  NotebookRepository get notebooks;

  AttachmentRepository get attachments;

  SearchHistoryRepository get searchHistory;

  SettingsRepository get settings;

  Future<void> close();
}
