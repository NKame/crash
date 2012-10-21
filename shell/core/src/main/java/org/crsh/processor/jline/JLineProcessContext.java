/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.crsh.processor.jline;

import org.crsh.shell.ShellProcessContext;
import org.crsh.shell.ShellResponse;
import org.crsh.text.Chunk;
import org.crsh.text.Style;
import org.crsh.text.Text;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

class JLineProcessContext implements ShellProcessContext {

  /** . */
  private static final Character NO_ECHO = (char)0;

  /** . */
  final JLineProcessor processor;

  /** . */
  final CountDownLatch latch = new CountDownLatch(1);

  /** . */
  final AtomicReference<ShellResponse> resp = new AtomicReference<ShellResponse>();

  public JLineProcessContext(JLineProcessor processor) {
    this.processor = processor;
  }

  public int getWidth() {
    return processor.reader.getTerminal().getWidth();
  }

  public int getHeight() {
    return processor.reader.getTerminal().getHeight();
  }

  public String getProperty(String name) {
    return null;
  }

  public String readLine(String msg, boolean echo) {
    try {
      if (echo) {
        return processor.reader.readLine(msg);
      } else {
        return processor.reader.readLine(msg, NO_ECHO);
      }
    }
    catch (IOException e) {
      return null;
    }
  }

  public void provide(Chunk element) throws IOException {
    if (element instanceof Text) {
      Text textChunk = (Text)element;
      processor.writer.append(textChunk.getText());
    } else if (element instanceof Style) {
      try {
        ((Style)element).writeAnsiTo(processor.writer);
      }
      catch (IOException ignore) {
      }
    } else {


      // Clear screen : this method has drawback to modifying history
//      processor.writer.print("\033[");
//      processor.writer.print("2J");
//      processor.writer.flush();

      // Clear all lines without touching the history
      int height = processor.reader.getTerminal().getHeight();
      for (int i = 1;i < height;i++) {
        processor.writer.print("\033[");
        processor.writer.print(i + ";1H");
        processor.writer.print("\033[");
        processor.writer.print("K");
      }

      // Move cursor to top
      processor.writer.print("\033[");
      processor.writer.print("1;1H");

      // Flush all the changes
//      processor.writer.flush();
    }
  }

  public void flush() {
    processor.writer.flush();
  }

  public void end(ShellResponse response) {
    resp.set(response);
    latch.countDown();
  }
}