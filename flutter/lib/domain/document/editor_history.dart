import 'note_document.dart';
import 'note_document_editing.dart';

// -- Type Definitions

final class EditorSnapshot {
  const EditorSnapshot({
    required this.title,
    required this.document,
    required this.selection,
  });

  final String title;
  final NoteDocument document;
  final EditorSelection selection;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is EditorSnapshot &&
        other.title == title &&
        other.document == document &&
        other.selection == selection;
  }

  @override
  int get hashCode => Object.hash(title, document, selection);
}

final class EditorHistory {
  EditorHistory({this.limit = 50}) {
    if (limit < 1) {
      throw ArgumentError.value(
          limit, 'limit', 'History limit must be positive');
    }
  }

  final int limit;

  // -- State and Variables

  final List<EditorSnapshot> _undoStack = <EditorSnapshot>[];
  final List<EditorSnapshot> _redoStack = <EditorSnapshot>[];
  String? _coalesceKey;

  // -- Derived Values

  bool get canUndo => _undoStack.isNotEmpty;

  bool get canRedo => _redoStack.isNotEmpty;

  // -- Functions

  void capture(EditorSnapshot snapshot, {String? coalesceKey}) {
    if (coalesceKey != null && coalesceKey == _coalesceKey) {
      return;
    }
    _coalesceKey = coalesceKey;
    _undoStack.add(snapshot);
    if (_undoStack.length > limit) {
      _undoStack.removeAt(0);
    }
    _redoStack.clear();
  }

  EditorSnapshot? undo(EditorSnapshot current) {
    if (_undoStack.isEmpty) {
      return null;
    }
    final previous = _undoStack.removeLast();
    _redoStack.add(current);
    _coalesceKey = null;
    return previous;
  }

  EditorSnapshot? redo(EditorSnapshot current) {
    if (_redoStack.isEmpty) {
      return null;
    }
    final next = _redoStack.removeLast();
    _undoStack.add(current);
    _coalesceKey = null;
    return next;
  }
}

final class MarkdownEditorHistory {
  MarkdownEditorHistory({this.limit = 50}) {
    if (limit < 1) {
      throw ArgumentError.value(
          limit, 'limit', 'History limit must be positive');
    }
  }

  final int limit;

  // -- State and Variables

  final List<String> _undoStack = <String>[];
  final List<String> _redoStack = <String>[];
  String? _coalesceKey;

  // -- Derived Values

  bool get canUndo => _undoStack.isNotEmpty;

  bool get canRedo => _redoStack.isNotEmpty;

  // -- Functions

  void capture(String value, {String? coalesceKey}) {
    if (coalesceKey != null && coalesceKey == _coalesceKey) {
      return;
    }
    _coalesceKey = coalesceKey;
    _undoStack.add(value);
    if (_undoStack.length > limit) {
      _undoStack.removeAt(0);
    }
    _redoStack.clear();
  }

  String? undo(String current) {
    if (_undoStack.isEmpty) {
      return null;
    }
    final previous = _undoStack.removeLast();
    _redoStack.add(current);
    _coalesceKey = null;
    return previous;
  }

  String? redo(String current) {
    if (_redoStack.isEmpty) {
      return null;
    }
    final next = _redoStack.removeLast();
    _undoStack.add(current);
    _coalesceKey = null;
    return next;
  }
}
