/**
 * 
 */
package org.gusdb.wdk.model.answer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.record.RecordClass;

/**
 * An object representation of {@code <answerFilterLayout>}. It is used to group
 * {@link AnswerFilterInstance}s into different display groups.
 * 
 * @author xingao
 * 
 */
public class AnswerFilterLayout extends WdkModelBase {

  @SuppressWarnings("unused")
  private static final Logger logger = Logger.getLogger(AnswerFilterLayout.class);
  
  private RecordClass recordClass;

  private String name;
  private String displayName;
  private boolean visible = true;
  private String fileName;
  private boolean vertical = false;

  private List<WdkModelText> descriptionList = new ArrayList<WdkModelText>();
  private String description;

  private List<AnswerFilterInstanceReference> referenceList = new ArrayList<AnswerFilterInstanceReference>();
  private Map<String, AnswerFilterInstance> instanceMap = new LinkedHashMap<String, AnswerFilterInstance>();

	// Dec 2013 cris: adds three maps to handle new jsp for organism gene filters generated by the injector, shared by all websites
  //   sortedInstanceMap: alphabetically sorted and with all filter instances in original instanceMap defined above
  //   instanceCountMap:  not sorted and with all species (e.g: "pfal"),
	//                      as value: instance count (how many organisms per species)
	//   sortedFamilyCountMap: alphabetically sorted and with all the families (e.g.: "Plasmodium"),
  //                         as value: instance count (how many organisms per family)
  //
	// these counts (per family and per species) are needed to build a filter table using colspan
  // -I think we could generate instanceMap already as a treemap and we would not need sortedInstanceMap
  // -also, it would all be simpler if the model provides the sort, the family names and the org counts per species and per family

  private Map<String, AnswerFilterInstance> sortedInstanceMap;
	private Map<String, Integer> instanceCountMap = new LinkedHashMap<String, Integer>();
	private Map<String, Integer> sortedFamilyCountMap = new TreeMap<String, Integer>();

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
   */
  /**
   * @return the recordClass
   */
  public RecordClass getRecordClass() {
    return recordClass;
  }

  /**
   * @param recordClass
   *          the recordClass to set
   */
  public void setRecordClass(RecordClass recordClass) {
    this.recordClass = recordClass;
    if (referenceList != null) {
      for (AnswerFilterInstanceReference reference : referenceList) {
        reference.setRecordClass(recordClass);
      }
    }
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name
   *          the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the displayName
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * @param displayName
   *          the displayName to set
   */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /**
   * @return the visible
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * @param visible
   *          the visible to set
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public void addDescription(WdkModelText description) {
    this.descriptionList.add(description);
  }

  /**
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  public void addReference(AnswerFilterInstanceReference reference) {
    if (recordClass != null)
      reference.setRecordClass(recordClass);
    this.referenceList.add(reference);
  }

  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    // exclude the descriptions
    for (WdkModelText text : descriptionList) {
      if (text.include(projectId)) {
        text.excludeResources(projectId);
        if (description != null)
          throw new WdkModelException("Description of "
              + "answerFilterLayout '" + name + "' in "
              + recordClass.getFullName() + " is included more than once.");
        this.description = text.getText();
      }
    }
    descriptionList = null;

    // exclude the instances
    List<AnswerFilterInstanceReference> newReferences = new ArrayList<AnswerFilterInstanceReference>();
    for (AnswerFilterInstanceReference reference : referenceList) {
      if (reference.include(projectId)) {
        reference.excludeResources(projectId);
        newReferences.add(reference);
      }
    }
    referenceList = newReferences;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.WdkModelBase#resolveReferences(org.gusdb.wdk.model
   * .WdkModel)
   */
  @Override
  public void resolveReferences(WdkModel wodkModel) throws WdkModelException {
    if (resolved)
      return;

    // resolve the instances
    for (AnswerFilterInstanceReference reference : referenceList) {
      reference.resolveReferences(wodkModel);
      String ref = reference.getRef();

			// for genes_filter
			String[] filterName;
			String familySpecies;
			int dashPos;
			String family;

      if (instanceMap.containsKey(ref))
        throw new WdkModelException("More than one instance [" + ref
            + "] are defined in filter layout [" + name + "]");
      instanceMap.put(ref, reference.getInstance());

			if (this.name.equals("gene_filters")) {	
					sortedInstanceMap = new TreeMap<String, AnswerFilterInstance>(instanceMap);
					//logger.debug("\n\n===========" + sortedInstanceMap + "===========\n\n");

					// reading FamilySpecies name from the filter instance name, generated by gene filter injector
					filterName = ref.split("_");
					familySpecies = filterName[0];
					//logger.debug("\n\n\n" + familySpecies + "====" + ref + "\n\n\n");
					dashPos = familySpecies.indexOf("-");
					if (dashPos == -1) family = familySpecies;
					else family = familySpecies.substring(0,dashPos);
					//logger.debug("\n\n\n" + family + "\n\n\n");
		
					if (ref.contains("instances") && !ref.contains("distinct")) { 
							if (!instanceCountMap.containsKey(familySpecies))  instanceCountMap.put(familySpecies, 1);
							else instanceCountMap.put(familySpecies, instanceCountMap.get(familySpecies)+1);
							if (!sortedFamilyCountMap.containsKey(family)) {
									sortedFamilyCountMap.put(family, 1);
							}
							else sortedFamilyCountMap.put(family, sortedFamilyCountMap.get(family)+1);
					}
			} //end if gene_filters

    }

		if (this.name.equals("gene_filters")) {	
				sortedInstanceMap = new TreeMap<String, AnswerFilterInstance>(instanceMap);
				//logger.debug("\n\n===========" + sortedInstanceMap + "===========\n\n");
				//logger.debug("\n\n===========" + sortedFamilyCountMap + "===========\n\n");
		}

    referenceList = null;

    resolved = true;
  }

  public Map<String, AnswerFilterInstance> getInstanceMap() {
    return new LinkedHashMap<String, AnswerFilterInstance>(instanceMap);
  }

  public AnswerFilterInstance[] getInstances() {
    AnswerFilterInstance[] array = new AnswerFilterInstance[instanceMap.size()];
    instanceMap.values().toArray(array);
    return array;
  }

 public Map<String, AnswerFilterInstance> getSortedInstanceMap() {
    return new TreeMap<String, AnswerFilterInstance>(sortedInstanceMap);
  }

  public AnswerFilterInstance[] getSortedInstances() {
    AnswerFilterInstance[] array = new AnswerFilterInstance[sortedInstanceMap.size()];
    sortedInstanceMap.values().toArray(array);
    return array;
  }

 public Map<String, Integer> getInstanceCountMap() {
    return new LinkedHashMap<String, Integer>(instanceCountMap);
  }

 public Map<String, Integer> getSortedFamilyCountMap() {
    return new LinkedHashMap<String, Integer>(sortedFamilyCountMap);
  }


  /**
   * @return the fileName
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * @param fileName
   *          the fileName to set
   */
  public void setFileName(String fileName) {
    this.fileName = fileName;
    if (this.fileName != null)
      this.fileName = this.fileName.trim();
  }

  /**
   * @return the vertical
   */
  public boolean isVertical() {
    return vertical;
  }

  /**
   * @param vertical
   *          the vertical to set
   */
  public void setVertical(boolean vertical) {
    this.vertical = vertical;
  }

}
