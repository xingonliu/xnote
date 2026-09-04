import 'package:xnote/core/ids/id_generator.dart';
import 'package:xnote/core/time/clock.dart';
import 'package:xnote/domain/document/note_block.dart';
import 'package:xnote/domain/document/note_document.dart';
import 'package:xnote/domain/model/note.dart';

// -- Type Definitions

final class SequenceIdGenerator implements IdGenerator {
  SequenceIdGenerator(Iterable<String> ids) : _ids = ids.iterator;

  final Iterator<String> _ids;

  // -- Functions

  @override
  String nextId() {
    if (!_ids.moveNext()) {
      throw StateError('No test ids remain');
    }
    return _ids.current;
  }
}

final class FixedClock implements Clock {
  const FixedClock(this.value);

  final int value;

  // -- Functions

  @override
  int nowEpochMilliseconds() => value;
}

// -- Functions

Note richNote({
  String id = 'note-1',
  String? notebookId,
  String title = '标题',
  NoteDocument? document,
  int? deletedAtEpochMilliseconds,
  String? originalNotebookName,
}) {
  return Note(
    id: id,
    notebookId: notebookId,
    title: title,
    kind: NoteKind.rich,
    document: document ??
        NoteDocument(
          blocks: <NoteBlock>[TextBlock(id: 'body')],
        ),
    markdownText: null,
    backgroundKey: null,
    sortIndex: 0,
    visibleCharacterCount: 0,
    latinWordCount: 0,
    summary: '',
    createdAtEpochMilliseconds: 1,
    updatedAtEpochMilliseconds: 1,
    deletedAtEpochMilliseconds: deletedAtEpochMilliseconds,
    originalNotebookName: originalNotebookName,
  );
}
