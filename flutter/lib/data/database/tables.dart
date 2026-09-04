import 'package:drift/drift.dart';

// -- Type Definitions

@DataClassName('NotebookRow')
class Notebooks extends Table {
  TextColumn get id => text()();

  TextColumn get name => text()();

  IntColumn get sortIndex => integer()();

  IntColumn get createdAtEpochMilliseconds => integer()();

  IntColumn get updatedAtEpochMilliseconds => integer()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};
}

@DataClassName('NoteRow')
class Notes extends Table {
  TextColumn get id => text()();

  TextColumn get notebookId => text()
      .nullable()
      .references(Notebooks, #id, onDelete: KeyAction.setNull)();

  TextColumn get title => text()();

  TextColumn get kind => text()();

  TextColumn get documentJson => text().nullable()();

  TextColumn get markdownText => text().nullable()();

  TextColumn get backgroundKey => text().nullable()();

  IntColumn get sortIndex => integer()();

  IntColumn get visibleCharacterCount => integer()();

  IntColumn get latinWordCount => integer()();

  TextColumn get summary => text()();

  IntColumn get createdAtEpochMilliseconds => integer()();

  IntColumn get updatedAtEpochMilliseconds => integer()();

  IntColumn get deletedAtEpochMilliseconds => integer().nullable()();

  TextColumn get originalNotebookName => text().nullable()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};

  @override
  List<String> get customConstraints => <String>[
        "CHECK (kind IN ('rich', 'markdown'))",
        "CHECK ((kind = 'rich' AND document_json IS NOT NULL AND markdown_text IS NULL) "
            "OR (kind = 'markdown' AND document_json IS NULL AND markdown_text IS NOT NULL))",
        'CHECK (visible_character_count >= 0)',
        'CHECK (latin_word_count >= 0)',
      ];
}

@DataClassName('NoteRevisionRow')
class NoteRevisions extends Table {
  TextColumn get id => text()();

  TextColumn get noteId => text().references(
        Notes,
        #id,
        onDelete: KeyAction.cascade,
      )();

  TextColumn get reason => text()();

  TextColumn get kind => text()();

  TextColumn get title => text()();

  TextColumn get documentJson => text().nullable()();

  TextColumn get markdownText => text().nullable()();

  IntColumn get createdAtEpochMilliseconds => integer()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};

  @override
  List<String> get customConstraints => <String>[
        "CHECK (reason IN ('convertToMarkdown', 'agentPolish'))",
        "CHECK (kind IN ('rich', 'markdown'))",
        "CHECK ((kind = 'rich' AND document_json IS NOT NULL AND markdown_text IS NULL) "
            "OR (kind = 'markdown' AND document_json IS NULL AND markdown_text IS NOT NULL))",
      ];
}

@DataClassName('AttachmentRow')
class Attachments extends Table {
  TextColumn get id => text()();

  TextColumn get kind => text()();

  TextColumn get mimeType => text()();

  TextColumn get originalFileName => text().nullable()();

  TextColumn get relativePath => text()();

  IntColumn get byteSize => integer()();

  IntColumn get widthPixels => integer().nullable()();

  IntColumn get heightPixels => integer().nullable()();

  IntColumn get createdAtEpochMilliseconds => integer()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{id};

  @override
  List<String> get customConstraints => <String>[
        "CHECK (kind IN ('image', 'sticker', 'drawing'))",
        'CHECK (byte_size >= 0)',
        'CHECK (width_pixels IS NULL OR width_pixels > 0)',
        'CHECK (height_pixels IS NULL OR height_pixels > 0)',
      ];
}

@DataClassName('SearchHistoryRow')
class SearchHistoryEntries extends Table {
  @override
  String get tableName => 'search_history';

  TextColumn get query => text()();

  IntColumn get usedAtEpochMilliseconds => integer()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{query};
}

@DataClassName('AppSettingsRow')
class AppSettingsEntries extends Table {
  @override
  String get tableName => 'app_settings';

  IntColumn get singletonId => integer().withDefault(const Constant<int>(1))();

  TextColumn get defaultBackgroundKey => text()();

  TextColumn get themeMode => text()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{singletonId};

  @override
  List<String> get customConstraints => <String>[
        'CHECK (singleton_id = 1)',
        "CHECK (theme_mode IN ('system', 'light', 'dark'))",
      ];
}
