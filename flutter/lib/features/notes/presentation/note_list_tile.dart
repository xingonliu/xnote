import 'package:flutter/material.dart';

import '../../../design/icons/xnote_icon.dart';
import '../../../design/tokens/xnote_tokens.dart';
import '../../../domain/model/note.dart';

// -- Type Definitions

final class NoteListTile extends StatelessWidget {
  const NoteListTile({
    required this.note,
    required this.notebookName,
    required this.selected,
    required this.selectionMode,
    required this.onTap,
    required this.onLongPress,
    this.trailing,
    super.key,
  });

  final Note note;
  final String? notebookName;
  final bool selected;
  final bool selectionMode;
  final VoidCallback onTap;
  final VoidCallback onLongPress;
  final Widget? trailing;

  // -- Functions

  String _subtitle() {
    final details = <String>[
      if (note.summary.trim().isNotEmpty) note.summary.trim(),
      if (notebookName?.trim().isNotEmpty ?? false) notebookName!.trim(),
      _formatUpdatedAt(note.updatedAtEpochMilliseconds),
    ];
    return details.join(' · ');
  }

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      selected: selected,
      button: true,
      child: Material(
        color: selected
            ? colors.primaryContainer.withValues(alpha: 0.72)
            : colors.surface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(xnoteCardRadius),
        child: InkWell(
          key: ValueKey<String>('note-row-${note.id}'),
          borderRadius: BorderRadius.circular(xnoteCardRadius),
          onTap: onTap,
          onLongPress: onLongPress,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 76),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: xnoteSpacingMedium,
                vertical: xnoteSpacingSmall,
              ),
              child: Row(
                children: <Widget>[
                  if (selectionMode) ...<Widget>[
                    XNoteIconView(
                      icon: selected ? XNoteIcon.checked : XNoteIcon.unchecked,
                      color:
                          selected ? colors.primary : colors.onSurfaceVariant,
                    ),
                    const SizedBox(width: xnoteSpacingSmall),
                  ],
                  Expanded(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Text(
                          note.title.trim().isEmpty ? '无标题' : note.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: xnoteSpacingExtraSmall),
                        Text(
                          _subtitle(),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style:
                              Theme.of(context).textTheme.bodyMedium?.copyWith(
                                    color: colors.onSurfaceVariant,
                                  ),
                        ),
                      ],
                    ),
                  ),
                  if (trailing != null) trailing!,
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// -- Functions

String _formatUpdatedAt(int epochMilliseconds) {
  final value = DateTime.fromMillisecondsSinceEpoch(epochMilliseconds);
  String twoDigits(int number) => number.toString().padLeft(2, '0');
  return '${value.year}-${twoDigits(value.month)}-${twoDigits(value.day)} '
      '${twoDigits(value.hour)}:${twoDigits(value.minute)}';
}
