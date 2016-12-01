package org.gusdb.wdk.model.query;

import java.io.PrintWriter;

import org.gusdb.fgputil.Named.NamedObject;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Column defines the fields in the result that will be returned by a query.
 * 
 * A column can have a type defined, but in the SqlQuery, the type defined in
 * the model will be ignored, and the actual type will be derived from the
 * database. In the ProcessQuery, the type will be used to create the fields of
 * the cache table. The width property of the column is similar to the type, and
 * it is ignored in SqlQuery, but used in ProcessQuery in cache creation.
 * 
 * @author jerric
 * 
 */
public class Column extends WdkModelBase implements NamedObject {

  private String _name;
  private Query _query;
  private ColumnType _type = ColumnType.STRING;
  private int _width = 0; // for wsColumns (width of datatype)

  /**
   * The name is used by WSF service.
   */
  private String _wsName;

  private boolean _ignoreCase = false;

  private String _sortingColumn;

  private boolean _isPkColumn = false;

  public Column() {}

  public Column(Column column) {
    _name = column._name;
    _query = column._query;
    _type = column._type;
    _width = column._width;
    _wsName = column._wsName;
    _ignoreCase = column._ignoreCase;
    _sortingColumn = column._sortingColumn;
    _isPkColumn = column._isPkColumn;
  }

  public void setName(String name) {
    _name = name;
  }

  @Override
  public String getName() {
    return _name;
  }

  public void setColumnType(String typeName) throws WdkModelException {
    _type = ColumnType.parse(typeName);
  }

  public void setType(ColumnType type) {
    _type = type;
  }

  public ColumnType getType() {
    return _type;
  }

  public void setQuery(Query query) {
    _query = query;
  }

  public void setWidth(int width) {
    _width = width;
  }

  public Query getQuery() {
    return _query;
  }

  public int getWidth() {
    return (_width == 0) ? _type.getDefaultWidth() : _width;
  }

  /**
   * @return Returns the wsName if defined, or column name, if the wsName is not defined.
   */
  public String getWsName() {
    return (_wsName == null)? _name : _wsName;
  }

  /**
   * @param wsName
   *          The wsName to set.
   */
  public void setWsName(String wsName) {
    _wsName = wsName;
  }

  public JSONObject getJSONContent() throws JSONException {
    JSONObject jsColumn = new JSONObject();
    jsColumn.put("name", _name);
    jsColumn.put("type", _type);
    jsColumn.put("width", _width);
    return jsColumn;
  }

  @Override
  public String toString() {
    String newline = System.getProperty("line.separator");
    String classnm = getClass().getSimpleName();
    StringBuffer buf = new StringBuffer(classnm + ": name='" + _name + "', "
        + "  dataTypeName='" + _type + "'" + newline);

    return buf.toString();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
   */
  @Override
  public void excludeResources(String projectId) {
    // do nothing
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.WdkModelBase#resolveReferences(org.gusdb.wdk.model
   * .WdkModel)
   */
  @Override
  public void resolveReferences(WdkModel wodkModel) throws WdkModelException {
    // nothing to resolve
  }

  /**
   * @return the sortingColumn
   */
  public String getSortingColumn() {
    return _sortingColumn;
  }

  /**
   * @param sortingColumn
   *          the sortingColumn to set
   */
  public void setSortingColumn(String sortingColumn) {
    _sortingColumn = sortingColumn;
  }

  /**
   * @return the ignoreCase
   */
  public boolean isIgnoreCase() {
    return _ignoreCase;
  }

  /**
   * @param ignoreCase
   *          the ignoreCase to set
   */
  public void setIgnoreCase(boolean ignoreCase) {
    _ignoreCase = ignoreCase;
  }

  public final void printDependency(PrintWriter writer, String indent) {
    writer.println(indent + "<" + getClass().getSimpleName() + " name=\"" + getName() + "\">");
    printDependencyContent(writer, indent + WdkModel.INDENT);
    writer.println(indent + "</" + getClass().getSimpleName() + ">");
  }

  /**
   * Prints nothing
   * 
   * @param writer
   * @param indent
   */
  protected void printDependencyContent(PrintWriter writer, String indent) {
    // by default, nothing to print out.
  }

  public void setPrimaryKeyColumn(boolean isPkColumn) {
    _isPkColumn = isPkColumn;
  }

  public boolean isPrimaryKeyColumn() {
    return _isPkColumn;
  }
}
