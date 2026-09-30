"""Validate the actual Room migration SQL against the exported schemas and saved history."""

from pathlib import Path
import argparse
import json
import re
import sqlite3

# -- Constants

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/com.xnote.app.data.db.XNoteDatabase"
MIGRATION = ROOT / "app/src/main/java/com/xnote/app/data/db/AgentConversationMigration.kt"

# -- Functions

def verify_migration():
    before = json.loads((SCHEMAS / "17.json").read_text())["database"]
    after = json.loads((SCHEMAS / "18.json").read_text())["database"]
    connection = sqlite3.connect(":memory:")
    for entity in before["entities"]:
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for segment, reason in [("first", "idle"), ("auto", "new_topic"), ("second", "switch_conversation"), ("second-auto", None)]:
        connection.execute("INSERT INTO agent_segments VALUES (?, 1, 2, ?)", (segment, reason))
    connection.execute("INSERT INTO agent_runs (id, segmentId, userMessageId, profileId, profileVersion, status, createdAtEpochMs, updatedAtEpochMs) VALUES ('run', 'first', 'user', 'model', 1, 'Complete', 1, 2)")
    for message, role, text in [("user", "User", "Question"), ("process", "Assistant", "Working"), ("ending", "Assistant", "Done")]:
        connection.execute("INSERT INTO agent_messages (id, segmentId, runId, role, text, status, createdAtEpochMs, sourcesJson) VALUES (?, 'first', 'run', ?, ?, 'Complete', 1, '[]')", (message, role, text))
    code = MIGRATION.read_text(encoding="utf-8")
    statements = re.findall(r'connection\.execSQL\((?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\)', code, re.S)
    assert len(statements) == 4
    for multiline, singleline in statements:
        connection.execute(multiline or singleline)
    assert dict(connection.execute("SELECT id, conversationId FROM agent_segments")) == {
        "first": "first", "auto": "first", "second": "second", "second-auto": "second"
    }
    assert list(connection.execute("SELECT id, text, isFinal FROM agent_messages ORDER BY sequence")) == [
        ("user", "Question", 0), ("process", "Working", 0), ("ending", "Done", 1)
    ]
    for entity in after["entities"]:
        if entity["tableName"] not in ("agent_segments", "agent_messages"):
            assert entity == next(item for item in before["entities"] if item["tableName"] == entity["tableName"])
            continue
        columns = {row[1]: row for row in connection.execute(f"PRAGMA table_info('{entity['tableName']}')")}
        for field in entity["fields"]:
            column = columns[field["columnName"]]
            assert column[2] == field["affinity"], (entity["tableName"], field["columnName"])
            assert bool(column[3]) == field.get("notNull", False)
            if "defaultValue" in field:
                assert column[4] == field["defaultValue"]
    connection.close()
    print("Migration 17 -> 18 passed: history, final messages, conversation grouping and exported schema preserved.")


def verify_character(source_path):
    source = Path(source_path).read_text(encoding="utf-8").strip()
    kotlin = (ROOT / "app/src/main/java/com/xnote/app/domain/agent/AgentCharacterPrompt.kt").read_text(encoding="utf-8")
    imported = kotlin.split('"""', 1)[1].rsplit('"""', 1)[0].strip()
    assert imported == source, "Imported character prompt differs from the complete source file"
    print("Complete character source matches the imported system prompt.")


# -- Entry Point

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--character-source")
    arguments = parser.parse_args()
    verify_migration()
    if arguments.character_source:
        verify_character(arguments.character_source)
