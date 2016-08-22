package org.gusdb.wdk.model.user;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkRuntimeException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.AnswerFilterInstance;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.answer.SummaryView;
import org.gusdb.wdk.model.dataset.Dataset;
import org.gusdb.wdk.model.dataset.DatasetFactory;
import org.gusdb.wdk.model.filter.FilterOptionList;
import org.gusdb.wdk.model.query.BooleanOperator;
import org.gusdb.wdk.model.query.BooleanQuery;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordClassSet;
import org.gusdb.wdk.model.record.RecordView;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.user.StepFactory.NameCheckInfo;
import org.json.JSONException;

import org.gusdb.fgputil.functional.FunctionalInterfaces.Function;
import org.gusdb.fgputil.functional.FunctionalInterfaces.Predicate;

import static org.gusdb.fgputil.functional.Functions.*;

/**
 * @author xingao
 * 
 */
public class User /* implements Serializable */{

  public final static String PREF_ITEMS_PER_PAGE = "preference_global_items_per_page";
  public final static String PREF_REMOTE_KEY = "preference_remote_key";

  private final static int PREF_VALUE_LENGTH = 4000;

  public final static String SORTING_ATTRIBUTES_SUFFIX = "_sort";
  public final static String SUMMARY_ATTRIBUTES_SUFFIX = "_summary";

  public final static String SUMMARY_VIEW_PREFIX = "summary_view_";
  public final static String RECORD_VIEW_PREFIX = "record_view_";

  public final static String DEFAULT_SUMMARY_VIEW_PREF_SUFFIX = "";

  // represents the maximum number of sorts we apply to an answer
  public static final int SORTING_LEVEL = 3;

  private Logger logger = Logger.getLogger(User.class);

  private WdkModel wdkModel;
  private UserFactory userFactory;
  private StepFactory stepFactory;
  private DatasetFactory datasetFactory;
  private int _userId = 0;
  private String signature;

  // basic user information
  private String emailPrefix;
  private String openId;

  private Set<String> userRoles;
  private boolean guest = true;
  
  /**
   * the preferences for the user: <prefName, prefValue>. It only contains the preferences for the current
   * project
   */
  private Map<String, String> globalPreferences;
  private Map<String, String> projectPreferences;
  
  /**
   * Holds all key/value pairs associated with the user's profile.
   */
  private Map<UserProfileProperty,String> profile = new HashMap<>();

  // keep track in session , but don't serialize:
  // currently open strategies
  private transient ActiveStrategyFactory activeStrategyFactory;

  // keep track of most recent front end action
  private String frontAction = null;
  private Integer frontStrategy = null;
  private Integer frontStep = null;

  User(WdkModel model, int userId, String email, String signature) {
    this._userId = userId;
    setEmail(email);
    this.signature = signature;

    userRoles = new LinkedHashSet<String>();

    globalPreferences = new LinkedHashMap<String, String>();
    projectPreferences = new LinkedHashMap<String, String>();

    setWdkModel(model);

    activeStrategyFactory = new ActiveStrategyFactory(this);
  }

  /**
   * The setter is called when the session is restored (deserialized)
   * 
   * @param wdkModel
   * @throws WdkUserException
   */
  public void setWdkModel(WdkModel wdkModel) {
    this.wdkModel = wdkModel;
    this.userFactory = wdkModel.getUserFactory();
    this.stepFactory = wdkModel.getStepFactory();
    this.datasetFactory = wdkModel.getDatasetFactory();
  }

  public WdkModel getWdkModel() {
    return this.wdkModel;
  }

  /**
   * user the userId field as a flag for lazy creation of guest user
   * 
   * @return Returns the userId.
   * @throws WdkModelException
   */
  public synchronized int getUserId() throws WdkModelException {
    if (_userId == 0)
      userFactory.saveTemporaryUser(this);
    return _userId;
  }

  public void setUserId(int userId) {
    this._userId = userId;
  }

  /**
   * @return Returns the signature.
   * @throws WdkModelException
   */
  public synchronized String getSignature() throws WdkModelException {
    if (signature == null)
      userFactory.saveTemporaryUser(this);
    return signature;
  }

  public void setSignature(String signature) {
    this.signature = signature;
  }

  /**
   * @return Returns the email.
   * @throws WdkModelException
   */
  public synchronized String getEmail() throws WdkModelException {
    if (this.profile.get(UserProfileProperty.EMAIL) == null) {
      userFactory.saveTemporaryUser(this);
    }
    return this.profile.get(UserProfileProperty.EMAIL);
  }

  public void setEmail(String email) {
    this.profile.put(UserProfileProperty.EMAIL, email);
  }

  public String getEmailPrefix() {
    return emailPrefix;
  }

  public void setEmailPrefix(String emailPrefix) {
    this.emailPrefix = emailPrefix;
  }
  
  /**
   * Sets the value of the profile property given by the UserProfileProperty enum
   * @param key
   * @param value
   */
  public void setProfileProperty(UserProfileProperty key, String value) {
    this.profile.put(key, value);
  }
  
  /**
   * Return the entire user profile property map
   * @return
   */
  public Map<UserProfileProperty, String> getProfileProperties() {
    return this.profile;  
  }
  
  /**
   * Removes all existing user profile properties
   */
  public void clearProfileProperties() {
    this.profile.clear();
  }
  
  
  /**
   * @return Returns the address.
   */
  public String getAddress() {
    return this.profile.get(UserProfileProperty.ADDRESS);
  }

  /**
   * @param address
   *          The address to set.
   */
  public void setAddress(String address) {
    this.profile.put(UserProfileProperty.ADDRESS, address);
  }

  /**
   * @return Returns the city.
   */
  public String getCity() {
    return this.profile.get(UserProfileProperty.CITY);
  }

  /**
   * @param city
   *          The city to set.
   */
  public void setCity(String city) {
    this.profile.put(UserProfileProperty.CITY, city);
  }

  /**
   * @return Returns the country.
   */
  public String getCountry() {
    return this.profile.get(UserProfileProperty.COUNTRY);
  }

  /**
   * @param country
   *          The country to set.
   */
  public void setCountry(String country) {
    this.profile.put(UserProfileProperty.COUNTRY, country);
  }

  /**
   * @return Returns the department.
   */
  public String getDepartment() {
    return this.profile.get(UserProfileProperty.DEPARTMENT);
  }

  /**
   * @param department
   *          The department to set.
   */
  public void setDepartment(String department) {
    this.profile.put(UserProfileProperty.DEPARTMENT, department);
  }

  /**
   * @return Returns the firstName.
   */
  public String getFirstName() {
    return this.profile.get(UserProfileProperty.FIRST_NAME);
  }

  /**
   * @param firstName
   *          The firstName to set.
   */
  public void setFirstName(String firstName) {
    this.profile.put(UserProfileProperty.FIRST_NAME, firstName);
  }

  /**
   * @return Returns the lastName.
   */
  public String getLastName() {
    return this.profile.get(UserProfileProperty.LAST_NAME);
  }

  /**
   * @param lastName
   *          The lastName to set.
   */
  public void setLastName(String lastName) {
    this.profile.put(UserProfileProperty.LAST_NAME, lastName);
  }

  /**
   * @return Returns the middleName.
   */
  public String getMiddleName() {
    return this.profile.get(UserProfileProperty.MIDDLE_NAME);
  }

  /**
   * @param middleName
   *          The middleName to set.
   */
  public void setMiddleName(String middleName) {
    this.profile.put(UserProfileProperty.MIDDLE_NAME, middleName);
  }

  /**
   * @return User's full name (first middle last)
   */
  public String getDisplayName() {
    return (formatNamePart(getFirstName()) + formatNamePart(getMiddleName()) + formatNamePart(getLastName())).trim();
  }

  private String formatNamePart(String namePart) {
    return (namePart == null || namePart.isEmpty() ? "" : " " + namePart.trim());
  }

  /**
   * @return Returns the organization.
   */
  public String getOrganization() {
    return this.profile.get(UserProfileProperty.ORGANIZATION);
  }

  /**
   * @param organization
   *          The organization to set.
   */
  public void setOrganization(String organization) {
    this.profile.put(UserProfileProperty.ORGANIZATION, organization);
  }

  /**
   * @return Returns the phoneNumber.
   */
  public String getPhoneNumber() {
    return this.profile.get(UserProfileProperty.PHONE_NUMBER);
  }

  /**
   * @param phoneNumber
   *          The phoneNumber to set.
   */
  public void setPhoneNumber(String phoneNumber) {
    this.profile.put(UserProfileProperty.PHONE_NUMBER, phoneNumber);
  }

  /**
   * @return Returns the state.
   */
  public String getState() {
    return this.profile.get(UserProfileProperty.STATE);
  }

  /**
   * @param state
   *          The state to set.
   */
  public void setState(String state) {
    this.profile.put(UserProfileProperty.STATE, state);
  }

  /**
   * @return Returns the title.
   */
  public String getTitle() {
    return this.profile.get(UserProfileProperty.TITLE);
  }

  /**
   * @param title
   *          The title to set.
   */
  public void setTitle(String title) {
    this.profile.put(UserProfileProperty.TITLE, title);
  }

  /**
   * @return Returns the zipCode.
   */
  public String getZipCode() {
    return this.profile.get(UserProfileProperty.ZIP_CODE);
  }

  /**
   * @param zipCode
   *          The zipCode to set.
   */
  public void setZipCode(String zipCode) {
    this.profile.put(UserProfileProperty.ZIP_CODE, zipCode);
  }

  /**
   * @return user's OpenID
   */
  public String getOpenId() {
    return openId;
  }

  /**
   * @param openId
   *          user's OpenID
   */
  public void setOpenId(String openId) {
    this.openId = openId;
  }

  /**
   * @return Returns the guest.
   * @throws WdkUserException
   */
  public boolean isGuest() {
    return guest;
  }

  /**
   * @return Returns the userRole.
   */
  public String[] getUserRoles() {
    String[] roles = new String[userRoles.size()];
    userRoles.toArray(roles);
    return roles;
  }

  /**
   * @param userRole
   *          The userRole to set.
   */
  public void addUserRole(String userRole) {
    this.userRoles.add(userRole);
  }

  public void removeUserRole(String userRole) {
    userRoles.remove(userRole);
  }

  public boolean containsUserRole(String userRole) {
    return userRoles.contains(userRole);
  }

  void setUserRole(Set<String> roles) {
    this.userRoles.clear();
    this.userRoles.addAll(roles);
  }

  public String getFrontAction() {
    return frontAction;
  }

  public Integer getFrontStrategy() {
    return frontStrategy;
  }

  public Integer getFrontStep() {
    return frontStep;
  }

  public void setFrontAction(String frontAction) {
    this.frontAction = frontAction;
  }

  public void setFrontStrategy(int frontStrategy) {
    System.out.println("Setting frontStrategy.");
    this.frontStrategy = Integer.valueOf(frontStrategy);
    System.out.println("Done.");
  }

  public void setFrontStep(int frontStep) {
    this.frontStep = Integer.valueOf(frontStep);
  }

  public void resetFrontAction() {
    frontAction = null;
    frontStrategy = null;
    frontStep = null;
  }

  /**
   * @param guest
   *          The guest to set.
   */
  void setGuest(boolean guest) {
    this.guest = guest;
  }

  /**
   * Create a step from the existing answerValue
   * 
   * @param answerValue
   * @return
   * @throws JSONException
   * @throws SQLException
   * @throws WdkModelException
   * @throws WdkUserException
   * @throws NoSuchAlgorithmException
   */
  Step createStep(Integer strategyId, AnswerValue answerValue, boolean deleted, int assignedWeight)
      throws WdkModelException {
    Question question = answerValue.getQuestion();
    Map<String, String> paramValues = answerValue.getIdsQueryInstance().getParamStableValues();
    AnswerFilterInstance filter = answerValue.getFilter();
    int startIndex = answerValue.getStartIndex();
    int endIndex = answerValue.getEndIndex();

    try {
      return createStep(strategyId, question, paramValues, filter, startIndex, endIndex, deleted, false,
          assignedWeight, answerValue.getFilterOptions());
    }
    catch (WdkUserException ex) {
      throw new WdkModelException(ex);
    }
  }

  public Step createStep(Integer strategyId, Question question, Map<String, String> paramValues,
      String filterName, boolean deleted, boolean validate, int assignedWeight) throws WdkModelException,
      WdkUserException {
    AnswerFilterInstance filter = null;
    RecordClass recordClass = question.getRecordClass();
    if (filterName != null) {
      filter = recordClass.getFilterInstance(filterName);
    }
    else
      filter = recordClass.getDefaultFilter();
    return createStep(strategyId, question, paramValues, filter, deleted, validate, assignedWeight);
  }

  public Step createStep(Integer strategyId, Question question, Map<String, String> paramValues,
      AnswerFilterInstance filter, boolean deleted, boolean validate, int assignedWeight)
      throws WdkModelException, WdkUserException {
    return createStep(strategyId, question, paramValues, filter, deleted, validate,
        assignedWeight, null);
  }

  public Step createStep(Integer strategyId, Question question, Map<String, String> paramValues,
      AnswerFilterInstance filter, boolean deleted, boolean validate, int assignedWeight,
      FilterOptionList filterOptions)
      throws WdkModelException, WdkUserException {
    int endIndex = getItemsPerPage();
    return createStep(strategyId, question, paramValues, filter, 1, endIndex, deleted, validate,
        assignedWeight, filterOptions);
  }

  public Step createStep(Integer strategyId, Question question, Map<String, String> paramValues,
      AnswerFilterInstance filter, int pageStart, int pageEnd, boolean deleted, boolean validate,
      int assignedWeight, FilterOptionList filterOptions) throws WdkModelException, WdkUserException {
    Step step = stepFactory.createStep(this, strategyId, question, paramValues, filter, pageStart, pageEnd,
        deleted, validate, assignedWeight, filterOptions);
    return step;
  }

  public Strategy createStrategy(Step step, boolean saved) throws WdkModelException, WdkUserException {
    return createStrategy(step, null, null, saved, null, false, false);
  }

  public Strategy createStrategy(Step step, boolean saved, boolean hidden) throws WdkModelException,
      WdkUserException {
    return createStrategy(step, null, null, saved, null, hidden, false);
  }

  // Transitional method...how to handle savedName properly?
  // Probably by expecting it if a name is given?
  public Strategy createStrategy(Step step, String name, boolean saved) throws WdkModelException,
      WdkUserException {
    return createStrategy(step, name, null, saved, null, false, false);
  }

  public Strategy createStrategy(Step step, String name, String savedName, boolean saved, String description,
      boolean hidden, boolean isPublic) throws WdkModelException, WdkUserException {
    Strategy strategy = stepFactory.createStrategy(this, step, name, savedName, saved, description, hidden,
        isPublic);

    // set the view to this one
    String strategyKey = Integer.toString(strategy.getStrategyId());
    this.activeStrategyFactory.openActiveStrategy(strategyKey);
    if (strategy.isValid()) {
      this.activeStrategyFactory.setViewStrategyKey(strategyKey);
      this.activeStrategyFactory.setViewStepId(step.getStepId());
    }
    return strategy;
  }

  public int getNewStrategyId() throws WdkModelException {
    return stepFactory.getNewStrategyId();
  }

  /**
   * this method is only called by UserFactory during the login process, it merges the existing history of the
   * current guest user into the logged-in user.
   * 
   * @param user
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public void mergeUser(User user) throws WdkModelException, WdkUserException {
    // TEST
    logger.debug("Merging user #" + user.getUserId() + " into user #" + getUserId() + "...");

    // first of all we import all the strategies
    Set<Integer> importedSteps = new LinkedHashSet<Integer>();
    Map<Integer, Integer> strategiesMap = new LinkedHashMap<Integer, Integer>();
    Map<Integer, Integer> stepsMap = new LinkedHashMap<Integer, Integer>();
    for (Strategy strategy : user.getStrategies()) {
      // the root step is considered as imported
      Step rootStep = strategy.getLatestStep();

      // import the strategy
      Strategy newStrategy = this.importStrategy(strategy, stepsMap);

      importedSteps.add(rootStep.getStepId());
      strategiesMap.put(strategy.getStrategyId(), newStrategy.getStrategyId());
    }

    // the current implementation can only keep the root level of the
    // imported strategies open;
    int[] oldActiveStrategies = user.activeStrategyFactory.getRootStrategies();
    for (int oldStrategyId : oldActiveStrategies) {
      int newStrategyId = strategiesMap.get(oldStrategyId);
      activeStrategyFactory.openActiveStrategy(Integer.toString(newStrategyId));
    }

    // no need to import steps that don't belong to any strategies, since they won't be referenced in any way.

    // if a front action is specified, copy it over and update ids
    if (user.getFrontAction() != null) {
      setFrontAction(user.getFrontAction());
      if (strategiesMap.containsKey(user.getFrontStrategy())) {
        setFrontStrategy(strategiesMap.get(user.getFrontStrategy()));
      }
      if (stepsMap.containsKey(user.getFrontStep())) {
        setFrontStep(stepsMap.get(user.getFrontStep()));
      }
    }
  }

  public Map<Integer, Step> getStepsMap() throws WdkModelException {
    logger.debug("loading steps...");
    Map<Integer, Step> invalidSteps = new LinkedHashMap<Integer, Step>();
    Map<Integer, Step> allSteps = stepFactory.loadSteps(this, invalidSteps);

    return allSteps;
  }

  public Map<Integer, Strategy> getStrategiesMap() throws WdkModelException {
    logger.debug("loading strategies...");
    Map<Integer, Strategy> invalidStrategies = new LinkedHashMap<Integer, Strategy>();
    Map<Integer, Strategy> strategies = stepFactory.loadStrategies(this, invalidStrategies);

    return strategies;
  }

  public Map<String, List<Step>> getStepsByCategory() throws WdkModelException {
    Map<Integer, Step> steps = getStepsMap();
    Map<String, List<Step>> category = new LinkedHashMap<String, List<Step>>();
    for (Step step : steps.values()) {
      // not include the histories marked as 'deleted'
      if (step.isDeleted())
        continue;

      String type = step.getRecordClass().getFullName();
      List<Step> list;
      if (category.containsKey(type)) {
        list = category.get(type);
      }
      else {
        list = new ArrayList<Step>();
        category.put(type, list);
      }
      list.add(step);
    }
    return category;
  }

  public Strategy[] getInvalidStrategies() throws WdkModelException {
    try {
      Map<Integer, Strategy> strategies = new LinkedHashMap<Integer, Strategy>();
      stepFactory.loadStrategies(this, strategies);

      Strategy[] array = new Strategy[strategies.size()];
      strategies.values().toArray(array);
      return array;
    }
    catch (WdkModelException ex) {
      System.out.println(ex);
      throw ex;
    }
  }

  public Strategy[] getStrategies() throws WdkModelException {
    Map<Integer, Strategy> map = getStrategiesMap();
    Strategy[] array = new Strategy[map.size()];
    map.values().toArray(array);
    return array;
  }

  public Map<String, List<Strategy>> getStrategiesByCategory() throws WdkModelException {
    Map<Integer, Strategy> strategies = getStrategiesMap();
    return formatStrategiesByRecordClass(strategies.values());
  }

  public Map<String, List<Strategy>> getUnsavedStrategiesByCategory() throws WdkModelException {
    List<Strategy> strategies = stepFactory.loadStrategies(this, false, false);
    return formatStrategiesByRecordClass(strategies);
  }

  /**
   * @return
   * @throws WdkUserException
   * @throws WdkModelException
   * @throws NoSuchAlgorithmException
   * @throws JSONException
   * @throws SQLException
   */
  public Map<String, List<Strategy>> getSavedStrategiesByCategory() throws WdkModelException {
    List<Strategy> strategies = stepFactory.loadStrategies(this, true, false);
    return formatStrategiesByRecordClass(strategies);
  }

  public Map<String, List<Strategy>> getRecentStrategiesByCategory() throws WdkModelException {
    List<Strategy> strategies = stepFactory.loadStrategies(this, false, true);
    return formatStrategiesByRecordClass(strategies);
  }

  public Map<String, List<Strategy>> getActiveStrategiesByCategory() throws WdkModelException,
      WdkUserException {
    Strategy[] strategies = getActiveStrategies();
    List<Strategy> list = new ArrayList<Strategy>();
    for (Strategy strategy : strategies)
      list.add(strategy);
    return formatStrategiesByRecordClass(list);
  }

  private Map<String, List<Strategy>> formatStrategiesByRecordClass(Collection<Strategy> strategies)
      throws WdkModelException {
    Map<String, List<Strategy>> category = new LinkedHashMap<String, List<Strategy>>();
    for (RecordClassSet rcSet : wdkModel.getAllRecordClassSets()) {
      for (RecordClass recordClass : rcSet.getRecordClasses()) {
        String type = recordClass.getFullName();
        category.put(type, new ArrayList<Strategy>());
      }
    }
    for (Strategy strategy : strategies) {
      String rcName = strategy.getRecordClass().getFullName();
      List<Strategy> list;
      if (category.containsKey(rcName)) {
        list = category.get(rcName);
      }
      else {
        list = new ArrayList<Strategy>();
        category.put(rcName, list);
      }
      category.get(rcName).add(strategy);
    }
    return category;
  }

  public Map<Integer, Step> getStepsMap(String rcName) throws WdkModelException {
    Map<Integer, Step> steps = getStepsMap();
    Map<Integer, Step> selected = new LinkedHashMap<Integer, Step>();
    for (int stepDisplayId : steps.keySet()) {
      Step step = steps.get(stepDisplayId);
      if (rcName.equalsIgnoreCase(step.getRecordClass().getFullName()))
        selected.put(stepDisplayId, step);
    }
    return selected;
  }

  public Step[] getSteps(String rcName) throws WdkModelException {
    Map<Integer, Step> map = getStepsMap(rcName);
    Step[] array = new Step[map.size()];
    map.values().toArray(array);
    return array;
  }

  public Step[] getSteps() throws WdkModelException {
    Map<Integer, Step> map = getStepsMap();
    Step[] array = new Step[map.size()];
    map.values().toArray(array);
    return array;
  }

  public Step[] getInvalidSteps() throws WdkModelException {
    Map<Integer, Step> steps = new LinkedHashMap<Integer, Step>();
    stepFactory.loadSteps(this, steps);

    Step[] array = new Step[steps.size()];
    steps.values().toArray(array);
    return array;
  }

  public Map<Integer, Strategy> getStrategiesMap(String rcName) throws WdkModelException {
    Map<Integer, Strategy> strategies = getStrategiesMap();
    Map<Integer, Strategy> selected = new LinkedHashMap<Integer, Strategy>();
    for (int strategyId : strategies.keySet()) {
      Strategy strategy = strategies.get(strategyId);
      if (rcName.equalsIgnoreCase(strategy.getRecordClass().getFullName()))
        selected.put(strategyId, strategy);
    }
    return selected;
  }

  public Strategy[] getStrategies(String dataType) throws WdkModelException {
    Map<Integer, Strategy> map = getStrategiesMap(dataType);
    Strategy[] array = new Strategy[map.size()];
    map.values().toArray(array);
    return array;
  }

  public Step getStep(int stepID) throws WdkModelException {
    return stepFactory.loadStep(this, stepID);
  }

  public Strategy getStrategy(int userStrategyId) throws WdkModelException, WdkUserException {
    return getStrategy(userStrategyId, true);
  }

  public Strategy getStrategy(int userStrategyId, boolean allowDeleted) throws WdkModelException,
      WdkUserException {
    return stepFactory.loadStrategy(this, userStrategyId, allowDeleted);
  }

  public void deleteSteps() throws WdkModelException {
    deleteSteps(false);
  }

  public void deleteSteps(boolean allProjects) throws WdkModelException {
    stepFactory.deleteSteps(this, allProjects);
  }

  public void deleteInvalidSteps() throws WdkModelException {
    stepFactory.deleteInvalidSteps(this);
  }

  public void deleteInvalidStrategies() throws WdkModelException {
    stepFactory.deleteInvalidStrategies(this);
  }

  public void deleteStep(int displayId) throws WdkModelException {
    stepFactory.deleteStep(displayId);
  }

  public void deleteStrategy(int strategyId) throws WdkModelException {
    String strategyKey = Integer.toString(strategyId);
    int order = activeStrategyFactory.getOrder(strategyKey);
    if (order > 0)
      activeStrategyFactory.closeActiveStrategy(strategyKey);
    stepFactory.deleteStrategy(strategyId);
  }

  public void deleteStrategies() throws WdkModelException {
    activeStrategyFactory.clear();
    deleteStrategies(false);
  }

  public void deleteStrategies(boolean allProjects) throws WdkModelException {
    activeStrategyFactory.clear();
    stepFactory.deleteStrategies(this, allProjects);
  }

  public int getStepCount() throws WdkModelException {
    return stepFactory.getStepCount(this);
  }

  public int getStrategyCount() throws WdkModelException {
    return stepFactory.getStrategyCount(this);
  }

  public void setProjectPreference(String prefName, String prefValue) {
    if (prefValue == null)
      prefValue = prefName;
    projectPreferences.put(prefName, prefValue);
  }

  public void unsetProjectPreference(String prefName) {
    projectPreferences.remove(prefName);
  }

  public Map<String, String> getProjectPreferences() {
    return new LinkedHashMap<String, String>(projectPreferences);
  }

  public String getProjectPreference(String key) {
    return projectPreferences.get(key);
  }

  public void setGlobalPreference(String prefName, String prefValue) {
    if (prefValue == null)
      prefValue = prefName;
    globalPreferences.put(prefName, prefValue);
  }

  public String getGlobalPreference(String key) {
    return globalPreferences.get(key);
  }

  public void unsetGlobalPreference(String prefName) {
    globalPreferences.remove(prefName);
  }

  public Map<String, String> getGlobalPreferences() {
    return new LinkedHashMap<String, String>(globalPreferences);
  }
  
  public void clearGlobalPreferences() {
    globalPreferences.clear();
  }
  
  public void clearProjectPreferences() {
    projectPreferences.clear();
  }
  
  public void clearPreferences() {
    globalPreferences.clear();
    projectPreferences.clear();
  }

  void setPreferences(List<Map<String, String>> preferences) {
    clearPreferences();
    globalPreferences.putAll(preferences.get(0));
    projectPreferences.putAll(preferences.get(1));
  }

  public void changePassword(String oldPassword, String newPassword, String confirmPassword)
      throws WdkUserException, WdkModelException {
    userFactory.changePassword(this.profile.get(UserProfileProperty.EMAIL), oldPassword, newPassword, confirmPassword);
  }

  DatasetFactory getDatasetFactory() {
    return datasetFactory;
  }

  public Dataset getDataset(int datasetId) throws WdkModelException {
    return datasetFactory.getDataset(this, datasetId);
  }

  /**
   * set this method synchronized to make sure the preferences are not updated at the same time.
   * 
   * @throws WdkModelException
   */
  public void save() throws WdkModelException {
    userFactory.saveUser(this);
  }

  public int getItemsPerPage() {
    String prefValue = getProjectPreference(User.PREF_ITEMS_PER_PAGE);
    int itemsPerPage = (prefValue == null) ? 20 : Integer.parseInt(prefValue);
    return itemsPerPage;
  }

  public void setItemsPerPage(int itemsPerPage) throws WdkModelException {
    if (itemsPerPage <= 0)
      itemsPerPage = 20;
    else if (itemsPerPage > 1000)
      itemsPerPage = 1000;
    setProjectPreference(User.PREF_ITEMS_PER_PAGE, Integer.toString(itemsPerPage));
    save();
  }

  //***********************************************************************
  //*** Methods to support specification and sorting of summary attributes
  //***********************************************************************

  public Map<String, Boolean> getSortingAttributes(
      String questionFullName, String keySuffix) throws WdkModelException {
    Question question = wdkModel.getQuestion(questionFullName);
    Map<String, AttributeField> attributes = question.getAttributeFieldMap();

    String sortKey = questionFullName + SORTING_ATTRIBUTES_SUFFIX + keySuffix;
    String sortingList = projectPreferences.get(sortKey);

    // user doesn't have sorting preference, return the default from question.
    if (sortingList == null)
      return question.getSortingAttributeMap();

    // convert the list into a map.
    Map<String, Boolean> sortingAttributes = new LinkedHashMap<>();
    for (String clause : sortingList.split(",")) {
      String[] sort = clause.trim().split("\\s+", 2);

      // ignore the invalid sorting attribute
      String attrName = sort[0];
      if (!attributes.containsKey(attrName))
        continue;

      boolean order = (sort.length == 1 || sort[1].equalsIgnoreCase("ASC"));
      sortingAttributes.put(sort[0], order);
    }
    return sortingAttributes;
  }

  public String addSortingAttribute(String questionFullName, String attrName,
      boolean ascending, String keySuffix) throws WdkModelException {
    // make sure the attribute exists in the question
    Question question = wdkModel.getQuestion(questionFullName);
    if (!question.getAttributeFieldMap().containsKey(attrName))
      throw new WdkModelException("Cannot sort by attribute '" + attrName +
          "' since it doesn't belong the question " + questionFullName);

    StringBuilder sort = new StringBuilder(attrName);
    sort.append(ascending ? " ASC" : " DESC");

    Map<String, Boolean> previousMap = getSortingAttributes(questionFullName, keySuffix);
    if (previousMap != null) {
      int count = 1;
      for (String name : previousMap.keySet()) {
        if (name.equals(attrName))
          continue;
        if (count >= Utilities.SORTING_LEVEL)
          break;
        sort.append(",").append(name).append(previousMap.get(name) ? " ASC" : " DESC");
      }
    }

    String sortKey = questionFullName + SORTING_ATTRIBUTES_SUFFIX + keySuffix;
    String sortValue = sort.toString();
    projectPreferences.put(sortKey, sortValue);
    return sortValue;
  }

  public void setSortingAttributes(String questionName, String sortings, String keySuffix) {
    String sortKey = questionName + SORTING_ATTRIBUTES_SUFFIX + keySuffix;
    projectPreferences.put(sortKey, sortings);
  }

  public String[] getSummaryAttributes(String questionFullName, String keySuffix) throws WdkModelException {
    Question question = wdkModel.getQuestion(questionFullName);
    Map<String, AttributeField> attributes = question.getAttributeFieldMap();

    String summaryKey = questionFullName + SUMMARY_ATTRIBUTES_SUFFIX + keySuffix;
    String summaryValue = projectPreferences.get(summaryKey);
    Set<String> summary = new LinkedHashSet<>();
    if (summaryValue != null) {
      for (String attrName : summaryValue.split(",")) {
        attrName = attrName.trim();
        // ignore invalid attribute names;
        if (attributes.containsKey(attrName) && !summary.contains(attrName)) {
          summary.add(attrName);
        }
      }
    }
    if (summary.isEmpty()) {
      return question.getSummaryAttributeFieldMap().keySet().toArray(new String[0]);
    }
    else {
      return summary.toArray(new String[0]);
    }
  }

  public void resetSummaryAttributes(String questionFullName, String keySuffix) {
    String summaryKey = questionFullName + SUMMARY_ATTRIBUTES_SUFFIX + keySuffix;
    projectPreferences.remove(summaryKey);
    logger.debug("reset used weight to false");
  }

  public String setSummaryAttributes(String questionFullName, String[] summaryNames, String keySuffix) throws WdkModelException {
    // make sure all the attribute names exist
    Question question = (Question) wdkModel.resolveReference(questionFullName);
    Map<String, AttributeField> attributes = question.getAttributeFieldMap();

    StringBuilder summary = new StringBuilder();
    for (String attrName : summaryNames) {
      // ignore invalid attribute names
      if (!attributes.keySet().contains(attrName))
        continue;

      // exit if we have too many attributes
      if (summary.length() + attrName.length() + 1 >= PREF_VALUE_LENGTH)
        break;

      if (summary.length() > 0)
        summary.append(",");
      summary.append(attrName);
    }

    String summaryKey = questionFullName + SUMMARY_ATTRIBUTES_SUFFIX + keySuffix;
    String summaryValue = summary.toString();
    projectPreferences.put(summaryKey, summaryValue);
    return summaryValue;
  }

  //***********************************************************************
  //*** END methods to support specification and sorting of summary attributes
  //***********************************************************************

  public String createRemoteKey() throws WdkUserException, WdkModelException {
    // user can remote key only if he/she is logged in
    if (isGuest())
      throw new WdkUserException("Guest user cannot create remote key.");

    // the key is a combination of user id and current time
    Date now = new Date();

    String key = Long.toString(now.getTime()) + "->" + Integer.toString(getUserId());
    key = UserFactory.encrypt(key);
    // save the remote key
    String saveKey = Long.toString(now.getTime()) + "<-" + key;
    globalPreferences.put(PREF_REMOTE_KEY, saveKey);
    save();

    return key;
  }

  public void verifyRemoteKey(String remoteKey) throws WdkUserException {
    // get save key and creating time
    String saveKey = globalPreferences.get(PREF_REMOTE_KEY);
    if (saveKey == null)
      throw new WdkUserException("Remote login failed. The remote key doesn't exist.");
    String[] parts = saveKey.split("<-");
    if (parts.length != 2)
      throw new WdkUserException("Remote login failed. The remote key is invalid.");
    long createTime = Long.parseLong(parts[0]);
    String createKey = parts[1].trim();

    // verify remote key
    if (!createKey.equals(remoteKey))
      throw new WdkUserException("Remote login failed. The remote key doesn't match.");

    // check if the remote key is expired. There is an mandatory 10 minutes
    // expiration time for the remote key
    long now = (new Date()).getTime();
    if (Math.abs(now - createTime) >= (10 * 60 * 1000))
      throw new WdkUserException("Remote login failed. The remote key is expired.");
  }

  /**
   * Imports strategy behind strategy key into new strategy owned by this user.
   * The input strategy key is either:
   * <ul>
   *   <li>a strategy signature (generated by a share link)</li>
   *   <li>
   * 
   * @param strategyKey strategy key
   * @return new strategy
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public Strategy importStrategy(String strategyKey) throws WdkModelException, WdkUserException {
    Strategy oldStrategy;
    String[] parts = strategyKey.split(":");
    if (parts.length == 1) {
      // new strategy export url
      String strategySignature = parts[0];
      oldStrategy = stepFactory.loadStrategy(strategySignature);
    }
    else {
      // get user from user signature
      User user = userFactory.getUser(parts[0]);
      // make sure strategy id is an integer
      String strategyIdStr = parts[1];
      if (!FormatUtil.isInteger(strategyIdStr)) {
        throw new WdkUserException("Invalid strategy ID: " + strategyIdStr);
      }
      int strategyId = Integer.parseInt(strategyIdStr);
      oldStrategy = user.getStrategy(strategyId, true);
    }
    return importStrategy(oldStrategy, null);
  }

  public Strategy importStrategy(Strategy oldStrategy, Map<Integer, Integer> stepIdsMap)
      throws WdkModelException, WdkUserException {
    Strategy newStrategy = stepFactory.importStrategy(this, oldStrategy, stepIdsMap);
    // highlight the imported strategy
    int rootStepId = newStrategy.getLatestStepId();
    String strategyKey = Integer.toString(newStrategy.getStrategyId());
    if (newStrategy.isValid())
      setViewResults(strategyKey, rootStepId, 0);
    return newStrategy;
  }

  public Strategy[] getActiveStrategies() throws WdkUserException, WdkModelException {
    int[] ids = activeStrategyFactory.getRootStrategies();
    List<Strategy> strategies = new ArrayList<Strategy>();
    for (int id : ids) {
      try {
        Strategy strategy = getStrategy(id);
        strategies.add(strategy);
      }
      catch (WdkModelException ex) {
        // something wrong with loading a strat, probably the strategy
        // doesn't exist anymore
        logger.warn("something wrong with loading a strat, probably " +
            "the strategy doesn't exist anymore. Please " + "investigate:\nUser #" + getUserId() +
            ", strategy display Id: " + id + "\nException: ", ex);
      }
    }
    Strategy[] array = new Strategy[strategies.size()];
    strategies.toArray(array);
    return array;
  }

  public void addActiveStrategy(String strategyKey) throws WdkModelException, WdkUserException {
    activeStrategyFactory.openActiveStrategy(strategyKey);
    int pos = strategyKey.indexOf('_');
    if (pos >= 0)
      strategyKey = strategyKey.substring(0, pos);
    int strategyId = Integer.parseInt(strategyKey);
    stepFactory.updateStrategyViewTime(strategyId);
  }

  public void removeActiveStrategy(String strategyKey) {
    activeStrategyFactory.closeActiveStrategy(strategyKey);
  }

  public void replaceActiveStrategy(int oldStrategyId, int newStrategyId, Map<Integer, Integer> stepIdsMap)
      throws WdkModelException, WdkUserException {
    activeStrategyFactory.replaceStrategy(this, oldStrategyId, newStrategyId, stepIdsMap);
  }

  public void setViewResults(String strategyKey, int stepId, int pagerOffset) {
    this.activeStrategyFactory.setViewStrategyKey(strategyKey);
    this.activeStrategyFactory.setViewStepId(stepId);
    this.activeStrategyFactory.setViewPagerOffset(pagerOffset);
  }

  public void resetViewResults() {
    this.activeStrategyFactory.setViewStrategyKey(null);
    this.activeStrategyFactory.setViewStepId(null);
    this.activeStrategyFactory.setViewPagerOffset(null);
  }

  public String getViewStrategyKey() {
    return this.activeStrategyFactory.getViewStrategyKey();
  }

  public int getViewStepId() {
    return this.activeStrategyFactory.getViewStepId();
  }

  public Integer getViewPagerOffset() {
    return this.activeStrategyFactory.getViewPagerOffset();
  }

  public NameCheckInfo checkNameExists(Strategy strategy, String name, boolean saved)
      throws WdkModelException {
    return stepFactory.checkNameExists(strategy, name, saved);
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof User) {
      User user = (User) obj;
      try {
        if (user.getUserId() != getUserId())
          return false;
      }
      catch (WdkModelException ex) {
        new WdkRuntimeException(ex);
      }
      if (!this.profile.get(UserProfileProperty.EMAIL).equals(user.profile.get(UserProfileProperty.EMAIL)))
        return false;
      if (!signature.equals(user.signature))
        return false;

      return true;
    }
    else
      return false;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    try {
      return getUserId();
    }
    catch (WdkModelException ex) {
      throw new WdkRuntimeException(ex);
    }
  }

  public Step createBooleanStep(int strategyId, Step leftStep, Step rightStep, String booleanOperator,
      boolean useBooleanFilter, String filterName) throws WdkModelException {
    BooleanOperator operator = BooleanOperator.parse(booleanOperator);
    Question question = null;
    try {
      question = leftStep.getQuestion();
    }
    catch (WdkModelException ex) {
      // in case the left step has an invalid question, try the right
      question = rightStep.getQuestion();
    }
    AnswerFilterInstance filter = null;
    RecordClass recordClass = question.getRecordClass();
    if (filterName != null) {
      filter = question.getRecordClass().getFilterInstance(filterName);
    }
    else
      filter = recordClass.getDefaultFilter();
    return createBooleanStep(strategyId, leftStep, rightStep, operator, useBooleanFilter, filter);
  }

  public Step createBooleanStep(int strategyId, Step leftStep, Step rightStep, BooleanOperator operator,
      boolean useBooleanFilter, AnswerFilterInstance filter) throws WdkModelException {
    // make sure the left & right step belongs to the user
    if (leftStep.getUser().getUserId() != getUserId())
      throw new WdkModelException("The Left Step [" + leftStep.getStepId() +
          "] doesn't belong to the user #" + getUserId());
    if (rightStep.getUser().getUserId() != getUserId())
      throw new WdkModelException("The Right Step [" + rightStep.getStepId() +
          "] doesn't belong to the user #" + getUserId());

    // verify the record type of the operands
    RecordClass leftRecordClass = leftStep.getQuestion().getRecordClass();
    RecordClass rightRecordClass = rightStep.getQuestion().getRecordClass();
    if (!leftRecordClass.getFullName().equals(rightRecordClass.getFullName()))
      throw new WdkModelException("Boolean operation cannot be applied " +
          "to results of different record types. Left operand is " + "of type " +
          leftRecordClass.getFullName() + ", but the" + " right operand is of type " +
          rightRecordClass.getFullName());

    Question question = wdkModel.getBooleanQuestion(leftRecordClass);
    BooleanQuery booleanQuery = (BooleanQuery) question.getQuery();

    Map<String, String> params = new LinkedHashMap<String, String>();

    String leftName = booleanQuery.getLeftOperandParam().getName();
    String leftKey = Integer.toString(leftStep.getStepId());
    params.put(leftName, leftKey);

    String rightName = booleanQuery.getRightOperandParam().getName();
    String rightKey = Integer.toString(rightStep.getStepId());
    params.put(rightName, rightKey);

    String operatorString = operator.getBaseOperator();
    params.put(booleanQuery.getOperatorParam().getName(), operatorString);
    //    params.put(booleanQuery.getUseBooleanFilter().getName(), Boolean.toString(useBooleanFilter));

    Step booleanStep;
    try {
      booleanStep = createStep(strategyId, question, params, filter, false, false, 0);
    }
    catch (WdkUserException ex) {
      throw new WdkModelException(ex);
    }
    booleanStep.setPreviousStep(leftStep);
    booleanStep.setChildStep(rightStep);
    return booleanStep;
  }

  public int getStrategyOrder(String strategyKey) {
    int order = activeStrategyFactory.getOrder(strategyKey);
    logger.debug("strat " + strategyKey + " order: " + order);
    return order;
  }

  public int[] getActiveStrategyIds() {
    return activeStrategyFactory.getRootStrategies();
  }

  public Strategy copyStrategy(Strategy strategy, Map<Integer, Integer> stepIdMap) throws WdkModelException,
      WdkUserException {
    Strategy copy = stepFactory.copyStrategy(strategy, stepIdMap);
    logger.info("Copy Strategy #" + strategy.getStrategyId() + " -> " + copy.getStrategyId());
    return copy;
  }

  public Strategy copyStrategy(Strategy strategy, Map<Integer, Integer> stepIdMap, String name)
      throws WdkModelException, WdkUserException {
    Strategy copy = stepFactory.copyStrategy(strategy, stepIdMap, name);
    logger.info("Copy Strategy #" + strategy.getStrategyId() + " -> " + copy.getStrategyId());
    return copy;
  }

  public void addToFavorite(RecordClass recordClass, List<Map<String, Object>> pkValues)
      throws WdkModelException, WdkUserException {
    FavoriteFactory favoriteFactory = wdkModel.getFavoriteFactory();
    favoriteFactory.addToFavorite(this, recordClass, pkValues);
  }

  public void clearFavorite() throws WdkModelException {
    wdkModel.getFavoriteFactory().clearFavorite(this);
  }

  public int getFavoriteCount() throws WdkModelException {
    return wdkModel.getFavoriteFactory().getFavoriteCounts(this);
  }

  public Map<RecordClass, List<Favorite>> getFavorites() throws WdkModelException {
    return wdkModel.getFavoriteFactory().getFavorites(this);
  }

  public void removeFromFavorite(RecordClass recordClass, List<Map<String, Object>> pkValues)
      throws WdkModelException {
    FavoriteFactory favoriteFactory = wdkModel.getFavoriteFactory();
    favoriteFactory.removeFromFavorite(this, recordClass, pkValues);
  }

  public boolean isInFavorite(RecordClass recordClass, Map<String, Object> pkValue) throws WdkModelException {
    FavoriteFactory favoriteFactory = wdkModel.getFavoriteFactory();
    return favoriteFactory.isInFavorite(this, recordClass, pkValue);
  }

  public void setFavoriteNotes(RecordClass recordClass, List<Map<String, Object>> pkValues, String note)
      throws WdkModelException {
    FavoriteFactory favoriteFactory = wdkModel.getFavoriteFactory();
    favoriteFactory.setNotes(this, recordClass, pkValues, note);
  }

  public void setFavoriteGroups(RecordClass recordClass, List<Map<String, Object>> pkValues, String group)
      throws WdkModelException {
    FavoriteFactory favoriteFactory = wdkModel.getFavoriteFactory();
    favoriteFactory.setGroups(this, recordClass, pkValues, group);
  }

  public String[] getFavoriteGroups() throws WdkModelException {
    FavoriteFactory favoriteFactory = wdkModel.getFavoriteFactory();
    return favoriteFactory.getGroups(this);
  }

  public Map<RecordClass, Integer> getBasketCounts() throws WdkModelException {
    BasketFactory basketFactory = wdkModel.getBasketFactory();
    return basketFactory.getBasketCounts(this);
  }

  public int getBasketCounts(List<String[]> records, RecordClass recordClass) throws WdkModelException {
    int count = wdkModel.getBasketFactory().getBasketCounts(this, records, recordClass);
    if (logger.isDebugEnabled()) {
      logger.debug("How many of " + convert(records) + " in basket? " + count);
    }
    return count;
  }

  private String convert(List<String[]> records) {
    StringBuilder sb = new StringBuilder("List { ");
    for (String[] item : records) {
      sb.append("[ ");
      for (String s : item) {
        sb.append(s).append(", ");
      }
      sb.append(" ],");
    }
    sb.append(" }");
    return sb.toString();
  }

  public int getFavoriteCount(List<Map<String, Object>> records, RecordClass recordClass)
      throws WdkModelException {
    FavoriteFactory favoriteFactory = wdkModel.getFavoriteFactory();
    int count = 0;
    for (Map<String, Object> item : records) {
      boolean inFavs = favoriteFactory.isInFavorite(this, recordClass, item);
      if (logger.isDebugEnabled()) {
        logger.debug("Is " + convert(item) + " in favorites? " + inFavs);
      }
      if (inFavs) {
        count++;
      }
    }
    return count;
  }

  private String convert(Map<String, Object> item) {
    StringBuilder sb = new StringBuilder("Map { ");
    for (String s : item.keySet()) {
      sb.append("{ ").append(s).append(", ").append(item.get(s)).append(" },");
    }
    sb.append(" }");
    return sb.toString();
  }

  public SummaryView getCurrentSummaryView(Question question) {
    String key = SUMMARY_VIEW_PREFIX + question.getFullName(); //+ question.getRecordClassName();
    String viewName = projectPreferences.get(key);
    SummaryView view;
    if (viewName == null) { // no summary view set, use the default one
      view = question.getDefaultSummaryView();
    }
    else {
      try {
        view = question.getSummaryView(viewName);
      }
      catch (WdkUserException e) {
        // stored user preference is no longer valid; choose default instead
        view = question.getDefaultSummaryView();
      }
    }
    return view;
  }

  public void setCurrentSummaryView(Question question, SummaryView summaryView) throws WdkModelException {
    String key = SUMMARY_VIEW_PREFIX + question.getFullName(); //+ question.getRecordClassName();
    if (summaryView == null) { // remove the current summary view
      projectPreferences.remove(key);
    }
    else { // store the current summary view
      String viewName = summaryView.getName();
      projectPreferences.put(key, viewName);
    }
    save();
  }

  public RecordView getCurrentRecordView(RecordClass recordClass) throws WdkUserException {
    String key = RECORD_VIEW_PREFIX + recordClass.getFullName();
    String viewName = projectPreferences.get(key);
    RecordView view;
    if (viewName == null) { // no record view set, use the default one
      view = recordClass.getDefaultRecordView();
    }
    else {
      view = recordClass.getRecordView(viewName);
    }
    return view;
  }

  public void setCurrentRecordView(RecordClass recordClass, RecordView recordView) throws WdkModelException {
    String key = RECORD_VIEW_PREFIX + recordClass.getFullName();
    if (recordView == null) { // remove the current record view
      projectPreferences.remove(key);
    }
    else { // store the current record view
      String viewName = recordView.getName();
      projectPreferences.put(key, viewName);
    }
    save();
  }

  @Override
  public String toString() {
    try {
      return "User #" + getUserId() + " - " + getEmail();
    }
    catch (WdkModelException ex) {
      // TODO Auto-generated catch block
      throw new WdkRuntimeException(ex);
    }
  }

  /**
   * Enumeration describing all the user attributes associated with a user profile
   */
  public static enum UserProfileProperty {
    FIRST_NAME("firstName", "first_name", "First Name", true, 50),
    MIDDLE_NAME("middleName", "middle_name", "Middle Name", false, 50),
    LAST_NAME("lastName", "last_name", "Last Name", true, 50),
    TITLE("title", "title", "Title", false, 255),
    DEPARTMENT("department", "department", "Department", false, 255),
    ORGANIZATION("organization", "organization", "Organization", true, 255),
    EMAIL("email", "email", "Email", true, 255),
    ADDRESS("address", "address", "Address", false, 500),
    CITY("city", "city", "City", false, 255),
    STATE("state", "state", "State", false, 255),
    ZIP_CODE("zipCode", "zip_code", "ZipCode", false, 20),
    COUNTRY("country", "country", "Country", false, 255),
    PHONE_NUMBER("phoneNumber", "phone_number", "Phone Number", false, 50);

    private final String _jsonPropertyName;
    private final String _dbColumnName;
    private final String _display;
    private final boolean _isRequired;
    private final int _maxLength;
    
    /**
     * Returns a list of all json property names applicable to the user profile.  
     */
    public static final List<String> JSON_PROPERTY_NAMES = mapToList(Arrays.asList(UserProfileProperty.values()),
      new Function<UserProfileProperty, String>() {
        @Override
        public String apply(UserProfileProperty property) {
          return property.getJsonPropertyName();
        }
      }
    );
    
    /**
     * Returns a list of all required user profile property enums
     */
    public static final List<UserProfileProperty> REQUIRED_PROPERTIES = filter(Arrays.asList(UserProfileProperty.values()),
      new Predicate<UserProfileProperty>() {
        @Override
        public boolean test(UserProfileProperty property) {
          return property.isRequired(); 
        }
      }
    );
    

    /**
     * Construction of new enumeration item
     * @param jsonPropertyName - name used in the json object delivered/received by REST web services
     * @param dbColumnName - database field name
     * @param display - display name for view
     * @param isRequired
     * @param maxLength - maximum length allowed for this property
     */
    private UserProfileProperty(String jsonPropertyName, String dbColumnName, String display, boolean isRequired, int maxLength) {
      _jsonPropertyName = jsonPropertyName;
      _dbColumnName = dbColumnName;
      _display = display;
      _isRequired = isRequired;
      _maxLength = maxLength;
    }
    
    public String getJsonPropertyName() {
      return _jsonPropertyName;
    }
    
    public String getDbColumnName() {
      return _dbColumnName;
    }  
    
    public String getDisplay() {
      return _display;
    }
    
    public boolean isRequired() {
      return _isRequired;
    }
    
    public int getMaxLength() {
      return _maxLength;
    }
    
    public static UserProfileProperty fromJsonPropertyName(String jsonPropertyName) {
      for(UserProfileProperty property : UserProfileProperty.values()) {
        if(property.getJsonPropertyName().equals(jsonPropertyName)) {
          return property;
        }
      }
      return null;
    }
    
  }  
}
