import 'xnote_dependencies.dart';
import 'xnote_dependency_loader_native.dart'
    if (dart.library.js_interop) 'xnote_dependency_loader_unsupported.dart'
    as platform;

// -- Functions

Future<XNoteDependencies> openXNoteDependencies() {
  return platform.openPlatformXNoteDependencies();
}
