import 'dart:io';

// -- Type Definitions

final class AttachmentFileStore {
  AttachmentFileStore(this.rootDirectory);

  final Directory rootDirectory;

  // -- Constants

  static const directoryName = 'attachments';

  // -- Functions

  static String relativePath(String id, String extension) {
    if (id.trim().isEmpty) {
      throw ArgumentError.value(id, 'id', 'Attachment id must not be empty');
    }
    final suffix = extension.trim().replaceFirst(RegExp(r'^\.+'), '');
    if (suffix.contains('/') || suffix.contains(r'\')) {
      throw ArgumentError.value(
        extension,
        'extension',
        'Attachment extension must not contain a path',
      );
    }
    return suffix.isEmpty ? '$directoryName/$id' : '$directoryName/$id.$suffix';
  }

  File resolve(String relativePath) {
    final normalized = relativePath.replaceAll(r'\', '/');
    final segments = normalized.split('/');
    if (normalized.isEmpty ||
        normalized.startsWith('/') ||
        RegExp(r'^[A-Za-z]:/').hasMatch(normalized) ||
        segments.any((segment) => segment == '..')) {
      throw ArgumentError.value(
        relativePath,
        'relativePath',
        'Attachment path must stay inside the file store',
      );
    }
    return File(
      <String>[rootDirectory.path, ...segments].join(Platform.pathSeparator),
    );
  }

  Future<File> write(
    String relativePath,
    Stream<List<int>> content,
  ) async {
    final target = resolve(relativePath);
    final temporary = File('${target.path}.part');
    await target.parent.create(recursive: true);
    IOSink? sink;
    try {
      sink = temporary.openWrite();
      await sink.addStream(content);
      await sink.close();
      sink = null;
      return temporary.rename(target.path);
    } catch (_) {
      if (sink != null) {
        try {
          await sink.close();
        } on FileSystemException {
          // The original stream or write error remains authoritative.
        }
      }
      if (await temporary.exists()) {
        await temporary.delete();
      }
      rethrow;
    }
  }

  Future<void> delete(String relativePath) async {
    final file = resolve(relativePath);
    if (await file.exists()) {
      await file.delete();
    }
  }
}
