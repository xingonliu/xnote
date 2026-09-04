import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../tokens/xnote_tokens.dart';

// -- Type Definitions

enum XNoteIcon {
  notesFill('fill/file-text.svg'),
  agentFill('fill/star.svg'),
  profileFill('fill/user.svg'),
  notes('stroke/square-pen.svg'),
  agent('stroke/star.svg'),
  profile('stroke/user.svg'),
  search('stroke/search.svg'),
  back('stroke/arrow-left.svg', directional: true),
  add('stroke/plus.svg'),
  more('stroke/more-horizontal.svg'),
  check('stroke/check.svg'),
  expand('stroke/chevron-down.svg'),
  forward('stroke/chevron-right.svg', directional: true),
  delete('stroke/bin.svg'),
  undo('stroke/arrow-u-turn-left.svg', directional: true),
  redo('stroke/arrow-u-turn-right.svg', directional: true),
  inbox('stroke/inbox.svg'),
  reorder('stroke/grip-vertical.svg'),
  unchecked('stroke/square.svg'),
  checked('stroke/square-check.svg');

  const XNoteIcon(this.relativeAssetPath, {this.directional = false});

  final String relativeAssetPath;
  final bool directional;

  // -- Derived Values

  String get assetPath => 'assets/icons/keyline/$relativeAssetPath';
}

final class XNoteIconView extends StatelessWidget {
  const XNoteIconView({
    required this.icon,
    this.size = xnoteIconSizeMedium,
    this.color,
    this.semanticLabel,
    super.key,
  });

  final XNoteIcon icon;
  final double size;
  final Color? color;
  final String? semanticLabel;

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    final iconColor = color ??
        IconTheme.of(context).color ??
        Theme.of(context).colorScheme.onSurface;
    return SvgPicture.asset(
      icon.assetPath,
      width: size,
      height: size,
      colorFilter: ColorFilter.mode(iconColor, BlendMode.srcIn),
      matchTextDirection: icon.directional,
      semanticsLabel: semanticLabel,
    );
  }
}
