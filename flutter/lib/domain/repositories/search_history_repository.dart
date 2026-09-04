// -- Type Definitions

abstract interface class SearchHistoryRepository {
  Stream<List<String>> watchRecentQueries();

  Future<void> recordQuery(String query);

  Future<void> clear();
}
