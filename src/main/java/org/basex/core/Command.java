package org.basex.core;

import org.basex.core.Commands.CmdPerm;
import static org.basex.core.Text.*;

import org.basex.core.cmd.Close;
import org.basex.data.Data;
import org.basex.data.Result;
import org.basex.io.out.ArrayOutput;
import org.basex.io.out.NullOutput;
import org.basex.io.out.PrintOutput;
import org.basex.util.Performance;
import org.basex.util.TokenBuilder;
import org.basex.util.Util;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * This class provides the architecture for all internal command
 * implementations. It evaluates queries that are sent by the GUI, the client or
 * the standalone version.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public abstract class Command extends Progress {
  /** Commands flag: standard. */
  protected static final int STANDARD = 256;
  /** Commands flag: data reference needed. */
  protected static final int DATAREF = 512;

  /** Container for query information. */
  private final TokenBuilder info = new TokenBuilder();
  /** Command arguments. */
  protected final String[] args;

  /** Performance measurements. */
  protected Performance perf;
  /** Database context. */
  protected Context context;
  /** Output stream. */
  protected PrintOutput out;
  /** Optional input source. */
  protected InputSource in;
  /** Database properties. */
  protected Prop prop;
  /** Main properties. */
  protected MainProp mprop;

  /** Flags for controlling command processing. */
  private final int flags;

  /**
   * Constructor.
   * @param flag command flags
   * @param arg arguments
   */
  protected Command(final int flag, final String... arg) {
    flags = flag;
    args = arg;
  }

  /**
   * Executes the command and prints the result to the specified output
   * stream. If an exception occurs, a {@link BaseXException} is thrown.
   * @param ctx database context
   * @param os output stream reference
   * @throws BaseXException command exception
   */
  public final void execute(final Context ctx, final OutputStream os)
      throws BaseXException {
    if(!exec(ctx, os)) throw new BaseXException(info());
  }

  /**
   * Executes the command and returns the result as string.
   * If an exception occurs, a {@link BaseXException} is thrown.
   * @param ctx database context
   * @return string result
   * @throws BaseXException command exception
   */
  public final String execute(final Context ctx) throws BaseXException {
    final ArrayOutput ao = new ArrayOutput();
    execute(ctx, ao);
    return ao.toString();
  }

  /**
   * Attaches an input stream.
   * @param is input stream
   */
  public void setInput(final InputStream is) {
    in = new InputSource(is);
  }

  /**
   * Attaches an input source.
   * @param is input source
   */
  public void setInput(final InputSource is) {
    in = is;
  }

  /**
   * Runs the command without permission, data and concurrency checks.
   * Should be called with care, and only by other database commands.
   * @param ctx database context
   * @return result of check
   */
  public final boolean run(final Context ctx) {
    return run(ctx, new NullOutput());
  }

  /**
   * Returns command information.
   * @return info string
   */
  public final String info() {
    return info.toString();
  }

  /**
   * Returns the result set, generated by a query command. Will only yield results if
   * {@link Prop#CACHEQUERY} is set, and can only be called once.
   * @return result set
   */
  public Result result() {
    return null;
  }

  /**
   * Checks if the command performs updates/write operations.
   * @param ctx database context
   * @return result of check
   */
  @SuppressWarnings("unused")
  public boolean updating(final Context ctx) {
    return createWrite();
  }

  /**
   * Checks if the database to be updated is opened (pinned) by a process in another JVM.
   * @param ctx database context
   * @return name of pinned database
   */
  public String pinned(final Context ctx) {
    final Data data = ctx.data();
    return createWrite() && data != null && data.pinned() ? data.meta.name : null;
  }

  /**
   * Checks if the command has updated any data.
   * If this method is called before command execution, it always returns {@code true}.
   * @return result of check
   */
  public boolean updated() {
    return true;
  }

  /**
   * Closes an open data reference and returns {@code true} if this command will change
   * the {@link Context#data} reference. This method is required by the progress dialog
   * in the frontend.
   * @param ctx database context
   * @return result of check
   */
  @SuppressWarnings("unused")
  public boolean newData(final Context ctx) {
    return false;
  }

  /**
   * Returns true if this command returns a progress value.
   * This method is required by the progress dialog in the frontend.
   * @return result of check
   */
  public boolean supportsProg() {
    return false;
  }

  /**
   * Returns true if this command can be stopped.
   * This method is required by the progress dialog in the frontend.
   * @return result of check
   */
  public boolean stoppable() {
    return false;
  }

  @Override
  public final String toString() {
    final CommandBuilder cb = new CommandBuilder(this);
    build(cb);
    return cb.toString();
  }

  // PROTECTED METHODS ========================================================

  /**
   * Executes the command and serializes the result (internal call).
   * @return success of operation
   * @throws IOException I/O exception
   */
  protected abstract boolean run() throws IOException;

  /**
   * Builds a string representation from the command. This string must be
   * correctly built, as commands are sent to the server as strings.
   * @param cb command builder
   */
  protected void build(final CommandBuilder cb) {
    cb.init().args();
  }

  /**
   * Adds the error message to the message buffer {@link #info}.
   * @param msg error message
   * @param ext error extension
   * @return {@code false}
   */
  protected final boolean error(final String msg, final Object... ext) {
    info.reset();
    info.addExt(msg == null ? "" : msg, ext);
    return false;
  }

  /**
   * Adds information on command execution.
   * @param str information to be added
   * @param ext extended info
   * @return {@code true}
   */
  protected final boolean info(final String str, final Object... ext) {
    info.addExt(str, ext).add(NL);
    return true;
  }

  /**
   * Returns the specified command option.
   * @param typ options enumeration
   * @param <E> token type
   * @return option
   */
  protected final <E extends Enum<E>> E getOption(final Class<E> typ) {
    final E e = getOption(args[0], typ);
    if(e == null) error(UNKNOWN_TRY_X, args[0]);
    return e;
  }

  /**
   * Returns the specified command option.
   * @param s string to be found
   * @param typ options enumeration
   * @param <E> token type
   * @return option
   */
  protected static <E extends Enum<E>> E getOption(final String s, final Class<E> typ) {
    try {
      return Enum.valueOf(typ, s.toUpperCase(Locale.ENGLISH));
    } catch(final Exception ex) {
      return null;
    }
  }

  /**
   * Closes the specified database if it is currently opened and only pinned once.
   * @param ctx database context
   * @param db database to be closed
   * @return closed flag
   */
  protected static boolean close(final Context ctx, final String db) {
    final boolean close = ctx.data() != null &&
        db.equals(ctx.data().meta.name) && ctx.datas.pins(db) == 1;
    return close && new Close().run(ctx);
  }

  // PRIVATE METHODS ==========================================================

  /**
   * Checks if the command demands write or create permissions.
   * @return result of check
   */
  private boolean createWrite() {
    return (flags & (User.CREATE | User.WRITE)) != 0;
  }

  /**
   * Executes the command, prints the result to the specified output stream
   * and returns a success flag.
   * @param ctx database context
   * @param os output stream
   * @return success flag. The {@link #info()} method returns information
   * on a potential exception
   */
  private boolean exec(final Context ctx, final OutputStream os) {
    // check if data reference is available
    final Data data = ctx.data();
    if(data == null && (flags & DATAREF) != 0) return error(NO_DB_OPENED);

    // check permissions
    if(!ctx.perm(flags & 0xFF, data != null ? data.meta : null)) {
      final CmdPerm[] perms = CmdPerm.values();
      int i = perms.length;
      final int f = flags & 0xFF;
      while(--i >= 0 && (1 << i & f) == 0);
      return error(PERM_NEEDED_X, perms[i + 1]);
    }

    // check if database is locked by a process in another JVM
    final String pin = pinned(ctx);
    if(pin != null) return error(DB_PINNED_X, pin);

    // set updating flag
    updating = updating(ctx);

    try {
      // register process
      ctx.register(this);
      // run command and return success flag
      return run(ctx, os);
    } finally {
      // guarantee that process will be unregistered
      ctx.unregister(this);
    }
  }

  /**
   * Runs the command without permission, data and concurrency checks.
   * @param ctx database context
   * @param os output stream
   * @return result of check
   */
  private boolean run(final Context ctx, final OutputStream os) {
    perf = new Performance();
    context = ctx;
    prop = ctx.prop;
    mprop = ctx.mprop;
    out = PrintOutput.get(os);

    try {
      return run();
    } catch(final ProgressException ex) {
      // process was interrupted by the user or server
      abort();
      return error(INTERRUPTED);
    } catch(final Throwable ex) {
      // unexpected error
      Performance.gc(2);
      abort();
      if(ex instanceof OutOfMemoryError) {
        Util.debug(ex);
        return error(OUT_OF_MEM + (createWrite() ? H_OUT_OF_MEM : ""));
      }
      return error(Util.bug(ex));
    } finally {
      // flushes the output
      try { if(out != null) out.flush(); } catch(final IOException ex) { }
    }
  }
}
