import 'package:drift/drift.dart';

import '../../domain/model/app_settings.dart';
import '../../domain/model/background_key.dart';
import '../../domain/repositories/settings_repository.dart';
import '../database/xnote_database.dart';

// -- Type Definitions

final class DriftSettingsRepository implements SettingsRepository {
  const DriftSettingsRepository(this._database);

  final XNoteDatabase _database;

  // -- Functions

  @override
  Stream<AppSettings> watchSettings() {
    final query = _database.select(_database.appSettingsEntries)
      ..where((row) => row.singletonId.equals(1));
    return query.watchSingle().map(_fromRow);
  }

  @override
  Future<AppSettings> getSettings() async {
    final query = _database.select(_database.appSettingsEntries)
      ..where((row) => row.singletonId.equals(1));
    return _fromRow(await query.getSingle());
  }

  @override
  Future<void> setDefaultBackground(BackgroundKey background) async {
    await (_database.update(_database.appSettingsEntries)
          ..where((row) => row.singletonId.equals(1)))
        .write(
      AppSettingsEntriesCompanion(
        defaultBackgroundKey: Value<String>(background.encode()),
      ),
    );
  }

  @override
  Future<void> setThemeMode(AppThemeMode mode) async {
    await (_database.update(_database.appSettingsEntries)
          ..where((row) => row.singletonId.equals(1)))
        .write(
            AppSettingsEntriesCompanion(themeMode: Value<String>(mode.name)));
  }

  AppSettings _fromRow(AppSettingsRow row) {
    final background = parseBackgroundKey(row.defaultBackgroundKey);
    if (background == null) {
      throw StateError(
        'Invalid stored background key: ${row.defaultBackgroundKey}',
      );
    }
    return AppSettings(
      defaultBackground: background,
      themeMode: AppThemeMode.values.byName(row.themeMode),
    );
  }
}
