package org.gusdb.wdk.model.test;

import static org.gusdb.fgputil.FormatUtil.NL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.param.AbstractEnumParam;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.query.param.ParamValuesSet;
import org.gusdb.wdk.model.query.param.SelectMode;
import org.gusdb.wdk.model.user.User;

public class ParamValuesFactory {

  private static final Logger LOG = Logger.getLogger(ParamValuesFactory.class);

  public static class ValuesSetWrapper {
    private ParamValuesSet _valuesSet;
    private Exception _exception;

    public ValuesSetWrapper(ParamValuesSet valuesSet) { _valuesSet = valuesSet; }
    public ValuesSetWrapper(Exception exception) { _exception = exception; }

    public boolean isCreated() { return (_valuesSet != null); }
    public ParamValuesSet getValuesSet() { return _valuesSet; }
    public Exception getException() { return _exception; }
  }

  public static List<ParamValuesSet> getParamValuesSets(User user, Query query) throws WdkModelException {
    List<ParamValuesSet> paramValuesSets = new ArrayList<>();
    for (ParamValuesSet valuesSet : getRawParamValuesSets(query)) {
      paramValuesSets.add(populateParamValuesSet(user, query, valuesSet));
    }
    return paramValuesSets;
  }

  public static List<ValuesSetWrapper> getValuesSetsNoError(User user, Query query) {
    List<ValuesSetWrapper> valuesSetWrappers = new ArrayList<>();
    for (ParamValuesSet valuesSet : getRawParamValuesSets(query)) {
      try {
        valuesSetWrappers.add(new ValuesSetWrapper(populateParamValuesSet(user, query, valuesSet)));
      }
      catch (Exception e) {
        valuesSetWrappers.add(new ValuesSetWrapper(e));
      }
    }
    return valuesSetWrappers;
  }

  private static List<ParamValuesSet> getRawParamValuesSets(Query query) {
    List<ParamValuesSet> rawParamValuesSets = new ArrayList<>(query.getRawParamValuesSets());
    if (rawParamValuesSets.isEmpty()) rawParamValuesSets.add(new ParamValuesSet());
    return rawParamValuesSets;
  }

  private static ParamValuesSet populateParamValuesSet(User user, Query query, ParamValuesSet valuesSet) throws WdkModelException {
    LOG.info("Populating param values set for query: " + query.getName() + ", params: " +
        joinParamNames(new HashSet<Param>(Arrays.asList(query.getParams()))));
    ParamValuesSet paramValuesSet = new ParamValuesSet(valuesSet);
    ParamValuesSet querySetDefaults = query.getQuerySet().getDefaultParamValuesSet();
    paramValuesSet.updateWithDefaults(querySetDefaults);
    // set user_id on queries that need it; query instance would do this for us, but
    //    doing it here makes the code below easier
    if (query.getParamMap().containsKey(Utilities.PARAM_USER_ID)) {
      paramValuesSet.updateWithDefault(Utilities.PARAM_USER_ID, Integer.toString(user.getUserId()));
    }
    Map<String, String> contextParamValues = paramValuesSet.getParamValues();
    try {
      populateRemainingValues(paramValuesSet, contextParamValues, query.getParams(),
          getRemainingParams(query.getParams(), contextParamValues), user);
    }
    catch (WdkModelException e) {
      LOG.error("Unable to populate param values set with defaults", e);
      throw e;
    }
    return paramValuesSet;
  }

  private static List<Param> getRemainingParams(Param[] params, Map<String, String> contextParamValues) {
    List<Param> remainingParams = new ArrayList<>();
    for (Param param : params) {
      if (!contextParamValues.containsKey(param.getName())) {
        remainingParams.add(param);
      }
    }
    return remainingParams;
  }

  private static String printParamMap(Param[] params, Map<String, String> contextParamValues) throws WdkModelException {
    StringBuilder out = new StringBuilder("{").append(NL);
    for (Param param : params) {
      out.append("  ").append(param.getName()).append(" = ")
         .append(contextParamValues.containsKey(param.getName()) ?
             contextParamValues.get(param.getName()) : "null")
         .append(", ").append(getDependedValues(param))
         .append(NL);
    }
    return out.append("}").toString();
  }

  private static String getDependedValues(Param param) throws WdkModelException {
    return (param instanceof AbstractEnumParam && ((AbstractEnumParam)param).isDependentParam()) ?
        "depends on [" + joinParamNames(((AbstractEnumParam)param).getDependedParams()) + "]" : "independent";
  }

  private static String joinParamNames(Set<Param> params) {
    List<String> names = new ArrayList<>();
    for (Param param : params) {
      names.add(param.getName());
    }
    return FormatUtil.arrayToString(names.toArray());
  }

  private static void populateRemainingValues(ParamValuesSet paramValuesSet,
      Map<String, String> contextParamValues, Param[] params, List<Param> remainingParams,
      User user) throws WdkModelException {
    LOG.info("Call made to populate remaining values, with current values = " + NL + printParamMap(params, contextParamValues));
    if (remainingParams.isEmpty()) {
      // all values populated
      LOG.info("All values populated.");
      return;
    }
    for (Param param : remainingParams) {
      String paramName = param.getName();
      String defaultValue = null;
      boolean isDependent = false;
      
      // Try to populate value; order of population for abstract enum params:
      //   1. ParamValuesSet value defined in Query
      //   2. ParamValuesSet default value defined in QuerySet
      // The above handled by populateParamValuesSet() above; which SelectMode returned by ParamValuesSet also determined above
      //   3. Value captured via ParamValuesSet SelectMode defined in Query
      //   4. Value captured via ParamValuesSet default SelectMode defined in QuerySet
      //   5. Sanity default value defined in Param
      // The following handled by AbstractEnumParam.getDefault(...)
      //   6. "Normal" default value defined in Param
      //   7. Value captured via SelectMode defined in Param
      if (param instanceof AbstractEnumParam && ((AbstractEnumParam)param).isDependentParam()) {
        AbstractEnumParam enumParam = (AbstractEnumParam)param;
        isDependent = true;

        // find depended params and see if all values are populated yet (should be no circular dependencies)
        if (allDependenciesMet(enumParam, contextParamValues)) {

          // all dependencies met, try to populate value
          SelectMode sanitySelectMode = paramValuesSet.getParamSelectModes().get(param.getName());
          if (sanitySelectMode != null ) {
            // ParamValuesSet defined a select mode; use it to fetch value
            // dependencies met; fetch value with sanity select mode
            defaultValue = enumParam.getSanityDefault(user, contextParamValues, sanitySelectMode);
          }
          else {
            defaultValue = param.getSanityDefault();
            if (defaultValue == null) {
              // need to pass context param values to get the default
              defaultValue = enumParam.getDefault(user, contextParamValues);
            }
          }
          
          if (defaultValue == null) {
            throw new WdkModelException("Unable to populate dependent param " +
                param.getName() + ", even though all dependencies met.");
          }
        }
      }

      // Populate values for non-enumParams and non-dependent enum params
      else {

        // first try to populate with sanity default
        defaultValue = param.getSanityDefault();

        // if no sanity default exists, use regular default
        if (defaultValue == null) {
          defaultValue = param.getDefault();
        }
        
        if (defaultValue == null) {
          throw new WdkModelException("Unable to populate independent param " +
              param.getName() + " with default value.");
        }
      }

      if (defaultValue != null) {
        remainingParams.remove(param);
        contextParamValues.put(paramName, defaultValue);
        paramValuesSet.updateWithDefault(paramName, defaultValue);
        LOG.info("Value for " + (isDependent ? "in" : "") + "dependent param " +
            param.getName() + " set to " + defaultValue);
      }
    }

    // populated all the params we could on this pass; call again to populate more params
    populateRemainingValues(paramValuesSet, contextParamValues, params, remainingParams, user);
  }

  private static boolean allDependenciesMet(AbstractEnumParam enumParam,
      Map<String, String> contextParamValues) throws WdkModelException {
    for (Param dependedParam : enumParam.getDependedParams()) {
      if (!contextParamValues.containsKey(dependedParam.getName())) {
        return false;
      }
    }
    return true;
  }
}
