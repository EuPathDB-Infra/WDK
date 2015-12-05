package org.gusdb.wdk.model.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.ModelSetI;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.query.param.ParamValuesSet;
import org.gusdb.wdk.model.test.sanity.OptionallyTestable;

/**
 * An object representation of the {@code <querySet>} in the model. A query set
 * can only contain queries.
 * You can define isCacheable on querySet to provide default value to the contained queries.
 * 
 * @author jerric
 * 
 */
/**
 * @author jerric
 * 
 */
public class QuerySet extends WdkModelBase implements ModelSetI<Query>, OptionallyTestable {

  @SuppressWarnings("unused")
  private static final Logger LOG = Logger.getLogger(QuerySet.class);

  private List<Query> queryList = new ArrayList<Query>();
  private Map<String, Query> queries = new LinkedHashMap<String, Query>();
  private String name;
  private PostCacheInsertSql postCacheInsertSql;

  /* for sanity testing */
  public static enum QueryType {
    VOCAB,        // sanity testable
    ATTRIBUTE,    // sanity testable
    TABLE,        // sanity testable
    ID,           // not sanity testable
    SUMMARY,      // not sanity testable
    TRANSFORM,    // not sanity testable
    UNSPECIFIED,  // default value if not set in model; not sanity testable
    TABLE_TOTAL;  // special type used only by sanity tester; not a valid query type
  }
  private QueryType queryType = QueryType.UNSPECIFIED;
  private boolean doNotTest = false;
  private List<ParamValuesSet> unexcludedDefaultParamValuesSets = new ArrayList<ParamValuesSet>();
  private ParamValuesSet defaultParamValuesSet;
  private List<WdkModelText> unexcludedTestRowCountSqls = new ArrayList<WdkModelText>();
  private String testRowCountSql;
  /* end sanity testing fields */

  private boolean cacheable = false;

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  /**
   * @return the default cacheable flag for the contained SqlQuery. If the
   *         contained SqlQuery doesn't define its own flag, this value will be
   *         used. This flag won't affect ProcessQuery, since it is always
   *         cached.
   */
  public boolean isCacheable() {
    return cacheable;
  }

  public void setCacheable(boolean cacheable) {
    this.cacheable = cacheable;
  }

  /**
   * This method is to match with the property of the xml
   * @param cacheable
   */
  public void setIsCacheable(boolean cacheable) {
    this.cacheable = cacheable;
  }

  public Query getQuery(String name) throws WdkModelException {
    Query q = queries.get(name);
    if (q == null)
      throw new WdkModelException("Query Set " + getName()
          + " does not include query " + name);
    return q;
  }

  @Override
  public Query getElement(String name) {
    return queries.get(name);
  }

  public Query[] getQueries() {
    Query[] array = new Query[queries.size()];
    queries.values().toArray(array);
    return array;
  }

  public void setQueryType(String type) {
    QueryType queryTypeLocal = QueryType.valueOf(type.toUpperCase());
    if (queryTypeLocal.equals(QueryType.TABLE_TOTAL)) {
      throw new IllegalArgumentException("QueryType TABLE_TOTAL " +
          "is an internal value and cannot be specified in the model");
    }
    queryType = queryTypeLocal;
  }

  // needed for digester to recognize bean property
  public String getQueryType() {
    return queryType.name();
  }

  // proper method to use to retrive query type
  public QueryType getQueryTypeEnum() {
    return queryType;
  }

  public PostCacheInsertSql getPostCacheInsertSql() {
    return postCacheInsertSql;
  }

  public void setPostCacheInsertSql(PostCacheInsertSql postCacheInsertSql) {
    this.postCacheInsertSql = postCacheInsertSql;
  }

  public void setDoNotTest(boolean doNotTest) {
    this.doNotTest = doNotTest;
  }

  @Override
  public boolean getDoNotTest() {
    return doNotTest;
  }

  public void addDefaultParamValuesSet(ParamValuesSet paramValuesSet) {
    unexcludedDefaultParamValuesSets.add(paramValuesSet);
  }

  public ParamValuesSet getDefaultParamValuesSet() {
    return defaultParamValuesSet;
  }

  // sql that returns number of rows expected by all queries in this query set
  public void addTestRowCountSql(WdkModelText text) {
    unexcludedTestRowCountSqls.add(text);
  }

  public String getTestRowCountSql() {
    return testRowCountSql;
  }

  public boolean contains(String queryName) {
    return queries.containsKey(queryName);
  }

  public void addQuery(Query query) {
    query.setQuerySet(this);
    if (queryList != null)
      queryList.add(query);
    else {
      // if (queries.containsKey(query.getName()))
      // throw new WdkModelException("query [" + query.getFullName()
      // + "] already exists in the set.");
      queries.put(query.getName(), query);
    }
  }

  @Override
  public void resolveReferences(WdkModel model) throws WdkModelException {
    super.resolveReferences(model);
    for (Query query : queries.values()) {
      query.resolveReferences(model);
    }

    if (postCacheInsertSql != null && (postCacheInsertSql.getSql() == null ||
        !postCacheInsertSql.getSql().contains(Utilities.MACRO_CACHE_TABLE) ||
        !postCacheInsertSql.getSql().contains(Utilities.MACRO_CACHE_INSTANCE_ID)))
      throw new WdkModelException(
          "Invalid PostCacheInsertSql. <sql> must be provided, and include the macros: " +
              Utilities.MACRO_CACHE_TABLE + " and " + Utilities.MACRO_CACHE_INSTANCE_ID);
  }

  /*
   * (non-Javadoc) do nothing
   * 
   * @see
   * org.gusdb.wdk.model.ModelSetI#setResources(org.gusdb.wdk.model.WdkModel)
   */
  @Override
  public void setResources(WdkModel model) throws WdkModelException {}

  @Override
  public String toString() {
    String newline = System.getProperty("line.separator");
    StringBuffer buf = new StringBuffer("QuerySet: name='" + name + "'");
    buf.append(newline);
    for (Query query : queries.values()) {
      buf.append(newline);
      buf.append(":::::::::::::::::::::::::::::::::::::::::::::");
      buf.append(newline);
      buf.append(query);
      buf.append("----------------");
      buf.append(newline);
    }
    return buf.toString();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
   */
  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    // exclude queries
    for (Query query : queryList) {
      if (query.include(projectId)) {
        query.excludeResources(projectId);
        String queryName = query.getName();
        if (queries.containsKey(queryName))
          throw new WdkModelException("Query named " + queryName
              + " already exists in query set " + getName());
        queries.put(queryName, query);
      }
    }
    queryList = null;

    // exclude paramValuesSets
    for (ParamValuesSet paramValuesSet : unexcludedDefaultParamValuesSets) {
      if (paramValuesSet.include(projectId)) {
        if (defaultParamValuesSet != null)
          throw new WdkModelException(
              "Duplicate <defaultTestParamValues> included in query set "
                  + getName() + " for projectId " + projectId);
        defaultParamValuesSet = paramValuesSet;

      }
    }

    // exclude textRowCountSqls
    for (WdkModelText text : unexcludedTestRowCountSqls) {
      if (text.include(projectId)) {
        if (testRowCountSql != null)
          throw new WdkModelException(
              "Duplicate <testRowCountSql> included in query set " + getName()
                  + " for projectId " + projectId);
        testRowCountSql = text.getText();

      }
    }
  }
  // ///////////////////////////////////////////////////////////////
  // ///// protected
  // ///////////////////////////////////////////////////////////////

}
