package org.gusdb.wdk.service.formatter;

import java.util.Collection;
import java.util.List;

import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.wdk.model.record.FieldScope;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordClassSet;
import org.gusdb.wdk.model.record.attribute.AttributeCategory;
import org.gusdb.wdk.model.report.ReporterRef;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Formats WDK RecordClass objects into the following form:
 * {
 *   name: String,
 *   displayName: String,
 *   displayNamePlural: String,
 *   shortDisplayName: String,
 *   shortDisplayNamePlural: String,
 *   urlSegment: String,
 *   useBasket: Boolean
 *   description: String,
 *   formats: [ see getAnswerFormatsJson() ],
 *   primaryKeyColumnRefs: [ String ],
 *   recordIdAttributeName: String,
 *   attributes: [ see AttributeFieldFormatter ],
 *   tables: [ see TableFieldFormatter ],
 *   categories: [ see getAttributeCategoriesJson() ]
 * }
 * 
 * @author rdoherty
 */
public class RecordClassFormatter {

  public static JSONArray getRecordClassesJson(RecordClassSet[] recordClassSets,
      boolean expandRecordClasses, boolean expandAttributes,
      boolean expandTables, boolean expandTableAttributes) {
    JSONArray json = new JSONArray();
    for (RecordClassSet rcSet : recordClassSets) {
      for (RecordClass rc : rcSet.getRecordClasses()) {
        json.put(expandRecordClasses ? getRecordClassJson(rc,
            expandAttributes, expandTables, expandTableAttributes) :
              rc.getFullName());
      }
    }
    return json;
  }

  public static JSONObject getRecordClassJson(RecordClass recordClass,
      boolean expandAttributes, boolean expandTables, boolean expandTableAttributes) {
    return new JSONObject()
      .put(Keys.NAME, recordClass.getFullName())
      .put(Keys.DISPLAY_NAME, recordClass.getDisplayName())
      .put(Keys.DISPLAY_NAME_PLURAL, recordClass.getDisplayNamePlural())
      .put(Keys.SHORT_DISPLAY_NAME, recordClass.getShortDisplayName())
      .put(Keys.SHORT_DISPLAY_NAME_PLURAL, recordClass.getShortDisplayNamePlural())
      .put(Keys.URL_SEGMENT,  recordClass.getUrlSegment())
      .put(Keys.USE_BASKET, recordClass.isUseBasket())
      .put(Keys.DESCRIPTION, recordClass.getDescription())
      .put(Keys.FORMATS, getAnswerFormatsJson(recordClass.getReporterMap().values(), FieldScope.ALL))
      .put(Keys.HAS_ALL_RECORDS_QUERY, recordClass.hasAllRecordsQuery())
      .put(Keys.PRIMARY_KEY_REFS, JsonUtil.toJsonStringArray(recordClass.getPrimaryKeyDefinition().getColumnRefs()))
      .put(Keys.RECORD_ID_ATTRIBUTE_NAME, recordClass.getIdAttributeField().getName())
      .put(Keys.ATTRIBUTES, AttributeFieldFormatter.getAttributesJson(
        recordClass.getAttributeFieldMap().values(), FieldScope.ALL, expandAttributes))
      .put(Keys.TABLES, TableFieldFormatter.getTablesJson(recordClass.getTableFieldMap().values(),
        FieldScope.ALL, expandTables, expandTableAttributes))
      .put(Keys.CATEGORIES, getAttributeCategoriesJson(recordClass));
  }

  public static JSONArray getAnswerFormatsJson(Collection<ReporterRef> reporters, FieldScope scope) {
    JSONArray array = new JSONArray();
    for (ReporterRef reporter : reporters) {
      if (scope.isFieldInScope(reporter)) {
        JSONObject obj = new JSONObject()
          .put(Keys.NAME, reporter.getName())
          .put(Keys.DISPLAY_NAME, reporter.getDisplayName())
          .put(Keys.DESCRIPTION, reporter.getDescription())
          .put(Keys.IS_IN_REPORT, FieldScope.REPORT_MAKER.isFieldInScope(reporter))
          .put(Keys.SCOPES, reporter.getScopesList());
        array.put(obj);
      }
    }
    return array;
  }

  private static JSONArray getAttributeCategoriesJson(RecordClass recordClass) {
    List<AttributeCategory> categories = recordClass.getAttributeCategoryTree(FieldScope.ALL).getTopLevelCategories();
    JSONArray attributeCategoriesJson = new JSONArray();
    for (AttributeCategory category : categories) {
      attributeCategoriesJson.put(getAttributeCategoryJson(category));
    }
    return attributeCategoriesJson;
  }

  private static JSONObject getAttributeCategoryJson(AttributeCategory category) {
    JSONObject attributeCategoryJson = new JSONObject()
      .put(Keys.NAME,  category.getName())
      .put(Keys.DISPLAY_NAME,  category.getDisplayName())
      .put(Keys.DESCRIPTION, category.getDescription());
    JSONArray subCategoriesJson = new JSONArray();
    for (AttributeCategory subCategory : category.getSubCategories()) {
      subCategoriesJson.put(getAttributeCategoryJson(subCategory));
    }
    attributeCategoryJson.put(Keys.CATEGORIES, subCategoriesJson);
    return attributeCategoryJson;
  }
}
