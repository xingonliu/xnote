import '../model/app_settings.dart';
import '../model/background_key.dart';

// -- Type Definitions

abstract interface class SettingsRepository {
  Stream<AppSettings> watchSettings();

  Future<AppSettings> getSettings();

  Future<void> setDefaultBackground(BackgroundKey background);

  Future<void> setThemeMode(AppThemeMode mode);
}
