import 'note_block.dart';

// -- Type Definitions

enum InlineMark { bold, italic, underline, strikethrough, highlight }

final class InlineMarks {
  const InlineMarks({
    this.bold = false,
    this.italic = false,
    this.underline = false,
    this.strikethrough = false,
    this.highlight = false,
    this.linkUrl,
  });

  final bool bold;
  final bool italic;
  final bool underline;
  final bool strikethrough;
  final bool highlight;
  final String? linkUrl;

  // -- Functions

  InlineRun toRun(String text) => InlineRun(
        text: text,
        bold: bold,
        italic: italic,
        underline: underline,
        strikethrough: strikethrough,
        highlight: highlight,
        linkUrl: linkUrl,
      );

  bool has(InlineMark mark) => switch (mark) {
        InlineMark.bold => bold,
        InlineMark.italic => italic,
        InlineMark.underline => underline,
        InlineMark.strikethrough => strikethrough,
        InlineMark.highlight => highlight,
      };

  InlineMarks toggle(InlineMark mark, {required bool enabled}) {
    return InlineMarks(
      bold: mark == InlineMark.bold ? enabled : bold,
      italic: mark == InlineMark.italic ? enabled : italic,
      underline: mark == InlineMark.underline ? enabled : underline,
      strikethrough: mark == InlineMark.strikethrough ? enabled : strikethrough,
      highlight: mark == InlineMark.highlight ? enabled : highlight,
      linkUrl: linkUrl,
    );
  }

  InlineMarks copyWith({Object? linkUrl = _notProvided}) {
    return InlineMarks(
      bold: bold,
      italic: italic,
      underline: underline,
      strikethrough: strikethrough,
      highlight: highlight,
      linkUrl:
          identical(linkUrl, _notProvided) ? this.linkUrl : linkUrl as String?,
    );
  }

  @override
  bool operator ==(Object other) {
    return other is InlineMarks &&
        other.bold == bold &&
        other.italic == italic &&
        other.underline == underline &&
        other.strikethrough == strikethrough &&
        other.highlight == highlight &&
        other.linkUrl == linkUrl;
  }

  @override
  int get hashCode => Object.hash(
        bold,
        italic,
        underline,
        strikethrough,
        highlight,
        linkUrl,
      );
}

final class TextReplacement {
  const TextReplacement({
    required this.start,
    required this.end,
    required this.insertedText,
  });

  final int start;
  final int end;
  final String insertedText;
}

// -- Constants

const Object _notProvided = Object();

// -- Functions

InlineMarks marksOf(InlineRun run) => InlineMarks(
      bold: run.bold,
      italic: run.italic,
      underline: run.underline,
      strikethrough: run.strikethrough,
      highlight: run.highlight,
      linkUrl: run.linkUrl,
    );

InlineRun withMarks(InlineRun run, InlineMarks marks) => marks.toRun(run.text);

extension InlineRunListEditing on List<InlineRun> {
  List<InlineRun> coalesced() {
    final merged = <InlineRun>[];
    for (final run in this) {
      if (run.text.isEmpty) {
        continue;
      }
      final last = merged.lastOrNull;
      if (last != null && marksOf(last) == marksOf(run)) {
        merged[merged.length - 1] =
            last.copyWith(text: '${last.text}${run.text}');
      } else {
        merged.add(run);
      }
    }
    return List<InlineRun>.unmodifiable(merged);
  }

  (List<InlineRun>, List<InlineRun>) splitAt(int offset) {
    final safeOffset = offset.clamp(0, plainText(this).length);
    if (safeOffset == 0) {
      return (<InlineRun>[], coalesced());
    }
    if (safeOffset == plainText(this).length) {
      return (coalesced(), <InlineRun>[]);
    }

    final before = <InlineRun>[];
    final after = <InlineRun>[];
    var remaining = safeOffset;
    var didSplit = false;
    for (final run in this) {
      if (didSplit) {
        after.add(run);
        continue;
      }
      if (remaining >= run.text.length) {
        before.add(run);
        remaining -= run.text.length;
        didSplit = remaining == 0;
      } else if (remaining == 0) {
        after.add(run);
        didSplit = true;
      } else {
        before.add(run.copyWith(text: run.text.substring(0, remaining)));
        after.add(run.copyWith(text: run.text.substring(remaining)));
        didSplit = true;
      }
    }
    return (before.coalesced(), after.coalesced());
  }

  List<InlineRun> replacedRange(int start, int end, InlineRun insertion) {
    final length = plainText(this).length;
    final lower = (start < end ? start : end).clamp(0, length);
    final upper = (start > end ? start : end).clamp(0, length);
    final (left, rest) = splitAt(lower);
    final (_, right) = rest.splitAt(upper - lower);
    final middle =
        insertion.text.isEmpty ? <InlineRun>[] : <InlineRun>[insertion];
    return <InlineRun>[...left, ...middle, ...right].coalesced();
  }

  List<InlineRun> mapRange(
    int start,
    int end,
    InlineRun Function(InlineRun run) transform,
  ) {
    final length = plainText(this).length;
    final lower = (start < end ? start : end).clamp(0, length);
    final upper = (start > end ? start : end).clamp(0, length);
    if (lower == upper) {
      return coalesced();
    }
    final (left, rest) = splitAt(lower);
    final (middle, right) = rest.splitAt(upper - lower);
    return <InlineRun>[
      ...left,
      ...middle.map(transform),
      ...right,
    ].coalesced();
  }

  List<InlineRun> runsInRange(int start, int end) {
    final length = plainText(this).length;
    final lower = (start < end ? start : end).clamp(0, length);
    final upper = (start > end ? start : end).clamp(0, length);
    if (lower == upper) {
      return <InlineRun>[];
    }
    final (_, rest) = splitAt(lower);
    final (middle, _) = rest.splitAt(upper - lower);
    return middle;
  }

  InlineMarks marksAt(int offset) {
    if (isEmpty) {
      return const InlineMarks();
    }
    final length = plainText(this).length;
    if (length == 0) {
      return marksOf(first);
    }
    final index = offset <= 0 ? 0 : (offset - 1).clamp(0, length - 1);
    var cursor = 0;
    for (final run in this) {
      final next = cursor + run.text.length;
      if (index < next) {
        return marksOf(run);
      }
      cursor = next;
    }
    return marksOf(last);
  }

  bool rangeHasMark(int start, int end, InlineMark mark) {
    final runs = runsInRange(start, end);
    return runs.isNotEmpty && runs.every((run) => marksOf(run).has(mark));
  }

  bool rangeHasLink(int start, int end) {
    final runs = runsInRange(start, end);
    return runs.isNotEmpty &&
        runs.every((run) => run.linkUrl?.trim().isNotEmpty ?? false);
  }
}

TextReplacement findTextReplacement(String oldText, String newText) {
  if (oldText == newText) {
    return TextReplacement(
      start: newText.length,
      end: newText.length,
      insertedText: '',
    );
  }
  var start = 0;
  final sharedLength =
      oldText.length < newText.length ? oldText.length : newText.length;
  while (start < sharedLength && oldText[start] == newText[start]) {
    start += 1;
  }
  var oldEnd = oldText.length;
  var newEnd = newText.length;
  while (oldEnd > start &&
      newEnd > start &&
      oldText[oldEnd - 1] == newText[newEnd - 1]) {
    oldEnd -= 1;
    newEnd -= 1;
  }
  return TextReplacement(
    start: start,
    end: oldEnd,
    insertedText: newText.substring(start, newEnd),
  );
}
