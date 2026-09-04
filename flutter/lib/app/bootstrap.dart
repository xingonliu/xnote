import 'package:flutter/widgets.dart';

import 'xnote_app.dart';

// -- Lifecycle Hooks

void bootstrap() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const XNoteApp());
}
