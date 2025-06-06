/*
 * This file is part of a <a href="http://ostermiller.org/syntax/">syntax
 * highlighting</a> package.
 * Copyright (C) 2002 Stephen Ostermiller
 * http://ostermiller.org/contact.pl?regarding=Syntax+Highlighting
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * See COPYING.TXT for details.
 */

package workbench.sql.lexer;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;
import workbench.util.CharSequenceReader;

/**
 * StandardLexer is a SQL language lexer.  Created with JFlex.  An example of how it is used:
 *  <CODE>
 *  <PRE>
 *  StandardLexer shredder = new StandardLexer(System.in);
 *  SQLToken t;
 *  while ((t = shredder.getNextToken()) != null){
 *      System.out.println(t);
 *  }
 *  </PRE>
 *  </CODE>
 *
 * @see SQLToken
 */

%%

%public
%implements SQLLexer
%class StandardLexer
%function getNextToken
%type SQLToken
%column
%char
%unicode
%ignorecase
%state COMMENT

%{
	private int lastToken;
	private int nextState=YYINITIAL;
	private StringBuilder commentBuffer = new StringBuilder();
    private int commentStartChar = 0;

	/**
	 * NextToken method that allows to control if whitespace and comments are
	 * returned as tokens.
	 */
	public SQLToken getNextToken(boolean returnComments, boolean returnWhiteSpace)
	{
      try
      {
        SQLToken t = getNextToken();
        if (returnComments && returnWhiteSpace) return t;

        while (t != null && ((!returnWhiteSpace && t.isWhiteSpace()) || (!returnComments && t.isComment())))
        {
          t = getNextToken();
        }
        return t;
      }
      catch (Exception e)
      {
        e.printStackTrace();
        return null;
      }
	}

  /**
   * Closes the current input stream, and resets the scanner to read from a new input stream.
   * All internal variables are reset, the old input stream  cannot be reused
   * (content of the internal buffer is discarded and lost).

   * The lexical state is set to the initial state.
   * Subsequent tokens read from the lexer will start with the line, char, and column
   * values given here.
   *
   * @param reader The new input.
   * @param yyline The line number of the first token.
   * @param yychar The position (relative to the start of the stream) of the first token.
   * @param yycolumn The position (relative to the line) of the first token.
   * @throws IOException if an IOExecption occurs while switching readers.
   */
  public void reset(java.io.Reader reader, int yychar, int yycolumn)
    throws IOException
  {
    yyreset(reader);
    this.yychar = yychar;
    this.yycolumn = yycolumn;
  }

  public void setInput(String sql)
  {
    try
    {
      reset(new StringReader(sql), 0,0);
    }
    catch (Exception ex)
    {
       // cannot happen
    }
  }

  public void setInput(CharSequence sql)
  {
    try
    {
      reset(new CharSequenceReader(sql), 0,0);
    }
    catch (Exception ex)
    {
       // cannot happen
    }
  }

  StandardLexer(String source)
  {
    this(new StringReader(source));
  }

  StandardLexer(CharSequence source)
  {
    this(new CharSequenceReader(source));
  }
%}


wsp = [ \r\n\t\f]+

keyword=(


(ALTER{wsp}SESSION)|
(PRIMARY{wsp}KEY)|
(FOREIGN{wsp}KEY)|
(UNION{wsp}ALL)|
(EXCEPT{wsp}ALL)|
(PARTITION{wsp}BY)|
(GROUP{wsp}BY)|
(ORDER{wsp}BY)|
(CREATE{wsp}OR{wsp}REPLACE)|
(CREATE{wsp}OR{wsp}ALTER)|
(IS{wsp}NOT{wsp}NULL)|
(MATERIALIZED{wsp}VIEW)|
(OUTER{wsp}JOIN)|
(CROSS{wsp}JOIN)|
(CROSS{wsp}JOIN{wsp}LATERAL)|
(OUTER{wsp}APPLY)|
(CROSS{wsp}APPLY)|
(FULL{wsp}JOIN)|
(FULL{wsp}JOIN{wsp}LATERAL)|
(FULL{wsp}OUTER{wsp}JOIN)|
(FULL{wsp}OUTER{wsp}JOIN{wsp}LATERAL)|
(JOIN{wsp}LATERAL)|
(INNER{wsp}JOIN)|
(INNER{wsp}JOIN{wsp}LATERAL)|
(LEFT{wsp}JOIN)|
(LEFT{wsp}JOIN{wsp}LATERAL)|
(LEFT{wsp}OUTER{wsp}JOIN)|
(LEFT{wsp}OUTER{wsp}JOIN{wsp}LATERAL)|
(RIGHT{wsp}JOIN)|
(RIGHT{wsp}JOIN{wsp}LATERAL)|
(RIGHT{wsp}OUTER{wsp}JOIN)|
(RIGHT{wsp}OUTER{wsp}JOIN{wsp}LATERAL)|
(NATURAL{wsp}JOIN)|
(IF{wsp}NOT{wsp}EXISTS)|
(IF{wsp}EXISTS)|
(IS{wsp}NULL)|
(IS{wsp}NOT{wsp}NULL)|
(IS{wsp}JSON)|
(IS{wsp}NOT{wsp}JSON)|
(CHARACTER{wsp}VARYING)|
(PRIMARY{wsp}KEY)|
(SNAPSHOT{wsp}LOG)|
(IF{wsp}EXISTS)|
(IF{wsp}NOT{wsp}EXISTS)|
(WITHIN{wsp}GROUP)|
(GROUPING{wsp}SETS)|
(OWNED{wsp}BY)|
(FETCH{wsp}FIRST)|
(FETCH{wsp}NEXT)|
(NEXT{wsp}VALUE{wsp}FOR)|
(NOT{wsp}MATCHED)|
"AFTER"|
"AGGREGATE"|
"ALL"|
"ALTER"|
"ANALYZE"|
"AND"|
"ANY"|
"ARRAY"|
"AS"|
"ASC"|
"BEFORE"|
"BETWEEN"|
"BIGINT"|
"BINARY"|
"BIT"|
"BITMAP"|
"BIT_LENGTH"|
"BITVAR"|
"BLOB"|
"BOOLEAN"|
"CARDINALITY"|
"CASCADE"|
"CASE"|
"CAST"|
"CHAR"|
"CHAR_LENGTH"|
"CHARACTER"|
"CHECK"|
"CHECKPOINT"|
"CLOB"|
"CLOSE"|
"COALESCE"|
"COLLATE"|
"COLLATION"|
"COLUMN"|
"COMMENT"|
"COMMIT"|
"CONNECT"|
"CONNECTION"|
"CONSTRAINT"|
"CONSTRAINTS"|
"CONTAINS"|
"CORRESPONDING"|
"COUNT"|
"CREATE"|
"CREATEUSER"|
"CROSS"|
"CURRENT"|
"CURRENT_DATE"|
"CURRENT_TIME"|
"CURRENT_TIMESTAMP"|
"CURRENT_USER"|
"CURSOR"|
"CURSOR_NAME"|
"CYCLE"|
"DATABASE"|
"DATE"|
"DAY"|
"DECIMAL"|
"DECLARE"|
"DEFAULT"|
"DEFERRABLE"|
"DEFERRED"|
"DELIMITER"|
"DELETE"|
"DEPTH"|
"DENSE_RANK"|
"DESCRIBE"|
"DISCONNECT"|
"DISTINCT"|
"DOMAIN"|
"DOUBLE"|
(DOUBLE{wsp}PRECISION)|
"DROP"|
"ELSE"|
"ENCODING"|
"ENCRYPTED"|
"END"|
"ESCAPE"|
"EVERY"|
"EXCEPT"|
"EXISTS"|
"EXTERNAL"|
"EXTRACT"|
"FALSE"|
"FIRST"|
"FLOAT"|
"FOR"|
"FORWARD"|
"FROM"|
"FUNCTION"|
"GRANT"|
"GROUPING"|
"HAVING"|
"HOUR"|
"IDENTITY"|
"IGNORE"|
"IMMEDIATE"|
"IN"|
"INDEX"|
"INITIALLY"|
"INPUT"|
"INSERT"|
"INT"|
"INT4"|
"INT8"|
"INTEGER"|
"INTERSECT"|
"INTERVAL"|
"INTO"|
"IS"|
(IS{wsp}JSON)|
(IS{wsp}NOT{wsp}JSON)|
"ISOLATION"|
"ITERATE"|
"JOIN"|
"KEY"|
"LANGUAGE"|
"LATERAL"|
"LAST"|
"LEFT"|
"LENGTH"|
"LESS"|
"LIKE"|
"LIMIT"|
"LOAD"|
"LOCAL"|
"LOCALTIME"|
"LOCALTIMESTAMP"|
"LOCK"|
"LOWER"|
"MAP"|
"MATCH"|
"MATCHED"|
"MAX"|
"MAXVALUE"|
"MERGE"|
"MIN"|
"MINUS"|
"MINUTE"|
"MINVALUE"|
"MOD"|
"MODE"|
"MODIFIES"|
"MODIFY"|
"MODULE"|
"MONTH"|
"NAMES"|
"NATIONAL"|
"NCHAR"|
"NCLOB"|
"NEXT"|
"NONE"|
"NOT"|
"NOW"|
"NOWAIT"|
"NULL"|
"NULLS"|
"NULLABLE"|
"NUMBER"|
"NUMERIC"|
"NVARCHAR"|
"OBJECT"|
"OF"|
"OFFSET"|
"ON"|
"ONLY"|
"OPEN"|
"OPERATOR"|
"OR"|
"OUT"|
"OUTPUT"|
"OVER"|
"OVERLAPS"|
"OVERLAY"|
"OVERRIDING"|
"OWNER"|
"PACKAGE"|
"PARAMETER"|
"PARAMETERS"|
"PRIVILEGES"|
"PROCEDURE"|
"PUBLIC"|
"REAL"|
"RECREATE"|
"RECURSIVE"|
"REFERENCES"|
"REFERENCING"|
"RENAME"|
"REPEATABLE"|
"REPLACE"|
"RESET"|
"RESTRICT"|
"RESULT"|
"RETURN"|
"RETURNING"|
"RETURNS"|
"REVOKE"|
"RIGHT"|
"ROLLBACK"|
"ROLLUP"|
"CUBE"|
"ROW"|
"ROWS"|
"RULE"|
"SAVEPOINT"|
"SCHEMA"|
"SCROLL"|
"SEARCH"|
"SECOND"|
"SELECT"|
"SEQUENCE"|
"SERIALIZABLE"|
"SESSION"|
"SESSION_USER"|
"SET"|
"SHARE"|
"SIMILAR"|
(SIMILAR{wsp}TO)|
"SMALLINT"|
"SNAPSHOT"|
(START{wsp}TRANSACTION)|
(BEGIN{wsp}TRANSACTION)|
(BEGIN{wsp}WORK)|
(BEGIN{wsp}ATOMIC)|
"SUBSTRING"|
"SUM"|
"SYNONYM"|
"SYSTIMESTAMP"|
"SYSDATE"|
"TABLE"|
"TEMPORARY"|
(GLOBAL{wsp}TEMPORARY)|
(LOCAL{wsp}TEMPORARY)|
"THEN"|
"TIME"|
"TIMESTAMP"|
"TRIGGER"|
"TRIM"|
"TRUNCATE"|
"TRANSACTION"|
"TRUE"|
"TYPE"|
"UNION"|
"UNIQUE"|
"UNTIL"|
"UPDATE"|
"UPPER"|
"USAGE"|
"USER"|
"USING"|
"VALID"|
"VALUES"|
"VARCHAR"|
"VARIABLE"|
"VERBOSE"|
"VERSION"|
"VIEW"|
"WBCONFIRM"|
"WBCOPY"|
"WBDATADIFF"|
"WBDEFINEPK"|
"WBENDBATCH"|
"WBEXEC"|
"WBFEEDBACK"|
"WBEXPORT"|
"WBIMPORT"|
"WBINCLUDE"|
"WBISOLATIONLEVEL"|
"WBLIST"|
"WBLISTDB"|
"WBLISTCAT"|
"WBLISTINDEXES"|
"WBLISTPKDEF"|
"WBLISTPROCS"|
"WBLISTTRIGGERS"|
"WBLISTSCHEMAS"|
"WBLOADPKMAP"|
"WBREPORT"|
"WBENABLEWARNINGS"|
"WBGREPDATA"|
"WBGREPSOURCE"|
"WBSCHEMADIFF"|
"WBSELECTBLOB"|
"WBSTARTBATCH"|
"WBVARDEF"|
"WBVARDEFINE"|
"WBVARDELETE"|
"WBVARLIST"|
"WBXSLT"|
"WINDOW"|
"WHEN"|
"WHENEVER"|
"WHERE"|
"WITH"|
"WITHOUT"|
"WORK"|
"WRITE"|
"WRAPPER"|
"YEAR"|
"ZONE"|
"\$BLOBFILE"|
"\$CLOBFILE"
)

whitespace=([ \r\n\t\f])
wbvar=(\$\[)(\&|\?)?[a-zA-Z_0-9]+(\])|(\$\{)(\&|\?)?[a-zA-Z_0-9]+(\})
identifier=([^ \"\r\n\t\f\+\-\*\/\<\>\=\~\!\%\^\&\'\~\?\(\)\[\]\,\;\:\.0-9][^ \r\n\t\f\+\-\*\/\<\>\=\~\!\%\^\&\'\"\~\?\(\)\]\[\,\;\:\*]*)|(\"[^\r\n\t\f\"]*\")
digit=([0-9])
digits=({digit}+)
separator=([\(\)\[\]\,\;\:\*])
operator=([\+\-\*\/\<\>\=\~\!\%\^\&\?]|"||"|"!="|"<>"|"<="|">="|"=>"|"(+)")
integer=([-+]?{digits})
string=([\'](([^\']|\'\')*)[\']|[Nn][\'](([^\']|\'\')*)[\'])

bitstring=("B"[\']([01]+)[\'])
stringerror=([\'](([^\r\n\'])*)[\r\n])
bitstringerror1=("B"[\']([^01\r\n]*)[\'])
bitstringerror2=("B"[\'](([^\r\n\']|[\\][\'])*)[\r\n])
floatpoint=(([+-]?{digits}"."({digits}?)("E"[+-]{digits})?)|(([+-]?{digits}?)"."{digits}("E"[+-]{digits})?)|([+-]?{digits}"E"[+-]{digits}))
linecomment=("--"[^\r\n]*)
commentstart="/*"
commenttext=(([^\*\/]|([\*]+[^\*\/])|([\/]+[^\*\/]))*)
commentend=(([\*]*)"/")
%%

<YYINITIAL> {linecomment} {
    nextState = YYINITIAL;
    lastToken = SQLToken.COMMENT_END_OF_LINE;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {commentstart} {
    nextState = COMMENT;
    commentBuffer.setLength(0);
    commentBuffer.append(yytext());
    commentStartChar = yychar;
    yybegin(nextState);
}

<COMMENT> {commentstart} {
    nextState = COMMENT;
    commentBuffer.append(yytext());
    yybegin(nextState);
}

<COMMENT> {commenttext} {
    nextState = COMMENT;
    commentBuffer.append(yytext());
    yybegin(nextState);
}

<COMMENT> {commentend} {
    commentBuffer.append(yytext());
    nextState = YYINITIAL;
    lastToken = SQLToken.COMMENT_TRADITIONAL;
    SQLToken t = (new SQLToken(lastToken,commentBuffer.toString(),commentStartChar,commentStartChar+commentBuffer.length(),nextState));
    yybegin(nextState);
    return(t);
}

<COMMENT> <<EOF>> {
    nextState = YYINITIAL;
    lastToken = SQLToken.ERROR_UNCLOSED_COMMENT;
    SQLToken t = (new SQLToken(lastToken,commentBuffer.toString(),commentStartChar,commentStartChar+commentBuffer.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {keyword} {
    nextState = YYINITIAL;
    lastToken = SQLToken.RESERVED_WORD;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {wbvar} {
    nextState = YYINITIAL;
    lastToken = SQLToken.WB_VAR;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {separator} {
    nextState = YYINITIAL;
    lastToken = SQLToken.SEPARATOR;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {operator} {
    nextState = YYINITIAL;
    lastToken = SQLToken.OPERATOR;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {bitstring} {
    nextState = YYINITIAL;
    lastToken = SQLToken.LITERAL_BIT_STRING;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {bitstringerror1} {
    nextState = YYINITIAL;
    lastToken = SQLToken.ERROR_UNCLOSED_BIT_STRING;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {bitstringerror2} {
    nextState = YYINITIAL;
    lastToken = SQLToken.ERROR_BAD_BIT_STRING;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {identifier} {
    nextState = YYINITIAL;
    lastToken = SQLToken.IDENTIFIER;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {integer} {
    nextState = YYINITIAL;
    lastToken = SQLToken.LITERAL_INTEGER;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {string} {
    nextState = YYINITIAL;
    lastToken = SQLToken.LITERAL_STRING;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {stringerror} {
    nextState = YYINITIAL;
    lastToken = SQLToken.ERROR_UNCLOSED_STRING;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {floatpoint} {
    nextState = YYINITIAL;
    lastToken = SQLToken.LITERAL_FLOAT;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL> {whitespace}* {
    nextState = YYINITIAL;
    lastToken = SQLToken.WHITE_SPACE;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}

<YYINITIAL, COMMENT> [^] {
    nextState = YYINITIAL;
    lastToken = SQLToken.ERROR;
    String text = yytext();
    SQLToken t = (new SQLToken(lastToken,text,yychar,yychar+text.length(),nextState));
    yybegin(nextState);
    return(t);
}