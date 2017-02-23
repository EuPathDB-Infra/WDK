/**
 * 
 */
package org.gusdb.wdk.model.query;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.wdk.model.AttributeMetaQueryHandler;
import org.gusdb.wdk.model.RngAnnotations;
import org.gusdb.wdk.model.RngAnnotations.FieldSetter;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.param.DatasetParam;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A SqlQuery is use to access data from a database with SQL, and if the SQL is
 * relatively slow, it can be optionally cached for better performance.
 * 
 * the param can be embedded into SQL template in such form: $$param_name$$. the
 * param_name is the name of the param, and it doesn't have the paramSet name
 * prefix.
 * 
 * You can also define macros in the model and those macros will be substituted
 * into the SQL template. The difference between param and macro is that the
 * value of the macro is defined in the model and substituted into the SQL
 * template at initialization time, it will become a part of the SQL template;
 * while the value of param is provided by the user at run time, and it is
 * substituted into SQL template to produce the final SQL, but it doesn't change
 * the SQL template itself.
 * 
 * 
 * @author Jerric Gao
 * 
 */
public class SqlQuery extends Query {

  private List<WdkModelText> sqlList;
  private String _sql;
  private List<WdkModelText> sqlMacroList;
  private Map<String, String> sqlMacroMap;
  private boolean clobRow;
  private boolean _useDBLink = false;

  private String _attributeMetaQueryRef;
  private List<WdkModelText> dependentTableList;
  private Map<String, String> dependentTableMap;

  public SqlQuery() {
    super();
    clobRow = false;
    sqlList = new ArrayList<WdkModelText>();
    sqlMacroList = new ArrayList<WdkModelText>();
    sqlMacroMap = new LinkedHashMap<String, String>();
    dependentTableList = new ArrayList<WdkModelText>();
    dependentTableMap = new LinkedHashMap<String, String>();
  }

  public SqlQuery(SqlQuery query) {
    super(query);
    this.clobRow = query.clobRow;
    this._sql = query._sql;
    this.isCacheable = query.isCacheable;
    this._useDBLink = query._useDBLink;

    if (query.sqlList != null) this.sqlList = new ArrayList<>(query.sqlList);
    if (query.sqlMacroMap != null)
      this.sqlMacroMap = new LinkedHashMap<>(query.sqlMacroMap);
    if (query.sqlMacroList != null)
      this.sqlMacroList = new ArrayList<>(query.sqlMacroList);
    if (query.dependentTableMap != null)
      this.dependentTableMap = new LinkedHashMap<>(query.dependentTableMap);
    if (query.dependentTableList != null)
      this.dependentTableList = new ArrayList<>(query.dependentTableList);
  }

  public void addSql(WdkModelText sql) {
    this.sqlList.add(sql);
  }

  public void addSqlParamValue(WdkModelText sqlMacro) {
    this.sqlMacroList.add(sqlMacro);
  }

  public void addSqlParamValue(String macro, String value) {
    this.sqlMacroMap.put(macro, value);
  }

  public String getSql() {
    return replaceMacros(_sql);
  }
  
  public boolean isUseDBLink() {
    return _useDBLink;
  }

  public void setUseDBLink(boolean useDBLink) {
    _useDBLink = useDBLink;
  }
  
  /**
   * Sets an optional reference to a meta columns query
   * @param meta columns query ref of the form "set.element"
   */
  public void setAttributeMetaQueryRef(String attributeMetaQueryRef) {
	_attributeMetaQueryRef = attributeMetaQueryRef;
  }

  /**
   * this method is called by other WDK objects. It is not called by the model
   * xml parser.
   * 
   * @param sql
   */
  public void setSql(String sql) {
    // append new line to the end, in case the last line is a comment;
    // otherwise, all modified sql will fail.
    this._sql = sql + "\n";
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.Query#makeInstance()
   */
  @Override
  public SqlQueryInstance makeInstance(User user, Map<String, String> values,
      boolean validate, int assignedWeight, Map<String, String> context)
      throws WdkModelException, WdkUserException {
    return new SqlQueryInstance(user, this, values, validate, assignedWeight,
        context);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.Query#appendJSONContent(org.json.JSONObject)
   */
  @Override
  protected void appendJSONContent(JSONObject jsQuery, boolean extra)
      throws JSONException {
    if (extra) {
      // add macro into the content
      String[] macroNames = new String[sqlMacroMap.size()];
      sqlMacroMap.keySet().toArray(macroNames);
      Arrays.sort(macroNames);
      JSONObject jsMacros = new JSONObject();
      for (String macroName : macroNames) {
        jsMacros.put(macroName, sqlMacroMap.get(macroName));
      }
      jsQuery.put("macros", jsMacros);

      // add sql
      String sql = getSql().replaceAll("\\s+", " ");
      jsQuery.put("sql", sql);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.Query#excludeResources(java.lang.String)
   */
  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    super.excludeResources(projectId);

    // exclude sql
    for (WdkModelText sql : sqlList) {
      if (sql.include(projectId)) {
        sql.excludeResources(projectId);
        this.setSql(sql.getText());
        break;
      }
    }
    sqlList = null;

    // exclude sql
    for (WdkModelText dependentTable : dependentTableList) {
      if (dependentTable.include(projectId)) {
        dependentTable.excludeResources(projectId);
        String table = dependentTable.getText();
        this.dependentTableMap.put(table, table);
      }
    }
    dependentTableList = null;

    // exclude macros
    for (WdkModelText macro : sqlMacroList) {
      if (macro.include(projectId)) {
        macro.excludeResources(projectId);
        String name = macro.getName();
        if (sqlMacroMap.containsKey(name))
          throw new WdkModelException("The macro " + name
              + " is duplicated in query " + getFullName());

        sqlMacroMap.put(macro.getName(), macro.getText());
      }
    }
    sqlMacroList = null;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.Query#resolveReferences(org.gusdb.wdk.model
   * .WdkModel)
   */
  @Override
  public void resolveQueryReferences(WdkModel wdkModel)
      throws WdkModelException {
    // apply the sql macros into sql
    if (this._sql == null)
      throw new WdkModelException("null sql in " + getQuerySet().getName()
          + "." + getName());

    // don't replace the sql here. the macros have to be replaced on the fly
    // in order to inject overridden macros from question.
    String sql = replaceMacros(this._sql);

    // verify the all param macros have been replaced
    Matcher matcher = Pattern.compile("&&([^&]+)&&").matcher(sql);
    if (matcher.find())
      throw new WdkModelException("SqlParamValue macro " + matcher.group(1)
          + " found in <sql> of query " + getFullName()
          + ", but it's not defined.");
  }

  private String replaceMacros(String sql) {
    for (String paramName : sqlMacroMap.keySet()) {
      String pattern = "&&" + paramName + "&&";
      String value = sqlMacroMap.get(paramName);
      // escape the & $ \ chars in the value
      sql = sql.replaceAll(pattern, Matcher.quoteReplacement(value));
    }
    return sql;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.Query#clone()
   */
  @Override
  public Query clone() {
    return new SqlQuery(this);
  }

  /**
   * This is a way to declare the query returns clob columns. This property is
   * used when we generate download cache from table queries. If the
   * concatenated size of the column values exceeds the DBMS limit for string
   * columns, this flag should be set to true, so that the result can be casted
   * into a CLOB. However, since writing and reading clobs are much slower than
   * a normal string column, the flag should be set to false if CLOB is not
   * needed.
   * 
   * @return the clobRow
   */
  public boolean isClobRow() {
    return clobRow;
  }

  /**
   * @param clobRow
   *          the clobRow to set
   */
  public void setClobRow(boolean clobRow) {
    this.clobRow = clobRow;
  }

  public void addDependentTable(WdkModelText dependentTable) {
    this.dependentTableList.add(dependentTable);
  }

  /**
   * This is a way to declare the tables the SQL depends on without having to
   * parse the SQL. This feature is used in generating download cache.
   * 
   * @return
   */
  public String[] getDependentTables() {
    String[] array = new String[dependentTableMap.size()];
    dependentTableMap.keySet().toArray(array);
    return array;
  }
  
  @Override
  public void resolveReferences(WdkModel wdkModel) throws WdkModelException {
    if (_resolved) return;
    super.resolveReferences(wdkModel);
    
    // set the dblink flag if any of the params is a datasetParam;
    for (Param param : getParams()) {
      if (param instanceof DatasetParam) {
        _useDBLink = true;
        break;
      }
    }
    
    // Continue only if an attribute meta query reference is provided.  
 	if(_attributeMetaQueryRef != null) { 
 	  SqlQuery query = (SqlQuery) wdkModel.resolveReference(_attributeMetaQueryRef);
 	  String sql = query.getSql();
 	  ResultSet resultSet = null;
 	  Column column = null;
 	  List<FieldSetter> fieldSetters = RngAnnotations.getRngFields(Column.class);
 	  try {
 		  
 		// Call the attribute meta query  
 	    resultSet = SqlUtils.executeQuery(wdkModel.getAppDb().getDataSource(), sql, query.getFullName() + "__meta-cols");
 	    ResultSetMetaData metaData = resultSet.getMetaData();
 	    
 	    // Compile a list of database column names - the list will likely be different for
	    // every attribute meta query table.
 	    int columnCount = metaData.getColumnCount();
 	    List<String> columnNames = new ArrayList<>();
		for (int i = 1; i <= columnCount; i++ ) {
		  String columnName = metaData.getColumnName(i).toLowerCase();
		  columnNames.add(columnName);
		} 
		
		// Iterate over each row (database loaded attribute)
	    while(resultSet.next()) {  
		  
		  column = new Column();
		  
		  // Need to set this here since this column originates from the database
		  column.setQuery(this);
		  
		  // Populate the attributeField with the attribute meta data
		  AttributeMetaQueryHandler.populate(column, resultSet,
				  metaData, columnNames, fieldSetters);
		  
		  this.columnMap.put(column.getName(), column);
	    }
 	  }  
 	  catch(SQLException se) {
 	    throw new WdkModelException("Unable to resolve database loaded attributes.", se);
 	  }
 	}
  }
}
