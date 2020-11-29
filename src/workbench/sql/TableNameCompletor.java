package workbench.sql;

import java.util.ArrayList;

import jline.Completor;

import workbench.db.WbConnection;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import java.util.List;
import java.util.stream.Collectors;

import workbench.gui.completion.SelectAllMarker;
import workbench.gui.completion.StatementContext;

import workbench.util.StringUtil;


/**
 * A JLine Completor that loads up the tables and columns in the DB
 *
 * @author Jeremiah Spaulding, Thomas Kellerer
 */
public class TableNameCompletor
  implements Completor
{
  /**
   * The connection for which this completor works.
   */
  private WbConnection connection;

  private String currentPrefix;
  private List<String> currentList;
  private int nextCycleIndex;

  /**
   * Simple constructor called only by BatchRunner
   *
   * @param stmnt
   */
  public TableNameCompletor(WbConnection conn)
  {
    this.connection = conn;
  }

  public boolean isSameConnection(WbConnection conn)
  {
    if (conn == null) return false;
    if (this.connection == null) return false;
    if (conn == this.connection) return true;

    if (conn.getUrl().equals(this.connection.getUrl()))
    {
      if (conn.getCurrentUser().equals(this.connection.getCurrentUser())) return true;
    }
    return false;
  }

  /**
   * This is called with the buffer to get completion candidates when tab is hit
   *
   * @param buffer
   * @param candidates
   * @param cursor
   */
  @Override
  public int complete(String buffer, int cursor, List candidates)
  {
    if (connection == null || connection.isClosed())
    {
      return -1;
    }
    LogMgr.logDebug(new CallerInfo(){}, "Completer called with cursor position=" + cursor + ", statement: "+ buffer);

    StatementContext ctx = new StatementContext(connection, buffer, cursor);
    if (!ctx.isStatementSupported())
    {
      LogMgr.logDebug(new CallerInfo(){}, "Statement not supported");
      return -1;
    }

    List<Object> data = ctx.getData();

    if (!data.isEmpty() && data.get(0) instanceof SelectAllMarker)
    {
      data.remove(0);
    }
    List<String> names = data.stream().map(Object::toString).collect(Collectors.toList());

    String token = ctx.getAnalyzer().getCurrentWord();
		int start = cursor;
		while (start > 0 && !Character.isWhitespace(buffer.charAt(start-1)))
    {
			start--;
    }

    String prefix = buffer.substring(0, start);

		if (currentPrefix != null && currentPrefix.equals(prefix) && currentList != null)
		{
			if (nextCycleIndex >= currentList.size()) //back to the beginning
			{
				nextCycleIndex = 0;
			}

			candidates.add(currentList.get(nextCycleIndex++));
			return prefix.length();
		}

    if (StringUtil.isBlank(token) && !names.isEmpty())
    {
      currentList = new ArrayList<>();
      currentList.add(names.get(0));
    }
    else
    {
      currentList = names.stream().filter(x -> startsWithIgnoreCase(x, token)).collect(Collectors.toList());
    }

		if (currentList.isEmpty())
		{
			currentPrefix = null;
			nextCycleIndex = -1;
			return -1;
		}

		currentPrefix = prefix;
		nextCycleIndex = 0;
		candidates.add(currentList.get(nextCycleIndex++));
    return prefix.length();
  }

  private boolean startsWithIgnoreCase(String input, String compare)
  {
    if (input == null || compare == null) return false;
    return input.toLowerCase().startsWith(compare.toLowerCase());
  }
}
