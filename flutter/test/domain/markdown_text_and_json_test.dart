import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xnote/domain/document/note_block.dart';
import 'package:xnote/domain/document/note_document.dart';
import 'package:xnote/domain/document/note_document_json.dart';
import 'package:xnote/domain/markdown/markdown_visible_text.dart';
import 'package:xnote/domain/markdown/rich_note_markdown.dart';
import 'package:xnote/domain/text/fts_index_text.dart';
import 'package:xnote/domain/text/note_plain_text.dart';
import 'package:xnote/domain/text/search_text.dart';

import 'domain_test_fixtures.dart';

// -- Tests

void main() {
  group('Rich note Markdown', () {
    test('preserves block order and supported formatting', () {
      final document = NoteDocument(
        blocks: <NoteBlock>[
          TextBlock(
            id: 'heading',
            paragraphStyle: ParagraphStyle.heading,
            inlines: const <InlineRun>[
              InlineRun(text: '概览', bold: true),
            ],
          ),
          TextBlock(
            id: 'check',
            listMarker: ListMarker.checklist,
            indent: 1,
            checked: true,
            inlines: const <InlineRun>[InlineRun(text: '完成转换')],
          ),
          TextBlock(
            id: 'quote',
            quoted: true,
            inlines: const <InlineRun>[
              InlineRun(text: '链接', linkUrl: 'https://example.com/a)'),
              InlineRun(text: '与高亮', highlight: true, underline: true),
            ],
          ),
          TableBlock(
            id: 'table',
            rows: <TableRow>[
              TableRow(
                cells: <TableCell>[
                  TableCell(inlines: const <InlineRun>[InlineRun(text: '项目')]),
                  TableCell(
                    inlines: const <InlineRun>[
                      InlineRun(text: '状态', italic: true),
                    ],
                  ),
                ],
              ),
              TableRow(
                cells: <TableCell>[
                  TableCell(inlines: const <InlineRun>[InlineRun(text: 'A|B')]),
                  TableCell(
                    inlines: const <InlineRun>[
                      InlineRun(text: '完成', strikethrough: true),
                    ],
                  ),
                ],
              ),
            ],
          ),
          TextBlock(
            id: 'code',
            paragraphStyle: ParagraphStyle.monospace,
            inlines: const <InlineRun>[
              InlineRun(text: 'val answer = 42\nprintln(answer)'),
            ],
          ),
        ],
      );
      expect(
        richNoteToMarkdown('计划', document),
        '# 计划\n\n'
        '## **概览**\n\n'
        '  - [x] 完成转换\n\n'
        '> [链接](https://example.com/a\\))==<u>与高亮</u>==\n\n'
        '| 项目 | *状态* |\n'
        '| --- | --- |\n'
        '| A\\|B | ~~完成~~ |\n\n'
        '```\n'
        'val answer = 42\n'
        'println(answer)\n'
        '```',
      );
    });

    test('drops alignment and collapse while keeping text', () {
      final document = NoteDocument(
        blocks: <NoteBlock>[
          TextBlock(
            id: 'subheading',
            paragraphStyle: ParagraphStyle.subheading,
            alignment: TextAlignment.right,
            collapsed: true,
            inlines: const <InlineRun>[InlineRun(text: '小节')],
          ),
        ],
      );
      expect(richNoteToMarkdown('标题', document), '# 标题\n\n### 小节');
    });

    test('chooses a code delimiter longer than embedded backticks', () {
      final document = NoteDocument(
        blocks: <NoteBlock>[
          TextBlock(
            id: 'code',
            paragraphStyle: ParagraphStyle.monospace,
            inlines: const <InlineRun>[InlineRun(text: 'use ``code``')],
          ),
        ],
      );
      expect(richNoteToMarkdown('', document), '``` use ``code`` ```');
    });

    test('media cannot bypass conversion preconditions', () {
      final document = NoteDocument(
        blocks: <NoteBlock>[ImageBlock(id: 'image', attachmentId: 'asset')],
      );
      expect(() => richNoteToMarkdown('', document), throwsStateError);
    });

    test('title comes only from a leading level-one heading', () {
      expect(markdownDocumentTitle('# **格式**标题 #\n正文'), '格式标题');
      expect(markdownDocumentTitle('## 小标题\n正文'), isEmpty);
      expect(markdownDocumentTitle('正文\n# 后置标题'), isEmpty);
      expect(
          richNoteToMarkdown(
              '', NoteDocument(blocks: <NoteBlock>[_body('正文')])),
          '正文');
    });
  });

  group('Visible text and statistics', () {
    test('counts body and table cells but not title or media', () {
      final document = NoteDocument(
        blocks: <NoteBlock>[
          _body('你好 Hello', id: 'text'),
          TableBlock(
            id: 'table',
            rows: <TableRow>[
              TableRow(
                cells: <TableCell>[
                  TableCell(inlines: const <InlineRun>[InlineRun(text: '世界')]),
                  TableCell(
                      inlines: const <InlineRun>[InlineRun(text: 'world')]),
                ],
              ),
            ],
          ),
          ImageBlock(id: 'image', attachmentId: 'photo'),
          DrawingBlock(
              id: 'drawing', attachmentId: 'ink', width: 10, height: 10),
        ],
      );
      final stats =
          noteVisibleTextStats(richNote(title: '不计入', document: document));
      expect(
          stats, const VisibleTextStats(characterCount: 14, latinWordCount: 2));
    });

    test('Markdown strips syntax and its leading title', () {
      const markdown = '# 笔记标题\n'
          '这是 **正文** 和 [链接](https://example.com)\n'
          '```\n'
          'code\n'
          '```';
      final visible = extractMarkdownVisibleText(markdown);
      expect(visibleTextStats(visible).characterCount, 11);
    });

    test('escaped syntax stays visible', () {
      expect(
        extractMarkdownVisibleText(r'# 标题' '\n\n' r'字面 \* 星号与 A\|B'),
        '\n字面 * 星号与 A|B',
      );
    });

    test('summary collapses whitespace and respects its limit', () {
      expect(summarizePlainText(' 一\n\t二  三 ', maximumLength: 3), '一 二');
      expect(() => summarizePlainText('a', maximumLength: -1),
          throwsArgumentError);
    });
  });

  group('Search text', () {
    test('FTS splits consecutive CJK characters into a phrase', () {
      expect(prepareFtsIndexText('我的笔记本'), '我 的 笔 记 本');
      expect(ftsMatchQuery('笔记本'), '"笔 记 本"');
      expect(ftsMatchQuery('   '), isNull);
      expect(ftsMatchQuery('a"(b)'), '"a b"');
    });

    test('finds every case-insensitive phrase match', () {
      expect(
        searchMatchRanges('Plan plan', 'plan'),
        const <TextMatchRange>[
          TextMatchRange(start: 0, endExclusive: 4),
          TextMatchRange(start: 5, endExclusive: 9),
        ],
      );
    });

    test('falls back to terms when phrase spacing differs', () {
      expect(
        searchMatchRanges('hello, world', 'hello world'),
        const <TextMatchRange>[
          TextMatchRange(start: 0, endExclusive: 5),
          TextMatchRange(start: 7, endExclusive: 12),
        ],
      );
    });

    test('snippet keeps a match and marks trimmed edges', () {
      final text = '${List<String>.filled(80, '前').join()}笔记本'
          '${List<String>.filled(80, '后').join()}';
      final snippet = searchSnippet(text, '笔记本', maximumLength: 40);
      expect(snippet, startsWith('…'));
      expect(snippet, endsWith('…'));
      expect(snippet, contains('笔记本'));
    });
  });

  group('Note document JSON', () {
    final document = NoteDocument(
      blocks: <NoteBlock>[
        TextBlock(
          id: 'text',
          paragraphStyle: ParagraphStyle.heading,
          alignment: TextAlignment.center,
          listMarker: ListMarker.checklist,
          indent: 1,
          quoted: true,
          collapsed: true,
          checked: true,
          inlines: const <InlineRun>[
            InlineRun(
              text: '标题',
              bold: true,
              italic: true,
              underline: true,
              strikethrough: true,
              highlight: true,
              linkUrl: 'https://example.com',
            ),
          ],
        ),
        emptyTableBlock('table'),
        ImageBlock(
          id: 'image',
          attachmentId: 'asset-image',
          layout: MediaLayout.wrap,
          scale: 1.5,
          rotationDegrees: 5,
          offsetX: 2,
          offsetY: -3,
          zIndex: 4,
        ),
        StickerBlock(
          id: 'sticker',
          attachmentId: 'asset-sticker',
          libraryEntryId: 'library-1',
        ),
        DrawingBlock(
          id: 'drawing',
          attachmentId: 'asset-drawing',
          width: 320,
          height: 240,
        ),
      ],
    );

    test('strictly round trips every block type', () {
      final encoded = encodeNoteDocument(document);
      expect(decodeNoteDocument(encoded), document);
      expect(encoded, contains('"type":"text"'));
      expect(encoded, contains('"type":"table"'));
      expect(encoded, contains('"type":"image"'));
      expect(encoded, contains('"type":"sticker"'));
      expect(encoded, contains('"type":"drawing"'));
    });

    test('rejects malformed JSON, unknown keys and missing keys', () {
      expect(() => decodeNoteDocument('{'), throwsFormatException);

      final unknown =
          jsonDecode(encodeNoteDocument(document)) as Map<String, Object?>;
      unknown['legacy'] = true;
      expect(
          () => decodeNoteDocument(jsonEncode(unknown)), throwsFormatException);

      final missing =
          jsonDecode(encodeNoteDocument(document)) as Map<String, Object?>;
      missing.remove('blocks');
      expect(
          () => decodeNoteDocument(jsonEncode(missing)), throwsFormatException);
    });

    test('rejects unknown schema, block type, enum and duplicate ids', () {
      expect(
        () => decodeNoteDocument('{"schemaVersion":2,"blocks":[]}'),
        throwsFormatException,
      );
      expect(
        () => decodeNoteDocument(
          '{"schemaVersion":1,"blocks":[{"type":"legacy"}]}',
        ),
        throwsFormatException,
      );

      final badEnum =
          jsonDecode(encodeNoteDocument(document)) as Map<String, Object?>;
      final blocks = badEnum['blocks']! as List<Object?>;
      (blocks.first! as Map<String, Object?>)['paragraphStyle'] = 'title';
      expect(
          () => decodeNoteDocument(jsonEncode(badEnum)), throwsFormatException);

      final duplicate =
          jsonDecode(encodeNoteDocument(document)) as Map<String, Object?>;
      final duplicateBlocks = duplicate['blocks']! as List<Object?>;
      (duplicateBlocks[1]! as Map<String, Object?>)['id'] = 'text';
      expect(() => decodeNoteDocument(jsonEncode(duplicate)),
          throwsFormatException);
    });
  });
}

// -- Functions

TextBlock _body(String text, {String id = 'body'}) => TextBlock(
      id: id,
      inlines: <InlineRun>[InlineRun(text: text)],
    );
