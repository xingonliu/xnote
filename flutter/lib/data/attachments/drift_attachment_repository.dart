import '../../core/ids/id_generator.dart';
import '../../core/time/clock.dart';
import '../../domain/model/attachment.dart';
import '../../domain/repositories/attachment_repository.dart';
import '../database/row_mappers.dart';
import '../database/xnote_database.dart';
import '../files/attachment_file_store.dart';
import 'orphan_attachment_cleanup.dart';

// -- Type Definitions

final class DriftAttachmentRepository implements AttachmentRepository {
  const DriftAttachmentRepository({
    required XNoteDatabase database,
    required AttachmentFileStore files,
    required IdGenerator idGenerator,
    required Clock clock,
  })  : _database = database,
        _files = files,
        _idGenerator = idGenerator,
        _clock = clock;

  final XNoteDatabase _database;
  final AttachmentFileStore _files;
  final IdGenerator _idGenerator;
  final Clock _clock;

  // -- Functions

  @override
  Future<Attachment> saveAttachment({
    required AttachmentKind kind,
    required String mimeType,
    required String extension,
    required Stream<List<int>> content,
    String? originalFileName,
    int? widthPixels,
    int? heightPixels,
  }) async {
    final id = _idGenerator.nextId();
    final relativePath = AttachmentFileStore.relativePath(id, extension);
    final file = await _files.write(relativePath, content);
    final attachment = Attachment(
      id: id,
      kind: kind,
      mimeType: mimeType,
      originalFileName: originalFileName,
      relativePath: relativePath,
      byteSize: await file.length(),
      widthPixels: widthPixels,
      heightPixels: heightPixels,
      createdAtEpochMilliseconds: _clock.nowEpochMilliseconds(),
    );
    try {
      await _database
          .into(_database.attachments)
          .insert(attachmentToRow(attachment));
      return attachment;
    } catch (_) {
      await _files.delete(relativePath);
      rethrow;
    }
  }

  @override
  Future<Attachment?> getAttachment(String id) async {
    final query = _database.select(_database.attachments)
      ..where((row) => row.id.equals(id));
    final row = await query.getSingleOrNull();
    return row == null ? null : attachmentFromRow(row);
  }

  @override
  Future<void> deleteUnreferencedAttachments() async {
    final orphans = await _database.transaction(
      () => removeOrphanAttachmentRows(_database),
    );
    for (final attachment in orphans) {
      await _files.delete(attachment.relativePath);
    }
  }
}
