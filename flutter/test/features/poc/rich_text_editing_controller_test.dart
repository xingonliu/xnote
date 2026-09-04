import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xnote/features/poc/rich_text_editing_controller.dart';

// -- Functions

void main() {
  test('keeps composing and selection while applying inline bold', () {
    final controller = RichTextEditingController(text: '中文 English');
    addTearDown(controller.dispose);
    const selection = TextSelection(baseOffset: 0, extentOffset: 2);
    const composing = TextRange(start: 1, end: 2);
    controller.value = const TextEditingValue(
      text: '中文 English',
      selection: selection,
      composing: composing,
    );

    controller.toggleBold();

    expect(controller.selection, selection);
    expect(controller.value.composing, composing);
    expect(
        controller.boldRanges, const <TextRange>[TextRange(start: 0, end: 2)]);
  });

  test('extends a bold run when composing inserts text inside it', () {
    final controller = RichTextEditingController(text: '中文');
    addTearDown(controller.dispose);
    controller.selection = const TextSelection(baseOffset: 0, extentOffset: 2);
    controller.toggleBold();

    controller.value = const TextEditingValue(
      text: '中wen文🙂',
      selection: TextSelection.collapsed(offset: 4),
      composing: TextRange(start: 1, end: 4),
    );

    expect(
      controller.boldRanges,
      const <TextRange>[TextRange(start: 0, end: 7)],
    );
    expect(controller.selection, const TextSelection.collapsed(offset: 4));
    expect(controller.value.composing, const TextRange(start: 1, end: 4));
  });
}
