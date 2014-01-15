package org.basex.query.up;

import static org.basex.query.util.Err.*;

import org.basex.core.*;
import org.basex.query.*;
import org.basex.query.up.primitives.*;

/**
 * The database modifier holds all database updates during a snapshot.
 * Database permissions are checked to ensure that a user possesses enough
 * privileges to alter database contents.
 *
 * @author BaseX Team 2005-13, BSD License
 * @author Lukas Kircher
 */
final class DatabaseModifier extends ContextModifier {
  @Override
  void add(final Operation o, final QueryContext ctx) throws QueryException {
    String name = o instanceof DBCreate ? ((DBCreate) o).name : o.getData().meta.name;
    if (!ctx.writeLocks.contains(name))
      throw new QueryException("Trying to access unlocked database \"" + name + "\"!");

    super.add(o, ctx);
    // check permissions
    if(o instanceof DBCreate) {
      if(!ctx.context.perm(Perm.CREATE, null)) throw BASX_PERM.get(o.getInfo(), Perm.CREATE);
    } else if(!ctx.context.perm(Perm.WRITE, o.getData().meta)) {
      throw BASX_PERM.get(o.getInfo(), Perm.WRITE);
    }
  }
}
