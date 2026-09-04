import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import '../../../design/icons/xnote_icon.dart';
import '../../../design/tokens/xnote_tokens.dart';
import '../../../domain/model/notebook.dart';

// -- Type Definitions

enum NotebookSelectionKind { all, unfiled, notebook }

final class NotebookSelection {
  const NotebookSelection._(this.kind, this.notebookId);

  const NotebookSelection.all() : this._(NotebookSelectionKind.all, null);

  const NotebookSelection.unfiled()
      : this._(NotebookSelectionKind.unfiled, null);

  const NotebookSelection.notebook(String notebookId)
      : this._(NotebookSelectionKind.notebook, notebookId);

  final NotebookSelectionKind kind;
  final String? notebookId;
}

final class _NotebookOption extends StatelessWidget {
  const _NotebookOption({
    required this.icon,
    required this.title,
    required this.selected,
    required this.onTap,
    super.key,
  });

  final XNoteIcon icon;
  final String title;
  final bool selected;
  final VoidCallback onTap;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => ListTile(
        minTileHeight: xnoteMinimumTouchTarget,
        leading: XNoteIconView(icon: icon),
        title: Text(title),
        trailing: selected
            ? const XNoteIconView(icon: XNoteIcon.check)
            : const XNoteIconView(
                icon: XNoteIcon.forward,
                size: xnoteIconSizeSmall,
              ),
        selected: selected,
        onTap: onTap,
      );
}

// -- Functions

Future<NotebookSelection?> showNotebookSelectionSheet({
  required BuildContext context,
  required List<Notebook> notebooks,
  required bool includeAll,
  String? selectedNotebookId,
  bool unfiledSelected = false,
}) {
  return GlassModalSheet.show<NotebookSelection>(
    context: context,
    quality: GlassQuality.standard,
    initialState: GlassSheetState.half,
    builder: (sheetContext) => Material(
      type: MaterialType.transparency,
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: 440,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(
              xnoteSpacingMedium,
              xnoteSpacingSmall,
              xnoteSpacingMedium,
              xnoteSpacingLarge,
            ),
            children: <Widget>[
              Text(
                '选择笔记本',
                style: Theme.of(sheetContext).textTheme.headlineSmall,
              ),
              const SizedBox(height: xnoteSpacingSmall),
              if (includeAll)
                _NotebookOption(
                  key: const Key('notebook-option-all'),
                  icon: XNoteIcon.notes,
                  title: '全部笔记',
                  selected: !unfiledSelected && selectedNotebookId == null,
                  onTap: () => Navigator.of(sheetContext).pop(
                    const NotebookSelection.all(),
                  ),
                ),
              _NotebookOption(
                key: const Key('notebook-option-unfiled'),
                icon: XNoteIcon.inbox,
                title: '未归档',
                selected: unfiledSelected,
                onTap: () => Navigator.of(sheetContext).pop(
                  const NotebookSelection.unfiled(),
                ),
              ),
              for (final notebook in notebooks)
                _NotebookOption(
                  key: ValueKey<String>('notebook-option-${notebook.id}'),
                  icon: XNoteIcon.notes,
                  title: notebook.name,
                  selected: selectedNotebookId == notebook.id,
                  onTap: () => Navigator.of(sheetContext).pop(
                    NotebookSelection.notebook(notebook.id),
                  ),
                ),
            ],
          ),
        ),
      ),
    ),
  );
}

Future<String?> showNotebookNameDialog({
  required BuildContext context,
  required String title,
  String initialName = '',
}) {
  var draftName = initialName;
  return GlassDialog.show<String>(
    context: context,
    title: title,
    content: Material(
      type: MaterialType.transparency,
      child: TextFormField(
        key: const Key('notebook-name-field'),
        initialValue: initialName,
        autofocus: true,
        textInputAction: TextInputAction.done,
        decoration: const InputDecoration(hintText: '笔记本名称'),
        onChanged: (value) => draftName = value,
        onFieldSubmitted: (value) => _submitNotebookName(context, value),
      ),
    ),
    actions: <GlassDialogAction>[
      GlassDialogAction(
        label: '取消',
        onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
      ),
      GlassDialogAction(
        label: '完成',
        isPrimary: true,
        onPressed: () => _submitNotebookName(context, draftName),
      ),
    ],
  );
}

Future<bool> showDestructiveConfirmation({
  required BuildContext context,
  required String title,
  required String message,
  required String confirmLabel,
}) async {
  return await GlassDialog.show<bool>(
        context: context,
        title: title,
        message: message,
        actions: <GlassDialogAction>[
          GlassDialogAction(
            label: '取消',
            onPressed: () {
              Navigator.of(context, rootNavigator: true).pop(false);
            },
          ),
          GlassDialogAction(
            label: confirmLabel,
            isDestructive: true,
            onPressed: () {
              Navigator.of(context, rootNavigator: true).pop(true);
            },
          ),
        ],
      ) ??
      false;
}

void showNoteToast(BuildContext context, String message) {
  GlassToast.show(
    context,
    message: message,
    icon: const XNoteIconView(icon: XNoteIcon.check),
    type: GlassToastType.success,
    quality: GlassQuality.standard,
  );
}

void _submitNotebookName(BuildContext context, String rawName) {
  final name = rawName.trim();
  if (name.isEmpty) {
    return;
  }
  Navigator.of(context, rootNavigator: true).pop(name);
}
