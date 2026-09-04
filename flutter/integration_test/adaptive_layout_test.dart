import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:xnote/main.dart' as app;

// -- Functions

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('starts the Flutter application', (tester) async {
    await app.main();
    await tester.pumpAndSettle();

    expect(find.text('XNote · Glass PoC'), findsOneWidget);
  });
}
