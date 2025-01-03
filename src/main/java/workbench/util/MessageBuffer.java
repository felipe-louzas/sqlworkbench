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
package workbench.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import workbench.interfaces.ResultLogger;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

/**
 * A class to store messages efficiently.
 *
 * The messages are internally stored in a LinkedList, but only up to
 * a specified maximum total size (in characters) of all messages.
 *
 * If the maximum is reached {@link #getBuffer()} will add "(...)" at the beginning
 * of the generated result to indicate that messages have been cut off.
 *
 * This ensures that collecting warnings or errors during long running
 * jobs, does not cause an OutOfMemory error.
 *
 * @author Thomas Kellerer
 */
public class MessageBuffer
{
  private final Deque<CharSequence> messages = new ArrayDeque<>();
  private int length = 0;
  private final String newLine = "\n";
  private final int maxLength;
  private boolean trimmed = false;

  /**
   * Create a new MessageBuffer, retrieving the max. number of entries
   * from the Settings object. If nothing has been specified in the .settings
   * file, a maximum number of 1000 entries is used.
   */
  public MessageBuffer()
  {
    this(Settings.getInstance().getIntProperty("workbench.messagebuffer.maxsize", 1024 * 1024 * 10));
  }

  /**
   * Create a new MessageBuffer limiting the total size of all messages to <tt>maxSizeInBytes</tt>.

   * @param maxSizeInBytes the max. size in bytes of all entries to hold in the internal list
   */
  public MessageBuffer(int maxSizeInBytes)
  {
    maxLength = maxSizeInBytes;
  }

  public synchronized void clear()
  {
    this.messages.clear();
    this.length = 0;
  }

  /**
   * Write the messages of this MessageBuffer directly to a ResultLogger
   * The internal buffer is cleared during the writing
   *
   * @return the total number of characters written
   */
  public synchronized int appendTo(ResultLogger log)
  {
    int size = 0;

    // Write everything as a single string if it's small enough
    // That looks more "snappy" for the user if the message contains many lines, but isn't that large
    if (length <= Settings.getInstance().getIntProperty("workbench.resultlog.single.string", 1024 * 1024))
    {
      CharSequence msg = getMessage();
      log.appendToLog(msg);
      size = msg.length();
    }
    else
    {
      while (messages.size() > 0)
      {
        CharSequence s = messages.removeFirst();
        size += s.length();
        log.appendToLog(s.toString());
      }
    }
    clear();
    return size;
  }

  /**
   * Returns the current messages without clearing the buffer.
   *
   */
  public synchronized CharSequence getMessage()
  {
    StringBuilder result = new StringBuilder(this.length + 50);
    if (trimmed) result.append("(...)\n");

    Iterator<CharSequence> itr = messages.iterator();
    while (itr.hasNext())
    {
      result.append(itr.next());
    }
    return result;
  }

  /**
   * Create a StringBuilder that contains the collected messages.
   *
   * Once the result is returned, the internal list is emptied.
   * This means the second call to this method returns an empty
   * buffer if no messages have been added between the calls.
   */
  public synchronized CharSequence getBuffer()
  {
    StringBuilder result = new StringBuilder(this.length + 50);
    if (trimmed) result.append("(...)\n");

    while (messages.size() > 0)
    {
      CharSequence s = messages.removeFirst();
      result.append(s);
    }
    length = 0;
    return result;
  }

  private synchronized void trimSize()
  {
    if (maxLength > 0 && length >= maxLength)
    {
      trimmed = true;
      while (length >= maxLength)
      {
        CharSequence s = messages.removeFirst();
        if (s != null) this.length -= s.length();
      }
    }
  }

  /**
   * Returns the total length in characters of all messages
   * that are currently kept in this MessageBuffer.
   */
  public synchronized int getLength()
  {
    return length;
  }

  public synchronized void append(MessageBuffer buff)
  {
    if (buff == null) return;
    int count = buff.messages.size();
    if (count == 0) return;

    for (CharSequence s : buff.messages)
    {
      append(s);
    }
  }

  public synchronized void append(CharSequence s)
  {
    if (StringUtil.isEmpty(s)) return;
    this.messages.add(s);
    length += s.length();
    trimSize();
  }

  public synchronized void appendMessageKey(String key)
  {
    append(ResourceMgr.getString(key));
  }

  public synchronized void appendNewLine()
  {
    append(newLine);
  }

  @Override
  public String toString()
  {
    return "[" + messages.size() + " messages]";
  }

}
