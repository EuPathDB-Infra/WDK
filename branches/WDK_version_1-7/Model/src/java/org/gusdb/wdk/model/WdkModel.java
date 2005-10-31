package org.gusdb.wdk.model;

import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.io.File;
import java.net.MalformedURLException;

import org.w3c.dom.Document;

import org.gusdb.wdk.model.implementation.ModelXmlParser;  // why is this in impl?

public class WdkModel {

    public static final Integer TRUNCATE_DEFAULT = new Integer(100);

    protected RDBMSPlatformI platform;

    HashMap querySets = new HashMap();
    HashMap paramSets = new HashMap();
    HashMap recordClassSets = new HashMap();
    HashMap referenceLists = new HashMap();
    LinkedHashMap questionSets = new LinkedHashMap();
    HashMap allModelSets = new HashMap();
    String name;
    String displayName;
    String introduction;
    ResultFactory resultFactory;
    private EnumParam booleanOps;
    private Document document;
    
    
    /**
     * this map is used to store active users in memory
     */
    private Map<String, User> users;
    
    public static final WdkModel INSTANCE = new WdkModel();
    
     /**
     * Default constructor
     */
    public WdkModel() {
        users = new HashMap<String, User>();
    }
    
    /**
     * Convenience method for constructing a model from the configuration information
     */
    public static WdkModel construct(String modelName) throws WdkModelException {
        File configDir = new File(System.getProperties().getProperty("configDir"));
        
        File modelConfigXmlFile = new File(configDir, modelName+"-config.xml");
        File modelXmlFile = new File(configDir, modelName + ".xml");
        File modelPropFile = new File(configDir, modelName + ".prop");
	File schemaFile = new File(System.getProperty("schemaFile"));

	try {
	    return ModelXmlParser.parseXmlFile(modelXmlFile.toURL(), 
					       modelPropFile.toURL(), 
					       schemaFile.toURL(), 
					       modelConfigXmlFile.toURL());
	} catch (java.net.MalformedURLException e) {
	    throw new WdkModelException(e);
	}

    }
    
    /**
     * @param initRecordClassList
     * @return
     * @throws WdkUserException
     * @throws WdkModelException
     */
    public Question getQuestion(String initRecordClassList) 
	throws WdkUserException, WdkModelException 
    {
	Reference r = new Reference(initRecordClassList);
	QuestionSet ss = getQuestionSet(r.getSetName());
	return ss.getQuestion(r.getElementName());
    }

    
    public RecordClass getRecordClass(String recordClassReference)
	throws WdkUserException, WdkModelException 
    {
	Reference r = new Reference(recordClassReference);
	RecordClassSet rs = getRecordClassSet(r.getSetName());
	return rs.getRecordClass(r.getElementName());
    }

    public ResultFactory getResultFactory() {
        return resultFactory;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName(){
	return name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName(){
	return displayName;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public String getIntroduction(){
	return introduction;
    }

    //RecordClass Sets
    public void addRecordClassSet(RecordClassSet recordClassSet) throws WdkModelException {
        addSet(recordClassSet, recordClassSets);
    }

    public RecordClassSet getRecordClassSet(String recordClassSetName) throws WdkUserException {
	
        if (!recordClassSets.containsKey(recordClassSetName)) {   
            String err = "WDK Model " + name +
            " does not contain a recordClass set with name " + recordClassSetName;
	    
            throw new WdkUserException(err);
        }
        return (RecordClassSet)recordClassSets.get(recordClassSetName);
    }

    public RecordClassSet[] getAllRecordClassSets(){
	    
        RecordClassSet sets[] = new RecordClassSet[recordClassSets.size()];
        Iterator keys = recordClassSets.keySet().iterator();
        int counter = 0;
        while (keys.hasNext()){
            String name = (String)keys.next();
            RecordClassSet nextRecordClassSet = (RecordClassSet)recordClassSets.get(name);
            sets[counter] = nextRecordClassSet;
            counter++;
        }
        return sets;
    }

    //Query Sets
    public void addQuerySet(QuerySet querySet) throws WdkModelException {
        addSet(querySet, querySets);
    }

    public QuerySet getQuerySet(String setName) throws WdkUserException {
        if (!querySets.containsKey(setName)) {
            String err = "WDK Model " + name +
            " does not contain a query set with name " + setName;
            throw new WdkUserException(err);
        }
        return (QuerySet)querySets.get(setName);
    }

    public boolean hasQuerySet(String setName) {
        return querySets.containsKey(setName);
    }

    public QuerySet[] getAllQuerySets(){
	    
        QuerySet sets[] = new QuerySet[querySets.size()];
        Iterator keys = querySets.keySet().iterator();
        int counter = 0;
        while (keys.hasNext()){
            String name = (String)keys.next();
            sets[counter] = (QuerySet)querySets.get(name);
            counter++;
        }
        return sets;
    }

    public QuestionSet[] getAllQuestionSets(){
	    
        QuestionSet sets[] = new QuestionSet[questionSets.size()];
        Iterator keys = questionSets.keySet().iterator();
        int counter = 0;
        while (keys.hasNext()){
            String name = (String)keys.next();
            sets[counter] = (QuestionSet)questionSets.get(name); 
            counter++;
        }
        return sets;
    }

    //Question Sets
    public void addQuestionSet(QuestionSet questionSet) throws WdkModelException {
        addSet(questionSet, questionSets);
    }

    public QuestionSet getQuestionSet(String setName) throws WdkUserException {
        if (!questionSets.containsKey(setName)) {
            String err = "WDK Model " + name +
            " does not contain a Question set with name " + setName;
            throw new WdkUserException(err);
        }
        return (QuestionSet)questionSets.get(setName);
    }

    public boolean hasQuestionSet(String setName) {
        return questionSets.containsKey(setName);
    }

    public Map getQuestionSets(){
	return new LinkedHashMap(questionSets);
    }

    //ReferenceLists
    public void addReferenceList(ReferenceList referenceList) throws WdkModelException {
        addSet(referenceList, referenceLists);
    }
    
    public ReferenceList getReferenceList(String referenceListName) throws WdkUserException {
	
        if (!referenceLists.containsKey(referenceListName)){
            String err = "WDK Model " + name +
            " does not contain a  query set with name " + referenceListName;
            throw new WdkUserException(err);
        }
        return (ReferenceList)referenceLists.get(referenceListName);
    }

    public ReferenceList[] getAllReferenceLists(){

        ReferenceList lists[] = new ReferenceList[referenceLists.size()];
        Iterator keys = referenceLists.keySet().iterator();
        int counter = 0;
        while (keys.hasNext()){
            String name = (String)keys.next();
            ReferenceList nextReferenceList = (ReferenceList)referenceLists.get(name);
            lists[counter] = nextReferenceList;
            counter++;
        }
        return lists;
    }
       
    public Question makeBooleanQuestion(RecordClass rc){
	
	Question q = new Question();
	q.setName("BooleanQuestion");
	q.setRecordClass(rc);
	BooleanQuery bq = makeBooleanQuery();
	q.setQuery(bq);
	return q;
    }

    public BooleanQuery makeBooleanQuery(){
	BooleanQuery booleanQuery = new BooleanQuery();
	booleanQuery.setResultFactory(resultFactory);
	booleanQuery.setRDBMSPlatform(platform);
	return booleanQuery;
    }

    public BooleanQueryInstance makeBooleanQueryInstance(){

	BooleanQuery booleanQuery = new BooleanQuery();
	BooleanQueryInstance bqi = new BooleanQueryInstance(booleanQuery);
	return bqi;
    }
    
    //ModelSetI's
    private void addSet(ModelSetI set, HashMap setMap) throws WdkModelException {
        String setName = set.getName();
        if (allModelSets.containsKey(setName)) {
            String err = "WDK Model " + name +
            " already contains a set with name " + setName;
	
            throw new WdkModelException(err);	
        }
        setMap.put(setName, set);
        allModelSets.put(setName, set);
    }
    
    /**
     * Set whatever resources the model needs.  It will pass them to its kids
     */
    public void setResources() throws WdkModelException {

	makeBooleanOps();

        Iterator modelSets = allModelSets.values().iterator();
        while (modelSets.hasNext()) {
	    ModelSetI modelSet = (ModelSetI)modelSets.next();
	    modelSet.setResources(this);
        }
    }

    public void configure(URL modelConfigXmlFileURL) throws Exception{
	
	ModelConfig modelConfig = 
	    ModelConfigParser.parseXmlFile(modelConfigXmlFileURL);
	String fileName = modelConfigXmlFileURL.getFile();
	String connectionUrl = modelConfig.getConnectionUrl();
	String login = modelConfig.getLogin();
	String password = modelConfig.getPassword();
	String instanceTable = modelConfig.getQueryInstanceTable();
	String platformClass = modelConfig.getPlatformClass();
	Integer maxIdle = modelConfig.getMaxIdle();
	Integer minIdle = modelConfig.getMinIdle();
	Integer maxWait = modelConfig.getMaxWait();
	Integer maxActive = modelConfig.getMaxActive();
	Integer initialSize = modelConfig.getInitialSize();
	
	RDBMSPlatformI platform = 
	    (RDBMSPlatformI)Class.forName(platformClass).newInstance();

	platform.init(connectionUrl, login, password, minIdle, maxIdle, maxWait, maxActive, initialSize, fileName);
	ResultFactory resultFactory = new ResultFactory(platform, login, instanceTable);
	this.platform = platform;
	this.resultFactory = resultFactory;
    }

    public void configure(File modelConfigXmlFile) throws Exception{
	configure(modelConfigXmlFile.toURL());
    }


    public RDBMSPlatformI getRDBMSPlatform() {
        return platform;
    }

    public Object resolveReference(String twoPartName, String refererName, 
                String refererClassName, String refererAttributeName) throws WdkModelException {
        String s = "Invalid reference in " + refererClassName + " '" + refererName 
            + "' at " + refererAttributeName + "=\"" + twoPartName + "\".";

        //ensures <code>twoPartName</code> is formatted correctly
        Reference reference = new Reference(twoPartName);

        String setName = reference.getSetName();
        String elementName = reference.getElementName();

        ModelSetI set = (ModelSetI)allModelSets.get(setName);

        if (set == null) {
            String s3 = s + " There is no set called '" + setName + "'";
            throw new WdkModelException(s3);
        }
        Object element = set.getElement(elementName);
        if (element == null) {
            String s4 = s + " Set '" + setName + 
            "' returned null for '" + elementName + "'";
            throw new WdkModelException(s4);
        }
        return element;
    }

    /**
     * Some elements within the set may refer to others by name.  Resolve those
     * references into real object references.
     */ 
    public void resolveReferences() throws WdkModelException {
        // Since we use Map here, the order of the sets in allModelSets are 
        // random. However, if QuestionSet is resolved before a RecordSet, and
        // it goes down to resolve: QuestionSet -> Question -> RecordClass, and
        // when we try to resolve the RecordClass, a copy of it has been put
        // into RecordSet yet not being resolved. That means the attribute won't
        // be compatible since one contains nothing.
//        Iterator modelSets = allModelSets.values().iterator();
//        while (modelSets.hasNext()) {
//            ModelSetI modelSet = (ModelSetI) modelSets.next();
//            modelSet.resolveReferences(this);
//        }
        
        // instead, we first resolve querySets, then recordSets, and then 
        // paramSets, and last on questionSets
        Iterator itQuerySets = querySets.values().iterator();
        while (itQuerySets.hasNext()){
            QuerySet querySet = (QuerySet)itQuerySets.next();
            querySet.resolveReferences(this);
        }
        Iterator itParamSets = paramSets.values().iterator();
        while (itParamSets.hasNext()){
            ParamSet paramSet = (ParamSet)itParamSets.next();
            paramSet.resolveReferences(this);
        }
        Iterator itRecordSets = recordClassSets.values().iterator();
        while (itRecordSets.hasNext()){
            RecordClassSet recordClassSet = (RecordClassSet)itRecordSets.next();
            recordClassSet.resolveReferences(this);
        }
        Iterator itQuestionSets = questionSets.values().iterator();
        while (itQuestionSets.hasNext()){
            QuestionSet questionSet = (QuestionSet)itQuestionSets.next();
            questionSet.resolveReferences(this);
        }
    }
    
    public String toString() {
       String newline = System.getProperty( "line.separator" );
       StringBuffer buf = new StringBuffer("WdkModel: name='" + name + "'" + newline
					   + "displayName='"  + displayName + "'" + newline
					   + "introduction='" + introduction + "'");
       buf.append(showSet("Param", paramSets));
       buf.append(showSet("Query", querySets));
       buf.append(showSet("RecordClass", recordClassSets));
       buf.append(showSet("Question", questionSets));
       return buf.toString();
    }
       
    protected String showSet(String setType, HashMap setMap) {
        StringBuffer buf = new StringBuffer();
        String newline = System.getProperty("line.separator");
        buf.append( newline );
        buf.append( "ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo" + newline );
        buf.append( "ooooooooooooooooooooooooooooo " + setType + " Sets oooooooooooooooooooooooooo" + newline );
        buf.append( "ooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo" + newline + newline);
        Iterator setIterator = setMap.values().iterator();
        while (setIterator.hasNext()) {
            ModelSetI set = (ModelSetI)setIterator.next();
            buf.append( "=========================== " + set.getName()+ " ===============================" + newline + newline);
            buf.append(set).append( newline );
        }
        buf.append(newline);
        
        return buf.toString();
    }
 
    //Param Sets
    public void addQuerySet(ParamSet paramSet) throws WdkModelException {
        addSet(paramSet, paramSets);
    }
    
    public void addParamSet(ParamSet paramSet) throws WdkModelException {
        addSet(paramSet, paramSets);
    }

    ///////////////////////////////////////////////////////////////////
    ///////   Protected methods
    ///////////////////////////////////////////////////////////////////

    void checkName(String setName) throws WdkModelException {
        // TODO What's supposed to be here?
    }
    
    public Document getDocument() {
        return document;
    }
    
    public void setDocument(Document document) {
        this.document = document;
    }

    public EnumParam getBooleanOps(){
	return this.booleanOps;
    }


    private void makeBooleanOps(){

	EnumItem union = new EnumItem();
	union.setTerm("Union With");
	union.setInternal("union");

	EnumItem intersect = new EnumItem();
	intersect.setTerm("Intersect With");
	intersect.setInternal("intersect");

	EnumItem subtract = new EnumItem();
	subtract.setTerm("Subtract");
	subtract.setInternal("minus");

	EnumParam booleanOpsEnum = new EnumParam();
	booleanOpsEnum.addItem(union);
	booleanOpsEnum.addItem(intersect);
	booleanOpsEnum.addItem(subtract);

	booleanOpsEnum.setMultiPick(new Boolean(false));

	this.booleanOps = booleanOpsEnum;
    }
    
    // =========================================================================
    // User related operations
    // =========================================================================
    
    public User createUser(String userID) {
        // check if the user exists
        if (users.containsKey(userID)) return users.get(userID);
        
        User user = new User(userID, this);
        users.put(userID, user);
        return user;
    }
    
    public User getUser(String userID) {
        return users.get(userID);
    }
    
    public boolean deleteUser(String userID) {
        User user = users.remove(userID);
        return (user != null);
    }
}
