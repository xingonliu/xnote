import 'package:drift/drift.dart';

import '../../core/time/clock.dart';
import '../../domain/repositories/search_history_repository.dart';
import '../database/xnote_database.dart';

// -- Type Definitions

final class DriftSearchHistoryRepository implements SearchHistoryRepository {
  const DriftSearchHistoryRepository({
    required XNoteDatabase database,
    required Clock clock,
  })  : _database = database,
        _clock = clock;

  final XNoteDatabase _database;
  final Clock _clock;

  // -- Constants

  static const maximumRecentSearches = 10;

  // -- Functions

  @override
  Stream<List<String>> watchRecentQueries() {
    final query = _database.select(_database.searchHistoryEntries)
      ..orderBy(<OrderingTerm Function($SearchHistoryEntriesTable)>[
        (row) => OrderingTerm.desc(row.usedAtEpochMilliseconds),
      ])
      ..limit(maximumRecentSearches);
    return query.watch().map(
          (rows) => List<String>.unmodifiable(rows.map((row) => row.query)),
        );
  }

  @override
  Future<void> recordQuery(String query) async {
    final normalized = query.trim().replaceAll(RegExp(r'\s+'), ' ');
    if (normalized.isEmpty) {
      return;
    }
    final now = _clock.nowEpochMilliseconds();
    await _database.transaction(() async {
      final current =
          await _database.select(_database.searchHistoryEntries).get();
      final duplicates = current
          .where(
            (row) => row.query.toLowerCase() == normalized.toLowerCase(),
          )
          .map((row) => row.query)
          .toList(growable: false);
      if (duplicates.isNotEmpty) {
        await (_database.delete(
          _database.searchHistoryEntries,
        )..where((row) => row.query.isIn(duplicates)))
            .go();
      }
      await _database.into(_database.searchHistoryEntries).insert(
            SearchHistoryEntriesCompanion.insert(
              query: normalized,
              usedAtEpochMilliseconds: now,
            ),
          );
      final ordered = await (_database.select(_database.searchHistoryEntries)
            ..orderBy(<OrderingTerm Function($SearchHistoryEntriesTable)>[
              (row) => OrderingTerm.desc(row.usedAtEpochMilliseconds),
            ]))
          .get();
      final stale = ordered
          .skip(maximumRecentSearches)
          .map((row) => row.query)
          .toList(growable: false);
      if (stale.isNotEmpty) {
        await (_database.delete(
          _database.searchHistoryEntries,
        )..where((row) => row.query.isIn(stale)))
            .go();
      }
    });
  }

  @override
  Future<void> clear() => _database.delete(_database.searchHistoryEntries).go();
}
