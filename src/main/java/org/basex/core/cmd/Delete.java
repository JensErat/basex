package org.basex.core.cmd;

import static org.basex.core.Text.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.io.*;
import org.basex.util.list.*;

/**
 * Evaluates the 'delete' command and deletes resources from a database.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class Delete extends ACreate {
  /**
   * Default constructor.
   * @param target target to delete
   */
  public Delete(final String target) {
    super(DATAREF | User.WRITE, target);
  }

  @Override
  protected boolean run() {
    final Data data = context.data();
    final String target = args[0];

    // set updating flag
    if(!startUpdate(data)) return false;

    // delete documents
    final IntList docs = data.resources.docs(target);
    delete(context, docs);
    // delete binaries
    final TokenList bins = data.resources.binaries(target);
    delete(data, target);

    // remove updating flag and return info message
    return stopUpdate(data) && info(DOCS_DELETED_X_X, docs.size() + bins.size(), perf);
  }

  /**
   * Deletes the specified nodes.
   * @param ctx database context
   * @param docs pre values of documents to be deleted
   */
  public static void delete(final Context ctx, final IntList docs) {
    // data was changed: update context
    if(docs.size() == 0) return;

    // loop through all documents in reverse order
    final Data data = ctx.data();
    for(int d = docs.size() - 1; d >= 0; d--) data.delete(docs.get(d));
    ctx.update();
    data.flush();
  }

  /**
   * Deletes the specified resources.
   * @param data data reference
   * @param res resource to be deleted
   */
  public static void delete(final Data data, final String res) {
    final IOFile file = data.meta.binary(res);
    if(file != null) file.delete();
  }
}
