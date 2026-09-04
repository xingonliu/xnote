// -- Type Definitions

enum AttachmentKind { image, sticker, drawing }

final class Attachment {
  Attachment({
    required this.id,
    required this.kind,
    required this.mimeType,
    required this.relativePath,
    required this.byteSize,
    required this.createdAtEpochMilliseconds,
    this.originalFileName,
    this.widthPixels,
    this.heightPixels,
  }) {
    if (id.isEmpty || mimeType.isEmpty || relativePath.isEmpty) {
      throw ArgumentError('Attachment identifiers and paths must not be empty');
    }
    if (relativePath.startsWith('/') ||
        relativePath.startsWith('\\') ||
        RegExp(r'^[A-Za-z]:[\\/]').hasMatch(relativePath)) {
      throw ArgumentError.value(
        relativePath,
        'relativePath',
        'Attachment paths must be relative',
      );
    }
    if (byteSize < 0 ||
        (widthPixels != null && widthPixels! <= 0) ||
        (heightPixels != null && heightPixels! <= 0)) {
      throw ArgumentError('Attachment dimensions and size must be positive');
    }
  }

  final String id;
  final AttachmentKind kind;
  final String mimeType;
  final String? originalFileName;
  final String relativePath;
  final int byteSize;
  final int? widthPixels;
  final int? heightPixels;
  final int createdAtEpochMilliseconds;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is Attachment &&
        other.id == id &&
        other.kind == kind &&
        other.mimeType == mimeType &&
        other.originalFileName == originalFileName &&
        other.relativePath == relativePath &&
        other.byteSize == byteSize &&
        other.widthPixels == widthPixels &&
        other.heightPixels == heightPixels &&
        other.createdAtEpochMilliseconds == createdAtEpochMilliseconds;
  }

  @override
  int get hashCode => Object.hash(
        id,
        kind,
        mimeType,
        originalFileName,
        relativePath,
        byteSize,
        widthPixels,
        heightPixels,
        createdAtEpochMilliseconds,
      );
}
