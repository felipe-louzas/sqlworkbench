/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.util;

import workbench.sql.parser.ParserType;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DdlObjectInfoTest
{
  public DdlObjectInfoTest()
  {
  }

  @Test
  public void testOracle()
    throws Exception
  {
    String sql = "-- test\ncreate or \t replace\n\nprocedure bla";
    DdlObjectInfo info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "bla");
    assertEquals(info.getDisplayType(), "Procedure");

    sql = "-- test\ncreate unique bitmap index idx_test on table (x,y);";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "idx_test");
    assertEquals(info.getDisplayType(), "Index");

    sql = "-- test\ncreate or replace package \n\n some_package \t\t\n as something";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("some_package", info.getObjectName());
    assertEquals("PACKAGE", info.getObjectType());

    sql = "-- test\ncreate package body \n\n some_body \t\t\n as something";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("some_body", info.getObjectName());
    assertEquals("PACKAGE BODY", info.getObjectType());

    sql = "CREATE FLASHBACK ARCHIVE main_archive";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("main_archive", info.getObjectName());
    assertEquals("FLASHBACK ARCHIVE", info.getObjectType());

    sql = "analyze table foo validate structure";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("foo", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    sql = "analyze index foo_idx validate structure";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("foo_idx", info.getObjectName());
    assertEquals("INDEX", info.getObjectType());

    sql = "create type body my_type is begin\n null; end;";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("my_type", info.getObjectName());
    assertEquals("TYPE BODY", info.getObjectType());

    sql = "alter function mystuff compile";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertNotNull(info);
    assertEquals("mystuff", info.getObjectName());
    assertEquals("FUNCTION", info.getObjectType());

    sql = "create force view v_test as select * from t;";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "v_test");
    assertEquals(info.getDisplayType(), "View");

    sql = "alter system set pga_target = 8g scope=spfile;";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("SYSTEM", info.getObjectType());
    assertNull(info.getObjectName());

    sql = "alter index FOO.\"BAR_IDX\" rebuild tablespace users;";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("INDEX", info.getObjectType());
    assertEquals("FOO.BAR_IDX", info.getObjectName());

    sql = "create pluggable database test_db;";
    info = new DdlObjectInfo(sql, ParserType.Oracle);
    assertTrue(info.isValid());
    assertEquals("DATABASE", info.getObjectType());
    assertEquals("test_db", info.getObjectName());
  }

  @Test
  public void testPostgres()
  {
    DdlObjectInfo info;

    info = new DdlObjectInfo("create global temporary table stuff.temp if not exists (id integer);", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("stuff.temp", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    info = new DdlObjectInfo("create tablespace users location 'foobar';", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("users", info.getObjectName());
    assertEquals("TABLESPACE", info.getObjectType());

    info = new DdlObjectInfo("create unlogged table foobar if not exists (id integer);", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("foobar", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    info = new DdlObjectInfo("create extension hstore", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("hstore", info.getObjectName());
    assertEquals("EXTENSION", info.getObjectType());

    String createRule =
      "create or replace rule insert_some_view_rule\n" +
      "as on insert to some_view\n" +
      "do instead \n" +
      "  insert into real_table (id, name)\n" +
      "  values (new.id, new.real_name);";

    info = new DdlObjectInfo(createRule, ParserType.Postgres);
    assertEquals("RULE", info.getObjectType());
    assertEquals("insert_some_view_rule", info.getObjectName());

    info = new DdlObjectInfo("create index concurrently if not exists ix_one on t(id)", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("ix_one", info.getObjectName());
    assertEquals("INDEX", info.getObjectType());

    info = new DdlObjectInfo("create index concurrently on t(id)", ParserType.Postgres);
    assertTrue(info.isValid());
    assertNull(info.getObjectName());
    assertEquals("INDEX", info.getObjectType());

    info = new DdlObjectInfo("create index concurrently ix_one on t(id)", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("ix_one", info.getObjectName());
    assertEquals("INDEX", info.getObjectType());

    info = new DdlObjectInfo("create user mapping for zaphod server heart options ()", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("zaphod", info.getObjectName());
    assertEquals("USER MAPPING", info.getObjectType());

    info = new DdlObjectInfo("create user mapping if not exists for zaphod server heart options ()", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("zaphod", info.getObjectName());
    assertEquals("USER MAPPING", info.getObjectType());

    info = new DdlObjectInfo("create server if not exists outerspace foreign data wrapper warp", ParserType.Postgres);
    assertTrue(info.isValid());
    assertEquals("outerspace", info.getObjectName());
    assertEquals("SERVER", info.getObjectType());
  }

  @Test
  public void testPostgresMultiple()
  {
    DdlObjectInfo info;
    info = new DdlObjectInfo("drop table a,  b, c cascade", ParserType.Postgres);
    assertEquals("a, b, c", info.getObjectName());
    info = new DdlObjectInfo("drop table public.a,  stuff.b, foo.c cascade", ParserType.Postgres);
    assertEquals("public.a, stuff.b, foo.c", info.getObjectName());
    info = new DdlObjectInfo("drop table public.a, b;", ParserType.Postgres);
    assertEquals("public.a, b", info.getObjectName());
  }

  @Test
  public void testSqlServer()
  {
    DdlObjectInfo info;
    String sql;

    sql = "create nonclustered index idx_test on table (x,y);";
    info = new DdlObjectInfo(sql, ParserType.SqlServer);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "idx_test");
    assertEquals(info.getDisplayType(), "Index");

    sql = "create table ##mytemp (id integer);";
    info = new DdlObjectInfo(sql, ParserType.SqlServer);
    assertTrue(info.isValid());
    assertEquals("##mytemp", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    sql = "create table [ignore the standard] (id integer);";
    info = new DdlObjectInfo(sql, ParserType.SqlServer);
    assertTrue(info.isValid());
    assertEquals("ignore the standard", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    sql = "create table #someTemp(some_col integer);";
    info = new DdlObjectInfo(sql, ParserType.SqlServer);
    assertTrue(info.isValid());
    assertEquals("#someTemp", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());
  }

  @Test
  public void testMySQL()
  {
    DdlObjectInfo info;
    String sql;

    sql = "create table `stupid` (id integer;";
    info = new DdlObjectInfo(sql, ParserType.MySQL);
    assertTrue(info.isValid());
    assertEquals("stupid", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());
  }

  @Test
  public void testGetObjectInfo()
    throws Exception
  {
    DdlObjectInfo info;
    String sql;

    sql = "recreate view v_test as select * from t;";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "v_test");
    assertEquals(info.getDisplayType(), "View");

    sql = "create table my_schema.my_table (nr integer);";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "my_schema.my_table");
    assertEquals(info.getDisplayType(), "Table");

    sql = "-- test\ncreate memory table my_table (nr integer);";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "my_table");
    assertEquals(info.getDisplayType(), "Table");

    sql = "drop memory table my_table;";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "my_table");
    assertEquals(info.getDisplayType(), "Table");

    sql = "drop index idx_test;";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "idx_test");
    assertEquals(info.getDisplayType(), "Index");

    sql = "drop function f_answer;";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals("f_answer", info.getObjectName());
    assertEquals("Function", info.getDisplayType());

    sql = "drop procedure f_answer;";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals("f_answer", info.getObjectName());
    assertEquals("Procedure", info.getDisplayType());

    sql = "drop sequence s;";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals(info.getObjectName(), "s");
    assertEquals(info.getDisplayType(), "Sequence");

    sql = "drop role s;";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals("s", info.getObjectName());
    assertEquals("ROLE", info.getObjectType());

    sql = "-- test\ncreate \n\ntrigger test_trg for mytable";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals("test_trg", info.getObjectName());
    assertEquals("TRIGGER", info.getObjectType());

    sql = "CREATE TABLE IF NOT EXISTS some_table (id integer)";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals("some_table", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    sql = "DROP TABLE old_table IF EXISTS";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals("old_table", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    sql = "analyze local table foobar";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals("foobar", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    sql = "create index \"FOO\".\"IDX\" on foo.bar (id);";
    info = new DdlObjectInfo(sql);
    assertTrue(info.isValid());
    assertEquals("IDX", info.getObjectName());
    assertEquals("INDEX", info.getObjectType());

    info = new DdlObjectInfo("create temporary table if not exists temp (id integer);");
    assertTrue(info.isValid());
    assertEquals("temp", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());

    info = new DdlObjectInfo("create table foo_backup as select * from foo;");
    assertTrue(info.isValid());
    assertEquals("foo_backup", info.getObjectName());
    assertEquals("TABLE", info.getObjectType());
  }

}
