/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 *
 */
package workbench.db.oracle;

import java.util.Collections;
import java.util.List;

import workbench.db.ProcedureDefinition;
import workbench.db.RoutineType;

import workbench.util.CollectionUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Kellerer
 */
public class OraclePackageParserTest
{
  String decl = "CREATE   OR   REPLACE PACKAGE emp_actions AS  -- spec \n" +
             "   TYPE EmpRecTyp IS RECORD (emp_id INT, salary REAL); \n" +
             "   CURSOR desc_salary RETURN EmpRecTyp \n" +
             "   PROCEDURE hire_employee ( \n" +
             "      ename  VARCHAR2, \n" +
             "      job    VARCHAR2, \n" +
             "      mgr    NUMBER, \n" +
             "      sal    NUMBER, \n" +
             "      comm   NUMBER, \n" +
             "      deptno NUMBER) \n" +
             "   PROCEDURE fire_employee; \n" +
             "   PROCEDURE fire_employee (emp_id NUMBER); \n" +
             "   PROCEDURE fire_employee (emp_id NUMBER, fire_date DATE); \n" +
             "END emp_actions;";

  String body = "CREATE \nOR\t    REPLACE PACKAGE BODY emp_actions AS  -- body \n" +
             "   CURSOR desc_salary RETURN EmpRecTyp IS \n" +
             "      SELECT empno, sal FROM emp ORDER BY sal DESC; \n" +
             "   /** Procedure hire_employee **/ \n" +
             "   PROCEDURE hire_employee( \n" +
             "      ename  VARCHAR2, \n" +
             "      job    VARCHAR2, \n" +
             "      mgr    NUMBER, \n" +
             "      sal    NUMBER, \n" +
             "      comm   NUMBER DEFAULT 0, \n" +
             "      deptno NUMBER DEFAULT 42) \n" +
             "IS \n" +
             "   BEGIN \n" +
             "      INSERT INTO emp VALUES (empno_seq.NEXTVAL, ename, job, \n" +
             "         mgr, SYSDATE, sal, comm, deptno) \n" +
             "   END hire_employee; \n" +
             "---------------------------------------------------- \n" +
             " \n" +
             " \n" +
             "---------------------------------------------------- \n" +
             "   PROCEDURE fire_employee(emp_id NUMBER) IS \n" +
             "   BEGIN \n" +
             "      DELETE FROM emp WHERE empno = emp_id; \n" +
             "   END fire_employee; \n" +
             "---------------------------------------------------- \n" +
             " \n" +
             " \n" +
             " \n" +
             "---------------------------------------------------- \n" +
             "   PROCEDURE fire_employee IS \n" +
             "   BEGIN \n" +
             "      DELETE FROM emp WHERE empno = emp_id; \n" +
             "   END fire_employee; \n" +
             "---------------------------------------------------- \n" +
             " \n" +
             " \n" +
             " \n" +
             " \n" +
             "---------------------------------------------------- \n" +
             "   PROCEDURE fire_employee(emp_id NUMBER, fire_date DATE DEFAULT TRUNC(SYSDATE) - 1) IS \n" +
             "   BEGIN \n" +
             "      DELETE FROM emp WHERE empno = emp_id; \n" +
             "   END fire_employee; \n" +
             "---------------------------------------------------- \n" +
             "END emp_actions;";


  @Test
  public void testGetProcSource()
  {
    String script = decl + "\n/\n/" + body + "\n/\n/";
    List<String> params = CollectionUtil.arrayList("emp_id", "fire_date");
    ProcedureDefinition proc = new ProcedureDefinition("FIRE_EMPLOYEE", RoutineType.procedure);
    CharSequence source = OraclePackageParser.getProcedureSource(script, proc, params);
    assertNotNull(source);
    String src = source.toString().trim();
    assertTrue(src.startsWith("PROCEDURE fire_employee"));

    params = CollectionUtil.arrayList("ename", "job", "mgr", "sal","comm", "deptno");
    proc = new ProcedureDefinition("HIRE_EMPLOYEE", RoutineType.procedure);
    source = OraclePackageParser.getProcedureSource(script, proc, params);
    assertNotNull(source);
    src = source.toString().trim();
    assertTrue(src.startsWith("PROCEDURE hire_employee"));
    assertTrue(src.endsWith("END hire_employee;"));
  }

  @Test
  public void testParser()
  {
    String script = decl + "\n/\n/" + body;
    OraclePackageParser parser = new OraclePackageParser(script);
    String parsedBody = parser.getPackageBody();
    String parsedDecl = parser.getPackageDeclaration();
    assertEquals(body, parsedBody);
    assertEquals(decl, parsedDecl);
  }

  @Test
  public void testFindProcWithFunctionDefault()
    throws Exception
  {
    String source =
      "create or replace package my_package\n" +
      "as\n" +
      "  procedure some_procedure(p_name varchar default 'IMP_EXP', p_suffix varchar default to_char(sysdate, 'yyyy_mm_dd_hh24mi'), p_flag boolean default TRUE);\n" +
      "end my_package;\n" +
      "/\n" +
      "\n" +
      "create or replace package body my_package\n" +
      "as\n" +
      "  procedure some_procedure(p_name varchar default 'IMP_EXP',\n" +
      "                           p_suffix varchar default to_char(sysdate, 'yyyy_mm_dd_hh24mi'),\n" +
      "                           p_flag boolean default TRUE);\n" +
      "  as\n" +
      "    l_full_name      VARCHAR(500);\n" +
      "  begin\n" +
      "    l_full_name := 'EXPORT_'||p_name||'_'||p_suffix;\n" +
      "  end some_procedure;\n" +
      "\n" +
      "\n" +
      "end my_package;\n" +
      "/\n";

    ProcedureDefinition proc = new ProcedureDefinition("some_procedure", RoutineType.procedure);
    CharSequence proSource = OraclePackageParser.getProcedureSource(source, proc, List.of("P_NAME", "P_SUFFIX", "P_FLAG"));
    assertNotNull(proSource);
    assertTrue(proSource.toString().trim().endsWith("some_procedure;"));
  }

  @Test
  public void testFindProc()
  {
    String script = decl + "\n/\n/" + body + "\n/\n";
    int pos = script.indexOf("   PROCEDURE hire_employee(") + 3;
    ProcedureDefinition proc = new ProcedureDefinition("HIRE_EMPLOYEE", RoutineType.procedure);
    int procPos = OraclePackageParser.findProcedurePosition(script, proc, CollectionUtil.arrayList("emp_id"));
    assertEquals(pos, procPos);

    pos = script.indexOf("PROCEDURE fire_employee(emp_id NUMBER, fire_date DATE DEFAULT TRUNC(SYSDATE) - 1");
    proc = new ProcedureDefinition("fire_employee", RoutineType.procedure);
    procPos = OraclePackageParser.findProcedurePosition(script, proc, CollectionUtil.arrayList("emp_id", "fire_date"));
    assertEquals(pos, procPos);

    pos = script.indexOf("PROCEDURE fire_employee(emp_id NUMBER)");
    proc = new ProcedureDefinition("fire_employee", RoutineType.procedure);
    procPos = OraclePackageParser.findProcedurePosition(script, proc, CollectionUtil.arrayList("emp_id"));
    assertEquals(pos, procPos);

    pos = script.indexOf("PROCEDURE fire_employee IS");
    proc = new ProcedureDefinition("fire_employee", RoutineType.procedure);
    procPos = OraclePackageParser.findProcedurePosition(script, proc, Collections.emptyList());
    assertEquals(pos, procPos);
  }

  @Test
  public void testfindProcInHeader()
  {
    String script = decl + "\n/\n";
    int pos = script.indexOf("PROCEDURE hire_employee (");
    ProcedureDefinition proc = new ProcedureDefinition("HIRE_EMPLOYEE", RoutineType.procedure);
    int procPos = OraclePackageParser.findProcedurePosition(script, proc, CollectionUtil.arrayList("emp_id"));
    assertEquals(pos, procPos);

    pos = script.indexOf("PROCEDURE fire_employee (emp_id NUMBER);");
    proc = new ProcedureDefinition("FIRE_EMPLOYEE", RoutineType.procedure);
    procPos = OraclePackageParser.findProcedurePosition(script, proc, CollectionUtil.arrayList("emp_id"));
    assertEquals(pos, procPos);

    pos = script.indexOf("PROCEDURE fire_employee (emp_id NUMBER, fire_date DATE); ");
    proc = new ProcedureDefinition("FIRE_EMPLOYEE", RoutineType.procedure);
    procPos = OraclePackageParser.findProcedurePosition(script, proc, CollectionUtil.arrayList("EMP_ID", "FIRE_DATE"));
    assertEquals(pos, procPos);

  }
}
