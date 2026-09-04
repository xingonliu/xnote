import '../document/note_document.dart';
import 'background_key.dart';

// -- Type Definitions

enum NoteKind { rich, markdown }

enum NoteListSort { updatedAt, createdAt, title, manual }

final class Note {
  Note({
    required this.id,
    required this.notebookId,
    required this.title,
    required this.kind,
    required this.document,
    required this.markdownText,
    required this.backgroundKey,
    required this.sortIndex,
    required this.visibleCharacterCount,
    required this.latinWordCount,
    required this.summary,
    required this.createdAtEpochMilliseconds,
    required this.updatedAtEpochMilliseconds,
    required this.deletedAtEpochMilliseconds,
    required this.originalNotebookName,
  }) {
    if (id.isEmpty) {
      throw ArgumentError.value(id, 'id', 'Note id must not be empty');
    }
    if (kind == NoteKind.rich && (document == null || markdownText != null)) {
      throw ArgumentError('Rich notes require a document and no Markdown text');
    }
    if (kind == NoteKind.markdown &&
        (document != null || markdownText == null)) {
      throw ArgumentError(
          'Markdown notes require Markdown text and no document');
    }
    if (visibleCharacterCount < 0 || latinWordCount < 0) {
      throw ArgumentError('Text statistics must not be negative');
    }
  }

  final String id;
  final String? notebookId;
  final String title;
  final NoteKind kind;
  final NoteDocument? document;
  final String? markdownText;
  final BackgroundKey? backgroundKey;
  final int sortIndex;
  final int visibleCharacterCount;
  final int latinWordCount;
  final String summary;
  final int createdAtEpochMilliseconds;
  final int updatedAtEpochMilliseconds;
  final int? deletedAtEpochMilliseconds;
  final String? originalNotebookName;

  // -- Derived Values

  bool get isTrashed => deletedAtEpochMilliseconds != null;

  bool get isUnfiled => notebookId == null && !isTrashed;

  Set<String> get referencedAttachmentIds =>
      document?.attachmentIds ?? <String>{};

  // -- Functions

  Note copyWith({
    Object? notebookId = _notProvided,
    String? title,
    NoteKind? kind,
    Object? document = _notProvided,
    Object? markdownText = _notProvided,
    Object? backgroundKey = _notProvided,
    int? sortIndex,
    int? visibleCharacterCount,
    int? latinWordCount,
    String? summary,
    int? createdAtEpochMilliseconds,
    int? updatedAtEpochMilliseconds,
    Object? deletedAtEpochMilliseconds = _notProvided,
    Object? originalNotebookName = _notProvided,
  }) {
    return Note(
      id: id,
      notebookId: identical(notebookId, _notProvided)
          ? this.notebookId
          : notebookId as String?,
      title: title ?? this.title,
      kind: kind ?? this.kind,
      document: identical(document, _notProvided)
          ? this.document
          : document as NoteDocument?,
      markdownText: identical(markdownText, _notProvided)
          ? this.markdownText
          : markdownText as String?,
      backgroundKey: identical(backgroundKey, _notProvided)
          ? this.backgroundKey
          : backgroundKey as BackgroundKey?,
      sortIndex: sortIndex ?? this.sortIndex,
      visibleCharacterCount:
          visibleCharacterCount ?? this.visibleCharacterCount,
      latinWordCount: latinWordCount ?? this.latinWordCount,
      summary: summary ?? this.summary,
      createdAtEpochMilliseconds:
          createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds:
          updatedAtEpochMilliseconds ?? this.updatedAtEpochMilliseconds,
      deletedAtEpochMilliseconds:
          identical(deletedAtEpochMilliseconds, _notProvided)
              ? this.deletedAtEpochMilliseconds
              : deletedAtEpochMilliseconds as int?,
      originalNotebookName: identical(originalNotebookName, _notProvided)
          ? this.originalNotebookName
          : originalNotebookName as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is Note &&
        other.id == id &&
        other.notebookId == notebookId &&
        other.title == title &&
        other.kind == kind &&
        other.document == document &&
        other.markdownText == markdownText &&
        other.backgroundKey == backgroundKey &&
        other.sortIndex == sortIndex &&
        other.visibleCharacterCount == visibleCharacterCount &&
        other.latinWordCount == latinWordCount &&
        other.summary == summary &&
        other.createdAtEpochMilliseconds == createdAtEpochMilliseconds &&
        other.updatedAtEpochMilliseconds == updatedAtEpochMilliseconds &&
        other.deletedAtEpochMilliseconds == deletedAtEpochMilliseconds &&
        other.originalNotebookName == originalNotebookName;
  }

  @override
  int get hashCode => Object.hashAll(<Object?>[
        id,
        notebookId,
        title,
        kind,
        document,
        markdownText,
        backgroundKey,
        sortIndex,
        visibleCharacterCount,
        latinWordCount,
        summary,
        createdAtEpochMilliseconds,
        updatedAtEpochMilliseconds,
        deletedAtEpochMilliseconds,
        originalNotebookName,
      ]);
}

// -- Constants

const Object _notProvided = Object();
