package org.gusdb.wdk.model.query;

import java.io.PrintWriter;

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
public class Column extends WdkModelBase {

  // private static Logger logger = Logger.getLogger( Column.class );

  private String name;
  private Query query;
  private ColumnType type = ColumnType.STRING;
  private int width = 0; // for wsColumns (width of datatype)

  /**
   * The name is used by WSF service.
   */
  private String wsName;

  private boolean ignoreCase = false;

  private String sortingColumn;

  public Column() {}

  public Column(Column column) {
    this.name = column.name;
    this.query = column.query;
    this.type = column.type;
    this.width = column.width;
    this.wsName = column.wsName;
    this.ignoreCase = column.ignoreCase;
    this.sortingColumn = column.sortingColumn;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setColumnType(String typeName) throws WdkModelException {
    this.type = ColumnType.parse(typeName);
  }

  public void setType(ColumnType type) {
    this.type = type;
  }

  public ColumnType getType() {
    return type;
  }

  public void setQuery(Query query) {
    this.query = query;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public Query getQuery() {
    return query;
  }

  public int getWidth() {
    return (width == 0) ? type.getDefaultWidth() : width;
  }

  /**
   * @return Returns the wsName if defined, or column name, if the wsName is not defined.
   */
  public String getWsName() {
    return (wsName == null)? name : wsName;
  }

  /**
   * @param wsName
   *          The wsName to set.
   */
  public void setWsName(String wsName) {
    this.wsName = wsName;
  }

  public JSONObject getJSONContent() throws JSONException {
    JSONObject jsColumn = new JSONObject();
    jsColumn.put("name", name);
    jsColumn.put("type", type);
    jsColumn.put("width", width);
    return jsColumn;
  }

  @Override
  public String toString() {
    String newline = System.getProperty("line.separator");
    String classnm = this.getClass().getSimpleName();
    StringBuffer buf = new StringBuffer(classnm + ": name='" + name + "', "
        + "  dataTypeName='" + type + "'" + newline);

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
    return sortingColumn;
  }

  /**
   * @param sortingColumn
   *          the sortingColumn to set
   */
  public void setSortingColumn(String sortingColumn) {
    this.sortingColumn = sortingColumn;
  }

  /**
   * @return the ignoreCase
   */
  public boolean isIgnoreCase() {
    return ignoreCase;
  }

  /**
   * @param ignoreCase
   *          the ignoreCase to set
   */
  public void setIgnoreCase(boolean ignoreCase) {
    this.ignoreCase = ignoreCase;
  }

  public final void printDependency(PrintWriter writer, String indent) {
    writer.println(indent + "<" + getClass().getSimpleName() + " name=\"" + getName() + "\">");
    printDependencyContent(writer, indent + WdkModel.INDENT);
    writer.println(indent + "</" + getClass().getSimpleName() + ">");
  }
  
  protected void printDependencyContent(PrintWriter writer, String indent) {
    // by default, nothing to print out.
  }
}
