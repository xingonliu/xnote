import 'note.dart';

// -- Type Definitions

final class NoteSearchResult {
  const NoteSearchResult({required this.note, required this.matchedText});

  final Note note;
  final String matchedText;

  // -- Functions

  @override
  bool operator ==(Object other) =>
      other is NoteSearchResult &&
      other.note == note &&
      other.matchedText == matchedText;

  @override
  int get hashCode => Object.hash(note, matchedText);
}
