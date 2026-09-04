import '../model/notebook.dart';

// -- Type Definitions

abstract interface class NotebookRepository {
  Stream<List<Notebook>> watchNotebooks();

  Future<Notebook?> getNotebook(String id);

  Future<Notebook> createNotebook(String name);

  Future<Notebook> renameNotebook(String id, String name);

  Future<void> reorderNotebooks(List<String> orderedIds);

  Future<void> deleteNotebook(String id);
}
