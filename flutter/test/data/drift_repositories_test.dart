import 'dart:io';

import 'package:drift/drift.dart' as drift;
import 'package:flutter_test/flutter_test.dart';
import 'package:xnote/data/attachments/drift_attachment_repository.dart';
import 'package:xnote/data/database/xnote_database.dart';
import 'package:xnote/data/files/attachment_file_store.dart';
import 'package:xnote/data/maintenance/recycle_bin_maintenance.dart';
import 'package:xnote/data/notes/drift_note_repository.dart';
import 'package:xnote/data/notes/drift_notebook_repository.dart';
import 'package:xnote/data/search/drift_search_history_repository.dart';
import 'package:xnote/data/settings/drift_settings_repository.dart';
import 'package:xnote/domain/document/note_block.dart';
import 'package:xnote/domain/document/note_document.dart';
import 'package:xnote/domain/model/app_settings.dart';
import 'package:xnote/domain/model/attachment.dart';
import 'package:xnote/domain/model/background_key.dart';
import 'package:xnote/domain/model/note.dart';
import 'package:xnote/domain/model/note_revision.dart';
import 'package:xnote/domain/model/notebook.dart';

import '../domain/domain_test_fixtures.dart';

// -- Tests

void main() {
  late XNoteDatabase database;
  late Directory temporaryDirectory;
  late AttachmentFileStore files;
  late MutableClock clock;

  setUp(() async {
    database = XNoteDatabase.inMemory();
    temporaryDirectory = await Directory.systemTemp.createTemp(
      'xnote_data_test_',
    );
    files = AttachmentFileStore(temporaryDirectory);
    clock = MutableClock(1000);
    await database.customSelect('SELECT 1').get();
  });

  tearDown(() async {
    await database.close();
    if (await temporaryDirectory.exists()) {
      await temporaryDirectory.delete(recursive: true);
    }
  });

  test('schema enables foreign keys and FTS5', () async {
    final foreignKeys =
        await database.customSelect('PRAGMA foreign_keys').getSingle();
    expect(foreignKeys.read<int>('foreign_keys'), 1);
    final modules = await database
        .customSelect(
          "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'notes_fts'",
        )
        .get();
    expect(modules, hasLength(1));
  });

  test('notebook and note query streams reflect committed writes', () async {
    final ids = SequenceIdGenerator(<String>['notebook', 'note', 'body']);
    final notebooks = DriftNotebookRepository(
      database: database,
      idGenerator: ids,
      clock: clock,
    );
    final notes = DriftNoteRepository(
      database: database,
      attachmentFiles: files,
      idGenerator: ids,
      clock: clock,
    );

    final notebook = await notebooks.createNotebook(' 工作 ');
    expect((await notebooks.watchNotebooks().first).single.name, '工作');
    final note = await notes.createRichNote(notebookId: notebook.id);
    expect((await notes.watchActiveNotes().first).single.id, note.id);
    expect((await notes.watchNotesInNotebook(notebook.id).first).single.id,
        note.id);
    expect(await notes.watchUnfiledNotes().first, isEmpty);

    await notes.moveNotes(<String>[note.id], null);
    expect((await notes.watchUnfiledNotes().first).single.id, note.id);
    expect((await notes.watchNote(note.id).first)!.isUnfiled, isTrue);
  });

  test('save atomically derives text, statistics and Chinese FTS', () async {
    final notes = _noteRepository(
      database,
      files,
      clock,
      <String>['note', 'body'],
    );
    final created = await notes.createRichNote();
    clock.value = 2000;
    final saved = await notes.saveNote(
      created.copyWith(
        title: '我的计划',
        document: NoteDocument(
          blocks: <NoteBlock>[
            TextBlock(
              id: 'body',
              inlines: const <InlineRun>[InlineRun(text: '这是笔记本正文 Hello')],
            ),
          ],
        ),
        createdAtEpochMilliseconds: 999999,
      ),
    );
    expect(saved.createdAtEpochMilliseconds, 1000);
    expect(saved.updatedAtEpochMilliseconds, 2000);
    expect(saved.visibleCharacterCount, 12);
    expect(saved.latinWordCount, 1);
    expect(saved.summary, '这是笔记本正文 Hello');

    final bodyMatch = await notes.searchNotes('笔记本');
    expect(bodyMatch.single.note.id, created.id);
    expect(bodyMatch.single.matchedText, contains('笔记本'));
    expect((await notes.searchNotes('我的')).single.note.id, created.id);
    expect(await notes.unfiledStats(),
        const NotebookStats(noteCount: 1, characterCount: 12));
  });

  test('deleting a notebook trashes its notes and removes search entries',
      () async {
    final ids = SequenceIdGenerator(<String>['notebook', 'note', 'body']);
    final notebooks = DriftNotebookRepository(
      database: database,
      idGenerator: ids,
      clock: clock,
    );
    final notes = DriftNoteRepository(
      database: database,
      attachmentFiles: files,
      idGenerator: ids,
      clock: clock,
    );
    final notebook = await notebooks.createNotebook('工作');
    final note = await notes.createRichNote(notebookId: notebook.id);
    await notes.saveNote(note.copyWith(title: '可搜索标题'));

    clock.value = 3000;
    await notebooks.deleteNotebook(notebook.id);
    final trashed = (await notes.watchTrashedNotes().first).single;
    expect(trashed.notebookId, isNull);
    expect(trashed.originalNotebookName, '工作');
    expect(trashed.deletedAtEpochMilliseconds, 3000);
    expect(await notes.searchNotes('可搜索'), isEmpty);
  });

  test('deleting an empty notebook creates no recycle entry', () async {
    final notebooks = DriftNotebookRepository(
      database: database,
      idGenerator: SequenceIdGenerator(<String>['empty']),
      clock: clock,
    );
    final notes = _noteRepository(
      database,
      files,
      clock,
      const <String>[],
    );
    final notebook = await notebooks.createNotebook('空本');
    await notebooks.deleteNotebook(notebook.id);
    expect(await notebooks.getNotebook(notebook.id), isNull);
    expect(await notes.watchTrashedNotes().first, isEmpty);
  });

  test('restore returns to an existing notebook or becomes unfiled', () async {
    final ids = SequenceIdGenerator(<String>[
      'notebook',
      'note-1',
      'body-1',
      'note-2',
      'body-2',
    ]);
    final notebooks = DriftNotebookRepository(
      database: database,
      idGenerator: ids,
      clock: clock,
    );
    final notes = DriftNoteRepository(
      database: database,
      attachmentFiles: files,
      idGenerator: ids,
      clock: clock,
    );
    final notebook = await notebooks.createNotebook('工作');
    final first = await notes.createRichNote(notebookId: notebook.id);
    final second = await notes.createRichNote(notebookId: notebook.id);
    await notes.trashNotes(<String>[first.id]);
    await notes.restoreNotes(<String>[first.id]);
    expect((await notes.getNote(first.id))!.notebookId, notebook.id);

    await notebooks.deleteNotebook(notebook.id);
    await notes.restoreNotes(<String>[second.id]);
    expect((await notes.getNote(second.id))!.isUnfiled, isTrue);
  });

  test('Markdown conversion stores a revision before replacing content',
      () async {
    final notes = _noteRepository(
      database,
      files,
      clock,
      <String>['note', 'body', 'revision'],
    );
    final created = await notes.createRichNote();
    await notes.saveNote(
      created.copyWith(
        title: '计划',
        document: NoteDocument(
          blocks: <NoteBlock>[
            TextBlock(
              id: 'body',
              inlines: const <InlineRun>[InlineRun(text: '正文')],
            ),
          ],
        ),
      ),
    );
    clock.value = 4000;
    final converted = await notes.convertToMarkdown(created.id);
    expect(converted.kind, NoteKind.markdown);
    expect(converted.markdownText, '# 计划\n\n正文');
    final revisions = await notes.getNoteRevisions(created.id);
    expect(revisions.single.id, 'revision');
    expect(revisions.single.kind, NoteKind.rich);
    expect(revisions.single.document, isNotNull);
  });

  test('blocked conversion rolls back without writing a revision', () async {
    final notes = _noteRepository(
      database,
      files,
      clock,
      <String>['note', 'body'],
    );
    final created = await notes.createRichNote();
    final withImage = await notes.saveNote(
      created.copyWith(
        document: NoteDocument(
          blocks: <NoteBlock>[
            ImageBlock(id: 'image', attachmentId: 'missing'),
          ],
        ),
      ),
    );
    await expectLater(notes.convertToMarkdown(withImage.id), throwsStateError);
    expect((await notes.getNote(withImage.id))!.kind, NoteKind.rich);
    expect(await notes.getNoteRevisions(withImage.id), isEmpty);
  });

  test('manual order, notebook rename and background survive moves', () async {
    final ids = SequenceIdGenerator(<String>[
      'source',
      'destination',
      'note-1',
      'body-1',
      'note-2',
      'body-2',
      'revision',
    ]);
    final notebooks = DriftNotebookRepository(
      database: database,
      idGenerator: ids,
      clock: clock,
    );
    final notes = DriftNoteRepository(
      database: database,
      attachmentFiles: files,
      idGenerator: ids,
      clock: clock,
    );
    final source = await notebooks.createNotebook('来源');
    final destination = await notebooks.createNotebook('目标');
    final first = await notes.createRichNote(notebookId: source.id);
    clock.value = 2000;
    final second = await notes.createRichNote(notebookId: source.id);
    await notebooks.renameNotebook(source.id, '新来源');
    expect((await notes.getNote(first.id))!.notebookId, source.id);

    final background = BackgroundKey(gridBuiltinBackgroundId);
    await notes.setNoteBackground(first.id, background);
    await notes.moveNotes(<String>[first.id, second.id], destination.id);
    await notes.reorderNotes(<String>[second.id, first.id]);
    final ordered = await notes
        .watchNotesInNotebook(destination.id, sort: NoteListSort.manual)
        .first;
    expect(ordered.map((note) => note.id), <String>[second.id, first.id]);
    expect((await notes.getNote(first.id))!.backgroundKey, background);

    await notes.convertToMarkdown(first.id);
    expect((await notes.getNote(first.id))!.backgroundKey, background);
    await notes.setNoteBackground(first.id, null);
    expect((await notes.getNote(first.id))!.backgroundKey, isNull);
  });

  test('search filters notebooks and excludes trash', () async {
    final ids = SequenceIdGenerator(<String>[
      'included-notebook',
      'other-notebook',
      'included',
      'included-body',
      'other',
      'other-body',
      'trashed',
      'trashed-body',
    ]);
    final notebooks = DriftNotebookRepository(
      database: database,
      idGenerator: ids,
      clock: clock,
    );
    final notes = DriftNoteRepository(
      database: database,
      attachmentFiles: files,
      idGenerator: ids,
      clock: clock,
    );
    final includedNotebook = await notebooks.createNotebook('工作');
    final otherNotebook = await notebooks.createNotebook('生活');
    final included =
        await notes.createRichNote(notebookId: includedNotebook.id);
    await notes.saveNote(
      included.copyWith(
        document: NoteDocument(
          blocks: <NoteBlock>[
            TextBlock(
              id: 'included-body',
              inlines: const <InlineRun>[InlineRun(text: '我的笔记本记录')],
            ),
          ],
        ),
      ),
    );
    final other = await notes.createRichNote(notebookId: otherNotebook.id);
    await notes.saveNote(other.copyWith(title: '另一本笔记本'));
    final trashed = await notes.createRichNote(notebookId: includedNotebook.id);
    await notes.saveNote(trashed.copyWith(title: '回收站笔记本'));
    await notes.trashNotes(<String>[trashed.id]);

    final results = await notes.searchNotes(
      '笔记本',
      notebookId: includedNotebook.id,
    );
    expect(results.map((result) => result.note.id), <String>[included.id]);
    expect(results.single.matchedText, contains('我的笔记本'));
  });

  test('file database persists after close and reopen', () async {
    drift.driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;
    final databaseFile = File(
      '${temporaryDirectory.path}${Platform.pathSeparator}cold-start.sqlite',
    );
    final firstDatabase = XNoteDatabase.fromFile(databaseFile);
    final firstRepository = _noteRepository(
      firstDatabase,
      files,
      clock,
      <String>['note', 'body'],
    );
    final created = await firstRepository.createRichNote();
    await firstRepository.saveNote(created.copyWith(title: '冷启动仍存在'));
    await firstDatabase.close();

    final reopenedDatabase = XNoteDatabase.fromFile(databaseFile);
    try {
      final reopenedRepository = _noteRepository(
        reopenedDatabase,
        files,
        clock,
        const <String>[],
      );
      expect((await reopenedRepository.getNote(created.id))!.title, '冷启动仍存在');
    } finally {
      await reopenedDatabase.close();
      drift.driftRuntimeOptions.dontWarnAboutMultipleDatabases = false;
    }
  });

  test('expired recycle entries are purged on startup sweep', () async {
    final notes = _noteRepository(
      database,
      files,
      clock,
      <String>['note', 'body'],
    );
    final note = await notes.createRichNote();
    await notes.trashNotes(<String>[note.id]);
    clock.value = 1000 + 30 * Duration.millisecondsPerDay;
    await RecycleBinMaintenance(notes).runStartupSweep();
    expect(await notes.getNote(note.id), isNull);
  });

  test('search history normalizes, deduplicates and caps its stream', () async {
    final history =
        DriftSearchHistoryRepository(database: database, clock: clock);
    await history.recordQuery('  Hello   world ');
    clock.value += 1;
    await history.recordQuery('hello world');
    for (var index = 0; index < 11; index += 1) {
      clock.value += 1;
      await history.recordQuery('查询 $index');
    }
    final recent = await history.watchRecentQueries().first;
    expect(recent, hasLength(10));
    expect(recent.first, '查询 10');
    expect(
        recent.where((query) => query.toLowerCase() == 'hello world'), isEmpty);
    await history.clear();
    expect(await history.watchRecentQueries().first, isEmpty);
  });

  test('settings stream persists theme and background', () async {
    final settings = DriftSettingsRepository(database);
    expect(await settings.getSettings(), defaultAppSettings());
    await settings.setThemeMode(AppThemeMode.dark);
    await settings.setDefaultBackground(BackgroundKey(gridBuiltinBackgroundId));
    expect(
      await settings.watchSettings().first,
      AppSettings(
        defaultBackground: BackgroundKey(gridBuiltinBackgroundId),
        themeMode: AppThemeMode.dark,
      ),
    );
  });

  test('attachment writes atomically and unreferenced cleanup deletes file',
      () async {
    final attachments = DriftAttachmentRepository(
      database: database,
      files: files,
      idGenerator: SequenceIdGenerator(<String>['attachment']),
      clock: clock,
    );
    final saved = await attachments.saveAttachment(
      kind: AttachmentKind.image,
      mimeType: 'image/png',
      extension: '.png',
      content: Stream<List<int>>.value(<int>[1, 2, 3]),
      widthPixels: 1,
      heightPixels: 1,
    );
    expect(saved.byteSize, 3);
    expect(
        await files.resolve(saved.relativePath).readAsBytes(), <int>[1, 2, 3]);
    expect(await attachments.getAttachment(saved.id), saved);
    await attachments.deleteUnreferencedAttachments();
    expect(await attachments.getAttachment(saved.id), isNull);
    expect(await files.resolve(saved.relativePath).exists(), isFalse);
  });

  test('revision references retain files until their note is deleted',
      () async {
    final attachments = DriftAttachmentRepository(
      database: database,
      files: files,
      idGenerator: SequenceIdGenerator(<String>['attachment']),
      clock: clock,
    );
    final attachment = await attachments.saveAttachment(
      kind: AttachmentKind.image,
      mimeType: 'image/png',
      extension: 'png',
      content: Stream<List<int>>.value(<int>[1]),
    );
    final notes = _noteRepository(
      database,
      files,
      clock,
      <String>['note', 'body', 'revision'],
    );
    final note = await notes.createRichNote();
    final withImage = await notes.saveNote(
      note.copyWith(
        document: NoteDocument(
          blocks: <NoteBlock>[
            ImageBlock(id: 'image', attachmentId: attachment.id),
          ],
        ),
      ),
    );
    await notes.saveRevision(withImage.id, RevisionReason.agentPolish);
    await notes.saveNote(
      withImage.copyWith(
        document: NoteDocument(
          blocks: <NoteBlock>[TextBlock(id: 'body')],
        ),
      ),
    );

    await attachments.deleteUnreferencedAttachments();
    expect(await attachments.getAttachment(attachment.id), attachment);
    expect(await files.resolve(attachment.relativePath).exists(), isTrue);

    await notes.permanentlyDeleteNotes(<String>[note.id]);
    expect(await attachments.getAttachment(attachment.id), isNull);
    expect(await files.resolve(attachment.relativePath).exists(), isFalse);
  });

  test('file store rejects traversal outside its root', () {
    expect(() => files.resolve('../outside'), throwsArgumentError);
    expect(() => files.resolve(r'C:\outside'), throwsArgumentError);
    expect(
      () => AttachmentFileStore.relativePath('asset', '../png'),
      throwsArgumentError,
    );
    expect(AttachmentFileStore.relativePath('asset', '.png'),
        'attachments/asset.png');
  });

  test('thrown transaction rolls back every prior statement', () async {
    await expectLater(
      database.transaction<void>(() async {
        await database.into(database.notebooks).insert(
              const NotebookRow(
                id: 'rolled-back',
                name: '不会保留',
                sortIndex: 0,
                createdAtEpochMilliseconds: 0,
                updatedAtEpochMilliseconds: 0,
              ),
            );
        throw StateError('stop');
      }),
      throwsStateError,
    );
    final remaining = await (database.select(
      database.notebooks,
    )..where((row) => row.id.equals('rolled-back')))
        .get();
    expect(remaining, isEmpty);
  });

  test('database close terminates active streams and rejects new work',
      () async {
    final streamDone = expectLater(
      database.select(database.notes).watch(),
      emitsInOrder(<Object>[isEmpty, emitsDone]),
    );
    await Future<void>.delayed(Duration.zero);
    await database.close();
    await streamDone;
    await expectLater(
      database.select(database.notes).get(),
      throwsA(isA<StateError>()),
    );
  });
}

// -- Functions

DriftNoteRepository _noteRepository(
  XNoteDatabase database,
  AttachmentFileStore files,
  MutableClock clock,
  List<String> ids,
) {
  return DriftNoteRepository(
    database: database,
    attachmentFiles: files,
    idGenerator: SequenceIdGenerator(ids),
    clock: clock,
  );
}
