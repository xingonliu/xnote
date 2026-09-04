import 'package:uuid/uuid.dart';

import 'id_generator.dart';

// -- Type Definitions

final class UuidIdGenerator implements IdGenerator {
  const UuidIdGenerator({Uuid uuid = const Uuid()}) : _uuid = uuid;

  final Uuid _uuid;

  // -- Functions

  @override
  String nextId() => _uuid.v4();
}
