import 'dart:io';

import 'package:xnote/app/dependencies/xnote_dependency_loader_native.dart';
import 'package:xnote/core/ids/id_generator.dart';
import 'package:xnote/core/time/clock.dart';

// -- Type Definitions

final class TestDependencies {
  TestDependencies._({
    required this.dependencies,
    required Directory temporaryDirectory,
  }) : _temporaryDirectory = temporaryDirectory;

  final NativeXNoteDependencies dependencies;
  final Directory _temporaryDirectory;

  // -- Functions

  static Future<TestDependencies> create() async {
    final temporaryDirectory = await Directory.systemTemp.createTemp(
      'xnote_widget_test_',
    );
    try {
      final dependencies = await NativeXNoteDependencies.inMemory(
        rootDirectory: temporaryDirectory,
        idGenerator: TestIdGenerator(),
        clock: TestClock(),
      );
      return TestDependencies._(
        dependencies: dependencies,
        temporaryDirectory: temporaryDirectory,
      );
    } catch (_) {
      await temporaryDirectory.delete(recursive: true);
      rethrow;
    }
  }

  Future<void> close() async {
    await dependencies.close();
    if (await _temporaryDirectory.exists()) {
      await _temporaryDirectory.delete(recursive: true);
    }
  }
}

final class TestIdGenerator implements IdGenerator {
  int _next = 0;

  // -- Functions

  @override
  String nextId() => 'test-id-${_next += 1}';
}

final class TestClock implements Clock {
  TestClock([this._next = 1000]);

  int _next;

  // -- Functions

  @override
  int nowEpochMilliseconds() => _next += 1;
}
