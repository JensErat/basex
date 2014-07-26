package org.basex.query.up;

import static org.basex.query.util.Err.*;

import java.io.*;
import java.util.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.query.*;
import org.basex.query.up.primitives.*;
import org.basex.util.*;
import org.basex.util.list.*;

/**
 * Base class for the different context modifiers. A context modifier aggregates
 * all updates for a specific context.
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Lukas Kircher
 */
public abstract class ContextModifier {
  /** Update primitives, aggregated separately for each database. */
  private final Map<Data, DataUpdates> dbUpdates = new HashMap<>();
  /** Update primitives, aggregated separately for each database name. */
  private final Map<String, NameUpdates> nameUpdates = new HashMap<>();
  /** Temporary data reference, containing all XML fragments to be inserted. */
  private MemData tmp;

  /**
   * Adds an update primitive to this context modifier.
   * @param up update primitive
   * @param qc query context
   * @throws QueryException query exception
   */
  void add(final Update up, final QueryContext qc) throws QueryException {
    if(up instanceof DataUpdate) {
      final DataUpdate dataUp = (DataUpdate) up;
      final Data data = dataUp.data();
      testIsWriteLocked(qc, data.meta.name);
      DataUpdates ups = dbUpdates.get(data);
      if(ups == null) {
        ups = new DataUpdates(data);
        dbUpdates.put(data, ups);
      }
      // create temporary mem data instance if not available yet
      if(tmp == null) tmp = new MemData(data.meta.options);
      ups.add(dataUp, tmp);
    } else if(up instanceof NameUpdate) {
      final NameUpdate nameUp = (NameUpdate) up;
      final String name = nameUp.name();
      testIsWriteLocked(qc, name);
      NameUpdates ups = nameUpdates.get(name);
      if(ups == null) {
        ups = new NameUpdates();
        nameUpdates.put(name, ups);
      }
      ups.add(nameUp);
    } else {
      throw Util.notExpected();
    }
  }

  /**
   * Test if database is write locked or throw Exception.
   * @param qc query context
   * @param name database name
   * @throws QueryException if no write lock on database
   */
  private void testIsWriteLocked(final QueryContext qc, final String name) throws QueryException {
    if (qc.context.globalopts.get(GlobalOptions.MANUALLOCK) && !qc.writeLocks.contains(name))
      throw new QueryException("Trying to access unlocked database \"" + name + "\"!");
  }

  /**
   * Adds all databases to be updated to the specified list.
   * @param db databases
   */
  void databases(final StringList db) {
    for(final Data data : dbUpdates.keySet()) {
      if(!data.inMemory()) db.add(data.meta.name);
    }
    for(final NameUpdates up : nameUpdates.values()) {
      up.databases(db);
    }
  }

  /**
   * Prepares update operations.
   * @return updated data references
   * @throws QueryException query exception
   */
  final HashSet<Data> prepare() throws QueryException {
    final HashSet<Data> datas = new HashSet<>(dbUpdates.size());
    for(final DataUpdates up : dbUpdates.values()) {
      // create temporary mem data instance if not available yet
      if(tmp == null) tmp = new MemData(up.data().meta.options);
      up.prepare(tmp);
      datas.add(up.data());
    }
    for(final NameUpdates up : nameUpdates.values()) up.prepare();
    return datas;
  }

  /**
   * Applies all updates.
   * @throws QueryException query exception
   */
  final void apply() throws QueryException {
    // apply initial updates based on database names
    for(final NameUpdates up : nameUpdates.values()) up.apply(true);

    // collect data references to be locked
    final Set<Data> datas = new HashSet<>();
    for(final Data data : dbUpdates.keySet()) datas.add(data);

    // try to acquire write locks and keep track of the number of acquired locks in order to
    // release them in case of error. write locks prevent other JVMs from accessing currently
    // updated databases, but they cannot provide perfect safety.
    int i = 0;
    try {
      for(final Data data : datas) {
        data.startUpdate();
        i++;
      }
      // apply node and database update
      for(final DataUpdates up : dbUpdates.values()) up.apply();
    } catch(final IOException ex) {
      throw BXDB_LOCK.get(null, ex);
    } finally {
      // remove locks: in case of a crash, remove only already acquired write locks
      for(final Data data : datas) {
        if(i-- > 0) data.finishUpdate();
      }
    }

    // apply remaining updates based on database names
    for(final NameUpdates up : nameUpdates.values()) up.apply(false);
  }

  /**
   * Returns the total number of update operations.
   * @return number of updates
   */
  final int size() {
    int s = 0;
    for(final DataUpdates c : dbUpdates.values()) s += c.size();
    for(final NameUpdates c : nameUpdates.values()) s += c.size();
    return s;
  }
}
