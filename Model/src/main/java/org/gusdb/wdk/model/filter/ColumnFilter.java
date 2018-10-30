package org.gusdb.wdk.model.filter;

import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.answer.spec.SimpleAnswerSpec;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.record.attribute.QueryColumnAttributeField;
import org.json.JSONObject;

public abstract class ColumnFilter extends AbstractFilter {

  private final String _key;
  protected final QueryColumnAttributeField _attribute;

  public ColumnFilter(String name, QueryColumnAttributeField attribute) {
    _key = name + "-" + attribute.getName();
    _attribute = attribute;
  }

  @Override
  public String getKey() {
    return _key;
  }

  @Override
  public String getDisplay() {
    String display = super.getDisplay();
    if (display == null || display.length() == 0)
      display = _attribute.getDisplayName();
    return display;
  }

  protected String getAttributeSql(AnswerValue answer, String idSql) throws WdkModelException {
    String queryName = _attribute.getColumn().getQuery().getFullName();
    WdkModel wdkModel = _attribute.getWdkModel();
    Query query = (Query) wdkModel.resolveReference(queryName);
    String attributeSql = answer.getAttributeSql(query);
    String[] pkColumns = answer.getAnswerSpec().getQuestion().getRecordClass().getPrimaryKeyDefinition().getColumnRefs();
    StringBuilder sql = new StringBuilder("SELECT aq.* ");
    sql.append(" FROM (" + idSql + ") idq, (" + attributeSql + ") aq ");
    for (int i = 0; i < pkColumns.length; i++) {
      sql.append((i == 0) ? " WHERE " : " AND ");
      sql.append(" idq." + pkColumns[i] + " = aq." + pkColumns[i]);
    }
    return sql.toString();
  }

  @Override
  public void setDefaultValue(JSONObject defaultValue) {
    throw new UnsupportedOperationException("Not supported until the defaultValueEquals() method is fully implemented");
  }

  /**
   * Not fully implemented yet.
   */
  @Override
  public boolean defaultValueEquals(SimpleAnswerSpec answerSpec, JSONObject value) {
    return false;
  }
}
