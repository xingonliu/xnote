import '../../core/ids/id_generator.dart';
import '../../core/value/equality.dart';
import 'note_block.dart';

// -- Type Definitions

final class NoteDocument {
  factory NoteDocument({
    int schemaVersion = currentDocumentSchemaVersion,
    Iterable<NoteBlock> blocks = const <NoteBlock>[],
  }) {
    if (schemaVersion != currentDocumentSchemaVersion) {
      throw ArgumentError.value(
        schemaVersion,
        'schemaVersion',
        'Unsupported note document schema',
      );
    }
    final immutableBlocks = List<NoteBlock>.unmodifiable(blocks);
    final blockIds = immutableBlocks.map((block) => block.id).toSet();
    if (blockIds.length != immutableBlocks.length) {
      throw ArgumentError('Block ids must be unique within a document');
    }
    return NoteDocument._(
      schemaVersion: schemaVersion,
      blocks: immutableBlocks,
    );
  }

  const NoteDocument._({required this.schemaVersion, required this.blocks});

  final int schemaVersion;
  final List<NoteBlock> blocks;

  // -- Derived Values

  Set<String> get attachmentIds => <String>{
        for (final block in blocks)
          if (block case ImageBlock(:final attachmentId))
            attachmentId
          else if (block case StickerBlock(:final attachmentId))
            attachmentId
          else if (block case DrawingBlock(:final attachmentId))
            attachmentId,
      };

  // -- Functions

  NoteDocument copyWith({Iterable<NoteBlock>? blocks}) {
    return NoteDocument(
      schemaVersion: schemaVersion,
      blocks: blocks ?? this.blocks,
    );
  }

  NoteBlock? blockById(String id) {
    for (final block in blocks) {
      if (block.id == id) {
        return block;
      }
    }
    return null;
  }

  int blockIndex(String id) => blocks.indexWhere((block) => block.id == id);

  @override
  bool operator ==(Object other) {
    return other is NoteDocument &&
        other.schemaVersion == schemaVersion &&
        listValueEquals(other.blocks, blocks);
  }

  @override
  int get hashCode => Object.hash(schemaVersion, listValueHash(blocks));
}

// -- Constants

const currentDocumentSchemaVersion = 1;

// -- Functions

NoteDocument emptyNoteDocument(IdGenerator idGenerator) => NoteDocument(
      blocks: <NoteBlock>[emptyBodyBlock(idGenerator.nextId())],
    );
