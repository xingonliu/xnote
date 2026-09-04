import 'dart:math' as math;

import 'package:flutter/material.dart';

// -- Type Definitions

class RichTextEditingController extends TextEditingController {
  RichTextEditingController({super.text}) : _previousText = text ?? '' {
    addListener(_handleTextChange);
  }

  // -- State and Variables

  final List<TextRange> _boldRanges = <TextRange>[];
  String _previousText;

  // -- Derived Values

  List<TextRange> get boldRanges => List<TextRange>.unmodifiable(_boldRanges);

  // -- Functions

  void toggleBold() {
    final selectedRange = _normalizedRange(selection);
    if (!selectedRange.isValid || selectedRange.isCollapsed) {
      return;
    }

    final isEntireSelectionBold = _isRangeBold(selectedRange);
    if (isEntireSelectionBold) {
      _removeBold(selectedRange);
    } else {
      _boldRanges.add(selectedRange);
      _mergeBoldRanges();
    }
    notifyListeners();
  }

  @override
  TextSpan buildTextSpan({
    required BuildContext context,
    TextStyle? style,
    required bool withComposing,
  }) {
    final composingRange = withComposing && value.composing.isValid
        ? _normalizedRange(value.composing)
        : TextRange.empty;
    final boundaries = <int>{0, text.length};
    for (final range in _boldRanges) {
      boundaries
        ..add(range.start.clamp(0, text.length))
        ..add(range.end.clamp(0, text.length));
    }
    if (!composingRange.isCollapsed) {
      boundaries
        ..add(composingRange.start.clamp(0, text.length))
        ..add(composingRange.end.clamp(0, text.length));
    }

    final sortedBoundaries = boundaries.toList()..sort();
    final children = <InlineSpan>[];
    for (var index = 0; index < sortedBoundaries.length - 1; index += 1) {
      final start = sortedBoundaries[index];
      final end = sortedBoundaries[index + 1];
      if (start == end) {
        continue;
      }
      final range = TextRange(start: start, end: end);
      children.add(
        TextSpan(
          text: text.substring(start, end),
          style: TextStyle(
            fontWeight: _isRangeBold(range) ? FontWeight.w700 : null,
            decoration: _rangesOverlap(range, composingRange)
                ? TextDecoration.underline
                : null,
          ),
        ),
      );
    }

    return TextSpan(style: style, children: children);
  }

  bool _isRangeBold(TextRange target) {
    return _boldRanges.any(
      (range) => range.start <= target.start && range.end >= target.end,
    );
  }

  void _removeBold(TextRange target) {
    final nextRanges = <TextRange>[];
    for (final range in _boldRanges) {
      if (!_rangesOverlap(range, target)) {
        nextRanges.add(range);
        continue;
      }
      if (range.start < target.start) {
        nextRanges.add(TextRange(start: range.start, end: target.start));
      }
      if (range.end > target.end) {
        nextRanges.add(TextRange(start: target.end, end: range.end));
      }
    }
    _boldRanges
      ..clear()
      ..addAll(nextRanges);
  }

  void _handleTextChange() {
    if (text == _previousText) {
      return;
    }

    final previousText = _previousText;
    final currentText = text;
    var prefixLength = 0;
    while (prefixLength < previousText.length &&
        prefixLength < currentText.length &&
        previousText.codeUnitAt(prefixLength) ==
            currentText.codeUnitAt(prefixLength)) {
      prefixLength += 1;
    }

    var suffixLength = 0;
    while (suffixLength < previousText.length - prefixLength &&
        suffixLength < currentText.length - prefixLength &&
        previousText.codeUnitAt(previousText.length - suffixLength - 1) ==
            currentText.codeUnitAt(currentText.length - suffixLength - 1)) {
      suffixLength += 1;
    }

    final previousEditEnd = previousText.length - suffixLength;
    final currentEditEnd = currentText.length - suffixLength;
    final delta = currentText.length - previousText.length;
    final nextRanges = <TextRange>[];
    for (final range in _boldRanges) {
      if (range.end <= prefixLength) {
        nextRanges.add(range);
      } else if (range.start >= previousEditEnd) {
        nextRanges.add(
          TextRange(start: range.start + delta, end: range.end + delta),
        );
      } else {
        final nextStart = math.min(range.start, prefixLength);
        final nextEnd =
            range.end >= previousEditEnd ? range.end + delta : currentEditEnd;
        if (nextStart < nextEnd) {
          nextRanges.add(TextRange(start: nextStart, end: nextEnd));
        }
      }
    }

    _previousText = currentText;
    _boldRanges
      ..clear()
      ..addAll(nextRanges);
    _mergeBoldRanges();
  }

  void _mergeBoldRanges() {
    _boldRanges.removeWhere(
      (range) => !range.isValid || range.isCollapsed || range.start < 0,
    );
    _boldRanges.sort((left, right) => left.start.compareTo(right.start));
    final merged = <TextRange>[];
    for (final range in _boldRanges) {
      final clamped = TextRange(
        start: range.start.clamp(0, text.length),
        end: range.end.clamp(0, text.length),
      );
      if (clamped.isCollapsed) {
        continue;
      }
      if (merged.isEmpty || merged.last.end < clamped.start) {
        merged.add(clamped);
      } else {
        final previous = merged.removeLast();
        merged.add(
          TextRange(
            start: previous.start,
            end: math.max(previous.end, clamped.end),
          ),
        );
      }
    }
    _boldRanges
      ..clear()
      ..addAll(merged);
  }

  TextRange _normalizedRange(TextRange range) {
    return TextRange(
      start: math.min(range.start, range.end),
      end: math.max(range.start, range.end),
    );
  }

  bool _rangesOverlap(TextRange left, TextRange right) {
    return left.isValid &&
        right.isValid &&
        !left.isCollapsed &&
        !right.isCollapsed &&
        left.start < right.end &&
        right.start < left.end;
  }

  @override
  void dispose() {
    removeListener(_handleTextChange);
    super.dispose();
  }
}
