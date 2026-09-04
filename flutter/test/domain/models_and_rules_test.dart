import 'package:flutter_test/flutter_test.dart';
import 'package:xnote/core/ids/uuid_id_generator.dart';
import 'package:xnote/domain/document/note_block.dart';
import 'package:xnote/domain/document/note_document.dart';
import 'package:xnote/domain/model/app_settings.dart';
import 'package:xnote/domain/model/attachment.dart';
import 'package:xnote/domain/model/background_key.dart';
import 'package:xnote/domain/model/note.dart';
import 'package:xnote/domain/model/notebook.dart';
import 'package:xnote/domain/rules/markdown_conversion_rules.dart';
import 'package:xnote/domain/rules/notebook_rules.dart';
import 'package:xnote/domain/rules/recycle_bin_policy.dart';

import 'domain_test_fixtures.dart';

// -- Tests

void main() {
  group('BackgroundKey', () {
    test('round trips every built-in background', () {
      for (final id in builtinBackgroundIds) {
        final key = BackgroundKey(id);
        expect(parseBackgroundKey(key.encode()), key);
      }
    });

    test('rejects unknown and malformed stored values', () {
      expect(parseBackgroundKey(null), isNull);
      expect(parseBackgroundKey('cream'), isNull);
      expect(parseBackgroundKey('builtin:unknown'), isNull);
      expect(() => BackgroundKey('unknown'), throwsArgumentError);
    });

    test('note override wins over the application default', () {
      final fallback = BackgroundKey(defaultBuiltinBackgroundId);
      final override = BackgroundKey(gridBuiltinBackgroundId);
      expect(
        resolveBackgroundKey(
          noteBackground: override,
          defaultBackground: fallback,
        ),
        override,
      );
      expect(
        resolveBackgroundKey(
          noteBackground: null,
          defaultBackground: fallback,
        ),
        fallback,
      );
    });

    test('default settings match the product defaults', () {
      expect(
        defaultAppSettings(),
        AppSettings(
          defaultBackground: BackgroundKey(defaultBuiltinBackgroundId),
          themeMode: AppThemeMode.system,
        ),
      );
    });
  });

  group('Model invariants', () {
    test('production generator returns distinct UUID v4 values', () {
      const generator = UuidIdGenerator();
      final first = generator.nextId();
      final second = generator.nextId();
      expect(
        first,
        matches(
          RegExp(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
          ),
        ),
      );
      expect(second, isNot(first));
    });

    test('rich and Markdown note payloads are mutually exclusive', () {
      expect(
        () => Note(
          id: 'n',
          notebookId: null,
          title: '',
          kind: NoteKind.rich,
          document: null,
          markdownText: null,
          backgroundKey: null,
          sortIndex: 0,
          visibleCharacterCount: 0,
          latinWordCount: 0,
          summary: '',
          createdAtEpochMilliseconds: 0,
          updatedAtEpochMilliseconds: 0,
          deletedAtEpochMilliseconds: null,
          originalNotebookName: null,
        ),
        throwsArgumentError,
      );
      final markdown = richNote().copyWith(
        kind: NoteKind.markdown,
        document: null,
        markdownText: '# 标题',
      );
      expect(markdown.document, isNull);
      expect(markdown.markdownText, '# 标题');
    });

    test('attachment paths remain relative and dimensions positive', () {
      expect(
        () => Attachment(
          id: 'a',
          kind: AttachmentKind.image,
          mimeType: 'image/png',
          relativePath: r'C:\absolute.png',
          byteSize: 1,
          createdAtEpochMilliseconds: 1,
        ),
        throwsArgumentError,
      );
      expect(
        () => Attachment(
          id: 'a',
          kind: AttachmentKind.image,
          mimeType: 'image/png',
          relativePath: 'attachments/a.png',
          byteSize: 1,
          widthPixels: 0,
          createdAtEpochMilliseconds: 1,
        ),
        throwsArgumentError,
      );
    });

    test('document block ids are unique and attachment ids are derived', () {
      expect(
        () => NoteDocument(
          blocks: <NoteBlock>[TextBlock(id: 'same'), TextBlock(id: 'same')],
        ),
        throwsArgumentError,
      );
      final document = NoteDocument(
        blocks: <NoteBlock>[
          ImageBlock(id: 'image', attachmentId: 'a'),
          StickerBlock(id: 'sticker', attachmentId: 'b'),
          DrawingBlock(
            id: 'drawing',
            attachmentId: 'c',
            width: 10,
            height: 20,
          ),
        ],
      );
      expect(document.attachmentIds, <String>{'a', 'b', 'c'});
    });

    test('empty document and table ids come from injected values', () {
      final document = emptyNoteDocument(
        SequenceIdGenerator(<String>['generated-body']),
      );
      expect(document.blocks.single.id, 'generated-body');
      final table = emptyTableBlock('table');
      expect(table.rows, hasLength(2));
      expect(table.columnCount, 2);
    });
  });

  group('Markdown conversion rules', () {
    test('text and tables can convert', () {
      final document = NoteDocument(
        blocks: <NoteBlock>[
          TextBlock(
              id: 'text', inlines: const <InlineRun>[InlineRun(text: 'hello')]),
          emptyTableBlock('table'),
        ],
      );
      expect(documentConversionBlockers(document), isEmpty);
    });

    test('image, sticker and drawing block conversion', () {
      final document = NoteDocument(
        blocks: <NoteBlock>[
          ImageBlock(id: 'image', attachmentId: 'a'),
          StickerBlock(id: 'sticker', attachmentId: 'b'),
          DrawingBlock(
            id: 'drawing',
            attachmentId: 'c',
            width: 10,
            height: 20,
          ),
        ],
      );
      expect(
        documentConversionBlockers(document),
        <ConversionBlocker>{
          ConversionBlocker.image,
          ConversionBlocker.sticker,
          ConversionBlocker.drawing,
        },
      );
    });

    test('Markdown note cannot convert again', () {
      final note = richNote().copyWith(
        kind: NoteKind.markdown,
        document: null,
        markdownText: '# 标题\n正文',
      );
      expect(
        noteConversionBlockers(note),
        const <ConversionBlocker>{ConversionBlocker.alreadyMarkdown},
      );
      expect(canConvertToMarkdown(note), isFalse);
    });

    test('conversion snapshots rich revision before changing the note', () {
      final original = richNote(
        title: '计划',
        document: NoteDocument(
          blocks: <NoteBlock>[
            TextBlock(
              id: 'body',
              inlines: const <InlineRun>[InlineRun(text: 'Hello 世界')],
            ),
          ],
        ),
      );
      final conversion = convertRichNoteToMarkdown(
        original,
        idGenerator: SequenceIdGenerator(<String>['revision-1']),
        clock: const FixedClock(500),
      );
      expect(conversion.revision.id, 'revision-1');
      expect(conversion.revision.document, original.document);
      expect(conversion.revision.createdAtEpochMilliseconds, 500);
      expect(conversion.note.kind, NoteKind.markdown);
      expect(conversion.note.markdownText, '# 计划\n\nHello 世界');
      expect(conversion.note.updatedAtEpochMilliseconds, 500);
      expect(conversion.note.visibleCharacterCount, 7);
      expect(conversion.note.latinWordCount, 1);
      expect(conversion.note.summary, 'Hello 世界');
    });
  });

  group('Notebook deletion and restore', () {
    final notebook = Notebook(
      id: 'notebook-1',
      name: '工作',
      sortIndex: 0,
      createdAtEpochMilliseconds: 1,
      updatedAtEpochMilliseconds: 1,
    );

    test('deleting empty notebook produces no patches', () {
      expect(
        patchesForDeletedNotebook(
          notebook: notebook,
          notesInNotebook: const <Note>[],
          nowEpochMilliseconds: 50,
        ),
        isEmpty,
      );
    });

    test('deletion snapshots name and preserves existing deletion time', () {
      final patches = patchesForDeletedNotebook(
        notebook: notebook,
        notesInNotebook: <Note>[
          richNote(id: 'active', notebookId: notebook.id),
          richNote(
            id: 'trashed',
            notebookId: notebook.id,
            deletedAtEpochMilliseconds: 20,
          ),
        ],
        nowEpochMilliseconds: 50,
      );
      expect(patches[0].originalNotebookName, '工作');
      expect(patches[0].deletedAtEpochMilliseconds, 50);
      expect(patches[1].deletedAtEpochMilliseconds, 20);
    });

    test('restore returns existing notebook otherwise unfiled', () {
      final note = richNote(
        notebookId: notebook.id,
        deletedAtEpochMilliseconds: 20,
      );
      expect(
          notebookIdAfterRestore(note, (id) => id == notebook.id), notebook.id);
      expect(notebookIdAfterRestore(note, (_) => false), isNull);
      expect(
        notebookIdAfterRestore(
          richNote(
            notebookId: null,
            deletedAtEpochMilliseconds: 20,
            originalNotebookName: '工作',
          ),
          (_) => true,
        ),
        isNull,
      );
    });
  });

  group('Recycle bin policy', () {
    const deletedAt = 1000000;

    test('starts at thirty days and partial days round up', () {
      expect(
        recycleBinRemainingDays(
          deletedAtEpochMilliseconds: deletedAt,
          nowEpochMilliseconds: deletedAt,
        ),
        30,
      );
      expect(
        recycleBinRemainingDays(
          deletedAtEpochMilliseconds: deletedAt,
          nowEpochMilliseconds: deletedAt + Duration.millisecondsPerHour,
        ),
        30,
      );
    });

    test('expires exactly after thirty days', () {
      final expiresAt = recycleBinExpireAt(deletedAt);
      expect(
        isRecycleBinEntryExpired(
          deletedAtEpochMilliseconds: deletedAt,
          nowEpochMilliseconds: expiresAt,
        ),
        isTrue,
      );
      expect(
        recycleBinRemainingDays(
          deletedAtEpochMilliseconds: deletedAt,
          nowEpochMilliseconds: expiresAt,
        ),
        0,
      );
    });
  });
}
