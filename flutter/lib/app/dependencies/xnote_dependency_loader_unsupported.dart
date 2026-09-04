import 'xnote_dependencies.dart';

// -- Functions

Future<XNoteDependencies> openPlatformXNoteDependencies() {
  return Future<XNoteDependencies>.error(
    UnsupportedError(
      'XNote persistent storage is not available on this unverified host.',
    ),
  );
}
