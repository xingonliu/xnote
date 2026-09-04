import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import 'xnote_app.dart';

// -- Lifecycle Hooks

Future<void> bootstrap() async {
  WidgetsFlutterBinding.ensureInitialized();
  await LiquidGlassWidgets.initialize();
  runApp(
    LiquidGlassWidgets.wrap(
      child: const ProviderScope(child: XNoteApp()),
      adaptiveQuality: true,
      brightnessResolver: Theme.maybeBrightnessOf,
    ),
  );
}
