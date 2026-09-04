// -- Type Definitions

final class BackgroundKey {
  factory BackgroundKey(String id) {
    if (!builtinBackgroundIds.contains(id)) {
      throw ArgumentError.value(id, 'id', 'Unsupported built-in background');
    }
    return BackgroundKey._(id);
  }

  const BackgroundKey._(this.id);

  final String id;

  // -- Functions

  String encode() => 'builtin:$id';

  @override
  bool operator ==(Object other) => other is BackgroundKey && other.id == id;

  @override
  int get hashCode => id.hashCode;

  @override
  String toString() => 'BackgroundKey($id)';
}

// -- Constants

const defaultBuiltinBackgroundId = 'default';
const creamBuiltinBackgroundId = 'cream';
const ruledBuiltinBackgroundId = 'ruled';
const gridBuiltinBackgroundId = 'grid';
const builtinBackgroundIds = <String>{
  defaultBuiltinBackgroundId,
  creamBuiltinBackgroundId,
  ruledBuiltinBackgroundId,
  gridBuiltinBackgroundId,
};

// -- Functions

BackgroundKey? parseBackgroundKey(String? raw) {
  const prefix = 'builtin:';
  if (raw == null || !raw.startsWith(prefix)) {
    return null;
  }
  final id = raw.substring(prefix.length);
  return builtinBackgroundIds.contains(id) ? BackgroundKey(id) : null;
}

BackgroundKey defaultBackgroundKey() =>
    BackgroundKey(defaultBuiltinBackgroundId);

BackgroundKey resolveBackgroundKey({
  required BackgroundKey? noteBackground,
  required BackgroundKey defaultBackground,
}) =>
    noteBackground ?? defaultBackground;
