package org.gusdb.wdk.model.report;

import java.util.List;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;

public interface TabularReporterRowsProvider {
  boolean hasNext() throws WdkModelException, WdkUserException;
  List<Object> next() throws WdkModelException, WdkUserException;
  void close() throws WdkModelException, WdkUserException;

}
