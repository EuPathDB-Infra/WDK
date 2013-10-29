/**
 * 
 */
package org.gusdb.wdk.model.query.param;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.config.ModelConfig;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.user.Dataset;
import org.gusdb.wdk.model.user.DatasetFactory;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Dataset param represents a list of user input ids. The list is readonly, and
 * stored in persistence instance, along with other user related data.
 * 
 * A dataset param is typed, and the author has to define a recordClass as the
 * type of the input IDs. This type is required as a function for getting a
 * snapshot of user basket and make a step from it.
 * 
 * @author xingao
 * 
 *         raw value: can be any kind of string. The default handler only
 *         support a list of values.
 * 
 *         reference value: user_dataset_id; which references to dataset_id,
 *         then to actual list of values.
 * 
 *         internal data: an SQL that represents the list of values.
 * 
 */
public class DatasetParam extends Param {

  public static final String METHOD_DATA = "data";
  public static final String METHOD_FILE = "file";
  
  public static final String TYPE_LIST = "list";
  public static final String TYPE_BASKET = "basket";

  private static final String SUB_PARAM_METHOD = "_method";
  private static final String SUB_PARAM_TYPE = "_type";
  private static final String SUB_PARAM_FILE = "_file";

  private String recordClassRef;
  private RecordClass recordClass;

  /**
   * Only used by datasetParam, determines what input type to be selected as
   * default.
   */
  private String defaultType;

  public DatasetParam() {
    setHandler(new DatasetParamHandler());
  }

  public DatasetParam(DatasetParam param) {
    super(param);
    this.recordClass = param.recordClass;
    this.recordClassRef = param.recordClassRef;
    this.defaultType = param.defaultType;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.gusdb.wdk.model.Param#resolveReferences(org.gusdb.wdk.model.WdkModel)
   */
  @Override
  public void resolveReferences(WdkModel model) throws WdkModelException {
    super.resolveReferences(model);
    recordClass = (RecordClass) wdkModel.resolveReference(recordClassRef);

    // make sure the handler is a DatasetParamHandler
    if (handler == null || !(handler instanceof DatasetParamHandler))
      throw new WdkModelException("The handler for datasetParam "
          + getFullName() + " has to be DatasetParamHandler or a subclass "
          + "of it.");
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.Param#clone()
   */
  @Override
  public Param clone() {
    return new DatasetParam(this);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.Param#appendJSONContent(org.json.JSONObject)
   */
  @Override
  protected void appendJSONContent(JSONObject jsParam, boolean extra)
      throws JSONException {
    if (extra) {
      jsParam.put("recordClass", recordClass.getFullName());
    }
  }

  /**
   * convert from user dataset id to dataset checksum
   * 
   * @see org.gusdb.wdk.model.query.param.Param#dependentValueToIndependentValue(org.gusdb.wdk.model.user.User,
   *      java.lang.String)
   */
  @Override
  public String dependentValueToIndependentValue(User user,
      String dependentValue) throws WdkModelException {
    logger.debug("dependent to independent: " + dependentValue);
    int userDatasetId = Integer.parseInt(dependentValue);
    Dataset dataset = user.getDataset(userDatasetId);
    dataset.setRecordClass(recordClass);
    return dataset.getChecksum();
  }

  /**
   * the internal value is an sql that represents the query from the dataset
   * tables, and returns the primary key columns.
   * 
   * @see org.gusdb.wdk.model.query.param.Param#independentValueToInternalValue
   *      (org.gusdb.wdk.model.user.User, java.lang.String)
   */
  @Override
  public String dependentValueToInternalValue(User user, String dependentValue)
      throws WdkModelException {
    // the input has to be a user-dataset-id
    int userDatasetId = Integer.parseInt(dependentValue);

    if (isNoTranslation())
      return Integer.toString(userDatasetId);

    ModelConfig config = wdkModel.getModelConfig();
    String dbLink = config.getAppDB().getUserDbLink();
    String wdkSchema = config.getUserDB().getWdkEngineSchema();
    String userSchema = config.getUserDB().getUserSchema();
    String dvTable = wdkSchema + DatasetFactory.TABLE_DATASET_VALUE + dbLink;
    String udTable = userSchema + DatasetFactory.TABLE_USER_DATASET + dbLink;
    String colDatasetId = DatasetFactory.COLUMN_DATASET_ID;
    String colUserDatasetId = DatasetFactory.COLUMN_USER_DATASET_ID;
    StringBuffer sql = new StringBuffer("SELECT ");
    String[] pkColumns = recordClass.getPrimaryKeyAttributeField().getColumnRefs();
    for (int i = 1; i <= pkColumns.length; i++) {
      if (i > 1)
        sql.append(", ");
      sql.append("dv." + Utilities.COLUMN_PK_PREFIX + i);
      sql.append(" AS " + pkColumns[i - 1]);
    }
    sql.append(" FROM ");
    sql.append(udTable + " ud, " + dvTable + " dv ");
    sql.append(" WHERE dv." + colDatasetId + " = ud." + colDatasetId);
    sql.append(" AND ud." + colUserDatasetId + " = " + userDatasetId);
    return sql.toString();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.param.Param#independentValueToRawValue(org.
   * gusdb.wdk.model.user.User, java.lang.String)
   */
  @Override
  public String dependentValueToRawValue(User user, String dependentValue)
      throws WdkModelException {
    logger.debug("dependent to raw: " + dependentValue);
    int userDatasetId = Integer.parseInt(dependentValue);
    Dataset dataset = user.getDataset(userDatasetId);
    dataset.setRecordClass(recordClass);
    return dataset.getValue();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.param.Param#rawValueToIndependentValue(org.
   * gusdb.wdk.model.user.User, java.lang.String)
   */
  @Override
  public String rawOrDependentValueToDependentValue(User user, String rawValue)
      throws WdkModelException {
    // first assume the input is dependent value, that is, user dataset id
    if (rawValue == null || rawValue.length() == 0)
      return null;
    if (rawValue.matches("\\d+")) {
      int userDatasetId = Integer.parseInt(rawValue);
      try {
        user.getDataset(userDatasetId);
        return rawValue;
      } catch (Exception ex) {
        // dataset doesn't exist, create one
        logger.info("user dataset id doesn't exist: " + userDatasetId);
      }
    }
    return rawValueToDependentValue(user, "", rawValue);
  }

  /**
   * @param user
   * @param uploadFile
   * @param rawValue
   * @return
   */
  public String rawValueToDependentValue(User user, String uploadFile,
      String rawValue) throws WdkModelException {
    logger.debug("raw to dependent: " + rawValue);
    Dataset dataset = user.createDataset(recordClass, uploadFile, rawValue);
    return Integer.toString(dataset.getUserDatasetId());
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.gusdb.wdk.model.query.param.Param#validateValue(org.gusdb.wdk.model
   * .user.User, java.lang.String)
   */
  @Override
  protected void validateValue(User user, String dependentValue,
      Map<String, String> contextValues) throws WdkModelException {
    // try to get the dataset
    int userDatasetId = Integer.parseInt(dependentValue);
    user.getDataset(userDatasetId);
  }

  /**
   * @return the recordClass
   */
  public RecordClass getRecordClass() {
    return recordClass;
  }

  /**
   * @param recordClassRef
   *          the recordClassRef to set
   */
  public void setRecordClassRef(String recordClassRef) {
    this.recordClassRef = recordClassRef;
  }

  public void setRecordClass(RecordClass recordClass) {
    this.recordClass = recordClass;
  }

  public String getDefaultType() {
    return (defaultType != null) ? defaultType : TYPE_DATA;
  }

  public void setDefaultType(String defaultType) {
    this.defaultType = defaultType;
  }

  @Override
  protected void applySuggection(ParamSuggestion suggest) {
    defaultType = suggest.getDefaultType();
  }

  public String getTypeSubParam() {
    return name + SUB_PARAM_TYPE;
  }

  public String getMethodSubParam() {
    return name + SUB_PARAM_METHOD;
  }
  
  public String getFileSubParam() {
    return name + SUB_PARAM_FILE;
  }

  public Map<String, String> getTypes() {
    Map<String, String> types = new LinkedHashMap<>();
    DatasetParamHandler datasetHandler = (DatasetParamHandler) handler;
    for (DatasetParser parser : datasetHandler.getParsers()) {
      types.put(parser.getName(), parser.getDisplay());
    }
    return types;
  }
}
