package org.gusdb.wdk.service.formatter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gusdb.wdk.core.api.JsonKeys;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.FieldScope;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordClassSet;
import org.gusdb.wdk.model.report.ReporterRef;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Formats WDK RecordClass objects into the following form:
 * {
 *   fullName: String,
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


  public static List<String> getRecordClassNames(RecordClassSet[] recordClassSets) {
    final List<String> out = new ArrayList<>();
    for (RecordClassSet rcSet : recordClassSets) {
      for (RecordClass rc : rcSet.getRecordClasses()) {
        out.add(rc.getUrlSegment());
      }
    }
    return out;
  }

  public static Collection<Object> getExpandedRecordClassesJson(
      RecordClassSet[] recordClassSets, List<Question> allQuestions) {
    
    Map<String, List<Question>> rcQuestionMap = getRecordClassQuestionMap(allQuestions);
    final Collection<Object> out = new ArrayList<>();
    for (RecordClassSet rcSet : recordClassSets) {
      for (RecordClass rc : rcSet.getRecordClasses()) {
        List<Question> rcQuestions = rcQuestionMap.get(rc.getFullName());
        JSONArray questionsJson = QuestionFormatter.getQuestionsJsonWithoutParams(rcQuestions);
        out.add(getRecordClassJson(rc, true, true, true).put(JsonKeys.SEARCHES, questionsJson));
      }
    }
    return out;
  }
  
  private static Map<String, List<Question>> getRecordClassQuestionMap(List<Question> allQuestions) {
    Map<String, List<Question>> recordClassQuestionMap = new HashMap<String, List<Question>>();
    for (Question q : allQuestions) {
      String rcName = q.getRecordClass().getFullName();
      if (!recordClassQuestionMap.containsKey(rcName)) 
        recordClassQuestionMap.put(rcName, new ArrayList<>());
      recordClassQuestionMap.get(rcName).add(q);
    }
    return recordClassQuestionMap;
  }

  public static JSONObject getRecordClassJson(RecordClass recordClass,
      boolean expandAttributes, boolean expandTables, boolean expandTableAttributes) {
    

    return new JSONObject()
      .put(JsonKeys.FULL_NAME, recordClass.getFullName())
      .put(JsonKeys.DISPLAY_NAME, recordClass.getDisplayName())
      .put(JsonKeys.DISPLAY_NAME_PLURAL, recordClass.getDisplayNamePlural())
      .put(JsonKeys.SHORT_DISPLAY_NAME, recordClass.getShortDisplayName())
      .put(JsonKeys.SHORT_DISPLAY_NAME_PLURAL, recordClass.getShortDisplayNamePlural())
      .put(JsonKeys.NATIVE_DISPLAY_NAME, recordClass.getNativeDisplayName())
      .put(JsonKeys.NATIVE_DISPLAY_NAME_PLURAL, recordClass.getNativeDisplayNamePlural())
      .put(JsonKeys.NATIVE_SHORT_DISPLAY_NAME, recordClass.getNativeShortDisplayName())
      .put(JsonKeys.NATIVE_SHORT_DISPLAY_NAME_PLURAL, recordClass.getNativeShortDisplayNamePlural())
      .put(JsonKeys.URL_SEGMENT,  recordClass.getUrlSegment())
      .put(JsonKeys.ICON_NAME, recordClass.getIconName())
      .put(JsonKeys.USE_BASKET, recordClass.isUseBasket())
      .put(JsonKeys.DESCRIPTION, recordClass.getDescription())
      .put(JsonKeys.FORMATS, getAnswerFormatsJson(recordClass.getReporterMap().values(), FieldScope.ALL))
      .put(JsonKeys.HAS_ALL_RECORDS_QUERY, recordClass.hasAllRecordsQuery())
      .put(JsonKeys.PRIMARY_KEY_REFS, new JSONArray(recordClass.getPrimaryKeyDefinition().getColumnRefs()))
      .put(JsonKeys.RECORD_ID_ATTRIBUTE_NAME, recordClass.getIdAttributeField().getName())
      .put(JsonKeys.ATTRIBUTES, AttributeFieldFormatter.getAttributesJson(
          recordClass.getAttributeFieldMap().values(), FieldScope.ALL, expandAttributes))
      .put(JsonKeys.TABLES, TableFieldFormatter.getTablesJson(recordClass.getTableFieldMap().values(),
          FieldScope.ALL, expandTables, expandTableAttributes));
  }

  // TODO:  add a new field containing the JSON schema for the request
  public static JSONArray getAnswerFormatsJson(Collection<? extends ReporterRef> reporters, FieldScope scope) {
    JSONArray array = new JSONArray();
    for (ReporterRef reporter : reporters) {
      if (scope.isFieldInScope(reporter)) {
        JSONObject obj = new JSONObject()
          .put(JsonKeys.NAME, reporter.getName())
          .put(JsonKeys.TYPE,  reporter.getReferenceName())
          .put(JsonKeys.DISPLAY_NAME, reporter.getDisplayName())
          .put(JsonKeys.DESCRIPTION, reporter.getDescription())
          .put(JsonKeys.IS_IN_REPORT, FieldScope.REPORT_MAKER.isFieldInScope(reporter))
          .put(JsonKeys.SCOPES, reporter.getScopesList());
        array.put(obj);
      }
    }
    return array;
  }

  /* remove this after 4/22/2019 3:00:03pm EST
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
      .put(JsonKeys.NAME,  category.getName())
      .put(JsonKeys.DISPLAY_NAME,  category.getDisplayName())
      .put(JsonKeys.DESCRIPTION, category.getDescription());
    JSONArray subCategoriesJson = new JSONArray();
    for (AttributeCategory subCategory : category.getSubCategories()) {
      subCategoriesJson.put(getAttributeCategoryJson(subCategory));
    }
    attributeCategoryJson.put(JsonKeys.CATEGORIES, subCategoriesJson);
    return attributeCategoryJson;
  }
*/
}
