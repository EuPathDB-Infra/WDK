package org.gusdb.wdk.model;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ParamSet implements ModelSetI {

    Map<String, Param> paramSet;
    String name;
    ResultFactory resultFactory;

    public ParamSet() {
	paramSet = new LinkedHashMap<String, Param>();
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getName() {
	return name;
    }

    public Param getParam(String name) throws WdkUserException {
	Param q = paramSet.get(name);
	if (q == null) throw new WdkUserException("Param Set " + getName() + " does not include param " + name);
	return q;
    }

    public Object getElement(String name) {
	return paramSet.get(name);
    }

    public Param[] getParams() {
	Param[] queries = new Param[paramSet.size()];
	Iterator<Param> paramIterator = paramSet.values().iterator();
	int i = 0;
	while (paramIterator.hasNext()) {
	    queries[i++] = paramIterator.next();
	}
	return queries;
    }

    public void addParam(Param param) throws WdkModelException {
	if (paramSet.get(param.getName()) != null) 
	    throw new WdkModelException("Param named " 
					+ param.getName() 
					+ " already exists in param set "
					+ getName());
	paramSet.put(param.getName(), param);
    }

    public void resolveReferences(WdkModel model) throws WdkModelException {
	Iterator<Param> paramIterator = paramSet.values().iterator();
	while (paramIterator.hasNext()) {
	    Param param = paramIterator.next();
	    param.resolveReferences(model);
	}
    }

    public void setResources(WdkModel model) throws WdkModelException {
	Iterator<Param> paramIterator = paramSet.values().iterator();
	while (paramIterator.hasNext()) {
	    Param param = paramIterator.next();
	    param.setResources(model);
	    param.setFullName(this.getName());
	}
    }

    public String toString() {
	String newline = System.getProperty( "line.separator" );
	StringBuffer buf = new StringBuffer("ParamSet: name='" + name 
					   + "'");
	buf.append( newline );

	Iterator paramIterator = paramSet.values().iterator();
	while (paramIterator.hasNext()) {
	    buf.append( newline );
	    buf.append( ":::::::::::::::::::::::::::::::::::::::::::::" );
	    buf.append( newline );
	    buf.append( paramIterator.next() ).append( newline );	
	}
	return buf.toString();
    }

    /////////////////////////////////////////////////////////////////
    ///////  protected
    /////////////////////////////////////////////////////////////////

}
