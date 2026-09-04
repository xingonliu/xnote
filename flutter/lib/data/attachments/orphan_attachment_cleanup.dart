import '../../domain/model/attachment.dart';
import '../database/row_mappers.dart';
import '../database/xnote_database.dart';

// -- Functions

Future<List<Attachment>> removeOrphanAttachmentRows(
  XNoteDatabase database,
) async {
  final noteRows = await database.select(database.notes).get();
  final revisionRows = await database.select(database.noteRevisions).get();
  final referenced = <String>{};
  for (final row in noteRows) {
    referenced.addAll(noteFromRow(row).referencedAttachmentIds);
  }
  for (final row in revisionRows) {
    referenced.addAll(
        noteRevisionFromRow(row).document?.attachmentIds ?? const <String>{});
  }
  final attachmentRows = await database.select(database.attachments).get();
  final orphans = <Attachment>[
    for (final row in attachmentRows)
      if (!referenced.contains(row.id)) attachmentFromRow(row),
  ];
  if (orphans.isNotEmpty) {
    await (database.delete(database.attachments)
          ..where((row) => row.id.isIn(orphans.map((item) => item.id))))
        .go();
  }
  return orphans;
}
