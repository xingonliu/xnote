// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'xnote_database.dart';

// ignore_for_file: type=lint
class $NotebooksTable extends Notebooks
    with TableInfo<$NotebooksTable, NotebookRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $NotebooksTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
      'id', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
      'name', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _sortIndexMeta =
      const VerificationMeta('sortIndex');
  @override
  late final GeneratedColumn<int> sortIndex = GeneratedColumn<int>(
      'sort_index', aliasedName, false,
      type: DriftSqlType.int, requiredDuringInsert: true);
  static const VerificationMeta _createdAtEpochMillisecondsMeta =
      const VerificationMeta('createdAtEpochMilliseconds');
  @override
  late final GeneratedColumn<int> createdAtEpochMilliseconds =
      GeneratedColumn<int>('created_at_epoch_milliseconds', aliasedName, false,
          type: DriftSqlType.int, requiredDuringInsert: true);
  static const VerificationMeta _updatedAtEpochMillisecondsMeta =
      const VerificationMeta('updatedAtEpochMilliseconds');
  @override
  late final GeneratedColumn<int> updatedAtEpochMilliseconds =
      GeneratedColumn<int>('updated_at_epoch_milliseconds', aliasedName, false,
          type: DriftSqlType.int, requiredDuringInsert: true);
  @override
  List<GeneratedColumn> get $columns => [
        id,
        name,
        sortIndex,
        createdAtEpochMilliseconds,
        updatedAtEpochMilliseconds
      ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'notebooks';
  @override
  VerificationContext validateIntegrity(Insertable<NotebookRow> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('name')) {
      context.handle(
          _nameMeta, name.isAcceptableOrUnknown(data['name']!, _nameMeta));
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('sort_index')) {
      context.handle(_sortIndexMeta,
          sortIndex.isAcceptableOrUnknown(data['sort_index']!, _sortIndexMeta));
    } else if (isInserting) {
      context.missing(_sortIndexMeta);
    }
    if (data.containsKey('created_at_epoch_milliseconds')) {
      context.handle(
          _createdAtEpochMillisecondsMeta,
          createdAtEpochMilliseconds.isAcceptableOrUnknown(
              data['created_at_epoch_milliseconds']!,
              _createdAtEpochMillisecondsMeta));
    } else if (isInserting) {
      context.missing(_createdAtEpochMillisecondsMeta);
    }
    if (data.containsKey('updated_at_epoch_milliseconds')) {
      context.handle(
          _updatedAtEpochMillisecondsMeta,
          updatedAtEpochMilliseconds.isAcceptableOrUnknown(
              data['updated_at_epoch_milliseconds']!,
              _updatedAtEpochMillisecondsMeta));
    } else if (isInserting) {
      context.missing(_updatedAtEpochMillisecondsMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  NotebookRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return NotebookRow(
      id: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}id'])!,
      name: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}name'])!,
      sortIndex: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}sort_index'])!,
      createdAtEpochMilliseconds: attachedDatabase.typeMapping.read(
          DriftSqlType.int,
          data['${effectivePrefix}created_at_epoch_milliseconds'])!,
      updatedAtEpochMilliseconds: attachedDatabase.typeMapping.read(
          DriftSqlType.int,
          data['${effectivePrefix}updated_at_epoch_milliseconds'])!,
    );
  }

  @override
  $NotebooksTable createAlias(String alias) {
    return $NotebooksTable(attachedDatabase, alias);
  }
}

class NotebookRow extends DataClass implements Insertable<NotebookRow> {
  final String id;
  final String name;
  final int sortIndex;
  final int createdAtEpochMilliseconds;
  final int updatedAtEpochMilliseconds;
  const NotebookRow(
      {required this.id,
      required this.name,
      required this.sortIndex,
      required this.createdAtEpochMilliseconds,
      required this.updatedAtEpochMilliseconds});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['name'] = Variable<String>(name);
    map['sort_index'] = Variable<int>(sortIndex);
    map['created_at_epoch_milliseconds'] =
        Variable<int>(createdAtEpochMilliseconds);
    map['updated_at_epoch_milliseconds'] =
        Variable<int>(updatedAtEpochMilliseconds);
    return map;
  }

  NotebooksCompanion toCompanion(bool nullToAbsent) {
    return NotebooksCompanion(
      id: Value(id),
      name: Value(name),
      sortIndex: Value(sortIndex),
      createdAtEpochMilliseconds: Value(createdAtEpochMilliseconds),
      updatedAtEpochMilliseconds: Value(updatedAtEpochMilliseconds),
    );
  }

  factory NotebookRow.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return NotebookRow(
      id: serializer.fromJson<String>(json['id']),
      name: serializer.fromJson<String>(json['name']),
      sortIndex: serializer.fromJson<int>(json['sortIndex']),
      createdAtEpochMilliseconds:
          serializer.fromJson<int>(json['createdAtEpochMilliseconds']),
      updatedAtEpochMilliseconds:
          serializer.fromJson<int>(json['updatedAtEpochMilliseconds']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'name': serializer.toJson<String>(name),
      'sortIndex': serializer.toJson<int>(sortIndex),
      'createdAtEpochMilliseconds':
          serializer.toJson<int>(createdAtEpochMilliseconds),
      'updatedAtEpochMilliseconds':
          serializer.toJson<int>(updatedAtEpochMilliseconds),
    };
  }

  NotebookRow copyWith(
          {String? id,
          String? name,
          int? sortIndex,
          int? createdAtEpochMilliseconds,
          int? updatedAtEpochMilliseconds}) =>
      NotebookRow(
        id: id ?? this.id,
        name: name ?? this.name,
        sortIndex: sortIndex ?? this.sortIndex,
        createdAtEpochMilliseconds:
            createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
        updatedAtEpochMilliseconds:
            updatedAtEpochMilliseconds ?? this.updatedAtEpochMilliseconds,
      );
  NotebookRow copyWithCompanion(NotebooksCompanion data) {
    return NotebookRow(
      id: data.id.present ? data.id.value : this.id,
      name: data.name.present ? data.name.value : this.name,
      sortIndex: data.sortIndex.present ? data.sortIndex.value : this.sortIndex,
      createdAtEpochMilliseconds: data.createdAtEpochMilliseconds.present
          ? data.createdAtEpochMilliseconds.value
          : this.createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds: data.updatedAtEpochMilliseconds.present
          ? data.updatedAtEpochMilliseconds.value
          : this.updatedAtEpochMilliseconds,
    );
  }

  @override
  String toString() {
    return (StringBuffer('NotebookRow(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('sortIndex: $sortIndex, ')
          ..write('createdAtEpochMilliseconds: $createdAtEpochMilliseconds, ')
          ..write('updatedAtEpochMilliseconds: $updatedAtEpochMilliseconds')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, name, sortIndex,
      createdAtEpochMilliseconds, updatedAtEpochMilliseconds);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is NotebookRow &&
          other.id == this.id &&
          other.name == this.name &&
          other.sortIndex == this.sortIndex &&
          other.createdAtEpochMilliseconds == this.createdAtEpochMilliseconds &&
          other.updatedAtEpochMilliseconds == this.updatedAtEpochMilliseconds);
}

class NotebooksCompanion extends UpdateCompanion<NotebookRow> {
  final Value<String> id;
  final Value<String> name;
  final Value<int> sortIndex;
  final Value<int> createdAtEpochMilliseconds;
  final Value<int> updatedAtEpochMilliseconds;
  final Value<int> rowid;
  const NotebooksCompanion({
    this.id = const Value.absent(),
    this.name = const Value.absent(),
    this.sortIndex = const Value.absent(),
    this.createdAtEpochMilliseconds = const Value.absent(),
    this.updatedAtEpochMilliseconds = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  NotebooksCompanion.insert({
    required String id,
    required String name,
    required int sortIndex,
    required int createdAtEpochMilliseconds,
    required int updatedAtEpochMilliseconds,
    this.rowid = const Value.absent(),
  })  : id = Value(id),
        name = Value(name),
        sortIndex = Value(sortIndex),
        createdAtEpochMilliseconds = Value(createdAtEpochMilliseconds),
        updatedAtEpochMilliseconds = Value(updatedAtEpochMilliseconds);
  static Insertable<NotebookRow> custom({
    Expression<String>? id,
    Expression<String>? name,
    Expression<int>? sortIndex,
    Expression<int>? createdAtEpochMilliseconds,
    Expression<int>? updatedAtEpochMilliseconds,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (name != null) 'name': name,
      if (sortIndex != null) 'sort_index': sortIndex,
      if (createdAtEpochMilliseconds != null)
        'created_at_epoch_milliseconds': createdAtEpochMilliseconds,
      if (updatedAtEpochMilliseconds != null)
        'updated_at_epoch_milliseconds': updatedAtEpochMilliseconds,
      if (rowid != null) 'rowid': rowid,
    });
  }

  NotebooksCompanion copyWith(
      {Value<String>? id,
      Value<String>? name,
      Value<int>? sortIndex,
      Value<int>? createdAtEpochMilliseconds,
      Value<int>? updatedAtEpochMilliseconds,
      Value<int>? rowid}) {
    return NotebooksCompanion(
      id: id ?? this.id,
      name: name ?? this.name,
      sortIndex: sortIndex ?? this.sortIndex,
      createdAtEpochMilliseconds:
          createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds:
          updatedAtEpochMilliseconds ?? this.updatedAtEpochMilliseconds,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (sortIndex.present) {
      map['sort_index'] = Variable<int>(sortIndex.value);
    }
    if (createdAtEpochMilliseconds.present) {
      map['created_at_epoch_milliseconds'] =
          Variable<int>(createdAtEpochMilliseconds.value);
    }
    if (updatedAtEpochMilliseconds.present) {
      map['updated_at_epoch_milliseconds'] =
          Variable<int>(updatedAtEpochMilliseconds.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('NotebooksCompanion(')
          ..write('id: $id, ')
          ..write('name: $name, ')
          ..write('sortIndex: $sortIndex, ')
          ..write('createdAtEpochMilliseconds: $createdAtEpochMilliseconds, ')
          ..write('updatedAtEpochMilliseconds: $updatedAtEpochMilliseconds, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $NotesTable extends Notes with TableInfo<$NotesTable, NoteRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $NotesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
      'id', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _notebookIdMeta =
      const VerificationMeta('notebookId');
  @override
  late final GeneratedColumn<String> notebookId = GeneratedColumn<String>(
      'notebook_id', aliasedName, true,
      type: DriftSqlType.string,
      requiredDuringInsert: false,
      defaultConstraints: GeneratedColumn.constraintIsAlways(
          'REFERENCES notebooks (id) ON DELETE SET NULL'));
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
      'title', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
      'kind', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _documentJsonMeta =
      const VerificationMeta('documentJson');
  @override
  late final GeneratedColumn<String> documentJson = GeneratedColumn<String>(
      'document_json', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _markdownTextMeta =
      const VerificationMeta('markdownText');
  @override
  late final GeneratedColumn<String> markdownText = GeneratedColumn<String>(
      'markdown_text', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _backgroundKeyMeta =
      const VerificationMeta('backgroundKey');
  @override
  late final GeneratedColumn<String> backgroundKey = GeneratedColumn<String>(
      'background_key', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _sortIndexMeta =
      const VerificationMeta('sortIndex');
  @override
  late final GeneratedColumn<int> sortIndex = GeneratedColumn<int>(
      'sort_index', aliasedName, false,
      type: DriftSqlType.int, requiredDuringInsert: true);
  static const VerificationMeta _visibleCharacterCountMeta =
      const VerificationMeta('visibleCharacterCount');
  @override
  late final GeneratedColumn<int> visibleCharacterCount = GeneratedColumn<int>(
      'visible_character_count', aliasedName, false,
      type: DriftSqlType.int, requiredDuringInsert: true);
  static const VerificationMeta _latinWordCountMeta =
      const VerificationMeta('latinWordCount');
  @override
  late final GeneratedColumn<int> latinWordCount = GeneratedColumn<int>(
      'latin_word_count', aliasedName, false,
      type: DriftSqlType.int, requiredDuringInsert: true);
  static const VerificationMeta _summaryMeta =
      const VerificationMeta('summary');
  @override
  late final GeneratedColumn<String> summary = GeneratedColumn<String>(
      'summary', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _createdAtEpochMillisecondsMeta =
      const VerificationMeta('createdAtEpochMilliseconds');
  @override
  late final GeneratedColumn<int> createdAtEpochMilliseconds =
      GeneratedColumn<int>('created_at_epoch_milliseconds', aliasedName, false,
          type: DriftSqlType.int, requiredDuringInsert: true);
  static const VerificationMeta _updatedAtEpochMillisecondsMeta =
      const VerificationMeta('updatedAtEpochMilliseconds');
  @override
  late final GeneratedColumn<int> updatedAtEpochMilliseconds =
      GeneratedColumn<int>('updated_at_epoch_milliseconds', aliasedName, false,
          type: DriftSqlType.int, requiredDuringInsert: true);
  static const VerificationMeta _deletedAtEpochMillisecondsMeta =
      const VerificationMeta('deletedAtEpochMilliseconds');
  @override
  late final GeneratedColumn<int> deletedAtEpochMilliseconds =
      GeneratedColumn<int>('deleted_at_epoch_milliseconds', aliasedName, true,
          type: DriftSqlType.int, requiredDuringInsert: false);
  static const VerificationMeta _originalNotebookNameMeta =
      const VerificationMeta('originalNotebookName');
  @override
  late final GeneratedColumn<String> originalNotebookName =
      GeneratedColumn<String>('original_notebook_name', aliasedName, true,
          type: DriftSqlType.string, requiredDuringInsert: false);
  @override
  List<GeneratedColumn> get $columns => [
        id,
        notebookId,
        title,
        kind,
        documentJson,
        markdownText,
        backgroundKey,
        sortIndex,
        visibleCharacterCount,
        latinWordCount,
        summary,
        createdAtEpochMilliseconds,
        updatedAtEpochMilliseconds,
        deletedAtEpochMilliseconds,
        originalNotebookName
      ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'notes';
  @override
  VerificationContext validateIntegrity(Insertable<NoteRow> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('notebook_id')) {
      context.handle(
          _notebookIdMeta,
          notebookId.isAcceptableOrUnknown(
              data['notebook_id']!, _notebookIdMeta));
    }
    if (data.containsKey('title')) {
      context.handle(
          _titleMeta, title.isAcceptableOrUnknown(data['title']!, _titleMeta));
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
          _kindMeta, kind.isAcceptableOrUnknown(data['kind']!, _kindMeta));
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('document_json')) {
      context.handle(
          _documentJsonMeta,
          documentJson.isAcceptableOrUnknown(
              data['document_json']!, _documentJsonMeta));
    }
    if (data.containsKey('markdown_text')) {
      context.handle(
          _markdownTextMeta,
          markdownText.isAcceptableOrUnknown(
              data['markdown_text']!, _markdownTextMeta));
    }
    if (data.containsKey('background_key')) {
      context.handle(
          _backgroundKeyMeta,
          backgroundKey.isAcceptableOrUnknown(
              data['background_key']!, _backgroundKeyMeta));
    }
    if (data.containsKey('sort_index')) {
      context.handle(_sortIndexMeta,
          sortIndex.isAcceptableOrUnknown(data['sort_index']!, _sortIndexMeta));
    } else if (isInserting) {
      context.missing(_sortIndexMeta);
    }
    if (data.containsKey('visible_character_count')) {
      context.handle(
          _visibleCharacterCountMeta,
          visibleCharacterCount.isAcceptableOrUnknown(
              data['visible_character_count']!, _visibleCharacterCountMeta));
    } else if (isInserting) {
      context.missing(_visibleCharacterCountMeta);
    }
    if (data.containsKey('latin_word_count')) {
      context.handle(
          _latinWordCountMeta,
          latinWordCount.isAcceptableOrUnknown(
              data['latin_word_count']!, _latinWordCountMeta));
    } else if (isInserting) {
      context.missing(_latinWordCountMeta);
    }
    if (data.containsKey('summary')) {
      context.handle(_summaryMeta,
          summary.isAcceptableOrUnknown(data['summary']!, _summaryMeta));
    } else if (isInserting) {
      context.missing(_summaryMeta);
    }
    if (data.containsKey('created_at_epoch_milliseconds')) {
      context.handle(
          _createdAtEpochMillisecondsMeta,
          createdAtEpochMilliseconds.isAcceptableOrUnknown(
              data['created_at_epoch_milliseconds']!,
              _createdAtEpochMillisecondsMeta));
    } else if (isInserting) {
      context.missing(_createdAtEpochMillisecondsMeta);
    }
    if (data.containsKey('updated_at_epoch_milliseconds')) {
      context.handle(
          _updatedAtEpochMillisecondsMeta,
          updatedAtEpochMilliseconds.isAcceptableOrUnknown(
              data['updated_at_epoch_milliseconds']!,
              _updatedAtEpochMillisecondsMeta));
    } else if (isInserting) {
      context.missing(_updatedAtEpochMillisecondsMeta);
    }
    if (data.containsKey('deleted_at_epoch_milliseconds')) {
      context.handle(
          _deletedAtEpochMillisecondsMeta,
          deletedAtEpochMilliseconds.isAcceptableOrUnknown(
              data['deleted_at_epoch_milliseconds']!,
              _deletedAtEpochMillisecondsMeta));
    }
    if (data.containsKey('original_notebook_name')) {
      context.handle(
          _originalNotebookNameMeta,
          originalNotebookName.isAcceptableOrUnknown(
              data['original_notebook_name']!, _originalNotebookNameMeta));
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  NoteRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return NoteRow(
      id: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}id'])!,
      notebookId: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}notebook_id']),
      title: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}title'])!,
      kind: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}kind'])!,
      documentJson: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}document_json']),
      markdownText: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}markdown_text']),
      backgroundKey: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}background_key']),
      sortIndex: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}sort_index'])!,
      visibleCharacterCount: attachedDatabase.typeMapping.read(
          DriftSqlType.int, data['${effectivePrefix}visible_character_count'])!,
      latinWordCount: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}latin_word_count'])!,
      summary: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}summary'])!,
      createdAtEpochMilliseconds: attachedDatabase.typeMapping.read(
          DriftSqlType.int,
          data['${effectivePrefix}created_at_epoch_milliseconds'])!,
      updatedAtEpochMilliseconds: attachedDatabase.typeMapping.read(
          DriftSqlType.int,
          data['${effectivePrefix}updated_at_epoch_milliseconds'])!,
      deletedAtEpochMilliseconds: attachedDatabase.typeMapping.read(
          DriftSqlType.int,
          data['${effectivePrefix}deleted_at_epoch_milliseconds']),
      originalNotebookName: attachedDatabase.typeMapping.read(
          DriftSqlType.string,
          data['${effectivePrefix}original_notebook_name']),
    );
  }

  @override
  $NotesTable createAlias(String alias) {
    return $NotesTable(attachedDatabase, alias);
  }
}

class NoteRow extends DataClass implements Insertable<NoteRow> {
  final String id;
  final String? notebookId;
  final String title;
  final String kind;
  final String? documentJson;
  final String? markdownText;
  final String? backgroundKey;
  final int sortIndex;
  final int visibleCharacterCount;
  final int latinWordCount;
  final String summary;
  final int createdAtEpochMilliseconds;
  final int updatedAtEpochMilliseconds;
  final int? deletedAtEpochMilliseconds;
  final String? originalNotebookName;
  const NoteRow(
      {required this.id,
      this.notebookId,
      required this.title,
      required this.kind,
      this.documentJson,
      this.markdownText,
      this.backgroundKey,
      required this.sortIndex,
      required this.visibleCharacterCount,
      required this.latinWordCount,
      required this.summary,
      required this.createdAtEpochMilliseconds,
      required this.updatedAtEpochMilliseconds,
      this.deletedAtEpochMilliseconds,
      this.originalNotebookName});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    if (!nullToAbsent || notebookId != null) {
      map['notebook_id'] = Variable<String>(notebookId);
    }
    map['title'] = Variable<String>(title);
    map['kind'] = Variable<String>(kind);
    if (!nullToAbsent || documentJson != null) {
      map['document_json'] = Variable<String>(documentJson);
    }
    if (!nullToAbsent || markdownText != null) {
      map['markdown_text'] = Variable<String>(markdownText);
    }
    if (!nullToAbsent || backgroundKey != null) {
      map['background_key'] = Variable<String>(backgroundKey);
    }
    map['sort_index'] = Variable<int>(sortIndex);
    map['visible_character_count'] = Variable<int>(visibleCharacterCount);
    map['latin_word_count'] = Variable<int>(latinWordCount);
    map['summary'] = Variable<String>(summary);
    map['created_at_epoch_milliseconds'] =
        Variable<int>(createdAtEpochMilliseconds);
    map['updated_at_epoch_milliseconds'] =
        Variable<int>(updatedAtEpochMilliseconds);
    if (!nullToAbsent || deletedAtEpochMilliseconds != null) {
      map['deleted_at_epoch_milliseconds'] =
          Variable<int>(deletedAtEpochMilliseconds);
    }
    if (!nullToAbsent || originalNotebookName != null) {
      map['original_notebook_name'] = Variable<String>(originalNotebookName);
    }
    return map;
  }

  NotesCompanion toCompanion(bool nullToAbsent) {
    return NotesCompanion(
      id: Value(id),
      notebookId: notebookId == null && nullToAbsent
          ? const Value.absent()
          : Value(notebookId),
      title: Value(title),
      kind: Value(kind),
      documentJson: documentJson == null && nullToAbsent
          ? const Value.absent()
          : Value(documentJson),
      markdownText: markdownText == null && nullToAbsent
          ? const Value.absent()
          : Value(markdownText),
      backgroundKey: backgroundKey == null && nullToAbsent
          ? const Value.absent()
          : Value(backgroundKey),
      sortIndex: Value(sortIndex),
      visibleCharacterCount: Value(visibleCharacterCount),
      latinWordCount: Value(latinWordCount),
      summary: Value(summary),
      createdAtEpochMilliseconds: Value(createdAtEpochMilliseconds),
      updatedAtEpochMilliseconds: Value(updatedAtEpochMilliseconds),
      deletedAtEpochMilliseconds:
          deletedAtEpochMilliseconds == null && nullToAbsent
              ? const Value.absent()
              : Value(deletedAtEpochMilliseconds),
      originalNotebookName: originalNotebookName == null && nullToAbsent
          ? const Value.absent()
          : Value(originalNotebookName),
    );
  }

  factory NoteRow.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return NoteRow(
      id: serializer.fromJson<String>(json['id']),
      notebookId: serializer.fromJson<String?>(json['notebookId']),
      title: serializer.fromJson<String>(json['title']),
      kind: serializer.fromJson<String>(json['kind']),
      documentJson: serializer.fromJson<String?>(json['documentJson']),
      markdownText: serializer.fromJson<String?>(json['markdownText']),
      backgroundKey: serializer.fromJson<String?>(json['backgroundKey']),
      sortIndex: serializer.fromJson<int>(json['sortIndex']),
      visibleCharacterCount:
          serializer.fromJson<int>(json['visibleCharacterCount']),
      latinWordCount: serializer.fromJson<int>(json['latinWordCount']),
      summary: serializer.fromJson<String>(json['summary']),
      createdAtEpochMilliseconds:
          serializer.fromJson<int>(json['createdAtEpochMilliseconds']),
      updatedAtEpochMilliseconds:
          serializer.fromJson<int>(json['updatedAtEpochMilliseconds']),
      deletedAtEpochMilliseconds:
          serializer.fromJson<int?>(json['deletedAtEpochMilliseconds']),
      originalNotebookName:
          serializer.fromJson<String?>(json['originalNotebookName']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'notebookId': serializer.toJson<String?>(notebookId),
      'title': serializer.toJson<String>(title),
      'kind': serializer.toJson<String>(kind),
      'documentJson': serializer.toJson<String?>(documentJson),
      'markdownText': serializer.toJson<String?>(markdownText),
      'backgroundKey': serializer.toJson<String?>(backgroundKey),
      'sortIndex': serializer.toJson<int>(sortIndex),
      'visibleCharacterCount': serializer.toJson<int>(visibleCharacterCount),
      'latinWordCount': serializer.toJson<int>(latinWordCount),
      'summary': serializer.toJson<String>(summary),
      'createdAtEpochMilliseconds':
          serializer.toJson<int>(createdAtEpochMilliseconds),
      'updatedAtEpochMilliseconds':
          serializer.toJson<int>(updatedAtEpochMilliseconds),
      'deletedAtEpochMilliseconds':
          serializer.toJson<int?>(deletedAtEpochMilliseconds),
      'originalNotebookName': serializer.toJson<String?>(originalNotebookName),
    };
  }

  NoteRow copyWith(
          {String? id,
          Value<String?> notebookId = const Value.absent(),
          String? title,
          String? kind,
          Value<String?> documentJson = const Value.absent(),
          Value<String?> markdownText = const Value.absent(),
          Value<String?> backgroundKey = const Value.absent(),
          int? sortIndex,
          int? visibleCharacterCount,
          int? latinWordCount,
          String? summary,
          int? createdAtEpochMilliseconds,
          int? updatedAtEpochMilliseconds,
          Value<int?> deletedAtEpochMilliseconds = const Value.absent(),
          Value<String?> originalNotebookName = const Value.absent()}) =>
      NoteRow(
        id: id ?? this.id,
        notebookId: notebookId.present ? notebookId.value : this.notebookId,
        title: title ?? this.title,
        kind: kind ?? this.kind,
        documentJson:
            documentJson.present ? documentJson.value : this.documentJson,
        markdownText:
            markdownText.present ? markdownText.value : this.markdownText,
        backgroundKey:
            backgroundKey.present ? backgroundKey.value : this.backgroundKey,
        sortIndex: sortIndex ?? this.sortIndex,
        visibleCharacterCount:
            visibleCharacterCount ?? this.visibleCharacterCount,
        latinWordCount: latinWordCount ?? this.latinWordCount,
        summary: summary ?? this.summary,
        createdAtEpochMilliseconds:
            createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
        updatedAtEpochMilliseconds:
            updatedAtEpochMilliseconds ?? this.updatedAtEpochMilliseconds,
        deletedAtEpochMilliseconds: deletedAtEpochMilliseconds.present
            ? deletedAtEpochMilliseconds.value
            : this.deletedAtEpochMilliseconds,
        originalNotebookName: originalNotebookName.present
            ? originalNotebookName.value
            : this.originalNotebookName,
      );
  NoteRow copyWithCompanion(NotesCompanion data) {
    return NoteRow(
      id: data.id.present ? data.id.value : this.id,
      notebookId:
          data.notebookId.present ? data.notebookId.value : this.notebookId,
      title: data.title.present ? data.title.value : this.title,
      kind: data.kind.present ? data.kind.value : this.kind,
      documentJson: data.documentJson.present
          ? data.documentJson.value
          : this.documentJson,
      markdownText: data.markdownText.present
          ? data.markdownText.value
          : this.markdownText,
      backgroundKey: data.backgroundKey.present
          ? data.backgroundKey.value
          : this.backgroundKey,
      sortIndex: data.sortIndex.present ? data.sortIndex.value : this.sortIndex,
      visibleCharacterCount: data.visibleCharacterCount.present
          ? data.visibleCharacterCount.value
          : this.visibleCharacterCount,
      latinWordCount: data.latinWordCount.present
          ? data.latinWordCount.value
          : this.latinWordCount,
      summary: data.summary.present ? data.summary.value : this.summary,
      createdAtEpochMilliseconds: data.createdAtEpochMilliseconds.present
          ? data.createdAtEpochMilliseconds.value
          : this.createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds: data.updatedAtEpochMilliseconds.present
          ? data.updatedAtEpochMilliseconds.value
          : this.updatedAtEpochMilliseconds,
      deletedAtEpochMilliseconds: data.deletedAtEpochMilliseconds.present
          ? data.deletedAtEpochMilliseconds.value
          : this.deletedAtEpochMilliseconds,
      originalNotebookName: data.originalNotebookName.present
          ? data.originalNotebookName.value
          : this.originalNotebookName,
    );
  }

  @override
  String toString() {
    return (StringBuffer('NoteRow(')
          ..write('id: $id, ')
          ..write('notebookId: $notebookId, ')
          ..write('title: $title, ')
          ..write('kind: $kind, ')
          ..write('documentJson: $documentJson, ')
          ..write('markdownText: $markdownText, ')
          ..write('backgroundKey: $backgroundKey, ')
          ..write('sortIndex: $sortIndex, ')
          ..write('visibleCharacterCount: $visibleCharacterCount, ')
          ..write('latinWordCount: $latinWordCount, ')
          ..write('summary: $summary, ')
          ..write('createdAtEpochMilliseconds: $createdAtEpochMilliseconds, ')
          ..write('updatedAtEpochMilliseconds: $updatedAtEpochMilliseconds, ')
          ..write('deletedAtEpochMilliseconds: $deletedAtEpochMilliseconds, ')
          ..write('originalNotebookName: $originalNotebookName')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
      id,
      notebookId,
      title,
      kind,
      documentJson,
      markdownText,
      backgroundKey,
      sortIndex,
      visibleCharacterCount,
      latinWordCount,
      summary,
      createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds,
      deletedAtEpochMilliseconds,
      originalNotebookName);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is NoteRow &&
          other.id == this.id &&
          other.notebookId == this.notebookId &&
          other.title == this.title &&
          other.kind == this.kind &&
          other.documentJson == this.documentJson &&
          other.markdownText == this.markdownText &&
          other.backgroundKey == this.backgroundKey &&
          other.sortIndex == this.sortIndex &&
          other.visibleCharacterCount == this.visibleCharacterCount &&
          other.latinWordCount == this.latinWordCount &&
          other.summary == this.summary &&
          other.createdAtEpochMilliseconds == this.createdAtEpochMilliseconds &&
          other.updatedAtEpochMilliseconds == this.updatedAtEpochMilliseconds &&
          other.deletedAtEpochMilliseconds == this.deletedAtEpochMilliseconds &&
          other.originalNotebookName == this.originalNotebookName);
}

class NotesCompanion extends UpdateCompanion<NoteRow> {
  final Value<String> id;
  final Value<String?> notebookId;
  final Value<String> title;
  final Value<String> kind;
  final Value<String?> documentJson;
  final Value<String?> markdownText;
  final Value<String?> backgroundKey;
  final Value<int> sortIndex;
  final Value<int> visibleCharacterCount;
  final Value<int> latinWordCount;
  final Value<String> summary;
  final Value<int> createdAtEpochMilliseconds;
  final Value<int> updatedAtEpochMilliseconds;
  final Value<int?> deletedAtEpochMilliseconds;
  final Value<String?> originalNotebookName;
  final Value<int> rowid;
  const NotesCompanion({
    this.id = const Value.absent(),
    this.notebookId = const Value.absent(),
    this.title = const Value.absent(),
    this.kind = const Value.absent(),
    this.documentJson = const Value.absent(),
    this.markdownText = const Value.absent(),
    this.backgroundKey = const Value.absent(),
    this.sortIndex = const Value.absent(),
    this.visibleCharacterCount = const Value.absent(),
    this.latinWordCount = const Value.absent(),
    this.summary = const Value.absent(),
    this.createdAtEpochMilliseconds = const Value.absent(),
    this.updatedAtEpochMilliseconds = const Value.absent(),
    this.deletedAtEpochMilliseconds = const Value.absent(),
    this.originalNotebookName = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  NotesCompanion.insert({
    required String id,
    this.notebookId = const Value.absent(),
    required String title,
    required String kind,
    this.documentJson = const Value.absent(),
    this.markdownText = const Value.absent(),
    this.backgroundKey = const Value.absent(),
    required int sortIndex,
    required int visibleCharacterCount,
    required int latinWordCount,
    required String summary,
    required int createdAtEpochMilliseconds,
    required int updatedAtEpochMilliseconds,
    this.deletedAtEpochMilliseconds = const Value.absent(),
    this.originalNotebookName = const Value.absent(),
    this.rowid = const Value.absent(),
  })  : id = Value(id),
        title = Value(title),
        kind = Value(kind),
        sortIndex = Value(sortIndex),
        visibleCharacterCount = Value(visibleCharacterCount),
        latinWordCount = Value(latinWordCount),
        summary = Value(summary),
        createdAtEpochMilliseconds = Value(createdAtEpochMilliseconds),
        updatedAtEpochMilliseconds = Value(updatedAtEpochMilliseconds);
  static Insertable<NoteRow> custom({
    Expression<String>? id,
    Expression<String>? notebookId,
    Expression<String>? title,
    Expression<String>? kind,
    Expression<String>? documentJson,
    Expression<String>? markdownText,
    Expression<String>? backgroundKey,
    Expression<int>? sortIndex,
    Expression<int>? visibleCharacterCount,
    Expression<int>? latinWordCount,
    Expression<String>? summary,
    Expression<int>? createdAtEpochMilliseconds,
    Expression<int>? updatedAtEpochMilliseconds,
    Expression<int>? deletedAtEpochMilliseconds,
    Expression<String>? originalNotebookName,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (notebookId != null) 'notebook_id': notebookId,
      if (title != null) 'title': title,
      if (kind != null) 'kind': kind,
      if (documentJson != null) 'document_json': documentJson,
      if (markdownText != null) 'markdown_text': markdownText,
      if (backgroundKey != null) 'background_key': backgroundKey,
      if (sortIndex != null) 'sort_index': sortIndex,
      if (visibleCharacterCount != null)
        'visible_character_count': visibleCharacterCount,
      if (latinWordCount != null) 'latin_word_count': latinWordCount,
      if (summary != null) 'summary': summary,
      if (createdAtEpochMilliseconds != null)
        'created_at_epoch_milliseconds': createdAtEpochMilliseconds,
      if (updatedAtEpochMilliseconds != null)
        'updated_at_epoch_milliseconds': updatedAtEpochMilliseconds,
      if (deletedAtEpochMilliseconds != null)
        'deleted_at_epoch_milliseconds': deletedAtEpochMilliseconds,
      if (originalNotebookName != null)
        'original_notebook_name': originalNotebookName,
      if (rowid != null) 'rowid': rowid,
    });
  }

  NotesCompanion copyWith(
      {Value<String>? id,
      Value<String?>? notebookId,
      Value<String>? title,
      Value<String>? kind,
      Value<String?>? documentJson,
      Value<String?>? markdownText,
      Value<String?>? backgroundKey,
      Value<int>? sortIndex,
      Value<int>? visibleCharacterCount,
      Value<int>? latinWordCount,
      Value<String>? summary,
      Value<int>? createdAtEpochMilliseconds,
      Value<int>? updatedAtEpochMilliseconds,
      Value<int?>? deletedAtEpochMilliseconds,
      Value<String?>? originalNotebookName,
      Value<int>? rowid}) {
    return NotesCompanion(
      id: id ?? this.id,
      notebookId: notebookId ?? this.notebookId,
      title: title ?? this.title,
      kind: kind ?? this.kind,
      documentJson: documentJson ?? this.documentJson,
      markdownText: markdownText ?? this.markdownText,
      backgroundKey: backgroundKey ?? this.backgroundKey,
      sortIndex: sortIndex ?? this.sortIndex,
      visibleCharacterCount:
          visibleCharacterCount ?? this.visibleCharacterCount,
      latinWordCount: latinWordCount ?? this.latinWordCount,
      summary: summary ?? this.summary,
      createdAtEpochMilliseconds:
          createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
      updatedAtEpochMilliseconds:
          updatedAtEpochMilliseconds ?? this.updatedAtEpochMilliseconds,
      deletedAtEpochMilliseconds:
          deletedAtEpochMilliseconds ?? this.deletedAtEpochMilliseconds,
      originalNotebookName: originalNotebookName ?? this.originalNotebookName,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (notebookId.present) {
      map['notebook_id'] = Variable<String>(notebookId.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (documentJson.present) {
      map['document_json'] = Variable<String>(documentJson.value);
    }
    if (markdownText.present) {
      map['markdown_text'] = Variable<String>(markdownText.value);
    }
    if (backgroundKey.present) {
      map['background_key'] = Variable<String>(backgroundKey.value);
    }
    if (sortIndex.present) {
      map['sort_index'] = Variable<int>(sortIndex.value);
    }
    if (visibleCharacterCount.present) {
      map['visible_character_count'] =
          Variable<int>(visibleCharacterCount.value);
    }
    if (latinWordCount.present) {
      map['latin_word_count'] = Variable<int>(latinWordCount.value);
    }
    if (summary.present) {
      map['summary'] = Variable<String>(summary.value);
    }
    if (createdAtEpochMilliseconds.present) {
      map['created_at_epoch_milliseconds'] =
          Variable<int>(createdAtEpochMilliseconds.value);
    }
    if (updatedAtEpochMilliseconds.present) {
      map['updated_at_epoch_milliseconds'] =
          Variable<int>(updatedAtEpochMilliseconds.value);
    }
    if (deletedAtEpochMilliseconds.present) {
      map['deleted_at_epoch_milliseconds'] =
          Variable<int>(deletedAtEpochMilliseconds.value);
    }
    if (originalNotebookName.present) {
      map['original_notebook_name'] =
          Variable<String>(originalNotebookName.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('NotesCompanion(')
          ..write('id: $id, ')
          ..write('notebookId: $notebookId, ')
          ..write('title: $title, ')
          ..write('kind: $kind, ')
          ..write('documentJson: $documentJson, ')
          ..write('markdownText: $markdownText, ')
          ..write('backgroundKey: $backgroundKey, ')
          ..write('sortIndex: $sortIndex, ')
          ..write('visibleCharacterCount: $visibleCharacterCount, ')
          ..write('latinWordCount: $latinWordCount, ')
          ..write('summary: $summary, ')
          ..write('createdAtEpochMilliseconds: $createdAtEpochMilliseconds, ')
          ..write('updatedAtEpochMilliseconds: $updatedAtEpochMilliseconds, ')
          ..write('deletedAtEpochMilliseconds: $deletedAtEpochMilliseconds, ')
          ..write('originalNotebookName: $originalNotebookName, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $NoteRevisionsTable extends NoteRevisions
    with TableInfo<$NoteRevisionsTable, NoteRevisionRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $NoteRevisionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
      'id', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _noteIdMeta = const VerificationMeta('noteId');
  @override
  late final GeneratedColumn<String> noteId = GeneratedColumn<String>(
      'note_id', aliasedName, false,
      type: DriftSqlType.string,
      requiredDuringInsert: true,
      defaultConstraints: GeneratedColumn.constraintIsAlways(
          'REFERENCES notes (id) ON DELETE CASCADE'));
  static const VerificationMeta _reasonMeta = const VerificationMeta('reason');
  @override
  late final GeneratedColumn<String> reason = GeneratedColumn<String>(
      'reason', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
      'kind', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
      'title', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _documentJsonMeta =
      const VerificationMeta('documentJson');
  @override
  late final GeneratedColumn<String> documentJson = GeneratedColumn<String>(
      'document_json', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _markdownTextMeta =
      const VerificationMeta('markdownText');
  @override
  late final GeneratedColumn<String> markdownText = GeneratedColumn<String>(
      'markdown_text', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _createdAtEpochMillisecondsMeta =
      const VerificationMeta('createdAtEpochMilliseconds');
  @override
  late final GeneratedColumn<int> createdAtEpochMilliseconds =
      GeneratedColumn<int>('created_at_epoch_milliseconds', aliasedName, false,
          type: DriftSqlType.int, requiredDuringInsert: true);
  @override
  List<GeneratedColumn> get $columns => [
        id,
        noteId,
        reason,
        kind,
        title,
        documentJson,
        markdownText,
        createdAtEpochMilliseconds
      ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'note_revisions';
  @override
  VerificationContext validateIntegrity(Insertable<NoteRevisionRow> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('note_id')) {
      context.handle(_noteIdMeta,
          noteId.isAcceptableOrUnknown(data['note_id']!, _noteIdMeta));
    } else if (isInserting) {
      context.missing(_noteIdMeta);
    }
    if (data.containsKey('reason')) {
      context.handle(_reasonMeta,
          reason.isAcceptableOrUnknown(data['reason']!, _reasonMeta));
    } else if (isInserting) {
      context.missing(_reasonMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
          _kindMeta, kind.isAcceptableOrUnknown(data['kind']!, _kindMeta));
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
          _titleMeta, title.isAcceptableOrUnknown(data['title']!, _titleMeta));
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('document_json')) {
      context.handle(
          _documentJsonMeta,
          documentJson.isAcceptableOrUnknown(
              data['document_json']!, _documentJsonMeta));
    }
    if (data.containsKey('markdown_text')) {
      context.handle(
          _markdownTextMeta,
          markdownText.isAcceptableOrUnknown(
              data['markdown_text']!, _markdownTextMeta));
    }
    if (data.containsKey('created_at_epoch_milliseconds')) {
      context.handle(
          _createdAtEpochMillisecondsMeta,
          createdAtEpochMilliseconds.isAcceptableOrUnknown(
              data['created_at_epoch_milliseconds']!,
              _createdAtEpochMillisecondsMeta));
    } else if (isInserting) {
      context.missing(_createdAtEpochMillisecondsMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  NoteRevisionRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return NoteRevisionRow(
      id: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}id'])!,
      noteId: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}note_id'])!,
      reason: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}reason'])!,
      kind: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}kind'])!,
      title: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}title'])!,
      documentJson: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}document_json']),
      markdownText: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}markdown_text']),
      createdAtEpochMilliseconds: attachedDatabase.typeMapping.read(
          DriftSqlType.int,
          data['${effectivePrefix}created_at_epoch_milliseconds'])!,
    );
  }

  @override
  $NoteRevisionsTable createAlias(String alias) {
    return $NoteRevisionsTable(attachedDatabase, alias);
  }
}

class NoteRevisionRow extends DataClass implements Insertable<NoteRevisionRow> {
  final String id;
  final String noteId;
  final String reason;
  final String kind;
  final String title;
  final String? documentJson;
  final String? markdownText;
  final int createdAtEpochMilliseconds;
  const NoteRevisionRow(
      {required this.id,
      required this.noteId,
      required this.reason,
      required this.kind,
      required this.title,
      this.documentJson,
      this.markdownText,
      required this.createdAtEpochMilliseconds});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['note_id'] = Variable<String>(noteId);
    map['reason'] = Variable<String>(reason);
    map['kind'] = Variable<String>(kind);
    map['title'] = Variable<String>(title);
    if (!nullToAbsent || documentJson != null) {
      map['document_json'] = Variable<String>(documentJson);
    }
    if (!nullToAbsent || markdownText != null) {
      map['markdown_text'] = Variable<String>(markdownText);
    }
    map['created_at_epoch_milliseconds'] =
        Variable<int>(createdAtEpochMilliseconds);
    return map;
  }

  NoteRevisionsCompanion toCompanion(bool nullToAbsent) {
    return NoteRevisionsCompanion(
      id: Value(id),
      noteId: Value(noteId),
      reason: Value(reason),
      kind: Value(kind),
      title: Value(title),
      documentJson: documentJson == null && nullToAbsent
          ? const Value.absent()
          : Value(documentJson),
      markdownText: markdownText == null && nullToAbsent
          ? const Value.absent()
          : Value(markdownText),
      createdAtEpochMilliseconds: Value(createdAtEpochMilliseconds),
    );
  }

  factory NoteRevisionRow.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return NoteRevisionRow(
      id: serializer.fromJson<String>(json['id']),
      noteId: serializer.fromJson<String>(json['noteId']),
      reason: serializer.fromJson<String>(json['reason']),
      kind: serializer.fromJson<String>(json['kind']),
      title: serializer.fromJson<String>(json['title']),
      documentJson: serializer.fromJson<String?>(json['documentJson']),
      markdownText: serializer.fromJson<String?>(json['markdownText']),
      createdAtEpochMilliseconds:
          serializer.fromJson<int>(json['createdAtEpochMilliseconds']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'noteId': serializer.toJson<String>(noteId),
      'reason': serializer.toJson<String>(reason),
      'kind': serializer.toJson<String>(kind),
      'title': serializer.toJson<String>(title),
      'documentJson': serializer.toJson<String?>(documentJson),
      'markdownText': serializer.toJson<String?>(markdownText),
      'createdAtEpochMilliseconds':
          serializer.toJson<int>(createdAtEpochMilliseconds),
    };
  }

  NoteRevisionRow copyWith(
          {String? id,
          String? noteId,
          String? reason,
          String? kind,
          String? title,
          Value<String?> documentJson = const Value.absent(),
          Value<String?> markdownText = const Value.absent(),
          int? createdAtEpochMilliseconds}) =>
      NoteRevisionRow(
        id: id ?? this.id,
        noteId: noteId ?? this.noteId,
        reason: reason ?? this.reason,
        kind: kind ?? this.kind,
        title: title ?? this.title,
        documentJson:
            documentJson.present ? documentJson.value : this.documentJson,
        markdownText:
            markdownText.present ? markdownText.value : this.markdownText,
        createdAtEpochMilliseconds:
            createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
      );
  NoteRevisionRow copyWithCompanion(NoteRevisionsCompanion data) {
    return NoteRevisionRow(
      id: data.id.present ? data.id.value : this.id,
      noteId: data.noteId.present ? data.noteId.value : this.noteId,
      reason: data.reason.present ? data.reason.value : this.reason,
      kind: data.kind.present ? data.kind.value : this.kind,
      title: data.title.present ? data.title.value : this.title,
      documentJson: data.documentJson.present
          ? data.documentJson.value
          : this.documentJson,
      markdownText: data.markdownText.present
          ? data.markdownText.value
          : this.markdownText,
      createdAtEpochMilliseconds: data.createdAtEpochMilliseconds.present
          ? data.createdAtEpochMilliseconds.value
          : this.createdAtEpochMilliseconds,
    );
  }

  @override
  String toString() {
    return (StringBuffer('NoteRevisionRow(')
          ..write('id: $id, ')
          ..write('noteId: $noteId, ')
          ..write('reason: $reason, ')
          ..write('kind: $kind, ')
          ..write('title: $title, ')
          ..write('documentJson: $documentJson, ')
          ..write('markdownText: $markdownText, ')
          ..write('createdAtEpochMilliseconds: $createdAtEpochMilliseconds')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, noteId, reason, kind, title, documentJson,
      markdownText, createdAtEpochMilliseconds);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is NoteRevisionRow &&
          other.id == this.id &&
          other.noteId == this.noteId &&
          other.reason == this.reason &&
          other.kind == this.kind &&
          other.title == this.title &&
          other.documentJson == this.documentJson &&
          other.markdownText == this.markdownText &&
          other.createdAtEpochMilliseconds == this.createdAtEpochMilliseconds);
}

class NoteRevisionsCompanion extends UpdateCompanion<NoteRevisionRow> {
  final Value<String> id;
  final Value<String> noteId;
  final Value<String> reason;
  final Value<String> kind;
  final Value<String> title;
  final Value<String?> documentJson;
  final Value<String?> markdownText;
  final Value<int> createdAtEpochMilliseconds;
  final Value<int> rowid;
  const NoteRevisionsCompanion({
    this.id = const Value.absent(),
    this.noteId = const Value.absent(),
    this.reason = const Value.absent(),
    this.kind = const Value.absent(),
    this.title = const Value.absent(),
    this.documentJson = const Value.absent(),
    this.markdownText = const Value.absent(),
    this.createdAtEpochMilliseconds = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  NoteRevisionsCompanion.insert({
    required String id,
    required String noteId,
    required String reason,
    required String kind,
    required String title,
    this.documentJson = const Value.absent(),
    this.markdownText = const Value.absent(),
    required int createdAtEpochMilliseconds,
    this.rowid = const Value.absent(),
  })  : id = Value(id),
        noteId = Value(noteId),
        reason = Value(reason),
        kind = Value(kind),
        title = Value(title),
        createdAtEpochMilliseconds = Value(createdAtEpochMilliseconds);
  static Insertable<NoteRevisionRow> custom({
    Expression<String>? id,
    Expression<String>? noteId,
    Expression<String>? reason,
    Expression<String>? kind,
    Expression<String>? title,
    Expression<String>? documentJson,
    Expression<String>? markdownText,
    Expression<int>? createdAtEpochMilliseconds,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (noteId != null) 'note_id': noteId,
      if (reason != null) 'reason': reason,
      if (kind != null) 'kind': kind,
      if (title != null) 'title': title,
      if (documentJson != null) 'document_json': documentJson,
      if (markdownText != null) 'markdown_text': markdownText,
      if (createdAtEpochMilliseconds != null)
        'created_at_epoch_milliseconds': createdAtEpochMilliseconds,
      if (rowid != null) 'rowid': rowid,
    });
  }

  NoteRevisionsCompanion copyWith(
      {Value<String>? id,
      Value<String>? noteId,
      Value<String>? reason,
      Value<String>? kind,
      Value<String>? title,
      Value<String?>? documentJson,
      Value<String?>? markdownText,
      Value<int>? createdAtEpochMilliseconds,
      Value<int>? rowid}) {
    return NoteRevisionsCompanion(
      id: id ?? this.id,
      noteId: noteId ?? this.noteId,
      reason: reason ?? this.reason,
      kind: kind ?? this.kind,
      title: title ?? this.title,
      documentJson: documentJson ?? this.documentJson,
      markdownText: markdownText ?? this.markdownText,
      createdAtEpochMilliseconds:
          createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (noteId.present) {
      map['note_id'] = Variable<String>(noteId.value);
    }
    if (reason.present) {
      map['reason'] = Variable<String>(reason.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (documentJson.present) {
      map['document_json'] = Variable<String>(documentJson.value);
    }
    if (markdownText.present) {
      map['markdown_text'] = Variable<String>(markdownText.value);
    }
    if (createdAtEpochMilliseconds.present) {
      map['created_at_epoch_milliseconds'] =
          Variable<int>(createdAtEpochMilliseconds.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('NoteRevisionsCompanion(')
          ..write('id: $id, ')
          ..write('noteId: $noteId, ')
          ..write('reason: $reason, ')
          ..write('kind: $kind, ')
          ..write('title: $title, ')
          ..write('documentJson: $documentJson, ')
          ..write('markdownText: $markdownText, ')
          ..write('createdAtEpochMilliseconds: $createdAtEpochMilliseconds, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $AttachmentsTable extends Attachments
    with TableInfo<$AttachmentsTable, AttachmentRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AttachmentsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
      'id', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
      'kind', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _mimeTypeMeta =
      const VerificationMeta('mimeType');
  @override
  late final GeneratedColumn<String> mimeType = GeneratedColumn<String>(
      'mime_type', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _originalFileNameMeta =
      const VerificationMeta('originalFileName');
  @override
  late final GeneratedColumn<String> originalFileName = GeneratedColumn<String>(
      'original_file_name', aliasedName, true,
      type: DriftSqlType.string, requiredDuringInsert: false);
  static const VerificationMeta _relativePathMeta =
      const VerificationMeta('relativePath');
  @override
  late final GeneratedColumn<String> relativePath = GeneratedColumn<String>(
      'relative_path', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _byteSizeMeta =
      const VerificationMeta('byteSize');
  @override
  late final GeneratedColumn<int> byteSize = GeneratedColumn<int>(
      'byte_size', aliasedName, false,
      type: DriftSqlType.int, requiredDuringInsert: true);
  static const VerificationMeta _widthPixelsMeta =
      const VerificationMeta('widthPixels');
  @override
  late final GeneratedColumn<int> widthPixels = GeneratedColumn<int>(
      'width_pixels', aliasedName, true,
      type: DriftSqlType.int, requiredDuringInsert: false);
  static const VerificationMeta _heightPixelsMeta =
      const VerificationMeta('heightPixels');
  @override
  late final GeneratedColumn<int> heightPixels = GeneratedColumn<int>(
      'height_pixels', aliasedName, true,
      type: DriftSqlType.int, requiredDuringInsert: false);
  static const VerificationMeta _createdAtEpochMillisecondsMeta =
      const VerificationMeta('createdAtEpochMilliseconds');
  @override
  late final GeneratedColumn<int> createdAtEpochMilliseconds =
      GeneratedColumn<int>('created_at_epoch_milliseconds', aliasedName, false,
          type: DriftSqlType.int, requiredDuringInsert: true);
  @override
  List<GeneratedColumn> get $columns => [
        id,
        kind,
        mimeType,
        originalFileName,
        relativePath,
        byteSize,
        widthPixels,
        heightPixels,
        createdAtEpochMilliseconds
      ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'attachments';
  @override
  VerificationContext validateIntegrity(Insertable<AttachmentRow> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
          _kindMeta, kind.isAcceptableOrUnknown(data['kind']!, _kindMeta));
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('mime_type')) {
      context.handle(_mimeTypeMeta,
          mimeType.isAcceptableOrUnknown(data['mime_type']!, _mimeTypeMeta));
    } else if (isInserting) {
      context.missing(_mimeTypeMeta);
    }
    if (data.containsKey('original_file_name')) {
      context.handle(
          _originalFileNameMeta,
          originalFileName.isAcceptableOrUnknown(
              data['original_file_name']!, _originalFileNameMeta));
    }
    if (data.containsKey('relative_path')) {
      context.handle(
          _relativePathMeta,
          relativePath.isAcceptableOrUnknown(
              data['relative_path']!, _relativePathMeta));
    } else if (isInserting) {
      context.missing(_relativePathMeta);
    }
    if (data.containsKey('byte_size')) {
      context.handle(_byteSizeMeta,
          byteSize.isAcceptableOrUnknown(data['byte_size']!, _byteSizeMeta));
    } else if (isInserting) {
      context.missing(_byteSizeMeta);
    }
    if (data.containsKey('width_pixels')) {
      context.handle(
          _widthPixelsMeta,
          widthPixels.isAcceptableOrUnknown(
              data['width_pixels']!, _widthPixelsMeta));
    }
    if (data.containsKey('height_pixels')) {
      context.handle(
          _heightPixelsMeta,
          heightPixels.isAcceptableOrUnknown(
              data['height_pixels']!, _heightPixelsMeta));
    }
    if (data.containsKey('created_at_epoch_milliseconds')) {
      context.handle(
          _createdAtEpochMillisecondsMeta,
          createdAtEpochMilliseconds.isAcceptableOrUnknown(
              data['created_at_epoch_milliseconds']!,
              _createdAtEpochMillisecondsMeta));
    } else if (isInserting) {
      context.missing(_createdAtEpochMillisecondsMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  AttachmentRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return AttachmentRow(
      id: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}id'])!,
      kind: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}kind'])!,
      mimeType: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}mime_type'])!,
      originalFileName: attachedDatabase.typeMapping.read(
          DriftSqlType.string, data['${effectivePrefix}original_file_name']),
      relativePath: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}relative_path'])!,
      byteSize: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}byte_size'])!,
      widthPixels: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}width_pixels']),
      heightPixels: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}height_pixels']),
      createdAtEpochMilliseconds: attachedDatabase.typeMapping.read(
          DriftSqlType.int,
          data['${effectivePrefix}created_at_epoch_milliseconds'])!,
    );
  }

  @override
  $AttachmentsTable createAlias(String alias) {
    return $AttachmentsTable(attachedDatabase, alias);
  }
}

class AttachmentRow extends DataClass implements Insertable<AttachmentRow> {
  final String id;
  final String kind;
  final String mimeType;
  final String? originalFileName;
  final String relativePath;
  final int byteSize;
  final int? widthPixels;
  final int? heightPixels;
  final int createdAtEpochMilliseconds;
  const AttachmentRow(
      {required this.id,
      required this.kind,
      required this.mimeType,
      this.originalFileName,
      required this.relativePath,
      required this.byteSize,
      this.widthPixels,
      this.heightPixels,
      required this.createdAtEpochMilliseconds});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['kind'] = Variable<String>(kind);
    map['mime_type'] = Variable<String>(mimeType);
    if (!nullToAbsent || originalFileName != null) {
      map['original_file_name'] = Variable<String>(originalFileName);
    }
    map['relative_path'] = Variable<String>(relativePath);
    map['byte_size'] = Variable<int>(byteSize);
    if (!nullToAbsent || widthPixels != null) {
      map['width_pixels'] = Variable<int>(widthPixels);
    }
    if (!nullToAbsent || heightPixels != null) {
      map['height_pixels'] = Variable<int>(heightPixels);
    }
    map['created_at_epoch_milliseconds'] =
        Variable<int>(createdAtEpochMilliseconds);
    return map;
  }

  AttachmentsCompanion toCompanion(bool nullToAbsent) {
    return AttachmentsCompanion(
      id: Value(id),
      kind: Value(kind),
      mimeType: Value(mimeType),
      originalFileName: originalFileName == null && nullToAbsent
          ? const Value.absent()
          : Value(originalFileName),
      relativePath: Value(relativePath),
      byteSize: Value(byteSize),
      widthPixels: widthPixels == null && nullToAbsent
          ? const Value.absent()
          : Value(widthPixels),
      heightPixels: heightPixels == null && nullToAbsent
          ? const Value.absent()
          : Value(heightPixels),
      createdAtEpochMilliseconds: Value(createdAtEpochMilliseconds),
    );
  }

  factory AttachmentRow.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return AttachmentRow(
      id: serializer.fromJson<String>(json['id']),
      kind: serializer.fromJson<String>(json['kind']),
      mimeType: serializer.fromJson<String>(json['mimeType']),
      originalFileName: serializer.fromJson<String?>(json['originalFileName']),
      relativePath: serializer.fromJson<String>(json['relativePath']),
      byteSize: serializer.fromJson<int>(json['byteSize']),
      widthPixels: serializer.fromJson<int?>(json['widthPixels']),
      heightPixels: serializer.fromJson<int?>(json['heightPixels']),
      createdAtEpochMilliseconds:
          serializer.fromJson<int>(json['createdAtEpochMilliseconds']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'kind': serializer.toJson<String>(kind),
      'mimeType': serializer.toJson<String>(mimeType),
      'originalFileName': serializer.toJson<String?>(originalFileName),
      'relativePath': serializer.toJson<String>(relativePath),
      'byteSize': serializer.toJson<int>(byteSize),
      'widthPixels': serializer.toJson<int?>(widthPixels),
      'heightPixels': serializer.toJson<int?>(heightPixels),
      'createdAtEpochMilliseconds':
          serializer.toJson<int>(createdAtEpochMilliseconds),
    };
  }

  AttachmentRow copyWith(
          {String? id,
          String? kind,
          String? mimeType,
          Value<String?> originalFileName = const Value.absent(),
          String? relativePath,
          int? byteSize,
          Value<int?> widthPixels = const Value.absent(),
          Value<int?> heightPixels = const Value.absent(),
          int? createdAtEpochMilliseconds}) =>
      AttachmentRow(
        id: id ?? this.id,
        kind: kind ?? this.kind,
        mimeType: mimeType ?? this.mimeType,
        originalFileName: originalFileName.present
            ? originalFileName.value
            : this.originalFileName,
        relativePath: relativePath ?? this.relativePath,
        byteSize: byteSize ?? this.byteSize,
        widthPixels: widthPixels.present ? widthPixels.value : this.widthPixels,
        heightPixels:
            heightPixels.present ? heightPixels.value : this.heightPixels,
        createdAtEpochMilliseconds:
            createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
      );
  AttachmentRow copyWithCompanion(AttachmentsCompanion data) {
    return AttachmentRow(
      id: data.id.present ? data.id.value : this.id,
      kind: data.kind.present ? data.kind.value : this.kind,
      mimeType: data.mimeType.present ? data.mimeType.value : this.mimeType,
      originalFileName: data.originalFileName.present
          ? data.originalFileName.value
          : this.originalFileName,
      relativePath: data.relativePath.present
          ? data.relativePath.value
          : this.relativePath,
      byteSize: data.byteSize.present ? data.byteSize.value : this.byteSize,
      widthPixels:
          data.widthPixels.present ? data.widthPixels.value : this.widthPixels,
      heightPixels: data.heightPixels.present
          ? data.heightPixels.value
          : this.heightPixels,
      createdAtEpochMilliseconds: data.createdAtEpochMilliseconds.present
          ? data.createdAtEpochMilliseconds.value
          : this.createdAtEpochMilliseconds,
    );
  }

  @override
  String toString() {
    return (StringBuffer('AttachmentRow(')
          ..write('id: $id, ')
          ..write('kind: $kind, ')
          ..write('mimeType: $mimeType, ')
          ..write('originalFileName: $originalFileName, ')
          ..write('relativePath: $relativePath, ')
          ..write('byteSize: $byteSize, ')
          ..write('widthPixels: $widthPixels, ')
          ..write('heightPixels: $heightPixels, ')
          ..write('createdAtEpochMilliseconds: $createdAtEpochMilliseconds')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
      id,
      kind,
      mimeType,
      originalFileName,
      relativePath,
      byteSize,
      widthPixels,
      heightPixels,
      createdAtEpochMilliseconds);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is AttachmentRow &&
          other.id == this.id &&
          other.kind == this.kind &&
          other.mimeType == this.mimeType &&
          other.originalFileName == this.originalFileName &&
          other.relativePath == this.relativePath &&
          other.byteSize == this.byteSize &&
          other.widthPixels == this.widthPixels &&
          other.heightPixels == this.heightPixels &&
          other.createdAtEpochMilliseconds == this.createdAtEpochMilliseconds);
}

class AttachmentsCompanion extends UpdateCompanion<AttachmentRow> {
  final Value<String> id;
  final Value<String> kind;
  final Value<String> mimeType;
  final Value<String?> originalFileName;
  final Value<String> relativePath;
  final Value<int> byteSize;
  final Value<int?> widthPixels;
  final Value<int?> heightPixels;
  final Value<int> createdAtEpochMilliseconds;
  final Value<int> rowid;
  const AttachmentsCompanion({
    this.id = const Value.absent(),
    this.kind = const Value.absent(),
    this.mimeType = const Value.absent(),
    this.originalFileName = const Value.absent(),
    this.relativePath = const Value.absent(),
    this.byteSize = const Value.absent(),
    this.widthPixels = const Value.absent(),
    this.heightPixels = const Value.absent(),
    this.createdAtEpochMilliseconds = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  AttachmentsCompanion.insert({
    required String id,
    required String kind,
    required String mimeType,
    this.originalFileName = const Value.absent(),
    required String relativePath,
    required int byteSize,
    this.widthPixels = const Value.absent(),
    this.heightPixels = const Value.absent(),
    required int createdAtEpochMilliseconds,
    this.rowid = const Value.absent(),
  })  : id = Value(id),
        kind = Value(kind),
        mimeType = Value(mimeType),
        relativePath = Value(relativePath),
        byteSize = Value(byteSize),
        createdAtEpochMilliseconds = Value(createdAtEpochMilliseconds);
  static Insertable<AttachmentRow> custom({
    Expression<String>? id,
    Expression<String>? kind,
    Expression<String>? mimeType,
    Expression<String>? originalFileName,
    Expression<String>? relativePath,
    Expression<int>? byteSize,
    Expression<int>? widthPixels,
    Expression<int>? heightPixels,
    Expression<int>? createdAtEpochMilliseconds,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (kind != null) 'kind': kind,
      if (mimeType != null) 'mime_type': mimeType,
      if (originalFileName != null) 'original_file_name': originalFileName,
      if (relativePath != null) 'relative_path': relativePath,
      if (byteSize != null) 'byte_size': byteSize,
      if (widthPixels != null) 'width_pixels': widthPixels,
      if (heightPixels != null) 'height_pixels': heightPixels,
      if (createdAtEpochMilliseconds != null)
        'created_at_epoch_milliseconds': createdAtEpochMilliseconds,
      if (rowid != null) 'rowid': rowid,
    });
  }

  AttachmentsCompanion copyWith(
      {Value<String>? id,
      Value<String>? kind,
      Value<String>? mimeType,
      Value<String?>? originalFileName,
      Value<String>? relativePath,
      Value<int>? byteSize,
      Value<int?>? widthPixels,
      Value<int?>? heightPixels,
      Value<int>? createdAtEpochMilliseconds,
      Value<int>? rowid}) {
    return AttachmentsCompanion(
      id: id ?? this.id,
      kind: kind ?? this.kind,
      mimeType: mimeType ?? this.mimeType,
      originalFileName: originalFileName ?? this.originalFileName,
      relativePath: relativePath ?? this.relativePath,
      byteSize: byteSize ?? this.byteSize,
      widthPixels: widthPixels ?? this.widthPixels,
      heightPixels: heightPixels ?? this.heightPixels,
      createdAtEpochMilliseconds:
          createdAtEpochMilliseconds ?? this.createdAtEpochMilliseconds,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (mimeType.present) {
      map['mime_type'] = Variable<String>(mimeType.value);
    }
    if (originalFileName.present) {
      map['original_file_name'] = Variable<String>(originalFileName.value);
    }
    if (relativePath.present) {
      map['relative_path'] = Variable<String>(relativePath.value);
    }
    if (byteSize.present) {
      map['byte_size'] = Variable<int>(byteSize.value);
    }
    if (widthPixels.present) {
      map['width_pixels'] = Variable<int>(widthPixels.value);
    }
    if (heightPixels.present) {
      map['height_pixels'] = Variable<int>(heightPixels.value);
    }
    if (createdAtEpochMilliseconds.present) {
      map['created_at_epoch_milliseconds'] =
          Variable<int>(createdAtEpochMilliseconds.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AttachmentsCompanion(')
          ..write('id: $id, ')
          ..write('kind: $kind, ')
          ..write('mimeType: $mimeType, ')
          ..write('originalFileName: $originalFileName, ')
          ..write('relativePath: $relativePath, ')
          ..write('byteSize: $byteSize, ')
          ..write('widthPixels: $widthPixels, ')
          ..write('heightPixels: $heightPixels, ')
          ..write('createdAtEpochMilliseconds: $createdAtEpochMilliseconds, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $SearchHistoryEntriesTable extends SearchHistoryEntries
    with TableInfo<$SearchHistoryEntriesTable, SearchHistoryRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SearchHistoryEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _queryMeta = const VerificationMeta('query');
  @override
  late final GeneratedColumn<String> query = GeneratedColumn<String>(
      'query', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _usedAtEpochMillisecondsMeta =
      const VerificationMeta('usedAtEpochMilliseconds');
  @override
  late final GeneratedColumn<int> usedAtEpochMilliseconds =
      GeneratedColumn<int>('used_at_epoch_milliseconds', aliasedName, false,
          type: DriftSqlType.int, requiredDuringInsert: true);
  @override
  List<GeneratedColumn> get $columns => [query, usedAtEpochMilliseconds];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'search_history';
  @override
  VerificationContext validateIntegrity(Insertable<SearchHistoryRow> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('query')) {
      context.handle(
          _queryMeta, query.isAcceptableOrUnknown(data['query']!, _queryMeta));
    } else if (isInserting) {
      context.missing(_queryMeta);
    }
    if (data.containsKey('used_at_epoch_milliseconds')) {
      context.handle(
          _usedAtEpochMillisecondsMeta,
          usedAtEpochMilliseconds.isAcceptableOrUnknown(
              data['used_at_epoch_milliseconds']!,
              _usedAtEpochMillisecondsMeta));
    } else if (isInserting) {
      context.missing(_usedAtEpochMillisecondsMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {query};
  @override
  SearchHistoryRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return SearchHistoryRow(
      query: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}query'])!,
      usedAtEpochMilliseconds: attachedDatabase.typeMapping.read(
          DriftSqlType.int,
          data['${effectivePrefix}used_at_epoch_milliseconds'])!,
    );
  }

  @override
  $SearchHistoryEntriesTable createAlias(String alias) {
    return $SearchHistoryEntriesTable(attachedDatabase, alias);
  }
}

class SearchHistoryRow extends DataClass
    implements Insertable<SearchHistoryRow> {
  final String query;
  final int usedAtEpochMilliseconds;
  const SearchHistoryRow(
      {required this.query, required this.usedAtEpochMilliseconds});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['query'] = Variable<String>(query);
    map['used_at_epoch_milliseconds'] = Variable<int>(usedAtEpochMilliseconds);
    return map;
  }

  SearchHistoryEntriesCompanion toCompanion(bool nullToAbsent) {
    return SearchHistoryEntriesCompanion(
      query: Value(query),
      usedAtEpochMilliseconds: Value(usedAtEpochMilliseconds),
    );
  }

  factory SearchHistoryRow.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return SearchHistoryRow(
      query: serializer.fromJson<String>(json['query']),
      usedAtEpochMilliseconds:
          serializer.fromJson<int>(json['usedAtEpochMilliseconds']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'query': serializer.toJson<String>(query),
      'usedAtEpochMilliseconds':
          serializer.toJson<int>(usedAtEpochMilliseconds),
    };
  }

  SearchHistoryRow copyWith({String? query, int? usedAtEpochMilliseconds}) =>
      SearchHistoryRow(
        query: query ?? this.query,
        usedAtEpochMilliseconds:
            usedAtEpochMilliseconds ?? this.usedAtEpochMilliseconds,
      );
  SearchHistoryRow copyWithCompanion(SearchHistoryEntriesCompanion data) {
    return SearchHistoryRow(
      query: data.query.present ? data.query.value : this.query,
      usedAtEpochMilliseconds: data.usedAtEpochMilliseconds.present
          ? data.usedAtEpochMilliseconds.value
          : this.usedAtEpochMilliseconds,
    );
  }

  @override
  String toString() {
    return (StringBuffer('SearchHistoryRow(')
          ..write('query: $query, ')
          ..write('usedAtEpochMilliseconds: $usedAtEpochMilliseconds')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(query, usedAtEpochMilliseconds);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is SearchHistoryRow &&
          other.query == this.query &&
          other.usedAtEpochMilliseconds == this.usedAtEpochMilliseconds);
}

class SearchHistoryEntriesCompanion extends UpdateCompanion<SearchHistoryRow> {
  final Value<String> query;
  final Value<int> usedAtEpochMilliseconds;
  final Value<int> rowid;
  const SearchHistoryEntriesCompanion({
    this.query = const Value.absent(),
    this.usedAtEpochMilliseconds = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  SearchHistoryEntriesCompanion.insert({
    required String query,
    required int usedAtEpochMilliseconds,
    this.rowid = const Value.absent(),
  })  : query = Value(query),
        usedAtEpochMilliseconds = Value(usedAtEpochMilliseconds);
  static Insertable<SearchHistoryRow> custom({
    Expression<String>? query,
    Expression<int>? usedAtEpochMilliseconds,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (query != null) 'query': query,
      if (usedAtEpochMilliseconds != null)
        'used_at_epoch_milliseconds': usedAtEpochMilliseconds,
      if (rowid != null) 'rowid': rowid,
    });
  }

  SearchHistoryEntriesCompanion copyWith(
      {Value<String>? query,
      Value<int>? usedAtEpochMilliseconds,
      Value<int>? rowid}) {
    return SearchHistoryEntriesCompanion(
      query: query ?? this.query,
      usedAtEpochMilliseconds:
          usedAtEpochMilliseconds ?? this.usedAtEpochMilliseconds,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (query.present) {
      map['query'] = Variable<String>(query.value);
    }
    if (usedAtEpochMilliseconds.present) {
      map['used_at_epoch_milliseconds'] =
          Variable<int>(usedAtEpochMilliseconds.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SearchHistoryEntriesCompanion(')
          ..write('query: $query, ')
          ..write('usedAtEpochMilliseconds: $usedAtEpochMilliseconds, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $AppSettingsEntriesTable extends AppSettingsEntries
    with TableInfo<$AppSettingsEntriesTable, AppSettingsRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AppSettingsEntriesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _singletonIdMeta =
      const VerificationMeta('singletonId');
  @override
  late final GeneratedColumn<int> singletonId = GeneratedColumn<int>(
      'singleton_id', aliasedName, false,
      type: DriftSqlType.int,
      requiredDuringInsert: false,
      defaultValue: const Constant<int>(1));
  static const VerificationMeta _defaultBackgroundKeyMeta =
      const VerificationMeta('defaultBackgroundKey');
  @override
  late final GeneratedColumn<String> defaultBackgroundKey =
      GeneratedColumn<String>('default_background_key', aliasedName, false,
          type: DriftSqlType.string, requiredDuringInsert: true);
  static const VerificationMeta _themeModeMeta =
      const VerificationMeta('themeMode');
  @override
  late final GeneratedColumn<String> themeMode = GeneratedColumn<String>(
      'theme_mode', aliasedName, false,
      type: DriftSqlType.string, requiredDuringInsert: true);
  @override
  List<GeneratedColumn> get $columns =>
      [singletonId, defaultBackgroundKey, themeMode];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'app_settings';
  @override
  VerificationContext validateIntegrity(Insertable<AppSettingsRow> instance,
      {bool isInserting = false}) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('singleton_id')) {
      context.handle(
          _singletonIdMeta,
          singletonId.isAcceptableOrUnknown(
              data['singleton_id']!, _singletonIdMeta));
    }
    if (data.containsKey('default_background_key')) {
      context.handle(
          _defaultBackgroundKeyMeta,
          defaultBackgroundKey.isAcceptableOrUnknown(
              data['default_background_key']!, _defaultBackgroundKeyMeta));
    } else if (isInserting) {
      context.missing(_defaultBackgroundKeyMeta);
    }
    if (data.containsKey('theme_mode')) {
      context.handle(_themeModeMeta,
          themeMode.isAcceptableOrUnknown(data['theme_mode']!, _themeModeMeta));
    } else if (isInserting) {
      context.missing(_themeModeMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {singletonId};
  @override
  AppSettingsRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return AppSettingsRow(
      singletonId: attachedDatabase.typeMapping
          .read(DriftSqlType.int, data['${effectivePrefix}singleton_id'])!,
      defaultBackgroundKey: attachedDatabase.typeMapping.read(
          DriftSqlType.string,
          data['${effectivePrefix}default_background_key'])!,
      themeMode: attachedDatabase.typeMapping
          .read(DriftSqlType.string, data['${effectivePrefix}theme_mode'])!,
    );
  }

  @override
  $AppSettingsEntriesTable createAlias(String alias) {
    return $AppSettingsEntriesTable(attachedDatabase, alias);
  }
}

class AppSettingsRow extends DataClass implements Insertable<AppSettingsRow> {
  final int singletonId;
  final String defaultBackgroundKey;
  final String themeMode;
  const AppSettingsRow(
      {required this.singletonId,
      required this.defaultBackgroundKey,
      required this.themeMode});
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['singleton_id'] = Variable<int>(singletonId);
    map['default_background_key'] = Variable<String>(defaultBackgroundKey);
    map['theme_mode'] = Variable<String>(themeMode);
    return map;
  }

  AppSettingsEntriesCompanion toCompanion(bool nullToAbsent) {
    return AppSettingsEntriesCompanion(
      singletonId: Value(singletonId),
      defaultBackgroundKey: Value(defaultBackgroundKey),
      themeMode: Value(themeMode),
    );
  }

  factory AppSettingsRow.fromJson(Map<String, dynamic> json,
      {ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return AppSettingsRow(
      singletonId: serializer.fromJson<int>(json['singletonId']),
      defaultBackgroundKey:
          serializer.fromJson<String>(json['defaultBackgroundKey']),
      themeMode: serializer.fromJson<String>(json['themeMode']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'singletonId': serializer.toJson<int>(singletonId),
      'defaultBackgroundKey': serializer.toJson<String>(defaultBackgroundKey),
      'themeMode': serializer.toJson<String>(themeMode),
    };
  }

  AppSettingsRow copyWith(
          {int? singletonId,
          String? defaultBackgroundKey,
          String? themeMode}) =>
      AppSettingsRow(
        singletonId: singletonId ?? this.singletonId,
        defaultBackgroundKey: defaultBackgroundKey ?? this.defaultBackgroundKey,
        themeMode: themeMode ?? this.themeMode,
      );
  AppSettingsRow copyWithCompanion(AppSettingsEntriesCompanion data) {
    return AppSettingsRow(
      singletonId:
          data.singletonId.present ? data.singletonId.value : this.singletonId,
      defaultBackgroundKey: data.defaultBackgroundKey.present
          ? data.defaultBackgroundKey.value
          : this.defaultBackgroundKey,
      themeMode: data.themeMode.present ? data.themeMode.value : this.themeMode,
    );
  }

  @override
  String toString() {
    return (StringBuffer('AppSettingsRow(')
          ..write('singletonId: $singletonId, ')
          ..write('defaultBackgroundKey: $defaultBackgroundKey, ')
          ..write('themeMode: $themeMode')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(singletonId, defaultBackgroundKey, themeMode);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is AppSettingsRow &&
          other.singletonId == this.singletonId &&
          other.defaultBackgroundKey == this.defaultBackgroundKey &&
          other.themeMode == this.themeMode);
}

class AppSettingsEntriesCompanion extends UpdateCompanion<AppSettingsRow> {
  final Value<int> singletonId;
  final Value<String> defaultBackgroundKey;
  final Value<String> themeMode;
  const AppSettingsEntriesCompanion({
    this.singletonId = const Value.absent(),
    this.defaultBackgroundKey = const Value.absent(),
    this.themeMode = const Value.absent(),
  });
  AppSettingsEntriesCompanion.insert({
    this.singletonId = const Value.absent(),
    required String defaultBackgroundKey,
    required String themeMode,
  })  : defaultBackgroundKey = Value(defaultBackgroundKey),
        themeMode = Value(themeMode);
  static Insertable<AppSettingsRow> custom({
    Expression<int>? singletonId,
    Expression<String>? defaultBackgroundKey,
    Expression<String>? themeMode,
  }) {
    return RawValuesInsertable({
      if (singletonId != null) 'singleton_id': singletonId,
      if (defaultBackgroundKey != null)
        'default_background_key': defaultBackgroundKey,
      if (themeMode != null) 'theme_mode': themeMode,
    });
  }

  AppSettingsEntriesCompanion copyWith(
      {Value<int>? singletonId,
      Value<String>? defaultBackgroundKey,
      Value<String>? themeMode}) {
    return AppSettingsEntriesCompanion(
      singletonId: singletonId ?? this.singletonId,
      defaultBackgroundKey: defaultBackgroundKey ?? this.defaultBackgroundKey,
      themeMode: themeMode ?? this.themeMode,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (singletonId.present) {
      map['singleton_id'] = Variable<int>(singletonId.value);
    }
    if (defaultBackgroundKey.present) {
      map['default_background_key'] =
          Variable<String>(defaultBackgroundKey.value);
    }
    if (themeMode.present) {
      map['theme_mode'] = Variable<String>(themeMode.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AppSettingsEntriesCompanion(')
          ..write('singletonId: $singletonId, ')
          ..write('defaultBackgroundKey: $defaultBackgroundKey, ')
          ..write('themeMode: $themeMode')
          ..write(')'))
        .toString();
  }
}

abstract class _$XNoteDatabase extends GeneratedDatabase {
  _$XNoteDatabase(QueryExecutor e) : super(e);
  $XNoteDatabaseManager get managers => $XNoteDatabaseManager(this);
  late final $NotebooksTable notebooks = $NotebooksTable(this);
  late final $NotesTable notes = $NotesTable(this);
  late final $NoteRevisionsTable noteRevisions = $NoteRevisionsTable(this);
  late final $AttachmentsTable attachments = $AttachmentsTable(this);
  late final $SearchHistoryEntriesTable searchHistoryEntries =
      $SearchHistoryEntriesTable(this);
  late final $AppSettingsEntriesTable appSettingsEntries =
      $AppSettingsEntriesTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
        notebooks,
        notes,
        noteRevisions,
        attachments,
        searchHistoryEntries,
        appSettingsEntries
      ];
  @override
  StreamQueryUpdateRules get streamUpdateRules => const StreamQueryUpdateRules(
        [
          WritePropagation(
            on: TableUpdateQuery.onTableName('notebooks',
                limitUpdateKind: UpdateKind.delete),
            result: [
              TableUpdate('notes', kind: UpdateKind.update),
            ],
          ),
          WritePropagation(
            on: TableUpdateQuery.onTableName('notes',
                limitUpdateKind: UpdateKind.delete),
            result: [
              TableUpdate('note_revisions', kind: UpdateKind.delete),
            ],
          ),
        ],
      );
}

typedef $$NotebooksTableCreateCompanionBuilder = NotebooksCompanion Function({
  required String id,
  required String name,
  required int sortIndex,
  required int createdAtEpochMilliseconds,
  required int updatedAtEpochMilliseconds,
  Value<int> rowid,
});
typedef $$NotebooksTableUpdateCompanionBuilder = NotebooksCompanion Function({
  Value<String> id,
  Value<String> name,
  Value<int> sortIndex,
  Value<int> createdAtEpochMilliseconds,
  Value<int> updatedAtEpochMilliseconds,
  Value<int> rowid,
});

final class $$NotebooksTableReferences
    extends BaseReferences<_$XNoteDatabase, $NotebooksTable, NotebookRow> {
  $$NotebooksTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static MultiTypedResultKey<$NotesTable, List<NoteRow>> _notesRefsTable(
          _$XNoteDatabase db) =>
      MultiTypedResultKey.fromTable(db.notes,
          aliasName: 'notebooks__id__notes__notebook_id');

  $$NotesTableProcessedTableManager get notesRefs {
    final manager = $$NotesTableTableManager($_db, $_db.notes)
        .filter((f) => f.notebookId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_notesRefsTable($_db));
    return ProcessedTableManager(
        manager.$state.copyWith(prefetchedData: cache));
  }
}

class $$NotebooksTableFilterComposer
    extends Composer<_$XNoteDatabase, $NotebooksTable> {
  $$NotebooksTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get name => $composableBuilder(
      column: $table.name, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get sortIndex => $composableBuilder(
      column: $table.sortIndex, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get updatedAtEpochMilliseconds => $composableBuilder(
      column: $table.updatedAtEpochMilliseconds,
      builder: (column) => ColumnFilters(column));

  Expression<bool> notesRefs(
      Expression<bool> Function($$NotesTableFilterComposer f) f) {
    final $$NotesTableFilterComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.id,
        referencedTable: $db.notes,
        getReferencedColumn: (t) => t.notebookId,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NotesTableFilterComposer(
              $db: $db,
              $table: $db.notes,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return f(composer);
  }
}

class $$NotebooksTableOrderingComposer
    extends Composer<_$XNoteDatabase, $NotebooksTable> {
  $$NotebooksTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get name => $composableBuilder(
      column: $table.name, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get sortIndex => $composableBuilder(
      column: $table.sortIndex, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get updatedAtEpochMilliseconds => $composableBuilder(
      column: $table.updatedAtEpochMilliseconds,
      builder: (column) => ColumnOrderings(column));
}

class $$NotebooksTableAnnotationComposer
    extends Composer<_$XNoteDatabase, $NotebooksTable> {
  $$NotebooksTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<int> get sortIndex =>
      $composableBuilder(column: $table.sortIndex, builder: (column) => column);

  GeneratedColumn<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds, builder: (column) => column);

  GeneratedColumn<int> get updatedAtEpochMilliseconds => $composableBuilder(
      column: $table.updatedAtEpochMilliseconds, builder: (column) => column);

  Expression<T> notesRefs<T extends Object>(
      Expression<T> Function($$NotesTableAnnotationComposer a) f) {
    final $$NotesTableAnnotationComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.id,
        referencedTable: $db.notes,
        getReferencedColumn: (t) => t.notebookId,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NotesTableAnnotationComposer(
              $db: $db,
              $table: $db.notes,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return f(composer);
  }
}

class $$NotebooksTableTableManager extends RootTableManager<
    _$XNoteDatabase,
    $NotebooksTable,
    NotebookRow,
    $$NotebooksTableFilterComposer,
    $$NotebooksTableOrderingComposer,
    $$NotebooksTableAnnotationComposer,
    $$NotebooksTableCreateCompanionBuilder,
    $$NotebooksTableUpdateCompanionBuilder,
    (NotebookRow, $$NotebooksTableReferences),
    NotebookRow,
    PrefetchHooks Function({bool notesRefs})> {
  $$NotebooksTableTableManager(_$XNoteDatabase db, $NotebooksTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$NotebooksTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$NotebooksTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$NotebooksTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback: ({
            Value<String> id = const Value.absent(),
            Value<String> name = const Value.absent(),
            Value<int> sortIndex = const Value.absent(),
            Value<int> createdAtEpochMilliseconds = const Value.absent(),
            Value<int> updatedAtEpochMilliseconds = const Value.absent(),
            Value<int> rowid = const Value.absent(),
          }) =>
              NotebooksCompanion(
            id: id,
            name: name,
            sortIndex: sortIndex,
            createdAtEpochMilliseconds: createdAtEpochMilliseconds,
            updatedAtEpochMilliseconds: updatedAtEpochMilliseconds,
            rowid: rowid,
          ),
          createCompanionCallback: ({
            required String id,
            required String name,
            required int sortIndex,
            required int createdAtEpochMilliseconds,
            required int updatedAtEpochMilliseconds,
            Value<int> rowid = const Value.absent(),
          }) =>
              NotebooksCompanion.insert(
            id: id,
            name: name,
            sortIndex: sortIndex,
            createdAtEpochMilliseconds: createdAtEpochMilliseconds,
            updatedAtEpochMilliseconds: updatedAtEpochMilliseconds,
            rowid: rowid,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) => (
                    e.readTable<$NotebooksTable, NotebookRow>(table),
                    $$NotebooksTableReferences(db, table, e)
                  ))
              .toList(),
          prefetchHooksCallback: ({notesRefs = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [if (notesRefs) db.notes],
              addJoins: null,
              getPrefetchedDataCallback: (items) async {
                return [
                  if (notesRefs)
                    await $_getPrefetchedData<NotebookRow, $NotebooksTable,
                            NoteRow>(
                        currentTable: table,
                        referencedTable:
                            $$NotebooksTableReferences._notesRefsTable(db),
                        managerFromTypedResult: (p0) =>
                            $$NotebooksTableReferences(db, table, p0).notesRefs,
                        referencedItemsForCurrentItem:
                            (item, referencedItems) => referencedItems
                                .where((e) => e.notebookId == item.id),
                        typedResults: items)
                ];
              },
            );
          },
        ));
}

typedef $$NotebooksTableProcessedTableManager = ProcessedTableManager<
    _$XNoteDatabase,
    $NotebooksTable,
    NotebookRow,
    $$NotebooksTableFilterComposer,
    $$NotebooksTableOrderingComposer,
    $$NotebooksTableAnnotationComposer,
    $$NotebooksTableCreateCompanionBuilder,
    $$NotebooksTableUpdateCompanionBuilder,
    (NotebookRow, $$NotebooksTableReferences),
    NotebookRow,
    PrefetchHooks Function({bool notesRefs})>;
typedef $$NotesTableCreateCompanionBuilder = NotesCompanion Function({
  required String id,
  Value<String?> notebookId,
  required String title,
  required String kind,
  Value<String?> documentJson,
  Value<String?> markdownText,
  Value<String?> backgroundKey,
  required int sortIndex,
  required int visibleCharacterCount,
  required int latinWordCount,
  required String summary,
  required int createdAtEpochMilliseconds,
  required int updatedAtEpochMilliseconds,
  Value<int?> deletedAtEpochMilliseconds,
  Value<String?> originalNotebookName,
  Value<int> rowid,
});
typedef $$NotesTableUpdateCompanionBuilder = NotesCompanion Function({
  Value<String> id,
  Value<String?> notebookId,
  Value<String> title,
  Value<String> kind,
  Value<String?> documentJson,
  Value<String?> markdownText,
  Value<String?> backgroundKey,
  Value<int> sortIndex,
  Value<int> visibleCharacterCount,
  Value<int> latinWordCount,
  Value<String> summary,
  Value<int> createdAtEpochMilliseconds,
  Value<int> updatedAtEpochMilliseconds,
  Value<int?> deletedAtEpochMilliseconds,
  Value<String?> originalNotebookName,
  Value<int> rowid,
});

final class $$NotesTableReferences
    extends BaseReferences<_$XNoteDatabase, $NotesTable, NoteRow> {
  $$NotesTableReferences(super.$_db, super.$_table, super.$_typedResult);

  static $NotebooksTable _notebookIdTable(_$XNoteDatabase db) =>
      db.notebooks.createAlias('notes__notebook_id__notebooks__id');

  $$NotebooksTableProcessedTableManager? get notebookId {
    final $_column = $_itemColumn<String>('notebook_id');
    if ($_column == null) return null;
    final manager = $$NotebooksTableTableManager($_db, $_db.notebooks)
        .filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_notebookIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
        manager.$state.copyWith(prefetchedData: [item]));
  }

  static MultiTypedResultKey<$NoteRevisionsTable, List<NoteRevisionRow>>
      _noteRevisionsRefsTable(_$XNoteDatabase db) =>
          MultiTypedResultKey.fromTable(db.noteRevisions,
              aliasName: 'notes__id__note_revisions__note_id');

  $$NoteRevisionsTableProcessedTableManager get noteRevisionsRefs {
    final manager = $$NoteRevisionsTableTableManager($_db, $_db.noteRevisions)
        .filter((f) => f.noteId.id.sqlEquals($_itemColumn<String>('id')!));

    final cache = $_typedResult.readTableOrNull(_noteRevisionsRefsTable($_db));
    return ProcessedTableManager(
        manager.$state.copyWith(prefetchedData: cache));
  }
}

class $$NotesTableFilterComposer
    extends Composer<_$XNoteDatabase, $NotesTable> {
  $$NotesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get title => $composableBuilder(
      column: $table.title, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get kind => $composableBuilder(
      column: $table.kind, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get documentJson => $composableBuilder(
      column: $table.documentJson, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get markdownText => $composableBuilder(
      column: $table.markdownText, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get backgroundKey => $composableBuilder(
      column: $table.backgroundKey, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get sortIndex => $composableBuilder(
      column: $table.sortIndex, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get visibleCharacterCount => $composableBuilder(
      column: $table.visibleCharacterCount,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get latinWordCount => $composableBuilder(
      column: $table.latinWordCount,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get summary => $composableBuilder(
      column: $table.summary, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get updatedAtEpochMilliseconds => $composableBuilder(
      column: $table.updatedAtEpochMilliseconds,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get deletedAtEpochMilliseconds => $composableBuilder(
      column: $table.deletedAtEpochMilliseconds,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get originalNotebookName => $composableBuilder(
      column: $table.originalNotebookName,
      builder: (column) => ColumnFilters(column));

  $$NotebooksTableFilterComposer get notebookId {
    final $$NotebooksTableFilterComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.notebookId,
        referencedTable: $db.notebooks,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NotebooksTableFilterComposer(
              $db: $db,
              $table: $db.notebooks,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }

  Expression<bool> noteRevisionsRefs(
      Expression<bool> Function($$NoteRevisionsTableFilterComposer f) f) {
    final $$NoteRevisionsTableFilterComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.id,
        referencedTable: $db.noteRevisions,
        getReferencedColumn: (t) => t.noteId,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NoteRevisionsTableFilterComposer(
              $db: $db,
              $table: $db.noteRevisions,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return f(composer);
  }
}

class $$NotesTableOrderingComposer
    extends Composer<_$XNoteDatabase, $NotesTable> {
  $$NotesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get title => $composableBuilder(
      column: $table.title, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get kind => $composableBuilder(
      column: $table.kind, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get documentJson => $composableBuilder(
      column: $table.documentJson,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get markdownText => $composableBuilder(
      column: $table.markdownText,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get backgroundKey => $composableBuilder(
      column: $table.backgroundKey,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get sortIndex => $composableBuilder(
      column: $table.sortIndex, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get visibleCharacterCount => $composableBuilder(
      column: $table.visibleCharacterCount,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get latinWordCount => $composableBuilder(
      column: $table.latinWordCount,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get summary => $composableBuilder(
      column: $table.summary, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get updatedAtEpochMilliseconds => $composableBuilder(
      column: $table.updatedAtEpochMilliseconds,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get deletedAtEpochMilliseconds => $composableBuilder(
      column: $table.deletedAtEpochMilliseconds,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get originalNotebookName => $composableBuilder(
      column: $table.originalNotebookName,
      builder: (column) => ColumnOrderings(column));

  $$NotebooksTableOrderingComposer get notebookId {
    final $$NotebooksTableOrderingComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.notebookId,
        referencedTable: $db.notebooks,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NotebooksTableOrderingComposer(
              $db: $db,
              $table: $db.notebooks,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }
}

class $$NotesTableAnnotationComposer
    extends Composer<_$XNoteDatabase, $NotesTable> {
  $$NotesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get documentJson => $composableBuilder(
      column: $table.documentJson, builder: (column) => column);

  GeneratedColumn<String> get markdownText => $composableBuilder(
      column: $table.markdownText, builder: (column) => column);

  GeneratedColumn<String> get backgroundKey => $composableBuilder(
      column: $table.backgroundKey, builder: (column) => column);

  GeneratedColumn<int> get sortIndex =>
      $composableBuilder(column: $table.sortIndex, builder: (column) => column);

  GeneratedColumn<int> get visibleCharacterCount => $composableBuilder(
      column: $table.visibleCharacterCount, builder: (column) => column);

  GeneratedColumn<int> get latinWordCount => $composableBuilder(
      column: $table.latinWordCount, builder: (column) => column);

  GeneratedColumn<String> get summary =>
      $composableBuilder(column: $table.summary, builder: (column) => column);

  GeneratedColumn<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds, builder: (column) => column);

  GeneratedColumn<int> get updatedAtEpochMilliseconds => $composableBuilder(
      column: $table.updatedAtEpochMilliseconds, builder: (column) => column);

  GeneratedColumn<int> get deletedAtEpochMilliseconds => $composableBuilder(
      column: $table.deletedAtEpochMilliseconds, builder: (column) => column);

  GeneratedColumn<String> get originalNotebookName => $composableBuilder(
      column: $table.originalNotebookName, builder: (column) => column);

  $$NotebooksTableAnnotationComposer get notebookId {
    final $$NotebooksTableAnnotationComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.notebookId,
        referencedTable: $db.notebooks,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NotebooksTableAnnotationComposer(
              $db: $db,
              $table: $db.notebooks,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }

  Expression<T> noteRevisionsRefs<T extends Object>(
      Expression<T> Function($$NoteRevisionsTableAnnotationComposer a) f) {
    final $$NoteRevisionsTableAnnotationComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.id,
        referencedTable: $db.noteRevisions,
        getReferencedColumn: (t) => t.noteId,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NoteRevisionsTableAnnotationComposer(
              $db: $db,
              $table: $db.noteRevisions,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return f(composer);
  }
}

class $$NotesTableTableManager extends RootTableManager<
    _$XNoteDatabase,
    $NotesTable,
    NoteRow,
    $$NotesTableFilterComposer,
    $$NotesTableOrderingComposer,
    $$NotesTableAnnotationComposer,
    $$NotesTableCreateCompanionBuilder,
    $$NotesTableUpdateCompanionBuilder,
    (NoteRow, $$NotesTableReferences),
    NoteRow,
    PrefetchHooks Function({bool notebookId, bool noteRevisionsRefs})> {
  $$NotesTableTableManager(_$XNoteDatabase db, $NotesTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$NotesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$NotesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$NotesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback: ({
            Value<String> id = const Value.absent(),
            Value<String?> notebookId = const Value.absent(),
            Value<String> title = const Value.absent(),
            Value<String> kind = const Value.absent(),
            Value<String?> documentJson = const Value.absent(),
            Value<String?> markdownText = const Value.absent(),
            Value<String?> backgroundKey = const Value.absent(),
            Value<int> sortIndex = const Value.absent(),
            Value<int> visibleCharacterCount = const Value.absent(),
            Value<int> latinWordCount = const Value.absent(),
            Value<String> summary = const Value.absent(),
            Value<int> createdAtEpochMilliseconds = const Value.absent(),
            Value<int> updatedAtEpochMilliseconds = const Value.absent(),
            Value<int?> deletedAtEpochMilliseconds = const Value.absent(),
            Value<String?> originalNotebookName = const Value.absent(),
            Value<int> rowid = const Value.absent(),
          }) =>
              NotesCompanion(
            id: id,
            notebookId: notebookId,
            title: title,
            kind: kind,
            documentJson: documentJson,
            markdownText: markdownText,
            backgroundKey: backgroundKey,
            sortIndex: sortIndex,
            visibleCharacterCount: visibleCharacterCount,
            latinWordCount: latinWordCount,
            summary: summary,
            createdAtEpochMilliseconds: createdAtEpochMilliseconds,
            updatedAtEpochMilliseconds: updatedAtEpochMilliseconds,
            deletedAtEpochMilliseconds: deletedAtEpochMilliseconds,
            originalNotebookName: originalNotebookName,
            rowid: rowid,
          ),
          createCompanionCallback: ({
            required String id,
            Value<String?> notebookId = const Value.absent(),
            required String title,
            required String kind,
            Value<String?> documentJson = const Value.absent(),
            Value<String?> markdownText = const Value.absent(),
            Value<String?> backgroundKey = const Value.absent(),
            required int sortIndex,
            required int visibleCharacterCount,
            required int latinWordCount,
            required String summary,
            required int createdAtEpochMilliseconds,
            required int updatedAtEpochMilliseconds,
            Value<int?> deletedAtEpochMilliseconds = const Value.absent(),
            Value<String?> originalNotebookName = const Value.absent(),
            Value<int> rowid = const Value.absent(),
          }) =>
              NotesCompanion.insert(
            id: id,
            notebookId: notebookId,
            title: title,
            kind: kind,
            documentJson: documentJson,
            markdownText: markdownText,
            backgroundKey: backgroundKey,
            sortIndex: sortIndex,
            visibleCharacterCount: visibleCharacterCount,
            latinWordCount: latinWordCount,
            summary: summary,
            createdAtEpochMilliseconds: createdAtEpochMilliseconds,
            updatedAtEpochMilliseconds: updatedAtEpochMilliseconds,
            deletedAtEpochMilliseconds: deletedAtEpochMilliseconds,
            originalNotebookName: originalNotebookName,
            rowid: rowid,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) => (
                    e.readTable<$NotesTable, NoteRow>(table),
                    $$NotesTableReferences(db, table, e)
                  ))
              .toList(),
          prefetchHooksCallback: (
              {notebookId = false, noteRevisionsRefs = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [
                if (noteRevisionsRefs) db.noteRevisions
              ],
              addJoins: <
                  T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic>>(state) {
                if (notebookId) {
                  state = state.withJoin(
                    currentTable: table,
                    currentColumn: table.notebookId,
                    referencedTable:
                        $$NotesTableReferences._notebookIdTable(db),
                    referencedColumn:
                        $$NotesTableReferences._notebookIdTable(db).id,
                  ) as T;
                }

                return state;
              },
              getPrefetchedDataCallback: (items) async {
                return [
                  if (noteRevisionsRefs)
                    await $_getPrefetchedData<NoteRow, $NotesTable,
                            NoteRevisionRow>(
                        currentTable: table,
                        referencedTable:
                            $$NotesTableReferences._noteRevisionsRefsTable(db),
                        managerFromTypedResult: (p0) =>
                            $$NotesTableReferences(db, table, p0)
                                .noteRevisionsRefs,
                        referencedItemsForCurrentItem: (item,
                                referencedItems) =>
                            referencedItems.where((e) => e.noteId == item.id),
                        typedResults: items)
                ];
              },
            );
          },
        ));
}

typedef $$NotesTableProcessedTableManager = ProcessedTableManager<
    _$XNoteDatabase,
    $NotesTable,
    NoteRow,
    $$NotesTableFilterComposer,
    $$NotesTableOrderingComposer,
    $$NotesTableAnnotationComposer,
    $$NotesTableCreateCompanionBuilder,
    $$NotesTableUpdateCompanionBuilder,
    (NoteRow, $$NotesTableReferences),
    NoteRow,
    PrefetchHooks Function({bool notebookId, bool noteRevisionsRefs})>;
typedef $$NoteRevisionsTableCreateCompanionBuilder = NoteRevisionsCompanion
    Function({
  required String id,
  required String noteId,
  required String reason,
  required String kind,
  required String title,
  Value<String?> documentJson,
  Value<String?> markdownText,
  required int createdAtEpochMilliseconds,
  Value<int> rowid,
});
typedef $$NoteRevisionsTableUpdateCompanionBuilder = NoteRevisionsCompanion
    Function({
  Value<String> id,
  Value<String> noteId,
  Value<String> reason,
  Value<String> kind,
  Value<String> title,
  Value<String?> documentJson,
  Value<String?> markdownText,
  Value<int> createdAtEpochMilliseconds,
  Value<int> rowid,
});

final class $$NoteRevisionsTableReferences extends BaseReferences<
    _$XNoteDatabase, $NoteRevisionsTable, NoteRevisionRow> {
  $$NoteRevisionsTableReferences(
      super.$_db, super.$_table, super.$_typedResult);

  static $NotesTable _noteIdTable(_$XNoteDatabase db) =>
      db.notes.createAlias('note_revisions__note_id__notes__id');

  $$NotesTableProcessedTableManager get noteId {
    final $_column = $_itemColumn<String>('note_id')!;

    final manager = $$NotesTableTableManager($_db, $_db.notes)
        .filter((f) => f.id.sqlEquals($_column));
    final item = $_typedResult.readTableOrNull(_noteIdTable($_db));
    if (item == null) return manager;
    return ProcessedTableManager(
        manager.$state.copyWith(prefetchedData: [item]));
  }
}

class $$NoteRevisionsTableFilterComposer
    extends Composer<_$XNoteDatabase, $NoteRevisionsTable> {
  $$NoteRevisionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get reason => $composableBuilder(
      column: $table.reason, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get kind => $composableBuilder(
      column: $table.kind, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get title => $composableBuilder(
      column: $table.title, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get documentJson => $composableBuilder(
      column: $table.documentJson, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get markdownText => $composableBuilder(
      column: $table.markdownText, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds,
      builder: (column) => ColumnFilters(column));

  $$NotesTableFilterComposer get noteId {
    final $$NotesTableFilterComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.noteId,
        referencedTable: $db.notes,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NotesTableFilterComposer(
              $db: $db,
              $table: $db.notes,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }
}

class $$NoteRevisionsTableOrderingComposer
    extends Composer<_$XNoteDatabase, $NoteRevisionsTable> {
  $$NoteRevisionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get reason => $composableBuilder(
      column: $table.reason, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get kind => $composableBuilder(
      column: $table.kind, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get title => $composableBuilder(
      column: $table.title, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get documentJson => $composableBuilder(
      column: $table.documentJson,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get markdownText => $composableBuilder(
      column: $table.markdownText,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds,
      builder: (column) => ColumnOrderings(column));

  $$NotesTableOrderingComposer get noteId {
    final $$NotesTableOrderingComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.noteId,
        referencedTable: $db.notes,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NotesTableOrderingComposer(
              $db: $db,
              $table: $db.notes,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }
}

class $$NoteRevisionsTableAnnotationComposer
    extends Composer<_$XNoteDatabase, $NoteRevisionsTable> {
  $$NoteRevisionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get reason =>
      $composableBuilder(column: $table.reason, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get documentJson => $composableBuilder(
      column: $table.documentJson, builder: (column) => column);

  GeneratedColumn<String> get markdownText => $composableBuilder(
      column: $table.markdownText, builder: (column) => column);

  GeneratedColumn<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds, builder: (column) => column);

  $$NotesTableAnnotationComposer get noteId {
    final $$NotesTableAnnotationComposer composer = $composerBuilder(
        composer: this,
        getCurrentColumn: (t) => t.noteId,
        referencedTable: $db.notes,
        getReferencedColumn: (t) => t.id,
        builder: (joinBuilder,
                {$addJoinBuilderToRootComposer,
                $removeJoinBuilderFromRootComposer}) =>
            $$NotesTableAnnotationComposer(
              $db: $db,
              $table: $db.notes,
              $addJoinBuilderToRootComposer: $addJoinBuilderToRootComposer,
              joinBuilder: joinBuilder,
              $removeJoinBuilderFromRootComposer:
                  $removeJoinBuilderFromRootComposer,
            ));
    return composer;
  }
}

class $$NoteRevisionsTableTableManager extends RootTableManager<
    _$XNoteDatabase,
    $NoteRevisionsTable,
    NoteRevisionRow,
    $$NoteRevisionsTableFilterComposer,
    $$NoteRevisionsTableOrderingComposer,
    $$NoteRevisionsTableAnnotationComposer,
    $$NoteRevisionsTableCreateCompanionBuilder,
    $$NoteRevisionsTableUpdateCompanionBuilder,
    (NoteRevisionRow, $$NoteRevisionsTableReferences),
    NoteRevisionRow,
    PrefetchHooks Function({bool noteId})> {
  $$NoteRevisionsTableTableManager(
      _$XNoteDatabase db, $NoteRevisionsTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$NoteRevisionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$NoteRevisionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$NoteRevisionsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback: ({
            Value<String> id = const Value.absent(),
            Value<String> noteId = const Value.absent(),
            Value<String> reason = const Value.absent(),
            Value<String> kind = const Value.absent(),
            Value<String> title = const Value.absent(),
            Value<String?> documentJson = const Value.absent(),
            Value<String?> markdownText = const Value.absent(),
            Value<int> createdAtEpochMilliseconds = const Value.absent(),
            Value<int> rowid = const Value.absent(),
          }) =>
              NoteRevisionsCompanion(
            id: id,
            noteId: noteId,
            reason: reason,
            kind: kind,
            title: title,
            documentJson: documentJson,
            markdownText: markdownText,
            createdAtEpochMilliseconds: createdAtEpochMilliseconds,
            rowid: rowid,
          ),
          createCompanionCallback: ({
            required String id,
            required String noteId,
            required String reason,
            required String kind,
            required String title,
            Value<String?> documentJson = const Value.absent(),
            Value<String?> markdownText = const Value.absent(),
            required int createdAtEpochMilliseconds,
            Value<int> rowid = const Value.absent(),
          }) =>
              NoteRevisionsCompanion.insert(
            id: id,
            noteId: noteId,
            reason: reason,
            kind: kind,
            title: title,
            documentJson: documentJson,
            markdownText: markdownText,
            createdAtEpochMilliseconds: createdAtEpochMilliseconds,
            rowid: rowid,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) => (
                    e.readTable<$NoteRevisionsTable, NoteRevisionRow>(table),
                    $$NoteRevisionsTableReferences(db, table, e)
                  ))
              .toList(),
          prefetchHooksCallback: ({noteId = false}) {
            return PrefetchHooks(
              db: db,
              explicitlyWatchedTables: [],
              addJoins: <
                  T extends TableManagerState<
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic,
                      dynamic>>(state) {
                if (noteId) {
                  state = state.withJoin(
                    currentTable: table,
                    currentColumn: table.noteId,
                    referencedTable:
                        $$NoteRevisionsTableReferences._noteIdTable(db),
                    referencedColumn:
                        $$NoteRevisionsTableReferences._noteIdTable(db).id,
                  ) as T;
                }

                return state;
              },
              getPrefetchedDataCallback: (items) async {
                return [];
              },
            );
          },
        ));
}

typedef $$NoteRevisionsTableProcessedTableManager = ProcessedTableManager<
    _$XNoteDatabase,
    $NoteRevisionsTable,
    NoteRevisionRow,
    $$NoteRevisionsTableFilterComposer,
    $$NoteRevisionsTableOrderingComposer,
    $$NoteRevisionsTableAnnotationComposer,
    $$NoteRevisionsTableCreateCompanionBuilder,
    $$NoteRevisionsTableUpdateCompanionBuilder,
    (NoteRevisionRow, $$NoteRevisionsTableReferences),
    NoteRevisionRow,
    PrefetchHooks Function({bool noteId})>;
typedef $$AttachmentsTableCreateCompanionBuilder = AttachmentsCompanion
    Function({
  required String id,
  required String kind,
  required String mimeType,
  Value<String?> originalFileName,
  required String relativePath,
  required int byteSize,
  Value<int?> widthPixels,
  Value<int?> heightPixels,
  required int createdAtEpochMilliseconds,
  Value<int> rowid,
});
typedef $$AttachmentsTableUpdateCompanionBuilder = AttachmentsCompanion
    Function({
  Value<String> id,
  Value<String> kind,
  Value<String> mimeType,
  Value<String?> originalFileName,
  Value<String> relativePath,
  Value<int> byteSize,
  Value<int?> widthPixels,
  Value<int?> heightPixels,
  Value<int> createdAtEpochMilliseconds,
  Value<int> rowid,
});

class $$AttachmentsTableFilterComposer
    extends Composer<_$XNoteDatabase, $AttachmentsTable> {
  $$AttachmentsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get kind => $composableBuilder(
      column: $table.kind, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get mimeType => $composableBuilder(
      column: $table.mimeType, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get originalFileName => $composableBuilder(
      column: $table.originalFileName,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get relativePath => $composableBuilder(
      column: $table.relativePath, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get byteSize => $composableBuilder(
      column: $table.byteSize, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get widthPixels => $composableBuilder(
      column: $table.widthPixels, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get heightPixels => $composableBuilder(
      column: $table.heightPixels, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds,
      builder: (column) => ColumnFilters(column));
}

class $$AttachmentsTableOrderingComposer
    extends Composer<_$XNoteDatabase, $AttachmentsTable> {
  $$AttachmentsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
      column: $table.id, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get kind => $composableBuilder(
      column: $table.kind, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get mimeType => $composableBuilder(
      column: $table.mimeType, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get originalFileName => $composableBuilder(
      column: $table.originalFileName,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get relativePath => $composableBuilder(
      column: $table.relativePath,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get byteSize => $composableBuilder(
      column: $table.byteSize, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get widthPixels => $composableBuilder(
      column: $table.widthPixels, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get heightPixels => $composableBuilder(
      column: $table.heightPixels,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds,
      builder: (column) => ColumnOrderings(column));
}

class $$AttachmentsTableAnnotationComposer
    extends Composer<_$XNoteDatabase, $AttachmentsTable> {
  $$AttachmentsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get mimeType =>
      $composableBuilder(column: $table.mimeType, builder: (column) => column);

  GeneratedColumn<String> get originalFileName => $composableBuilder(
      column: $table.originalFileName, builder: (column) => column);

  GeneratedColumn<String> get relativePath => $composableBuilder(
      column: $table.relativePath, builder: (column) => column);

  GeneratedColumn<int> get byteSize =>
      $composableBuilder(column: $table.byteSize, builder: (column) => column);

  GeneratedColumn<int> get widthPixels => $composableBuilder(
      column: $table.widthPixels, builder: (column) => column);

  GeneratedColumn<int> get heightPixels => $composableBuilder(
      column: $table.heightPixels, builder: (column) => column);

  GeneratedColumn<int> get createdAtEpochMilliseconds => $composableBuilder(
      column: $table.createdAtEpochMilliseconds, builder: (column) => column);
}

class $$AttachmentsTableTableManager extends RootTableManager<
    _$XNoteDatabase,
    $AttachmentsTable,
    AttachmentRow,
    $$AttachmentsTableFilterComposer,
    $$AttachmentsTableOrderingComposer,
    $$AttachmentsTableAnnotationComposer,
    $$AttachmentsTableCreateCompanionBuilder,
    $$AttachmentsTableUpdateCompanionBuilder,
    (
      AttachmentRow,
      BaseReferences<_$XNoteDatabase, $AttachmentsTable, AttachmentRow>
    ),
    AttachmentRow,
    PrefetchHooks Function()> {
  $$AttachmentsTableTableManager(_$XNoteDatabase db, $AttachmentsTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AttachmentsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AttachmentsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AttachmentsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback: ({
            Value<String> id = const Value.absent(),
            Value<String> kind = const Value.absent(),
            Value<String> mimeType = const Value.absent(),
            Value<String?> originalFileName = const Value.absent(),
            Value<String> relativePath = const Value.absent(),
            Value<int> byteSize = const Value.absent(),
            Value<int?> widthPixels = const Value.absent(),
            Value<int?> heightPixels = const Value.absent(),
            Value<int> createdAtEpochMilliseconds = const Value.absent(),
            Value<int> rowid = const Value.absent(),
          }) =>
              AttachmentsCompanion(
            id: id,
            kind: kind,
            mimeType: mimeType,
            originalFileName: originalFileName,
            relativePath: relativePath,
            byteSize: byteSize,
            widthPixels: widthPixels,
            heightPixels: heightPixels,
            createdAtEpochMilliseconds: createdAtEpochMilliseconds,
            rowid: rowid,
          ),
          createCompanionCallback: ({
            required String id,
            required String kind,
            required String mimeType,
            Value<String?> originalFileName = const Value.absent(),
            required String relativePath,
            required int byteSize,
            Value<int?> widthPixels = const Value.absent(),
            Value<int?> heightPixels = const Value.absent(),
            required int createdAtEpochMilliseconds,
            Value<int> rowid = const Value.absent(),
          }) =>
              AttachmentsCompanion.insert(
            id: id,
            kind: kind,
            mimeType: mimeType,
            originalFileName: originalFileName,
            relativePath: relativePath,
            byteSize: byteSize,
            widthPixels: widthPixels,
            heightPixels: heightPixels,
            createdAtEpochMilliseconds: createdAtEpochMilliseconds,
            rowid: rowid,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) => (
                    e.readTable<$AttachmentsTable, AttachmentRow>(table),
                    BaseReferences<_$XNoteDatabase, $AttachmentsTable,
                        AttachmentRow>(db, table, e)
                  ))
              .toList(),
          prefetchHooksCallback: null,
        ));
}

typedef $$AttachmentsTableProcessedTableManager = ProcessedTableManager<
    _$XNoteDatabase,
    $AttachmentsTable,
    AttachmentRow,
    $$AttachmentsTableFilterComposer,
    $$AttachmentsTableOrderingComposer,
    $$AttachmentsTableAnnotationComposer,
    $$AttachmentsTableCreateCompanionBuilder,
    $$AttachmentsTableUpdateCompanionBuilder,
    (
      AttachmentRow,
      BaseReferences<_$XNoteDatabase, $AttachmentsTable, AttachmentRow>
    ),
    AttachmentRow,
    PrefetchHooks Function()>;
typedef $$SearchHistoryEntriesTableCreateCompanionBuilder
    = SearchHistoryEntriesCompanion Function({
  required String query,
  required int usedAtEpochMilliseconds,
  Value<int> rowid,
});
typedef $$SearchHistoryEntriesTableUpdateCompanionBuilder
    = SearchHistoryEntriesCompanion Function({
  Value<String> query,
  Value<int> usedAtEpochMilliseconds,
  Value<int> rowid,
});

class $$SearchHistoryEntriesTableFilterComposer
    extends Composer<_$XNoteDatabase, $SearchHistoryEntriesTable> {
  $$SearchHistoryEntriesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get query => $composableBuilder(
      column: $table.query, builder: (column) => ColumnFilters(column));

  ColumnFilters<int> get usedAtEpochMilliseconds => $composableBuilder(
      column: $table.usedAtEpochMilliseconds,
      builder: (column) => ColumnFilters(column));
}

class $$SearchHistoryEntriesTableOrderingComposer
    extends Composer<_$XNoteDatabase, $SearchHistoryEntriesTable> {
  $$SearchHistoryEntriesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get query => $composableBuilder(
      column: $table.query, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<int> get usedAtEpochMilliseconds => $composableBuilder(
      column: $table.usedAtEpochMilliseconds,
      builder: (column) => ColumnOrderings(column));
}

class $$SearchHistoryEntriesTableAnnotationComposer
    extends Composer<_$XNoteDatabase, $SearchHistoryEntriesTable> {
  $$SearchHistoryEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get query =>
      $composableBuilder(column: $table.query, builder: (column) => column);

  GeneratedColumn<int> get usedAtEpochMilliseconds => $composableBuilder(
      column: $table.usedAtEpochMilliseconds, builder: (column) => column);
}

class $$SearchHistoryEntriesTableTableManager extends RootTableManager<
    _$XNoteDatabase,
    $SearchHistoryEntriesTable,
    SearchHistoryRow,
    $$SearchHistoryEntriesTableFilterComposer,
    $$SearchHistoryEntriesTableOrderingComposer,
    $$SearchHistoryEntriesTableAnnotationComposer,
    $$SearchHistoryEntriesTableCreateCompanionBuilder,
    $$SearchHistoryEntriesTableUpdateCompanionBuilder,
    (
      SearchHistoryRow,
      BaseReferences<_$XNoteDatabase, $SearchHistoryEntriesTable,
          SearchHistoryRow>
    ),
    SearchHistoryRow,
    PrefetchHooks Function()> {
  $$SearchHistoryEntriesTableTableManager(
      _$XNoteDatabase db, $SearchHistoryEntriesTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SearchHistoryEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SearchHistoryEntriesTableOrderingComposer(
                  $db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SearchHistoryEntriesTableAnnotationComposer(
                  $db: db, $table: table),
          updateCompanionCallback: ({
            Value<String> query = const Value.absent(),
            Value<int> usedAtEpochMilliseconds = const Value.absent(),
            Value<int> rowid = const Value.absent(),
          }) =>
              SearchHistoryEntriesCompanion(
            query: query,
            usedAtEpochMilliseconds: usedAtEpochMilliseconds,
            rowid: rowid,
          ),
          createCompanionCallback: ({
            required String query,
            required int usedAtEpochMilliseconds,
            Value<int> rowid = const Value.absent(),
          }) =>
              SearchHistoryEntriesCompanion.insert(
            query: query,
            usedAtEpochMilliseconds: usedAtEpochMilliseconds,
            rowid: rowid,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) => (
                    e.readTable<$SearchHistoryEntriesTable, SearchHistoryRow>(
                        table),
                    BaseReferences<_$XNoteDatabase, $SearchHistoryEntriesTable,
                        SearchHistoryRow>(db, table, e)
                  ))
              .toList(),
          prefetchHooksCallback: null,
        ));
}

typedef $$SearchHistoryEntriesTableProcessedTableManager
    = ProcessedTableManager<
        _$XNoteDatabase,
        $SearchHistoryEntriesTable,
        SearchHistoryRow,
        $$SearchHistoryEntriesTableFilterComposer,
        $$SearchHistoryEntriesTableOrderingComposer,
        $$SearchHistoryEntriesTableAnnotationComposer,
        $$SearchHistoryEntriesTableCreateCompanionBuilder,
        $$SearchHistoryEntriesTableUpdateCompanionBuilder,
        (
          SearchHistoryRow,
          BaseReferences<_$XNoteDatabase, $SearchHistoryEntriesTable,
              SearchHistoryRow>
        ),
        SearchHistoryRow,
        PrefetchHooks Function()>;
typedef $$AppSettingsEntriesTableCreateCompanionBuilder
    = AppSettingsEntriesCompanion Function({
  Value<int> singletonId,
  required String defaultBackgroundKey,
  required String themeMode,
});
typedef $$AppSettingsEntriesTableUpdateCompanionBuilder
    = AppSettingsEntriesCompanion Function({
  Value<int> singletonId,
  Value<String> defaultBackgroundKey,
  Value<String> themeMode,
});

class $$AppSettingsEntriesTableFilterComposer
    extends Composer<_$XNoteDatabase, $AppSettingsEntriesTable> {
  $$AppSettingsEntriesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get singletonId => $composableBuilder(
      column: $table.singletonId, builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get defaultBackgroundKey => $composableBuilder(
      column: $table.defaultBackgroundKey,
      builder: (column) => ColumnFilters(column));

  ColumnFilters<String> get themeMode => $composableBuilder(
      column: $table.themeMode, builder: (column) => ColumnFilters(column));
}

class $$AppSettingsEntriesTableOrderingComposer
    extends Composer<_$XNoteDatabase, $AppSettingsEntriesTable> {
  $$AppSettingsEntriesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get singletonId => $composableBuilder(
      column: $table.singletonId, builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get defaultBackgroundKey => $composableBuilder(
      column: $table.defaultBackgroundKey,
      builder: (column) => ColumnOrderings(column));

  ColumnOrderings<String> get themeMode => $composableBuilder(
      column: $table.themeMode, builder: (column) => ColumnOrderings(column));
}

class $$AppSettingsEntriesTableAnnotationComposer
    extends Composer<_$XNoteDatabase, $AppSettingsEntriesTable> {
  $$AppSettingsEntriesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get singletonId => $composableBuilder(
      column: $table.singletonId, builder: (column) => column);

  GeneratedColumn<String> get defaultBackgroundKey => $composableBuilder(
      column: $table.defaultBackgroundKey, builder: (column) => column);

  GeneratedColumn<String> get themeMode =>
      $composableBuilder(column: $table.themeMode, builder: (column) => column);
}

class $$AppSettingsEntriesTableTableManager extends RootTableManager<
    _$XNoteDatabase,
    $AppSettingsEntriesTable,
    AppSettingsRow,
    $$AppSettingsEntriesTableFilterComposer,
    $$AppSettingsEntriesTableOrderingComposer,
    $$AppSettingsEntriesTableAnnotationComposer,
    $$AppSettingsEntriesTableCreateCompanionBuilder,
    $$AppSettingsEntriesTableUpdateCompanionBuilder,
    (
      AppSettingsRow,
      BaseReferences<_$XNoteDatabase, $AppSettingsEntriesTable, AppSettingsRow>
    ),
    AppSettingsRow,
    PrefetchHooks Function()> {
  $$AppSettingsEntriesTableTableManager(
      _$XNoteDatabase db, $AppSettingsEntriesTable table)
      : super(TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AppSettingsEntriesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AppSettingsEntriesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AppSettingsEntriesTableAnnotationComposer(
                  $db: db, $table: table),
          updateCompanionCallback: ({
            Value<int> singletonId = const Value.absent(),
            Value<String> defaultBackgroundKey = const Value.absent(),
            Value<String> themeMode = const Value.absent(),
          }) =>
              AppSettingsEntriesCompanion(
            singletonId: singletonId,
            defaultBackgroundKey: defaultBackgroundKey,
            themeMode: themeMode,
          ),
          createCompanionCallback: ({
            Value<int> singletonId = const Value.absent(),
            required String defaultBackgroundKey,
            required String themeMode,
          }) =>
              AppSettingsEntriesCompanion.insert(
            singletonId: singletonId,
            defaultBackgroundKey: defaultBackgroundKey,
            themeMode: themeMode,
          ),
          withReferenceMapper: (p0) => p0
              .map((e) => (
                    e.readTable<$AppSettingsEntriesTable, AppSettingsRow>(
                        table),
                    BaseReferences<_$XNoteDatabase, $AppSettingsEntriesTable,
                        AppSettingsRow>(db, table, e)
                  ))
              .toList(),
          prefetchHooksCallback: null,
        ));
}

typedef $$AppSettingsEntriesTableProcessedTableManager = ProcessedTableManager<
    _$XNoteDatabase,
    $AppSettingsEntriesTable,
    AppSettingsRow,
    $$AppSettingsEntriesTableFilterComposer,
    $$AppSettingsEntriesTableOrderingComposer,
    $$AppSettingsEntriesTableAnnotationComposer,
    $$AppSettingsEntriesTableCreateCompanionBuilder,
    $$AppSettingsEntriesTableUpdateCompanionBuilder,
    (
      AppSettingsRow,
      BaseReferences<_$XNoteDatabase, $AppSettingsEntriesTable, AppSettingsRow>
    ),
    AppSettingsRow,
    PrefetchHooks Function()>;

class $XNoteDatabaseManager {
  final _$XNoteDatabase _db;
  $XNoteDatabaseManager(this._db);
  $$NotebooksTableTableManager get notebooks =>
      $$NotebooksTableTableManager(_db, _db.notebooks);
  $$NotesTableTableManager get notes =>
      $$NotesTableTableManager(_db, _db.notes);
  $$NoteRevisionsTableTableManager get noteRevisions =>
      $$NoteRevisionsTableTableManager(_db, _db.noteRevisions);
  $$AttachmentsTableTableManager get attachments =>
      $$AttachmentsTableTableManager(_db, _db.attachments);
  $$SearchHistoryEntriesTableTableManager get searchHistoryEntries =>
      $$SearchHistoryEntriesTableTableManager(_db, _db.searchHistoryEntries);
  $$AppSettingsEntriesTableTableManager get appSettingsEntries =>
      $$AppSettingsEntriesTableTableManager(_db, _db.appSettingsEntries);
}
