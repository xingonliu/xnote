import 'package:flutter/material.dart';

import '../icons/xnote_icon.dart';
import '../tokens/xnote_tokens.dart';

// -- Type Definitions

final class XNoteEmptyState extends StatelessWidget {
  const XNoteEmptyState({
    required this.icon,
    required this.title,
    required this.message,
    this.action,
    super.key,
  });

  final XNoteIcon icon;
  final String title;
  final String message;
  final Widget? action;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(xnoteSpacingLarge),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              XNoteIconView(
                icon: icon,
                size: xnoteIconSizeHero,
                color: Theme.of(context).colorScheme.primary,
              ),
              const SizedBox(height: xnoteSpacingMedium),
              Text(
                title,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: xnoteSpacingSmall),
              Text(
                message,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
              if (action != null) ...<Widget>[
                const SizedBox(height: xnoteSpacingLarge),
                action!,
              ],
            ],
          ),
        ),
      ),
    );
  }
}

final class XNoteLoadingState extends StatelessWidget {
  const XNoteLoadingState({this.label = '正在加载', super.key});

  final String label;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => Center(
        child: Semantics(
          label: label,
          child: const CircularProgressIndicator.adaptive(),
        ),
      );
}

final class XNoteErrorState extends StatelessWidget {
  const XNoteErrorState({
    required this.message,
    required this.onRetry,
    super.key,
  });

  final String message;
  final VoidCallback onRetry;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) => XNoteEmptyState(
        icon: XNoteIcon.notes,
        title: '暂时无法加载',
        message: message,
        action: FilledButton(onPressed: onRetry, child: const Text('重试')),
      );
}
