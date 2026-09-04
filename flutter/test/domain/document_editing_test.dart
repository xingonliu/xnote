import 'package:flutter_test/flutter_test.dart';
import 'package:xnote/domain/document/editor_history.dart';
import 'package:xnote/domain/document/inline_editing.dart';
import 'package:xnote/domain/document/note_block.dart';
import 'package:xnote/domain/document/note_document.dart';
import 'package:xnote/domain/document/note_document_editing.dart';
import 'package:xnote/domain/document/table_editing.dart';

import 'domain_test_fixtures.dart';

// -- Tests

void main() {
  group('Inline editing', () {
    test('coalesce merges adjacent runs with the same style', () {
      final merged = <InlineRun>[
        const InlineRun(text: '你', bold: true),
        const InlineRun(text: '好', bold: true),
        const InlineRun(text: '世界'),
      ].coalesced();
      expect(merged, hasLength(2));
      expect(merged[0], const InlineRun(text: '你好', bold: true));
      expect(merged[1], const InlineRun(text: '世界'));
    });

    test('replaceRange inserts styled text and keeps neighbors', () {
      final updated = <InlineRun>[
        const InlineRun(text: '你好世界'),
      ].replacedRange(2, 2, const InlineRun(text: '，', bold: true));
      expect(plainText(updated), '你好，世界');
      expect(updated, hasLength(3));
      expect(updated[1], const InlineRun(text: '，', bold: true));
    });

    test('mapRange changes only the selected text', () {
      final updated = <InlineRun>[
        const InlineRun(text: 'abcdef'),
      ].mapRange(2, 4, (run) => run.copyWith(bold: true));
      expect(updated.map((run) => run.text), <String>['ab', 'cd', 'ef']);
      expect(updated.map((run) => run.bold), <bool>[false, true, false]);
    });

    test('marksAt uses the character before the caret', () {
      final inlines = <InlineRun>[
        const InlineRun(text: 'aa', bold: true),
        const InlineRun(text: 'bb', italic: true),
      ];
      expect(inlines.marksAt(0).bold, isTrue);
      expect(inlines.marksAt(2).bold, isTrue);
      expect(inlines.marksAt(3).italic, isTrue);
      expect(inlines.marksAt(4).italic, isTrue);
    });

    test('reverse ranges and out-of-bounds offsets are clamped', () {
      final updated = <InlineRun>[
        const InlineRun(text: 'abcd'),
      ].replacedRange(99, -2, const InlineRun(text: '替换'));
      expect(plainText(updated), '替换');
    });

    test('findTextReplacement detects a middle edit', () {
      final replacement = findTextReplacement('你好世界', '你好，世界');
      expect(replacement.start, 2);
      expect(replacement.end, 2);
      expect(replacement.insertedText, '，');
    });
  });

  group('Document editing', () {
    test('typing replaces selection and moves caret', () {
      final change = replaceSelectedText(
        _document(_text('text', '你好')),
        selection: const EditorSelection(blockId: 'text', start: 2, end: 2),
        newText: '世界',
        marks: const InlineMarks(),
      );
      expect(_textValue(change.document, 'text'), '你好世界');
      expect(change.selection,
          const EditorSelection(blockId: 'text', start: 4, end: 4));
    });

    test('multiline insertion creates deterministic paragraph ids', () {
      final change = replaceSelectedText(
        _document(_text('text', '前后')),
        selection: const EditorSelection(blockId: 'text', start: 1, end: 1),
        newText: '一\n二\n三',
        marks: const InlineMarks(bold: true),
        idGenerator: SequenceIdGenerator(<String>['line-2', 'line-3']),
      );
      expect(change.document.blocks.map((block) => block.id), <String>[
        'text',
        'line-2',
        'line-3',
      ]);
      expect(_textValue(change.document, 'text'), '前一');
      expect(_textValue(change.document, 'line-2'), '二');
      expect(_textValue(change.document, 'line-3'), '三后');
    });

    test('enter splits a heading into a body block', () {
      final document = _document(
        TextBlock(
          id: 'heading',
          paragraphStyle: ParagraphStyle.heading,
          inlines: const <InlineRun>[InlineRun(text: '标题后接正文')],
        ),
      );
      final change = splitTextBlockAt(
        document,
        selection: const EditorSelection(blockId: 'heading', start: 2, end: 2),
        newBlockId: 'body',
      );
      expect(_textValue(change.document, 'heading'), '标题');
      expect(
        (change.document.blockById('heading')! as TextBlock).paragraphStyle,
        ParagraphStyle.heading,
      );
      expect(_textValue(change.document, 'body'), '后接正文');
      expect(
        (change.document.blockById('body')! as TextBlock).paragraphStyle,
        ParagraphStyle.body,
      );
    });

    test('enter on an empty structured block exits one level at a time', () {
      final original = _document(
        TextBlock(
          id: 'item',
          listMarker: ListMarker.checklist,
          indent: 1,
          quoted: true,
          checked: true,
        ),
      );
      final afterIndent = splitTextBlockAt(
        original,
        selection: const EditorSelection(blockId: 'item'),
        newBlockId: 'unused',
      );
      expect((afterIndent.document.blocks.single as TextBlock).indent, 0);
      final afterList = splitTextBlockAt(
        afterIndent.document,
        selection: afterIndent.selection,
        newBlockId: 'unused',
      );
      final listExited = afterList.document.blocks.single as TextBlock;
      expect(listExited.listMarker, ListMarker.none);
      expect(listExited.checked, isFalse);
      final afterQuote = splitTextBlockAt(
        afterList.document,
        selection: afterList.selection,
        newBlockId: 'unused',
      );
      expect((afterQuote.document.blocks.single as TextBlock).quoted, isFalse);
    });

    test('backspace at start merges with previous paragraph', () {
      final change = deleteBackward(
        _document(_text('first', '你好'), _text('second', '世界')),
        selection: const EditorSelection(blockId: 'second'),
      );
      expect(change.document.blocks, hasLength(1));
      expect(_textValue(change.document, 'first'), '你好世界');
      expect(change.selection.start, 2);
    });

    test('paragraph, list, quote, indent, alignment and checklist commands',
        () {
      var document = _document(_text('text', '正文'));
      const selection = EditorSelection(blockId: 'text');
      document = setParagraphStyle(document, selection, ParagraphStyle.heading)
          .document;
      expect((document.blocks.single as TextBlock).paragraphStyle,
          ParagraphStyle.heading);
      document =
          setListMarker(document, selection, ListMarker.checklist).document;
      document = toggleChecked(document, selection).document;
      document = changeIndent(document, selection, 99).document;
      document = toggleQuoted(document, selection).document;
      document =
          setAlignment(document, selection, TextAlignment.right).document;
      final block = document.blocks.single as TextBlock;
      expect(block.paragraphStyle, ParagraphStyle.body);
      expect(block.checked, isTrue);
      expect(block.indent, maxTextIndent);
      expect(block.quoted, isTrue);
      expect(block.alignment, TextAlignment.right);
      document =
          setListMarker(document, selection, ListMarker.checklist).document;
      expect((document.blocks.single as TextBlock).listMarker, ListMarker.none);
    });

    test('collapsed heading hides until same-level boundary', () {
      final document = _document(
        TextBlock(
          id: 'heading-1',
          paragraphStyle: ParagraphStyle.heading,
          collapsed: true,
        ),
        _text('body-1', '隐藏'),
        TextBlock(id: 'sub', paragraphStyle: ParagraphStyle.subheading),
        _text('body-2', '仍隐藏'),
        TextBlock(id: 'heading-2', paragraphStyle: ParagraphStyle.heading),
        _text('body-3', '可见'),
      );
      expect(
        hiddenBlockIds(document),
        <String>{'body-1', 'sub', 'body-2'},
      );
    });

    test('numbered labels restart after a break and nest by indent', () {
      final labels = numberedLabels(
        _document(
          TextBlock(id: 'n1', listMarker: ListMarker.numbered),
          TextBlock(id: 'n2', listMarker: ListMarker.numbered, indent: 1),
          TextBlock(id: 'n3', listMarker: ListMarker.numbered),
          _text('gap', '打断'),
          TextBlock(id: 'n4', listMarker: ListMarker.numbered),
        ),
      );
      expect(labels, <String, int>{'n1': 1, 'n2': 1, 'n3': 2, 'n4': 1});
    });

    test('inline style and links affect only the selection', () {
      final document = _document(_text('text', 'abcdef'));
      final styled = applyInlineMark(
        document,
        selection: const EditorSelection(blockId: 'text', start: 2, end: 4),
        mark: InlineMark.italic,
        typingMarks: const InlineMarks(),
      );
      final linked = setLink(
        styled.change.document,
        selection: styled.change.selection,
        url: ' https://example.com ',
        typingMarks: styled.typingMarks,
      );
      final runs = (linked.change.document.blocks.single as TextBlock).inlines;
      expect(runs.map((run) => run.text), <String>['ab', 'cd', 'ef']);
      expect(runs[1].italic, isTrue);
      expect(runs[1].linkUrl, 'https://example.com');
      final removed = setLink(
        linked.change.document,
        selection: linked.change.selection,
        url: ' ',
        typingMarks: linked.typingMarks,
      );
      expect(
        (removed.change.document.blocks.single as TextBlock).inlines[1].linkUrl,
        isNull,
      );
    });

    test('collapsed caret style changes typing marks only', () {
      final document = _document(_text('text', '正文'));
      final result = applyInlineMark(
        document,
        selection: const EditorSelection(blockId: 'text', start: 1, end: 1),
        mark: InlineMark.bold,
        typingMarks: const InlineMarks(),
      );
      expect(result.change.document, document);
      expect(result.typingMarks.bold, isTrue);
    });

    test('table edits stay rectangular and restore valid focus', () {
      final inserted = insertTable(
        _document(_text('keep', '上文')),
        selection: const EditorSelection(blockId: 'keep'),
        tableId: 'table',
      );
      expect(inserted.selection.tableRow, 0);
      var document = replaceSelectedText(
        inserted.document,
        selection: inserted.selection,
        newText: '单元格',
        marks: const InlineMarks(bold: true),
      ).document;
      var change = insertTableRow(
        document,
        selection: const EditorSelection(
          blockId: 'table',
          tableRow: 0,
          tableColumn: 0,
        ),
        afterRow: 0,
      );
      change = insertTableColumn(
        change.document,
        selection: change.selection,
        afterColumn: 0,
      );
      final table = change.document.blockById('table')! as TableBlock;
      expect(table.rows, hasLength(3));
      expect(table.columnCount, 3);
      expect(table.rows.every((row) => row.cells.length == 3), isTrue);
      document = deleteTableColumn(
        change.document,
        selection: change.selection,
        column: 1,
        fallbackTextId: 'fallback',
      ).document;
      expect((document.blockById('table')! as TableBlock).columnCount, 2);
    });

    test('deleting final table row focuses adjacent text or creates fallback',
        () {
      final withText = deleteTableRow(
        _document(
          _text('keep', '保留'),
          TableBlock(
            id: 'table',
            rows: <TableRow>[
              TableRow(cells: <TableCell>[TableCell()])
            ],
          ),
        ),
        selection: const EditorSelection(
          blockId: 'table',
          tableRow: 0,
          tableColumn: 0,
        ),
        row: 0,
        fallbackTextId: 'fallback',
      );
      expect(
          withText.document.blocks.map((block) => block.id), <String>['keep']);
      expect(withText.selection.blockId, 'keep');

      final empty = deleteBlock(
        NoteDocument(),
        blockId: 'missing',
        fallbackTextId: 'fallback',
      );
      expect(empty.document.blocks.single.id, 'fallback');
    });
  });

  group('Editor history', () {
    test('undo and redo restore snapshots and selection', () {
      final history = EditorHistory();
      final first = _snapshot('一', 'a');
      final second = _snapshot('二', 'b');
      history.capture(first);
      expect(history.undo(second), first);
      expect(history.redo(first), second);
    });

    test('same typing key coalesces while commands create boundaries', () {
      final history = EditorHistory();
      history.capture(_snapshot('一', 'a'), coalesceKey: 'type:a');
      history.capture(_snapshot('一二', 'a'), coalesceKey: 'type:a');
      history.capture(_snapshot('命令', 'a'));
      expect(history.undo(_snapshot('完成', 'a'))!.title, '命令');
      expect(history.undo(_snapshot('命令', 'a'))!.title, '一');
      expect(history.canUndo, isFalse);
    });

    test('history limit evicts the oldest snapshot', () {
      final history = MarkdownEditorHistory(limit: 2);
      history.capture('一');
      history.capture('二');
      history.capture('三');
      expect(history.undo('四'), '三');
      expect(history.undo('三'), '二');
      expect(history.undo('二'), isNull);
      expect(history.redo('二'), '三');
    });
  });
}

// -- Functions

NoteDocument _document(NoteBlock first,
    [NoteBlock? second,
    NoteBlock? third,
    NoteBlock? fourth,
    NoteBlock? fifth,
    NoteBlock? sixth]) {
  return NoteDocument(
    blocks: <NoteBlock>[
      first,
      if (second != null) second,
      if (third != null) third,
      if (fourth != null) fourth,
      if (fifth != null) fifth,
      if (sixth != null) sixth,
    ],
  );
}

TextBlock _text(String id, String value) => TextBlock(
      id: id,
      inlines: <InlineRun>[InlineRun(text: value)],
    );

String _textValue(NoteDocument document, String blockId) =>
    plainText((document.blockById(blockId)! as TextBlock).inlines);

EditorSnapshot _snapshot(String title, String blockId) => EditorSnapshot(
      title: title,
      document: _document(TextBlock(id: blockId)),
      selection: EditorSelection(blockId: blockId),
    );
