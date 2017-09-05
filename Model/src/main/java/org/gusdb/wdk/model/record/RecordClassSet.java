package org.gusdb.wdk.model.record;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gusdb.wdk.model.ModelSetI;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelException;

/**
 * A set used to organize recordClasses in the model.
 * 
 * @author jerric
 * 
 */
public class RecordClassSet extends WdkModelBase implements ModelSetI<RecordClass> {

  private List<RecordClass> _recordClassList = new ArrayList<RecordClass>();
  private Map<String, RecordClass> _recordClassMap = new LinkedHashMap<String, RecordClass>();
  private String _name;

  public void setName(String name) {
    _name = name;
  }

  @Override
  public String getName() {
    return _name;
  }

  public RecordClass getRecordClass(String name) throws WdkModelException {
    RecordClass s = _recordClassMap.get(name);
    if (s == null)
      throw new WdkModelException("RecordClass Set " + getName()
          + " does not include recordClass " + name);
    return s;
  }

  @Override
  public RecordClass getElement(String name) {
    return _recordClassMap.get(name);
  }

  public RecordClass[] getRecordClasses() {
    RecordClass[] array = new RecordClass[_recordClassMap.size()];
    _recordClassMap.values().toArray(array);
    return array;
  }
  
  public Map<String, RecordClass> getRecordClassMap() {
    return new LinkedHashMap<>(_recordClassMap);
  }

  boolean hasRecordClass(RecordClass recordClass) {
    return _recordClassMap.containsKey(recordClass.getName());
  }

  public void addRecordClass(RecordClass recordClass) {
    recordClass.setRecordClassSet(this);
    _recordClassList.add(recordClass);
  }

  @Override
  public String toString() {
    String newline = System.getProperty("line.separator");
    StringBuffer buf = new StringBuffer("RecordClassSet: name='" + _name + "'");
    buf.append(newline);
    Iterator<RecordClass> recordClassIterator = _recordClassMap.values().iterator();
    while (recordClassIterator.hasNext()) {
      buf.append(newline);
      buf.append(":::::::::::::::::::::::::::::::::::::::::::::");
      buf.append(newline);
      buf.append(recordClassIterator.next()).append(newline);
    }

    return buf.toString();
  }

  @Override
  public void resolveReferences(WdkModel model) throws WdkModelException {
    if (_name.length() == 0 || _name.indexOf('\'') >= 0)
      throw new WdkModelException("recordClassSet name cannot be empty "
          + "or having single quotes: " + _name);

    Iterator<RecordClass> recordClassIterator = _recordClassMap.values().iterator();
    while (recordClassIterator.hasNext()) {
      RecordClass recordClass = recordClassIterator.next();
      recordClass.resolveReferences(model);
    }
  }

  @Override
  public void setResources(WdkModel model) throws WdkModelException {
    for (RecordClass recordClass : _recordClassMap.values()) {
      recordClass.setResources(model);
    }
  }

  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    // exclude record classes
    for (RecordClass recordClass : _recordClassList) {
      if (recordClass.include(projectId)) {
        recordClass.excludeResources(projectId);
        String rcName = recordClass.getName();
        if (_recordClassMap.containsKey(rcName))
          throw new WdkModelException("RecordClass " + rcName
              + " already exists in recordClass set " + getName());
        _recordClassMap.put(rcName, recordClass);
      }
    }
    _recordClassList = null;
  }
}
