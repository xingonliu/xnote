// -- Type Definitions

final class TextMatchRange {
  const TextMatchRange({required this.start, required this.endExclusive});

  final int start;
  final int endExclusive;

  // -- Functions

  @override
  bool operator ==(Object other) {
    return other is TextMatchRange &&
        other.start == start &&
        other.endExclusive == endExclusive;
  }

  @override
  int get hashCode => Object.hash(start, endExclusive);
}

// -- Constants

const defaultSearchSnippetLength = 120;
const searchSnippetLeadingContext = 32;

// -- Functions

List<TextMatchRange> searchMatchRanges(String text, String query) {
  final normalizedQuery = query.trim();
  if (text.isEmpty || normalizedQuery.isEmpty) {
    return <TextMatchRange>[];
  }
  final phraseMatches = _nonOverlappingMatches(text, normalizedQuery);
  if (phraseMatches.isNotEmpty) {
    return phraseMatches;
  }
  final seenTerms = <String>{};
  final candidates = <TextMatchRange>[];
  for (final term in normalizedQuery.split(RegExp(r'\s+'))) {
    final normalizedTerm = term.toLowerCase();
    if (term.isNotEmpty && seenTerms.add(normalizedTerm)) {
      candidates.addAll(_nonOverlappingMatches(text, term));
    }
  }
  candidates.sort((left, right) => left.start.compareTo(right.start));
  final accepted = <TextMatchRange>[];
  for (final candidate in candidates) {
    if (accepted.isEmpty || accepted.last.endExclusive <= candidate.start) {
      accepted.add(candidate);
    }
  }
  return List<TextMatchRange>.unmodifiable(accepted);
}

String searchSnippet(
  String text,
  String query, {
  int maximumLength = defaultSearchSnippetLength,
}) {
  if (maximumLength <= 0) {
    throw ArgumentError.value(
      maximumLength,
      'maximumLength',
      'Maximum snippet length must be positive',
    );
  }
  final normalizedText = text.replaceAll(RegExp(r'\s+'), ' ').trim();
  if (normalizedText.length <= maximumLength) {
    return normalizedText;
  }
  final matches = searchMatchRanges(normalizedText, query);
  if (matches.isEmpty) {
    return '${normalizedText.substring(0, maximumLength).trimRight()}…';
  }
  final initialStart = matches.first.start - searchSnippetLeadingContext;
  final start = initialStart < 0 ? 0 : initialStart;
  final end = (start + maximumLength).clamp(0, normalizedText.length);
  final adjustedStart = end - maximumLength < 0 ? 0 : end - maximumLength;
  return '${adjustedStart > 0 ? '…' : ''}'
      '${normalizedText.substring(adjustedStart, end).trim()}'
      '${end < normalizedText.length ? '…' : ''}';
}

List<TextMatchRange> _nonOverlappingMatches(String text, String query) {
  if (query.isEmpty) {
    return <TextMatchRange>[];
  }
  final lowerText = text.toLowerCase();
  final lowerQuery = query.toLowerCase();
  final matches = <TextMatchRange>[];
  var searchFrom = 0;
  while (searchFrom <= lowerText.length - lowerQuery.length) {
    final index = lowerText.indexOf(lowerQuery, searchFrom);
    if (index < 0) {
      break;
    }
    matches.add(
      TextMatchRange(start: index, endExclusive: index + query.length),
    );
    searchFrom = index + query.length;
  }
  return List<TextMatchRange>.unmodifiable(matches);
}
