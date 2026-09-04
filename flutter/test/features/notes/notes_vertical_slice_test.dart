import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import 'package:xnote/app/providers/xnote_providers.dart';
import 'package:xnote/app/xnote_app.dart';
import 'package:xnote/domain/model/note.dart';
import 'package:xnote/domain/model/notebook.dart';

import '../../support/test_dependencies.dart';

// -- Functions

void _usePhoneSurface(WidgetTester tester) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = const Size(390, 844);
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
}

Widget _buildApp(TestDependencies testDependencies) {
  return LiquidGlassWidgets.wrap(
    child: ProviderScope(
      overrides: [
        xnoteDependenciesProvider.overrideWith(
          (ref) async => testDependencies.dependencies,
        ),
      ],
      child: const XNoteApp(initialThemeMode: ThemeMode.light),
    ),
    brightnessResolver: Theme.maybeBrightnessOf,
  );
}

Future<void> _pumpApp(
  WidgetTester tester,
  TestDependencies testDependencies,
) async {
  await tester.pumpWidget(_buildApp(testDependencies));
  await _pumpFrames(tester);
}

Future<void> _pumpFrames(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 500));
}

Future<void> _dismissToast(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 4));
  await tester.pump(const Duration(milliseconds: 500));
}

Future<void> _waitForDatabase(
  WidgetTester tester,
  Future<bool> Function() condition,
) async {
  final completed = await tester.runAsync(() async {
    for (var attempt = 0; attempt < 50; attempt += 1) {
      if (await condition()) {
        return true;
      }
      await Future<void>.delayed(const Duration(milliseconds: 10));
    }
    return false;
  });
  expect(completed, isTrue,
      reason: 'Timed out waiting for the in-memory store');
}

void main() {
  late TestDependencies testDependencies;

  setUp(() async {
    testDependencies = await TestDependencies.create();
  });

  tearDown(() => testDependencies.close());

  testWidgets('creates edits moves persists and trashes a note', (
    tester,
  ) async {
    _usePhoneSurface(tester);
    await _pumpApp(tester, testDependencies);

    expect(find.text('还没有笔记'), findsOneWidget);

    await tester.tap(find.byKey(const Key('create-notebook')));
    await _pumpFrames(tester);
    expect(find.byType(GlassDialog), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('notebook-name-field')),
      '旅行',
    );
    await tester.tap(find.text('完成'));
    await _pumpFrames(tester);

    late Notebook notebook;
    await _waitForDatabase(tester, () async {
      final notebooks =
          await testDependencies.dependencies.notebooks.watchNotebooks().first;
      if (notebooks.length != 1) {
        return false;
      }
      notebook = notebooks.single;
      return true;
    });
    expect(notebook.name, '旅行');
    await _dismissToast(tester);

    await tester.tap(find.byKey(const Key('create-note')));
    await _pumpFrames(tester);
    late Note created;
    await _waitForDatabase(tester, () async {
      final notes =
          await testDependencies.dependencies.notes.watchActiveNotes().first;
      if (notes.length != 1) {
        return false;
      }
      created = notes.single;
      return true;
    });
    expect(find.byKey(const Key('basic-editor-title')), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('basic-editor-title')),
      '杭州清单',
    );
    await tester.enterText(
      find.byKey(const Key('basic-editor-body')),
      '带伞，去西湖边散步。',
    );
    await tester.tap(find.byKey(const Key('move-current-note')));
    await _pumpFrames(tester);
    expect(find.byType(GlassModalSheet), findsOneWidget);
    await tester.tap(
      find.byKey(ValueKey<String>('notebook-option-${notebook.id}')),
    );
    await _pumpFrames(tester);
    await _dismissToast(tester);

    await tester.tap(find.bySemanticsLabel('返回'));
    await _pumpFrames(tester);
    await _waitForDatabase(tester, () async {
      final saved =
          await testDependencies.dependencies.notes.getNote(created.id);
      return saved?.title == '杭州清单' &&
          saved?.summary == '带伞，去西湖边散步。' &&
          saved?.notebookId == notebook.id;
    });

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
    await _pumpApp(tester, testDependencies);
    expect(find.text('杭州清单'), findsOneWidget);

    await tester.longPress(
      find.byKey(ValueKey<String>('note-row-${created.id}')),
    );
    await _pumpFrames(tester);
    expect(find.byKey(const Key('notes-selection-toolbar')), findsOneWidget);
    await tester.tap(find.bySemanticsLabel('删除所选笔记'));
    await _pumpFrames(tester);
    await tester.tap(find.text('移入回收站').last);
    await _pumpFrames(tester);

    await _waitForDatabase(tester, () async {
      final trashed =
          await testDependencies.dependencies.notes.getNote(created.id);
      return trashed?.isTrashed ?? false;
    });
    expect(find.text('杭州清单'), findsNothing);
  });

  testWidgets('filters unfiled notes and sorts them by title', (tester) async {
    _usePhoneSurface(tester);
    final seed = (await tester.runAsync(() async {
      final notebook =
          await testDependencies.dependencies.notebooks.createNotebook('项目');
      final assigned = await testDependencies.dependencies.notes.createRichNote(
        notebookId: notebook.id,
      );
      await testDependencies.dependencies.notes.saveNote(
        assigned.copyWith(title: '项目记录'),
      );
      final zulu = await testDependencies.dependencies.notes.createRichNote();
      await testDependencies.dependencies.notes.saveNote(
        zulu.copyWith(title: 'Zulu'),
      );
      final alpha = await testDependencies.dependencies.notes.createRichNote();
      await testDependencies.dependencies.notes.saveNote(
        alpha.copyWith(title: 'Alpha'),
      );
      for (var index = 0; index < 2; index += 1) {
        final note = await testDependencies.dependencies.notes.createRichNote(
          notebookId: notebook.id,
        );
        await testDependencies.dependencies.notes.saveNote(
          note.copyWith(title: '项目 ${index + 2}'),
        );
      }
      return (assigned, zulu, alpha);
    }))!;
    final assigned = seed.$1;
    final zulu = seed.$2;
    final alpha = seed.$3;

    await _pumpApp(tester, testDependencies);
    expect(find.text('最近编辑'), findsOneWidget);

    await tester.tap(find.byKey(const Key('open-notebook-picker')));
    await _pumpFrames(tester);
    await tester.tap(find.byKey(const Key('notebook-option-unfiled')));
    await _pumpFrames(tester);

    expect(find.text('未归档'), findsWidgets);
    expect(
      find.byKey(ValueKey<String>('note-row-${assigned.id}')),
      findsNothing,
    );
    expect(find.byKey(ValueKey<String>('note-row-${zulu.id}')), findsOneWidget);
    expect(
      find.byKey(ValueKey<String>('note-row-${alpha.id}')),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('open-notes-sort')));
    await _pumpFrames(tester);
    await tester.tap(find.text('按标题'));
    await _pumpFrames(tester);

    final alphaTop = tester.getTopLeft(
      find.byKey(ValueKey<String>('note-row-${alpha.id}')),
    );
    final zuluTop = tester.getTopLeft(
      find.byKey(ValueKey<String>('note-row-${zulu.id}')),
    );
    expect(alphaTop.dy, lessThan(zuluTop.dy));
  });

  testWidgets('reorders renames and deletes a notebook with confirmation', (
    tester,
  ) async {
    _usePhoneSurface(tester);
    final seed = (await tester.runAsync(() async {
      final notebook =
          await testDependencies.dependencies.notebooks.createNotebook('收件箱');
      final first = await testDependencies.dependencies.notes.createRichNote(
        notebookId: notebook.id,
      );
      await testDependencies.dependencies.notes.saveNote(
        first.copyWith(title: '第一篇'),
      );
      final second = await testDependencies.dependencies.notes.createRichNote(
        notebookId: notebook.id,
      );
      await testDependencies.dependencies.notes.saveNote(
        second.copyWith(title: '第二篇'),
      );
      return (notebook, first, second);
    }))!;
    final notebook = seed.$1;
    final first = seed.$2;
    final second = seed.$3;

    await _pumpApp(tester, testDependencies);
    await tester.tap(
      find.byKey(ValueKey<String>('notebook-card-${notebook.id}')),
    );
    await _pumpFrames(tester);

    await tester.tap(find.bySemanticsLabel('查看笔记本统计'));
    await _pumpFrames(tester);
    expect(find.textContaining('2 篇 ·'), findsOneWidget);
    await tester.tapAt(const Offset(380, 800));
    await _pumpFrames(tester);

    final reorderable = tester.widget<ReorderableListView>(
      find.byType(ReorderableListView),
    );
    reorderable.onReorderItem!(0, 1);
    await _pumpFrames(tester);
    late List<Note> reordered;
    await _waitForDatabase(tester, () async {
      reordered = await testDependencies.dependencies.notes
          .watchNotesInNotebook(notebook.id, sort: NoteListSort.manual)
          .first;
      return reordered.first.id == second.id;
    });
    expect(reordered.map((note) => note.id), <String>[second.id, first.id]);

    await tester.tap(find.byKey(const Key('open-notebook-more')));
    await _pumpFrames(tester);
    await tester.tap(find.text('重命名'));
    await _pumpFrames(tester);
    await tester.enterText(
      find.byKey(const Key('notebook-name-field')),
      '灵感',
    );
    await tester.tap(find.text('完成'));
    await _pumpFrames(tester);
    await _waitForDatabase(tester, () async {
      final renamed = await testDependencies.dependencies.notebooks
          .getNotebook(notebook.id);
      return renamed?.name == '灵感';
    });

    await tester.tap(find.byKey(const Key('open-notebook-more')));
    await _pumpFrames(tester);
    await tester.tap(find.text('删除笔记本').last);
    await _pumpFrames(tester);
    await tester.tap(find.text('删除笔记本').last);
    await _pumpFrames(tester);

    await _waitForDatabase(tester, () async {
      final deleted = await testDependencies.dependencies.notebooks
          .getNotebook(notebook.id);
      final trashed =
          await testDependencies.dependencies.notes.watchTrashedNotes().first;
      return deleted == null && trashed.length == 2;
    });
    expect(find.text('灵感'), findsNothing);
  });
}
