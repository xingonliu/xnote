import '../../domain/document/note_document_json.dart';
import '../../domain/model/attachment.dart' as domain;
import '../../domain/model/background_key.dart';
import '../../domain/model/note.dart' as domain;
import '../../domain/model/note_revision.dart' as domain;
import '../../domain/model/notebook.dart' as domain;
import 'xnote_database.dart';

// -- Functions

domain.Notebook notebookFromRow(NotebookRow row) => domain.Notebook(
      id: row.id,
      name: row.name,
      sortIndex: row.sortIndex,
      createdAtEpochMilliseconds: row.createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds: row.updatedAtEpochMilliseconds,
    );

NotebookRow notebookToRow(domain.Notebook notebook) => NotebookRow(
      id: notebook.id,
      name: notebook.name,
      sortIndex: notebook.sortIndex,
      createdAtEpochMilliseconds: notebook.createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds: notebook.updatedAtEpochMilliseconds,
    );

domain.Note noteFromRow(NoteRow row) {
  final kind = domain.NoteKind.values.byName(row.kind);
  return domain.Note(
    id: row.id,
    notebookId: row.notebookId,
    title: row.title,
    kind: kind,
    document:
        row.documentJson == null ? null : decodeNoteDocument(row.documentJson!),
    markdownText: row.markdownText,
    backgroundKey: _storedBackground(row.backgroundKey),
    sortIndex: row.sortIndex,
    visibleCharacterCount: row.visibleCharacterCount,
    latinWordCount: row.latinWordCount,
    summary: row.summary,
    createdAtEpochMilliseconds: row.createdAtEpochMilliseconds,
    updatedAtEpochMilliseconds: row.updatedAtEpochMilliseconds,
    deletedAtEpochMilliseconds: row.deletedAtEpochMilliseconds,
    originalNotebookName: row.originalNotebookName,
  );
}

NoteRow noteToRow(domain.Note note) => NoteRow(
      id: note.id,
      notebookId: note.notebookId,
      title: note.title,
      kind: note.kind.name,
      documentJson:
          note.document == null ? null : encodeNoteDocument(note.document!),
      markdownText: note.markdownText,
      backgroundKey: note.backgroundKey?.encode(),
      sortIndex: note.sortIndex,
      visibleCharacterCount: note.visibleCharacterCount,
      latinWordCount: note.latinWordCount,
      summary: note.summary,
      createdAtEpochMilliseconds: note.createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds: note.updatedAtEpochMilliseconds,
      deletedAtEpochMilliseconds: note.deletedAtEpochMilliseconds,
      originalNotebookName: note.originalNotebookName,
    );

domain.NoteRevision noteRevisionFromRow(NoteRevisionRow row) {
  return domain.NoteRevision(
    id: row.id,
    noteId: row.noteId,
    reason: domain.RevisionReason.values.byName(row.reason),
    kind: domain.NoteKind.values.byName(row.kind),
    title: row.title,
    document:
        row.documentJson == null ? null : decodeNoteDocument(row.documentJson!),
    markdownText: row.markdownText,
    createdAtEpochMilliseconds: row.createdAtEpochMilliseconds,
  );
}

NoteRevisionRow noteRevisionToRow(domain.NoteRevision revision) {
  return NoteRevisionRow(
    id: revision.id,
    noteId: revision.noteId,
    reason: revision.reason.name,
    kind: revision.kind.name,
    title: revision.title,
    documentJson: revision.document == null
        ? null
        : encodeNoteDocument(revision.document!),
    markdownText: revision.markdownText,
    createdAtEpochMilliseconds: revision.createdAtEpochMilliseconds,
  );
}

domain.Attachment attachmentFromRow(AttachmentRow row) => domain.Attachment(
      id: row.id,
      kind: domain.AttachmentKind.values.byName(row.kind),
      mimeType: row.mimeType,
      originalFileName: row.originalFileName,
      relativePath: row.relativePath,
      byteSize: row.byteSize,
      widthPixels: row.widthPixels,
      heightPixels: row.heightPixels,
      createdAtEpochMilliseconds: row.createdAtEpochMilliseconds,
    );

AttachmentRow attachmentToRow(domain.Attachment attachment) => AttachmentRow(
      id: attachment.id,
      kind: attachment.kind.name,
      mimeType: attachment.mimeType,
      originalFileName: attachment.originalFileName,
      relativePath: attachment.relativePath,
      byteSize: attachment.byteSize,
      widthPixels: attachment.widthPixels,
      heightPixels: attachment.heightPixels,
      createdAtEpochMilliseconds: attachment.createdAtEpochMilliseconds,
    );

BackgroundKey? _storedBackground(String? encoded) {
  if (encoded == null) {
    return null;
  }
  final parsed = parseBackgroundKey(encoded);
  if (parsed == null) {
    throw StateError('Invalid stored background key: $encoded');
  }
  return parsed;
}
