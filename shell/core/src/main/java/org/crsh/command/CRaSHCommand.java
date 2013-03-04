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

package org.crsh.command;

import org.crsh.cmdline.CommandDescriptor;
import org.crsh.cmdline.completion.CompletionMatch;
import org.crsh.cmdline.Delimiter;
import org.crsh.cmdline.IntrospectionException;
import org.crsh.cmdline.annotations.Man;
import org.crsh.cmdline.annotations.Option;
import org.crsh.cmdline.OptionDescriptor;
import org.crsh.cmdline.ParameterDescriptor;
import org.crsh.cmdline.annotations.Usage;
import org.crsh.cmdline.completion.CompletionException;
import org.crsh.cmdline.completion.CompletionMatcher;
import org.crsh.cmdline.impl.CommandFactoryImpl;
import org.crsh.cmdline.invocation.InvocationException;
import org.crsh.cmdline.invocation.InvocationMatch;
import org.crsh.cmdline.invocation.InvocationMatcher;
import org.crsh.cmdline.invocation.OptionMatch;
import org.crsh.cmdline.invocation.Resolver;
import org.crsh.cmdline.spi.Completer;
import org.crsh.cmdline.spi.Completion;
import org.crsh.shell.InteractionContext;
import org.crsh.util.TypeResolver;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class CRaSHCommand extends GroovyCommand implements ShellCommand {

  /** . */
  private final Logger log = Logger.getLogger(getClass().getName());

  /** . */
  private final CommandDescriptor<?> descriptor;

  /** The unmatched text, only valid during an invocation. */
  protected String unmatched;

  /** . */
  @Option(names = {"h","help"})
  @Usage("command usage")
  @Man("Provides command usage")
  private boolean help;

  protected CRaSHCommand() throws IntrospectionException {
    this.descriptor = new CommandFactoryImpl(getClass().getClassLoader()).create(getClass());
    this.help = false;
    this.unmatched = null;
  }

  /**
   * Returns the command descriptor.
   *
   * @return the command descriptor
   */
  public CommandDescriptor<?> getDescriptor() {
    return descriptor;
  }

  protected final String readLine(String msg) {
    return readLine(msg, true);
  }

  protected final String readLine(String msg, boolean echo) {
    if (context instanceof InvocationContext) {
      return ((InvocationContext)context).readLine(msg, echo);
    } else {
      throw new IllegalStateException("Cannot invoke read line without an invocation context");
    }
  }

  public final String getUnmatched() {
    return unmatched;
  }

  public final CompletionMatch complete(CommandContext context, String line) {

    // WTF
    CompletionMatcher analyzer = descriptor.completer("main");

    //
    Completer completer = this instanceof Completer ? (Completer)this : null;

    //
    this.context = context;
    try {
      return analyzer.match(completer, line);
    }
    catch (CompletionException e) {
      log.log(Level.SEVERE, "Error during completion of line " + line, e);
      return new CompletionMatch(Delimiter.EMPTY, Completion.create());
    }
    finally {
      this.context = null;
    }
  }

  public final String describe(String line, DescriptionFormat mode) {

    // WTF
    InvocationMatcher analyzer = descriptor.invoker("main");

    //
    InvocationMatch match;
    try {
      match = analyzer.match(line);
    }
    catch (org.crsh.cmdline.SyntaxException e) {
      throw new org.crsh.command.SyntaxException(e.getMessage());
    }

    //
    try {
      switch (mode) {
        case DESCRIBE:
          return match.getDescriptor().getUsage();
        case MAN:
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          match.getDescriptor().printMan(pw);
          return sw.toString();
        case USAGE:
          StringWriter sw2 = new StringWriter();
          PrintWriter pw2 = new PrintWriter(sw2);
          match.getDescriptor().printUsage(pw2);
          return sw2.toString();
      }
    }
    catch (IOException e) {
      throw new AssertionError(e);
    }

    //
    return null;
  }

  static ScriptException toScript(Throwable cause) {
    if (cause instanceof ScriptException) {
      return (ScriptException)cause;
    } if (cause instanceof groovy.util.ScriptException) {
      // Special handling for groovy.util.ScriptException
      // which may be thrown by scripts because it is imported by default
      // by groovy imports
      String msg = cause.getMessage();
      ScriptException translated;
      if (msg != null) {
        translated = new ScriptException(msg);
      } else {
        translated = new ScriptException();
      }
      translated.setStackTrace(cause.getStackTrace());
      return translated;
    } else {
      return new ScriptException(cause);
    }
  }

  public CommandInvoker<?, ?> resolveInvoker(String name, Map<String, ?> options, List<?> args) {
    if (options.containsKey("h") || options.containsKey("help")) {
      throw new UnsupportedOperationException("Implement me");
    } else {

      InvocationMatcher matcher = descriptor.invoker("main");
      InvocationMatch<CRaSHCommand> match = null;
      try {
        match = matcher.match(name, options, args);
      }
      catch (org.crsh.cmdline.SyntaxException e) {
        throw new org.crsh.command.SyntaxException(e.getMessage());
      }
      return resolveInvoker(match);
    }
  }

  public CommandInvoker<?, ?> resolveInvoker(String line) {
    InvocationMatcher analyzer = descriptor.invoker("main");
    InvocationMatch<CRaSHCommand> match;
    try {
      match = analyzer.match(line);
    }
    catch (org.crsh.cmdline.SyntaxException e) {
      throw new org.crsh.command.SyntaxException(e.getMessage());
    }
    return resolveInvoker(match);
  }

  public final void execute(String s) throws ScriptException, IOException {
    InvocationContext<?> context = peekContext();
    CommandInvoker invoker = context.resolve(s);
    invoker.open(context);
    invoker.flush();
    invoker.close();
  }

  public final CommandInvoker<?, ?> resolveInvoker(final InvocationMatch<CRaSHCommand> match) {

    //
    final org.crsh.cmdline.invocation.CommandInvoker invoker = match.getInvoker();

    //
    if (invoker != null) {

      //
      boolean help = false;
      // was: methodMatch.getOwner().getOptionMatches()
      for (OptionMatch optionMatch : match.options()) {
        ParameterDescriptor parameterDesc = optionMatch.getParameter();
        if (parameterDesc instanceof OptionDescriptor) {
          OptionDescriptor optionDesc = (OptionDescriptor)parameterDesc;
          if (optionDesc.getNames().contains("h")) {
            help = true;
          }
        }
      }
      final boolean doHelp = help;

      //
      Class consumedType;
      Class producedType;
      if (PipeCommand.class.isAssignableFrom(invoker.getReturnType())) {
        Type ret = invoker.getGenericReturnType();
        consumedType = TypeResolver.resolveToClass(ret, PipeCommand.class, 0);
        producedType = TypeResolver.resolveToClass(ret, PipeCommand.class, 1);
      } else {
        consumedType = Void.class;
        producedType = Object.class;
        Class<?>[] parameterTypes = invoker.getParameterTypes();
        for (int i = 0;i < parameterTypes.length;i++) {
          Class<?> parameterType = parameterTypes[i];
          if (InvocationContext.class.isAssignableFrom(parameterType)) {
            Type contextGenericParameterType = invoker.getGenericParameterTypes()[i];
            producedType = TypeResolver.resolveToClass(contextGenericParameterType, InvocationContext.class, 0);
            break;
          }
        }
      }
      final Class _consumedType = consumedType;
      final Class _producedType = producedType;

      if (doHelp) {
        return new CommandInvoker<Object, Object>() {

          /** . */
          private CommandContext session;

          /** . */
          private InvocationContextImpl context;

          public Class<Object> getProducedType() {
            return _producedType;
          }

          public void setSession(CommandContext session) {
            this.session = session;
          }

          public Class<Object> getConsumedType() {
            return _consumedType;
          }

          public void open(InteractionContext<Object> consumer) {
            this.context = new InvocationContextImpl(consumer, session);
            try {
              match.getDescriptor().printUsage(this.context.getWriter());
            }
            catch (IOException e) {
              throw new AssertionError(e);
            }
          }

          public void setPiped(boolean piped) {
          }

          public void provide(Object element) throws IOException {
          }

          public void flush() throws IOException {
            this.context.flush();
          }

          public void close() {
          }
        };
      } else {
        if (consumedType == Void.class) {

          return new CommandInvoker<Object, Object>() {

            /** . */
            private CommandContext session;

            public void setSession(CommandContext session) {
              this.session = session;
            }

            public Class<Object> getProducedType() {
              return _producedType;
            }

            public Class<Object> getConsumedType() {
              return _consumedType;
            }

            public void open(final InteractionContext<Object> consumer) {

              //
              pushContext(new InvocationContextImpl<Object>(consumer, session));
              CRaSHCommand.this.unmatched = match.getRest();
              final Resolver resolver = new Resolver() {
                public <T> T resolve(Class<T> type) {
                  if (type.equals(InvocationContext.class)) {
                    return type.cast(peekContext());
                  } else {
                    return null;
                  }
                }
              };

              //
              Object o;
              try {
                o = invoker.invoke(resolver, CRaSHCommand.this);
              } catch (org.crsh.cmdline.SyntaxException e) {
                throw new org.crsh.command.SyntaxException(e.getMessage());
              } catch (InvocationException e) {
                throw toScript(e.getCause());
              }
              if (o != null) {
                peekContext().getWriter().print(o);
              }
            }
            public void setPiped(boolean piped) {
            }
            public void provide(Object element) throws IOException {
              // We just drop the elements
            }
            public void flush() throws IOException {
              peekContext().flush();
            }
            public void close() {
              CRaSHCommand.this.unmatched = null;
              popContext();
            }
          };
        } else {
          return new CommandInvoker<Object, Object>() {

            /** . */
            PipeCommand real;

            /** . */
            boolean piped;

            /** . */
            private CommandContext session;

            public Class<Object> getProducedType() {
              return _producedType;
            }

            public Class<Object> getConsumedType() {
              return _consumedType;
            }

            public void setSession(CommandContext session) {
              this.session = session;
            }

            public void setPiped(boolean piped) {
              this.piped = piped;
            }

            public void open(final InteractionContext<Object> consumer) {

              //
              final InvocationContextImpl<Object> invocationContext = new InvocationContextImpl<Object>(consumer, session);

              //
              pushContext(invocationContext);
              CRaSHCommand.this.unmatched = match.getRest();
              final Resolver resolver = new Resolver() {
                public <T> T resolve(Class<T> type) {
                  if (type.equals(InvocationContext.class)) {
                    return type.cast(invocationContext);
                  } else {
                    return null;
                  }
                }
              };
              try {
                real = (PipeCommand)invoker.invoke(resolver, CRaSHCommand.this);
              }
              catch (org.crsh.cmdline.SyntaxException e) {
                throw new org.crsh.command.SyntaxException(e.getMessage());
              } catch (InvocationException e) {
                throw toScript(e.getCause());
              }

              //
              real.setPiped(piped);
              real.doOpen(invocationContext);
            }

            public void provide(Object element) throws IOException {
              real.provide(element);
            }

            public void flush() throws IOException {
              real.flush();
            }

            public void close() {
              try {
                real.close();
              }
              finally {
                popContext();
              }
            }
          };
        }
      }
    } else {

      //
      boolean help = false;
      for (OptionMatch optionMatch : match.options()) {
        ParameterDescriptor parameterDesc = optionMatch.getParameter();
        if (parameterDesc instanceof OptionDescriptor) {
          OptionDescriptor optionDesc = (OptionDescriptor)parameterDesc;
          if (optionDesc.getNames().contains("h")) {
            help = true;
          }
        }
      }
      final boolean doHelp = help;

      //
      return new CommandInvoker<Void, Object>() {

        /** . */
        private CommandContext session;

        /** . */
        InvocationContext context;

        public void open(InteractionContext<Object> consumer) {
          this.context = new InvocationContextImpl(consumer, session);
          try {
            match.getDescriptor().printUsage(context.getWriter());
          }
          catch (IOException e) {
            throw new AssertionError(e);
          }
        }

        public void setSession(CommandContext session) {
          this.session = session;
        }

        public void setPiped(boolean piped) {
        }

        public void close() {
          this.context = null;
        }

        public void provide(Void element) throws IOException {
        }

        public void flush() throws IOException {
          context.flush();
        }

        public Class<Object> getProducedType() {
          return Object.class;
        }

        public Class<Void> getConsumedType() {
          return Void.class;
        }
      };
    }
  }
}
