package org.gusdb.wdk.model.implementation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.digester.Digester;
import org.apache.log4j.Logger;
import org.gusdb.wdk.model.AnswerFilter;
import org.gusdb.wdk.model.AnswerFilterInstance;
import org.gusdb.wdk.model.AnswerFilterInstanceParam;
import org.gusdb.wdk.model.AnswerFilterLayout;
import org.gusdb.wdk.model.AnswerFilterLayoutInstance;
import org.gusdb.wdk.model.AnswerParam;
import org.gusdb.wdk.model.AttributeList;
import org.gusdb.wdk.model.AttributeQueryReference;
import org.gusdb.wdk.model.Categories;
import org.gusdb.wdk.model.Category;
import org.gusdb.wdk.model.Column;
import org.gusdb.wdk.model.ColumnAttributeField;
import org.gusdb.wdk.model.DatasetParam;
import org.gusdb.wdk.model.DynamicAttributeSet;
import org.gusdb.wdk.model.EnumItem;
import org.gusdb.wdk.model.EnumItemList;
import org.gusdb.wdk.model.EnumParam;
import org.gusdb.wdk.model.FlatVocabParam;
import org.gusdb.wdk.model.ParamValuesSet;
import org.gusdb.wdk.model.Group;
import org.gusdb.wdk.model.GroupSet;
import org.gusdb.wdk.model.LinkAttributeField;
import org.gusdb.wdk.model.ModelConfig;
import org.gusdb.wdk.model.ModelConfigParser;
import org.gusdb.wdk.model.NestedRecord;
import org.gusdb.wdk.model.NestedRecordList;
import org.gusdb.wdk.model.ParamConfiguration;
import org.gusdb.wdk.model.ParamReference;
import org.gusdb.wdk.model.ParamSet;
import org.gusdb.wdk.model.ParamSuggestion;
import org.gusdb.wdk.model.PrimaryKeyAttributeField;
import org.gusdb.wdk.model.PropertyList;
import org.gusdb.wdk.model.QuerySet;
import org.gusdb.wdk.model.Question;
import org.gusdb.wdk.model.QuestionSet;
import org.gusdb.wdk.model.RecordClass;
import org.gusdb.wdk.model.RecordClassSet;
import org.gusdb.wdk.model.ReporterProperty;
import org.gusdb.wdk.model.ReporterRef;
import org.gusdb.wdk.model.StringParam;
import org.gusdb.wdk.model.TableField;
import org.gusdb.wdk.model.TextAttributeField;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelName;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.XmlParser;
import org.gusdb.wdk.model.query.ProcessQuery;
import org.gusdb.wdk.model.query.SqlParamValue;
import org.gusdb.wdk.model.query.SqlQuery;
import org.gusdb.wdk.model.xml.XmlAttributeField;
import org.gusdb.wdk.model.xml.XmlQuestion;
import org.gusdb.wdk.model.xml.XmlQuestionSet;
import org.gusdb.wdk.model.xml.XmlRecordClass;
import org.gusdb.wdk.model.xml.XmlRecordClassSet;
import org.gusdb.wdk.model.xml.XmlTableField;
import org.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ModelXmlParser extends XmlParser {

    private static final Logger logger = Logger.getLogger(ModelXmlParser.class);

    private URL xmlSchemaURL;
    private String xmlDataDir;

    public ModelXmlParser(String gusHome) throws SAXException, IOException {
        super(gusHome, "lib/rng/wdkModel.rng");

        // get model schema file and xml schema file
        xmlSchemaURL = makeURL(gusHome, "lib/rng/xmlAnswer.rng");
        xmlDataDir = gusHome + "/lib/xml/";
    }

    public WdkModel parseModel(String projectId)
            throws ParserConfigurationException,
            TransformerFactoryConfigurationError, TransformerException,
            IOException, SAXException, WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException, InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        // get model config
        ModelConfig config = getModelConfig(projectId);
        String modelName = config.getModelName();

        // construct urls to model file, prop file, and config file
        URL modelURL = makeURL(gusHome, "lib/wdk/" + modelName + ".xml");
        URL modelPropURL = makeURL(gusHome, "config/" + projectId
                + "/model.prop");

        // validate the master model file
        if (!validate(modelURL))
            throw new WdkModelException("Master model validation failed.");

        // replace any <import> tag with content from sub-models in the
        // master model, and build the master document
        Document masterDoc = buildMasterDocument(modelURL);

        // load property map
        Map<String, String> properties = getPropMap(modelPropURL);

        // add project id into the prop map automatically
        properties.put(Utilities.PARAM_PROJECT_ID, projectId);

        InputStream modelXmlStream = substituteProps(masterDoc, properties);

        WdkModel model = (WdkModel) digester.parse(modelXmlStream);

        model.setXmlSchema(xmlSchemaURL); // set schema for xml data
        model.setXmlDataDir(new File(xmlDataDir)); // consider refactoring
        model.configure(config);
        model.setResources();
        model.setProperties(properties); // consider removing it

        return model;
    }

    private ModelConfig getModelConfig(String projectId) throws SAXException,
            IOException, WdkModelException {
        ModelConfigParser parser = new ModelConfigParser(gusHome);
        return parser.parseConfig(projectId);
    }

    private Document buildMasterDocument(URL wdkModelURL) throws SAXException,
            IOException, ParserConfigurationException, WdkModelException {
        // get the xml document of the model
        Document masterDoc = buildDocument(wdkModelURL);
        Node rootNode = masterDoc.getElementsByTagName("wdkModel").item(0);

        // get all imports, and replace each of them with the sub-model
        NodeList importNodes = masterDoc.getElementsByTagName("import");
        for (int i = 0; i < importNodes.getLength(); i++) {
            // get url to the first import
            Node importNode = importNodes.item(i);
            String href = importNode.getAttributes().getNamedItem("file").getNodeValue();
            URL importURL = makeURL(gusHome, "lib/wdk/" + href);

            // validate the sub-model
            if (!validate(importURL))
                throw new WdkModelException("sub model "
                        + importURL.toExternalForm() + " validation failed.");

            // logger.debug("Importing: " + importURL.toExternalForm());

            Document importDoc = buildDocument(importURL);

            // get the children nodes from imported sub-model, and add them
            // into master document
            Node subRoot = importDoc.getElementsByTagName("wdkModel").item(0);
            NodeList childrenNodes = subRoot.getChildNodes();
            for (int j = 0; j < childrenNodes.getLength(); j++) {
                Node childNode = childrenNodes.item(j);
                if (childNode instanceof Element) {
                    Node imported = masterDoc.importNode(childNode, true);
                    rootNode.appendChild(imported);
                }
            }
        }
        return masterDoc;
    }

    private Map<String, String> getPropMap(URL modelPropURL) throws IOException {
        Map<String, String> propMap = new LinkedHashMap<String, String>();
        Properties properties = new Properties();
        properties.load(modelPropURL.openStream());
        Iterator<Object> it = properties.keySet().iterator();
        while (it.hasNext()) {
            String propName = (String) it.next();
            String value = properties.getProperty(propName);
            propMap.put(propName, value);
        }
        return propMap;
    }

    private InputStream substituteProps(Document masterDoc,
            Map<String, String> properties)
            throws TransformerFactoryConfigurationError, TransformerException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // transform the DOM doc to a string
        Source source = new DOMSource(masterDoc);
        Result result = new StreamResult(out);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(source, result);
        String content = new String(out.toByteArray());

        // substitute prop macros
        for (String propName : properties.keySet()) {
            String propValue = properties.get(propName);
            content = content.replaceAll("\\@" + propName + "\\@",
                    Matcher.quoteReplacement(propValue));
        }

        // construct input stream
        return new ByteArrayInputStream(content.getBytes());
    }

    protected Digester configureDigester() {
        Digester digester = new Digester();
        digester.setValidating(false);

        // Root -- WDK Model
        digester.addObjectCreate("wdkModel", WdkModel.class);
        digester.addSetProperties("wdkModel");

        configureNode(digester, "wdkModel/modelName", WdkModelName.class,
                "addWdkModelName");

        configureNode(digester, "wdkModel/introduction", WdkModelText.class,
                "addIntroduction");
        digester.addCallMethod("wdkModel/introduction", "setText", 0);

        // default property list
        configureNode(digester, "wdkModel/defaultPropertyList",
                PropertyList.class, "addDefaultPropertyList");

        configureNode(digester, "wdkModel/defaultPropertyList/value",
                WdkModelText.class, "addValue");
        digester.addCallMethod("wdkModel/defaultPropertyList/value", "setText",
                0);

        // default property list
        configureNode(digester, "wdkModel/categories", Categories.class,
                "addCategories");

        configureNode(digester, "wdkModel/categories/category", Category.class,
                "addCategory");

        configureNode(digester, "wdkModel/categories/category/questionRef",
                WdkModelText.class, "addQuestionRef");
        digester.addCallMethod("wdkModel/categories/category/questionRef",
                "setText", 0);

        // configure all sub nodes of recordClassSet
        configureRecordClassSet(digester);

        // configure all sub nodes of querySet
        configureQuerySet(digester);

        // configure all sub nodes of paramSet
        configureParamSet(digester);

        // configure all sub nodes of questionSet
        configureQuestionSet(digester);

        // configure all sub nodes of xmlQuestionSet
        configureXmlQuestionSet(digester);

        // configure all sub nodes of xmlRecordSet
        configureXmlRecordClassSet(digester);

        // configure all sub nodes of xmlRecordSet
        configureGroupSet(digester);

        return digester;
    }

    private void configureRecordClassSet(Digester digester) {
        // record class set
        configureNode(digester, "wdkModel/recordClassSet",
                RecordClassSet.class, "addRecordClassSet");

        // record class
        configureNode(digester, "wdkModel/recordClassSet/recordClass",
                RecordClass.class, "addRecordClass");

        // primary key attribute
        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/primaryKeyAttribute",
                PrimaryKeyAttributeField.class, "addAttributeField");
        configureNode(
                digester,
                "wdkModel/recordClassSet/recordClass/primaryKeyAttribute/columnRef",
                WdkModelText.class, "addColumnRef");
        digester.addCallMethod(
                "wdkModel/recordClassSet/recordClass/primaryKeyAttribute/columnRef",
                "setText", 0);
        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/primaryKeyAttribute/text",
                WdkModelText.class, "addText");
        digester.addCallMethod(
                "wdkModel/recordClassSet/recordClass/primaryKeyAttribute/text",
                "setText", 0);

        configureNode(digester, "wdkModel/recordClassSet/recordClass/attributesList",
                AttributeList.class, "addAttributeList");

	// defaultTestParamValues
	configureParamValuesSet(digester, "wdkModel/recordClassSet/recordClass/testParamValues", "addParamValuesSet");

        // reporter
        configureNode(digester, "wdkModel/recordClassSet/recordClass/reporter",
                ReporterRef.class, "addReporterRef");
        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/reporter/property",
                ReporterProperty.class, "addProperty");
        digester.addCallMethod(
                "wdkModel/recordClassSet/recordClass/reporter/property",
                "setValue", 0);

        // filter layouts
        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/answerFilterLayout",
                AnswerFilterLayout.class, "addFilterLayout");
        configureNode(
                digester,
                "wdkModel/recordClassSet/recordClass/answerFilterLayout/description",
                WdkModelText.class, "addDescription");
        digester.addCallMethod(
                "wdkModel/recordClassSet/recordClass/answerFilterLayout/description",
                "setText", 0);
        configureNode(
                digester,
                "wdkModel/recordClassSet/recordClass/answerFilterLayout/instanceRef",
                AnswerFilterLayoutInstance.class, "addInstance");

        // filter instances
        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/answerFilter",
                AnswerFilter.class, "addFilter");
        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/answerFilter/instance",
                AnswerFilterInstance.class, "addInstance");

        configureNode(
                digester,
                "wdkModel/recordClassSet/recordClass/answerFilter/instance/description",
                WdkModelText.class, "addDescription");
        digester.addCallMethod(
                "wdkModel/recordClassSet/recordClass/answerFilter/instance/description",
                "setText", 0);

        configureNode(
                digester,
                "wdkModel/recordClassSet/recordClass/answerFilter/instance/paramValue",
                AnswerFilterInstanceParam.class, "addParamValue");
        digester.addCallMethod(
                "wdkModel/recordClassSet/recordClass/answerFilter/instance/paramValue",
                "setText", 0);

        // attribute query ref
        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/attributeQueryRef",
                AttributeQueryReference.class, "addAttributesQueryRef");

        configureNode(
                digester,
                "wdkModel/recordClassSet/recordClass/attributeQueryRef/columnAttribute",
                ColumnAttributeField.class, "addAttributeField");

        configureLinkTextFields(digester,
                "wdkModel/recordClassSet/recordClass/attributeQueryRef/");

        // tables
        configureNode(digester, "wdkModel/recordClassSet/recordClass/table",
                TableField.class, "addTableField");

        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/table/description",
                WdkModelText.class, "addDescription");
        digester.addCallMethod(
                "wdkModel/recordClassSet/recordClass/table/description",
                "setText", 0);

        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/table/columnAttribute",
                ColumnAttributeField.class, "addAttributeField");

        configureLinkTextFields(digester,
                "wdkModel/recordClassSet/recordClass/table/");

        // direct attribute fields in teh record class
        configureLinkTextFields(digester,
                "wdkModel/recordClassSet/recordClass/");

        // nested record and record list
        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/nestedRecord",
                NestedRecord.class, "addNestedRecordQuestionRef");

        configureNode(digester,
                "wdkModel/recordClassSet/recordClass/nestedRecordList",
                NestedRecordList.class, "addNestedRecordListQuestionRef");
    }

    private void configureQuerySet(Digester digester) {
        // QuerySet
        configureNode(digester, "wdkModel/querySet", QuerySet.class,
                "addQuerySet");

	// defaultTestParamValues
	configureParamValuesSet(digester, "wdkModel/querySet/defaultTestParamValues", "addDefaultParamValuesSet");

	// cardinalitySql
        configureNode(digester, "wdkModel/querySet/testRowCountSql",
                WdkModelText.class, "addTestRowCountSql");
        digester.addCallMethod("wdkModel/querySet/testRowCountSql", "setText", 0);

        // sqlQuery
        configureNode(digester, "wdkModel/querySet/sqlQuery", SqlQuery.class,
                "addQuery");

	// testParamValues
	configureParamValuesSet(digester, "wdkModel/querySet/sqlQuery/testParamValues", "addParamValuesSet");

        configureNode(digester, "wdkModel/querySet/sqlQuery/sql",
                WdkModelText.class, "addSql");
        digester.addCallMethod("wdkModel/querySet/sqlQuery/sql", "setText", 0);

        configureNode(digester, "wdkModel/querySet/sqlQuery/paramRef",
                ParamReference.class, "addParamRef");

        configureNode(digester, "wdkModel/querySet/sqlQuery/column",
                Column.class, "addColumn");

        configureNode(digester, "wdkModel/querySet/sqlQuery/sqlParamValue",
                SqlParamValue.class, "addSqlParamValue");
        digester.addCallMethod("wdkModel/querySet/sqlQuery/sqlParamValue",
                "setText", 0);

        // processQuery
        configureNode(digester, "wdkModel/querySet/processQuery",
                ProcessQuery.class, "addQuery");

	// testParamValues
	configureParamValuesSet(digester, "wdkModel/querySet/processQuery/testParamValues", "addParamValuesSet");

        configureNode(digester, "wdkModel/querySet/processQuery/paramRef",
                ParamReference.class, "addParamRef");

        configureNode(digester, "wdkModel/querySet/processQuery/wsColumn",
                Column.class, "addColumn");
    }

    private void configureParamSet(Digester digester) {
        // ParamSet
        configureNode(digester, "wdkModel/paramSet", ParamSet.class,
                "addParamSet");

        configureNode(digester, "wdkModel/paramSet/useTermOnly",
                ParamConfiguration.class, "addUseTermOnly");

        // string param
        configureNode(digester, "wdkModel/paramSet/stringParam",
                StringParam.class, "addParam");

        configureNode(digester, "wdkModel/paramSet/stringParam/help",
                WdkModelText.class, "addHelp");
        digester.addCallMethod("wdkModel/paramSet/stringParam/help", "setText",
                0);

        configureNode(digester, "wdkModel/paramSet/stringParam/suggest",
                ParamSuggestion.class, "addSuggest");

        // flatVocabParam
        configureNode(digester, "wdkModel/paramSet/flatVocabParam",
                FlatVocabParam.class, "addParam");

        configureNode(digester, "wdkModel/paramSet/flatVocabParam/help",
                WdkModelText.class, "addHelp");
        digester.addCallMethod("wdkModel/paramSet/flatVocabParam/help",
                "setText", 0);

        configureNode(digester, "wdkModel/paramSet/flatVocabParam/suggest",
                ParamSuggestion.class, "addSuggest");

        configureNode(digester, "wdkModel/paramSet/flatVocabParam/useTermOnly",
                ParamConfiguration.class, "addUseTermOnly");

        // history param
        configureNode(digester, "wdkModel/paramSet/answerParam",
                AnswerParam.class, "addParam");

        configureNode(digester, "wdkModel/paramSet/answerParam/help",
                WdkModelText.class, "addHelp");
        digester.addCallMethod("wdkModel/paramSet/answerParam/help", "setText",
                0);

        configureNode(digester, "wdkModel/paramSet/answerParam/suggest",
                ParamSuggestion.class, "addSuggest");

        // dataset param
        configureNode(digester, "wdkModel/paramSet/datasetParam",
                DatasetParam.class, "addParam");

        configureNode(digester, "wdkModel/paramSet/datasetParam/help",
                WdkModelText.class, "addHelp");
        digester.addCallMethod("wdkModel/paramSet/datasetParam/help",
                "setText", 0);

        configureNode(digester, "wdkModel/paramSet/datasetParam/suggest",
                ParamSuggestion.class, "addSuggest");

        // enum param
        configureNode(digester, "wdkModel/paramSet/enumParam", EnumParam.class,
                "addParam");

        configureNode(digester, "wdkModel/paramSet/enumParam/help",
                WdkModelText.class, "addHelp");
        digester.addCallMethod("wdkModel/paramSet/enumParam/help", "setText", 0);

        configureNode(digester, "wdkModel/paramSet/enumParam/suggest",
                ParamSuggestion.class, "addSuggest");

        configureNode(digester, "wdkModel/paramSet/enumParam/useTermOnly",
                ParamConfiguration.class, "addUseTermOnly");

        configureNode(digester, "wdkModel/paramSet/enumParam/enumList",
                EnumItemList.class, "addEnumItemList");

        configureNode(digester,
                "wdkModel/paramSet/enumParam/enumList/useTermOnly",
                ParamConfiguration.class, "addUseTermOnly");

        configureNode(digester,
                "wdkModel/paramSet/enumParam/enumList/enumValue",
                EnumItem.class, "addEnumItem");
        digester.addBeanPropertySetter("wdkModel/paramSet/enumParam/enumList/enumValue/display");
        digester.addBeanPropertySetter("wdkModel/paramSet/enumParam/enumList/enumValue/term");
        digester.addBeanPropertySetter("wdkModel/paramSet/enumParam/enumList/enumValue/internal");
    }

    private void configureQuestionSet(Digester digester) {
        // QuestionSet
        configureNode(digester, "wdkModel/questionSet", QuestionSet.class,
                "addQuestionSet");

        configureNode(digester, "wdkModel/questionSet/description",
                WdkModelText.class, "addDescription");
        digester.addCallMethod("wdkModel/questionSet/description", "setText", 0);

        // question
        configureNode(digester, "wdkModel/questionSet/question",
                Question.class, "addQuestion");

        configureNode(digester, "wdkModel/questionSet/question/description",
                WdkModelText.class, "addDescription");
        digester.addCallMethod("wdkModel/questionSet/question/description",
                "setText", 0);

        configureNode(digester, "wdkModel/questionSet/question/summary",
                WdkModelText.class, "addSummary");
        digester.addCallMethod("wdkModel/questionSet/question/summary",
                "setText", 0);

        configureNode(digester, "wdkModel/questionSet/question/help",
                WdkModelText.class, "addHelp");
        digester.addCallMethod("wdkModel/questionSet/question/help", "setText",
                0);

        // question's property list
        configureNode(digester, "wdkModel/questionSet/question/propertyList",
                PropertyList.class, "addPropertyList");

        configureNode(digester,
                "wdkModel/questionSet/question/propertyList/value",
                WdkModelText.class, "addValue");
        digester.addCallMethod(
                "wdkModel/questionSet/question/propertyList/value", "setText",
                0);

        configureNode(digester, "wdkModel/questionSet/question/attributesList",
                AttributeList.class, "addAttributeList");

        // dynamic attribute set
        configureNode(digester,
                "wdkModel/questionSet/question/dynamicAttributes",
                DynamicAttributeSet.class, "addDynamicAttributeSet");

        configureNode(
                digester,
                "wdkModel/questionSet/question/dynamicAttributes/columnAttribute",
                ColumnAttributeField.class, "addAttributeField");

        configureLinkTextFields(digester,
                "wdkModel/questionSet/question/dynamicAttributes/");
    }

    private void configureXmlQuestionSet(Digester digester) {
        // load XmlQuestionSet
        configureNode(digester, "wdkModel/xmlQuestionSet",
                XmlQuestionSet.class, "addXmlQuestionSet");

        configureNode(digester, "wdkModel/xmlQuestionSet/description",
                WdkModelText.class, "addDescription");
        digester.addCallMethod("wdkModel/xmlQuestionSet/description",
                "setText", 0);

        // load XmlQuestion
        configureNode(digester, "wdkModel/xmlQuestionSet/xmlQuestion",
                XmlQuestion.class, "addQuestion");

        configureNode(digester,
                "wdkModel/xmlQuestionSet/xmlQuestion/description",
                WdkModelText.class, "addDescription");
        digester.addCallMethod(
                "wdkModel/xmlQuestionSet/xmlQuestion/description", "setText", 0);

        configureNode(digester, "wdkModel/xmlQuestionSet/xmlQuestion/help",
                WdkModelText.class, "addHelp");
        digester.addCallMethod("wdkModel/xmlQuestionSet/xmlQuestion/help",
                "setText", 0);
    }

    private void configureParamValuesSet(Digester digester, String path,
					    String addMethodName) {
	digester.addObjectCreate(path, ParamValuesSet.class);
        digester.addSetProperties(path);
	digester.addCallMethod(path + "/paramValue", "put", 2);
	digester.addCallParam(path + "/paramValue", 0, "name");
	digester.addCallParam(path + "/paramValue", 1);
        digester.addSetNext(path, addMethodName);
    }

    private void configureXmlRecordClassSet(Digester digester) {
        // load XmlRecordClassSet
        configureNode(digester, "wdkModel/xmlRecordClassSet",
                XmlRecordClassSet.class, "addXmlRecordClassSet");

        // load XmlRecordClass
        configureNode(digester, "wdkModel/xmlRecordClassSet/xmlRecordClass",
                XmlRecordClass.class, "addRecordClass");

        // load XmlAttributeField
        configureNode(digester,
                "wdkModel/xmlRecordClassSet/xmlRecordClass/xmlAttribute",
                XmlAttributeField.class, "addAttributeField");

        // load XmlTableField
        configureNode(digester,
                "wdkModel/xmlRecordClassSet/xmlRecordClass/xmlTable",
                XmlTableField.class, "addTableField");

        // load XmlAttributeField within table
        configureNode(
                digester,
                "wdkModel/xmlRecordClassSet/xmlRecordClass/xmlTable/xmlAttribute",
                XmlAttributeField.class, "addAttributeField");
    }

    private void configureGroupSet(Digester digester) {
        // load GroupSet
        configureNode(digester, "wdkModel/groupSet", GroupSet.class,
                "addGroupSet");

        // load group
        configureNode(digester, "wdkModel/groupSet/group", Group.class,
                "addGroup");

        configureNode(digester, "wdkModel/groupSet/group/description",
                WdkModelText.class, "addDescription");
        digester.addCallMethod("wdkModel/groupSet/group/description",
                "setText", 0);
    }

    private void configureLinkTextFields(Digester digester, String prefix) {
        // link attribute
        configureNode(digester, prefix + "linkAttribute",
                LinkAttributeField.class, "addAttributeField");
        configureNode(digester, prefix + "linkAttribute/url",
                WdkModelText.class, "addUrl");
        digester.addCallMethod(prefix + "linkAttribute/url", "setText", 0);
        configureNode(digester, prefix + "linkAttribute/displayText",
                WdkModelText.class, "addDisplayText");
        digester.addCallMethod(prefix + "linkAttribute/displayText", "setText",
                0);

        // text attribute
        configureNode(digester, prefix + "textAttribute",
                TextAttributeField.class, "addAttributeField");

        configureNode(digester, prefix + "textAttribute/text",
                WdkModelText.class, "addText");
        digester.addCallMethod(prefix + "textAttribute/text", "setText", 0);
    }

    public static void main(String[] args) throws SAXException, IOException,
            ParserConfigurationException, TransformerFactoryConfigurationError,
            TransformerException, WdkModelException, NoSuchAlgorithmException,
            SQLException, JSONException, WdkUserException,
            InstantiationException, IllegalAccessException,
            ClassNotFoundException {
        String cmdName = System.getProperty("cmdName");
        String gusHome = System.getProperty(Utilities.SYSTEM_PROPERTY_GUS_HOME);

        // process args
        Options options = declareOptions();
        CommandLine cmdLine = parseOptions(cmdName, options, args);
        String projectId = cmdLine.getOptionValue(Utilities.ARGUMENT_PROJECT_ID);

        // create a parser, and parse the model file
        ModelXmlParser parser = new ModelXmlParser(gusHome);
        WdkModel wdkModel = parser.parseModel(projectId);

        // print out the model content
        System.out.println(wdkModel.toString());
        System.exit(0);
    }

    private static void addOption(Options options, String argName, String desc) {

        Option option = new Option(argName, true, desc);
        option.setRequired(true);
        option.setArgName(argName);

        options.addOption(option);
    }

    private static Options declareOptions() {
        Options options = new Options();

        // config file
        addOption(options, "model", "the name of the model.  This is used to "
                + "find the Model XML file ($GUS_HOME/lib/wdk/model_name.xml) "
                + "the Model property file ($GUS_HOME/config/model_name.prop) "
                + "and the Model config file "
                + "($GUS_HOME/config/model_name-config.xml)");

        return options;
    }

    private static CommandLine parseOptions(String cmdName, Options options,
            String[] args) {

        CommandLineParser parser = new BasicParser();
        CommandLine cmdLine = null;
        try {
            // parse the command line arguments
            cmdLine = parser.parse(options, args);
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("");
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
            System.err.println("");
            usage(cmdName, options);
        }

        return cmdLine;
    }

    private static void usage(String cmdName, Options options) {

        String newline = System.getProperty("line.separator");
        String cmdlineSyntax = cmdName + " -model model_name";

        String header = newline + "Parse and print out a WDK Model xml file."
                + newline + newline + "Options:";

        String footer = "";

        // PrintWriter stderr = new PrintWriter(System.err);
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(75, cmdlineSyntax, header, options, footer);
        System.exit(1);
    }
}
