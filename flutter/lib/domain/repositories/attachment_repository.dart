import '../model/attachment.dart';

// -- Type Definitions

abstract interface class AttachmentRepository {
  Future<Attachment> saveAttachment({
    required AttachmentKind kind,
    required String mimeType,
    required String extension,
    required Stream<List<int>> content,
    String? originalFileName,
    int? widthPixels,
    int? heightPixels,
  });

  Future<Attachment?> getAttachment(String id);

  Future<void> deleteUnreferencedAttachments();
}
