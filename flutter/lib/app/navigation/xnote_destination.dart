// -- Type Definitions

enum XNoteDestination {
  notes('/notes'),
  agent('/agent'),
  profile('/profile');

  const XNoteDestination(this.rootPath);

  final String rootPath;
}
