import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../domain/model/note.dart';
import '../../domain/model/notebook.dart';
import '../../domain/repositories/note_repository.dart';
import '../../domain/repositories/notebook_repository.dart';
import '../dependencies/xnote_dependencies.dart';
import '../dependencies/xnote_dependency_loader.dart';

// -- Constants

final xnoteDependenciesProvider =
    FutureProvider<XNoteDependencies>((ref) async {
  final dependencies = await openXNoteDependencies();
  ref.onDispose(() {
    unawaited(dependencies.close());
  });
  return dependencies;
});

final noteRepositoryProvider = Provider<NoteRepository>((ref) {
  return ref.watch(xnoteDependenciesProvider).requireValue.notes;
});

final notebookRepositoryProvider = Provider<NotebookRepository>((ref) {
  return ref.watch(xnoteDependenciesProvider).requireValue.notebooks;
});

final notebooksProvider = StreamProvider<List<Notebook>>((ref) {
  return ref.watch(notebookRepositoryProvider).watchNotebooks();
});

final activeNotesProvider =
    StreamProvider.family<List<Note>, NoteListSort>((ref, sort) {
  return ref.watch(noteRepositoryProvider).watchActiveNotes(sort: sort);
});

final unfiledNotesProvider =
    StreamProvider.family<List<Note>, NoteListSort>((ref, sort) {
  return ref.watch(noteRepositoryProvider).watchUnfiledNotes(sort: sort);
});

final notebookNotesProvider =
    StreamProvider.family<List<Note>, (String, NoteListSort)>((ref, argument) {
  return ref.watch(noteRepositoryProvider).watchNotesInNotebook(
        argument.$1,
        sort: argument.$2,
      );
});

final noteProvider = StreamProvider.family<Note?, String>((ref, id) {
  return ref.watch(noteRepositoryProvider).watchNote(id);
});
