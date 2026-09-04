import '../../domain/repositories/note_repository.dart';

// -- Type Definitions

abstract interface class BackgroundMaintenanceScheduler {
  Future<void> scheduleRecycleBinSweep();
}

final class RecycleBinMaintenance {
  const RecycleBinMaintenance(this._notes);

  final NoteRepository _notes;

  // -- Functions

  Future<void> runStartupSweep() => _notes.purgeExpiredTrash();
}
