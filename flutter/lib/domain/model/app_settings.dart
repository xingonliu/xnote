import 'background_key.dart';

// -- Type Definitions

enum AppThemeMode { system, light, dark }

final class AppSettings {
  const AppSettings({
    required this.defaultBackground,
    required this.themeMode,
  });

  final BackgroundKey defaultBackground;
  final AppThemeMode themeMode;

  // -- Functions

  AppSettings copyWith({
    BackgroundKey? defaultBackground,
    AppThemeMode? themeMode,
  }) {
    return AppSettings(
      defaultBackground: defaultBackground ?? this.defaultBackground,
      themeMode: themeMode ?? this.themeMode,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is AppSettings &&
        other.defaultBackground == defaultBackground &&
        other.themeMode == themeMode;
  }

  @override
  int get hashCode => Object.hash(defaultBackground, themeMode);
}

// -- Functions

AppSettings defaultAppSettings() => AppSettings(
      defaultBackground: defaultBackgroundKey(),
      themeMode: AppThemeMode.system,
    );
