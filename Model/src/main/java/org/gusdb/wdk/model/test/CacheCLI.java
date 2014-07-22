package org.gusdb.wdk.model.test;

import org.gusdb.fgputil.BaseCLI;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkRuntimeException;
import org.gusdb.wdk.model.dbms.CacheFactory;

/**
 * @author xingao
 * 
 */
public class CacheCLI extends BaseCLI {

    private static final String ARG_PROJECT_ID = "model";
    
    private static final String ARG_CREATE = "new";
    private static final String ARG_DROP = "drop";
    private static final String ARG_DROP_SINGLE = "dropSingle";
    private static final String ARG_DROP_PURGE = "purge";
    private static final String ARG_RESET = "reset";
    private static final String ARG_RECREATE = "recreate";
    private static final String ARG_FORCE_DROP = "forceDrop";
    private static final String ARG_NO_SCHEMA = "noSchemaOutput";
    private static final String ARG_SHOW = "show";
    
    private enum Operation {
      CREATE, RESET, RECREATE, DROP, DROP_SINGLE, SHOW;
    }

    public static void main(String[] args) {
        String cmdName = System.getProperty("cmdName");
        CacheCLI cacher = new CacheCLI(cmdName);
        try {
          cacher.invoke(args);
          System.exit(0);
        } catch (Exception ex) {
          ex.printStackTrace();
          System.exit(1);
        }
    }

    /**
     * @param command
     * @param description
     */
    protected CacheCLI(String command) {
        super((command == null) ? "wdkCache" : command,
                "Manages the cache tables of WDK");
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.fgputil.BaseCLI#declareOptions()
     */
    @Override
    protected void declareOptions() {
        addSingleValueOption(ARG_PROJECT_ID, true, null, "The ProjectId, which"
                + " should match the directory name under $GUS_HOME, where "
                + "model-config.xml is stored.");

        addNonValueOption(ARG_CREATE, false, "create new WDK cache index and "
                + "sorting tables and sequences. It fails if the cache index "
                + "or sorting table already exists.");
        addNonValueOption(ARG_DROP, false, "drop existing WDK cache tables, "
                + "cache index table, sorting table, and sequences.");
        addSingleValueOption(ARG_DROP_SINGLE, false, null, "drop a single "
                + "cache table or a group of cache tables with the same query "
                + "name. The input can be a cache id (query_instance_id, to "
                + "drop a single cache table), or a full queryName (to drop "
                + "all cache table created by the same query).");
        addNonValueOption(ARG_DROP_PURGE, false, "Optional argument, it will "
                + "affect the WDK behavior when dropping cache tables. This "
                + "option works on Oracle component database only, which "
                + "purges the cache tables on drop table. PostgreSQL will "
                + "ignore this option.");
        addNonValueOption(ARG_RESET, false, "drop existing WDK cache tables, "
                + "delete rows from cache index and sorting table, but it "
                + "won't reset sequences.");
        addNonValueOption(ARG_RECREATE, false, "drop any existing cache tables"
                + ", and drop cache index table, sorting table and sequences, "
                + "and then recreate them.");
        addNonValueOption(ARG_SHOW, false, "display the cache usage.");
        addGroup(true, ARG_CREATE, ARG_DROP, ARG_RESET, ARG_RECREATE,
                ARG_DROP_SINGLE, ARG_SHOW);

        addNonValueOption(ARG_FORCE_DROP, false, "this optional argument "
                + "will be ignored when [" + ARG_CREATE + "] or ["
                + ARG_DROP_SINGLE + "] is used. It drops all index tables and "
                + "cache tables, including dangling cache "
                + "tables, that is, the cache tables who does not have a "
                + "reference in the cache index table.");

        addNonValueOption(ARG_NO_SCHEMA, false, "remove references to the "
                + "schema when printing out messages regarding a table");
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.fgputil.BaseCLI#invoke()
     */
    @Override
    protected void execute() {
        String projectId = (String) getOptionValue(ARG_PROJECT_ID);
        // boolean noSchemaOutput = (Boolean) getOptionValue(ARG_NO_SCHEMA);
        boolean purgeCache = (Boolean) getOptionValue(ARG_DROP_PURGE);
        boolean forceDrop = (Boolean) getOptionValue(ARG_FORCE_DROP);

        try {
            // read config info
            String gusHome = System.getProperty(Utilities.SYSTEM_PROPERTY_GUS_HOME);
            WdkModel wdkModel = WdkModel.construct(projectId, gusHome);
            CacheFactory factory = wdkModel.getResultFactory().getCacheFactory();

            long start = System.currentTimeMillis();
            
            switch (getOperation()) {
              case CREATE:   factory.createCache(); break;
              case RESET:    factory.resetCache(purgeCache, forceDrop); break;
              case DROP:     factory.dropCache(purgeCache, forceDrop); break;
              case RECREATE: factory.recreateCache(purgeCache, true); break;
              case SHOW:     factory.showCache(); break;
              case DROP_SINGLE:
                String value = (String) getOptionValue(ARG_DROP_SINGLE);
                if (value.matches("\\d+")) {
                  factory.dropCache(Integer.parseInt(value), purgeCache);
                } else {
                  factory.dropCache(value, purgeCache);
                }
            }
            
            long end = System.currentTimeMillis();
            System.out.println("Command succeeded in "
                    + ((end - start) / 1000.0) + " seconds");

        } catch (Exception e) {
            System.err.println("FAILED");
            System.err.println("");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private Operation getOperation() {
      if ((Boolean)getOptionValue(ARG_CREATE)) return Operation.CREATE;
      if ((Boolean)getOptionValue(ARG_RESET)) return Operation.RESET;
      if ((Boolean)getOptionValue(ARG_DROP)) return Operation.DROP;
      if ((Boolean)getOptionValue(ARG_RECREATE)) return Operation.RECREATE;
      if ((Boolean)getOptionValue(ARG_SHOW)) return Operation.SHOW;
      if (getOptionValue(ARG_DROP_SINGLE) != null) return Operation.DROP_SINGLE;
      throw new WdkRuntimeException("No operation specified.");
    }
}
