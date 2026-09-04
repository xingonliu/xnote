// -- Type Definitions

final class Notebook {
  Notebook({
    required this.id,
    required this.name,
    required this.sortIndex,
    required this.createdAtEpochMilliseconds,
    required this.updatedAtEpochMilliseconds,
  }) {
    if (id.isEmpty || name.trim().isEmpty) {
      throw ArgumentError('Notebook id and name must not be empty');
    }
  }

  final String id;
  final String name;
  final int sortIndex;
  final int createdAtEpochMilliseconds;
  final int updatedAtEpochMilliseconds;

  // -- Functions

  Notebook copyWith(
      {String? name, int? sortIndex, int? updatedAtEpochMilliseconds}) {
    return Notebook(
      id: id,
      name: name ?? this.name,
      sortIndex: sortIndex ?? this.sortIndex,
      createdAtEpochMilliseconds: createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds:
          updatedAtEpochMilliseconds ?? this.updatedAtEpochMilliseconds,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is Notebook &&
        other.id == id &&
        other.name == name &&
        other.sortIndex == sortIndex &&
        other.createdAtEpochMilliseconds == createdAtEpochMilliseconds &&
        other.updatedAtEpochMilliseconds == updatedAtEpochMilliseconds;
  }

  @override
  int get hashCode => Object.hash(
        id,
        name,
        sortIndex,
        createdAtEpochMilliseconds,
        updatedAtEpochMilliseconds,
      );
}

final class NotebookStats {
  const NotebookStats({required this.noteCount, required this.characterCount});

  final int noteCount;
  final int characterCount;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is NotebookStats &&
        other.noteCount == noteCount &&
        other.characterCount == characterCount;
  }

  @override
  int get hashCode => Object.hash(noteCount, characterCount);
}
