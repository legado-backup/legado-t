"""Exercise the production migration/query SQL with SQLite, without an Android device.

Run from the repository root: python .github/scripts/check_read_records.py
Room's schema validation and Android rendering are covered by instrumentation tests.
"""
import json
import pathlib
import re
import sqlite3

ROOT = pathlib.Path(__file__).resolve().parents[2]
schema = json.loads((ROOT / "app/schemas/io.legado.app.data.AppDatabase/93.json").read_text(encoding="utf-8"))
connection = sqlite3.connect(":memory:")
connection.row_factory = sqlite3.Row
connection.create_collation("localized", lambda a, b: (a > b) - (a < b))
for table in schema["database"]["entities"]:
    connection.execute(table["createSql"].replace("${TABLE_NAME}", table["tableName"]))
    for index in table.get("indices", []):
        connection.execute(index["createSql"].replace("${TABLE_NAME}", table["tableName"]))
for view in schema["database"].get("views", []):
    connection.execute(view["createSql"].replace("${VIEW_NAME}", view["viewName"]))

connection.executescript("""
    INSERT INTO books(bookUrl,name,author,coverUrl,origin) VALUES('one','唯一','作者','cover','source');
    INSERT INTO books(bookUrl,name,author) VALUES('two','同名','甲'),('three','同名','乙');
    INSERT INTO books(bookUrl,name,author,customCoverUrl,origin) VALUES('custom','自定义','作者','local-cover','source');
    INSERT INTO readRecord VALUES('','唯一',100,1),('device','唯一',50,2),('other','同名',70,3),('device','已删除',10,4),('device','自定义',20,5);
""")
before = connection.execute("SELECT count(*),sum(readTime) FROM readRecord").fetchone()
migrations = (ROOT / "app/src/main/java/io/legado/app/data/DatabaseMigrations.kt").read_text(encoding="utf-8")
block = migrations.split("val migration_93_94 =", 1)[1].split('@Suppress("ClassName")', 1)[0]
statements = re.findall(r'db\.execSQL\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)', block, re.S)
assert len(statements) == 5, f"Unexpected migration structure: {len(statements)} statements"
for multi, single in statements:
    connection.execute(multi or single)
after = connection.execute("SELECT count(*),sum(readTime) FROM readRecord").fetchone()
assert tuple(before) == tuple(after) == (5, 250), (tuple(before), tuple(after))
unique = connection.execute("SELECT * FROM readRecord WHERE bookUrl='one' ORDER BY deviceId").fetchall()
assert len(unique) == 2 and unique[0]["deviceId"] == ""
assert all(row["author"] == "作者" and row["coverUrl"] == "cover" and row["coverOrigin"] == "source" for row in unique)
legacy_key = "legacy:" + "同名".encode().hex().upper()
ambiguous = connection.execute("SELECT * FROM readRecord WHERE bookName='同名'").fetchone()
assert ambiguous["bookUrl"] == legacy_key and ambiguous["author"] == "" and ambiguous["coverUrl"] == ""
custom = connection.execute("SELECT * FROM readRecord WHERE bookUrl='custom'").fetchone()
assert custom["coverUrl"] == "local-cover" and custom["coverOrigin"] == ""
connection.execute("SELECT hasLoginUrl FROM book_sources_part").fetchall()

# Same title, distinct identities; cross-device totals; paired cover/origin snapshot.
connection.executescript("""
    INSERT INTO readRecord(deviceId,bookName,bookUrl,readTime,lastRead,author,coverUrl,coverOrigin)
    VALUES('one','same','first',100,100,'','',''),
          ('two','same','first',20,200,'A','snapshot','snapshot-source'),
          ('one','same','second',30,100,'B','','');
    INSERT INTO books(bookUrl,name,author,coverUrl,origin) VALUES('first','same','A','current','source'),('second','same','B',null,'source2');
""")
dao = (ROOT / "app/src/main/java/io/legado/app/data/dao/ReadRecordDao.kt").read_text(encoding="utf-8")
query = re.search(r'private const val SHOW_RECORDS = """(.*?)"""', dao, re.S).group(1)
rows = connection.execute(query + " where r.bookName like '%' || ? || '%'", ("same",)).fetchall()
assert len(rows) == 2
records = {row["bookUrl"]: row for row in rows}
assert records["first"]["readTime"] == 120 and records["first"]["author"] == "A"
assert records["first"]["coverUrl"] == "snapshot" and records["first"]["coverOrigin"] == "snapshot-source"
assert records["second"]["readTime"] == 30 and records["second"]["author"] == "B"
connection.execute("DELETE FROM books WHERE bookUrl='first'")
assert connection.execute(query + " where r.bookUrl='first'").fetchone()["coverUrl"] == "snapshot"
connection.execute("DELETE FROM readRecord WHERE bookUrl='second'")
assert connection.execute("SELECT count(*) FROM readRecord WHERE bookUrl='first'").fetchone()[0] == 2
plan = connection.execute("EXPLAIN QUERY PLAN " + query).fetchall()
assert any("index_readRecord_bookUrl_lastRead" in row[3] for row in plan), [tuple(row) for row in plan]

# Compare the migrated table shape with Room's freshly generated schema if available.
latest = json.loads((ROOT / "app/schemas/io.legado.app.data.AppDatabase/94.json").read_text(encoding="utf-8"))
expected = next(table for table in latest["database"]["entities"] if table["tableName"] == "readRecord")
expected_db = sqlite3.connect(":memory:")
expected_db.execute(expected["createSql"].replace("${TABLE_NAME}", "readRecord"))
for index in expected.get("indices", []):
    expected_db.execute(index["createSql"].replace("${TABLE_NAME}", "readRecord"))
actual_columns = [tuple(row) for row in connection.execute("PRAGMA table_info(readRecord)")]
expected_columns = expected_db.execute("PRAGMA table_info(readRecord)").fetchall()
assert actual_columns == expected_columns, (actual_columns, expected_columns)
actual_indexes = sorted(tuple(row)[1:4] for row in connection.execute("PRAGMA index_list(readRecord)"))
expected_indexes = sorted(tuple(row)[1:4] for row in expected_db.execute("PRAGMA index_list(readRecord)"))
assert actual_indexes == expected_indexes, (actual_indexes, expected_indexes)
print("PASS: migration preserves all 5 legacy rows / 250ms, unique metadata backfill, ambiguous legacy keys, empty device provenance, view, indexed same-title isolation, snapshot cover/origin, full Room table shape")
