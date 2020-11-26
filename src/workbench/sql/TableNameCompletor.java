package workbench.sql;

import jline.Completor;

import workbench.db.WbConnection;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import java.util.List;
import java.util.stream.Collectors;

import workbench.gui.completion.SelectAllMarker;
import workbench.gui.completion.StatementContext;



/**
 * A JLine Completor that loads up the tables and columns in the DB
 *
 * @author Jeremiah Spaulding
 */
public class TableNameCompletor
  implements Completor
{
  /**
   * When we get a tab session, store the first thing we were given to cycle back through it
   */
  private String originalPrefix = "";
  private String originalToken = "";
  /**
   * Current list of potentials so we can cycle through them in the same order
   */
  private List<String> currentCycleList;
  private int nextCycleIndex = -1;

  /**
   * The connection for which this completor works.
   */
  private WbConnection connection;

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

    LogMgr.logDebug(new CallerInfo(){}, "Checking code completion at position " + cursor + " for: " + buffer);

    StatementContext ctx = new StatementContext(connection, buffer, cursor);
    List<Object> data = ctx.getData();
    if (!data.isEmpty() && data.get(0) instanceof SelectAllMarker)
    {
      data.remove(0);
    }
    List<String> toSearch = data.stream().map(Object::toString).collect(Collectors.toList());

    String token = ctx.getAnalyzer().getCurrentWord();
    String prefix = ctx.getAnalyzer().getQualifierLeftOfCursor();

    //This is not the first time they hit tab
    if (originalPrefix != null && originalPrefix.equals(prefix) && token.startsWith(originalToken) && currentCycleList.contains(token))   //we are cycling through
    {
      if (nextCycleIndex >= currentCycleList.size()) //back to the beginning
      {
        nextCycleIndex = 0;
      }

      candidates.add(currentCycleList.get(nextCycleIndex++));
      return prefix.length();
    }

    //otherwise if it is the first time, starting fresh
    currentCycleList = toSearch.stream().filter(x -> x.startsWith(token)).collect(Collectors.toList());
    if (currentCycleList.isEmpty())
    {
      originalPrefix = "";
      originalToken = "";
      nextCycleIndex = -1;
      return -1;
    }
    originalPrefix = prefix;
    originalToken = token;
    nextCycleIndex = 0;
    candidates.add(currentCycleList.get(nextCycleIndex++));

    return prefix.length();
  }

}
