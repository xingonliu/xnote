import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xnote/app/xnote_app.dart';

// -- Functions

void main() {
  testWidgets('starts with the XNote foundation screen', (tester) async {
    await tester.pumpWidget(const XNoteApp());

    expect(find.text('XNote'), findsOneWidget);
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}
