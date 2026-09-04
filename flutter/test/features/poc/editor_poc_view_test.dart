import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import 'package:xnote/features/poc/editor_poc_view.dart';

// -- Functions

Widget _buildSubject(ValueChanged<EditorPocDraft> onDraftSaved) {
  return MaterialApp(
    home: LiquidGlassWidgets.wrap(
      child: EditorPocView(onDraftSaved: onDraftSaved),
      brightnessResolver: Theme.maybeBrightnessOf,
    ),
  );
}

void main() {
  testWidgets('autosaves text after 450 milliseconds', (tester) async {
    final savedDrafts = <EditorPocDraft>[];
    await tester.pumpWidget(_buildSubject(savedDrafts.add));

    await tester.enterText(
      find.byKey(const Key('editor-paragraph')),
      '中文拼音 composing\nEmoji 🙂',
    );
    await tester.pump(const Duration(milliseconds: 449));
    expect(savedDrafts, isEmpty);

    await tester.pump(const Duration(milliseconds: 1));
    expect(savedDrafts, hasLength(1));
    expect(savedDrafts.single.paragraph, '中文拼音 composing\nEmoji 🙂');
  });

  testWidgets('persists inline bold ranges without changing selection', (
    tester,
  ) async {
    final savedDrafts = <EditorPocDraft>[];
    await tester.pumpWidget(_buildSubject(savedDrafts.add));
    final paragraph = tester.widget<TextField>(
      find.byKey(const Key('editor-paragraph')),
    );
    paragraph.controller!.selection = const TextSelection(
      baseOffset: 0,
      extentOffset: 2,
    );

    await tester.tap(find.byKey(const Key('toggle-bold')));
    await tester.pump(const Duration(milliseconds: 450));

    expect(savedDrafts, hasLength(1));
    expect(
      savedDrafts.single.boldRanges,
      const <TextRange>[TextRange(start: 0, end: 2)],
    );
    expect(
      paragraph.controller!.selection,
      const TextSelection(baseOffset: 0, extentOffset: 2),
    );
  });

  testWidgets('adds and removes table rows and columns with valid focus', (
    tester,
  ) async {
    await tester.pumpWidget(_buildSubject((draft) {}));

    await tester.ensureVisible(find.byKey(const Key('add-table-row')));
    await tester.tap(find.byKey(const Key('add-table-row')));
    await tester.pump();
    expect(find.byKey(const Key('table-cell-2-0')), findsOneWidget);

    await tester.tap(find.byKey(const Key('add-table-column')));
    await tester.pump();
    expect(find.byKey(const Key('table-cell-0-2')), findsOneWidget);

    await tester.tap(find.byKey(const Key('remove-table-row')));
    await tester.tap(find.byKey(const Key('remove-table-column')));
    await tester.pump();
    expect(find.byKey(const Key('table-cell-2-0')), findsNothing);
    expect(find.byKey(const Key('table-cell-0-2')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('forces a dirty draft save when the editor is disposed', (
    tester,
  ) async {
    final savedDrafts = <EditorPocDraft>[];
    await tester.pumpWidget(_buildSubject(savedDrafts.add));
    await tester.enterText(find.byKey(const Key('editor-title')), '返回前保存');

    await tester.pumpWidget(const SizedBox.shrink());

    expect(savedDrafts, hasLength(1));
    expect(savedDrafts.single.title, '返回前保存');
  });
}
