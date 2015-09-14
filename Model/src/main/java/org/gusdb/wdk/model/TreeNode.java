package org.gusdb.wdk.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class provides a common implementation of tree structure, and it is used
 * by the category for attributes and tree params.
 * 
 * @author rdoherty
 * 
 */
public class TreeNode {

  enum BoolField {
    SELECTED, DEFAULT;
  }

  private boolean _openByDefault = false;
  private String _name;
  private String _displayName;
  private String _help;
  private boolean[] _booleanFields = new boolean[BoolField.values().length];
  private List<TreeNode> _childNodes = new ArrayList<TreeNode>();

  public TreeNode(String name, String displayName) {
    this(name, displayName, "");
  }

  public TreeNode(String name, String displayName, String help) {
    _name = name;
    _displayName = displayName;
    _help = help;
    for (int i = 0; i < _booleanFields.length; i++) {
      _booleanFields[i] = false;
    }
  }

  public void setOpenByDefault(boolean openByDefault) {
    _openByDefault = openByDefault;
  }

  public boolean getOpenByDefault() {
    return _openByDefault;
  }

  public String getName() {
    return _name;
  }

  public String getDisplayName() {
    return _displayName;
  }

  public String getHelp() {
    return _help;
  }

  public boolean getIsLeaf() {
    return _childNodes.isEmpty();
  }

  public void addChildNode(TreeNode child) {
    _childNodes.add(child);
  }

  public List<TreeNode> getChildNodes() {
    return _childNodes;
  }

  public List<TreeNode> getNonLeafNodes() {
    return getByIfLeaf(false);
  }

  public List<TreeNode> getLeafNodes() {
    return getByIfLeaf(true);
  }

  private List<TreeNode> getByIfLeaf(boolean ifLeaf) {
    List<TreeNode> nodes = new ArrayList<TreeNode>();
    for (TreeNode node : _childNodes) {
      if (node.getIsLeaf() == ifLeaf) {
        nodes.add(node);
      }
    }
    return nodes;
  }

  public void setSelected(boolean isSelected) {
    _booleanFields[BoolField.SELECTED.ordinal()] = isSelected;
  }

  public boolean getSelected() {
    return _booleanFields[BoolField.SELECTED.ordinal()];
  }

  public void setIsDefault(boolean isDefault) {
    _booleanFields[BoolField.DEFAULT.ordinal()] = isDefault;
  }

  public boolean getIsDefault() {
    return _booleanFields[BoolField.DEFAULT.ordinal()];
  }

  public void setBoolField(BoolField fieldId, boolean value) {
    _booleanFields[fieldId.ordinal()] = value;
  }

  public boolean getBoolField(BoolField fieldId) {
    return _booleanFields[fieldId.ordinal()];
  }

  public void turnOnSelectedLeaves(String... names) {
    setBooleansToTrue(BoolField.SELECTED, Arrays.asList(names));
  }

  public void turnOnSelectedLeaves(List<String> selectedList) {
    setBooleansToTrue(BoolField.SELECTED, selectedList);
  }

  public void turnOnAllLeaves() {
    setAllBooleansToTrue(BoolField.SELECTED);
  }

  public String getSelectedAsList() {
    return getNamesOfLeavesWithBoolean(BoolField.SELECTED, true);
  }
  
  public List<TreeNode> getSelectedLeaves() {
    return getLeafNodesWithBoolean(BoolField.SELECTED, true);
  }

  public void setDefaultLeaves(String... names) {
    setBooleansToTrue(BoolField.DEFAULT, Arrays.asList(names));
  }

  public void setDefaultLeaves(List<String> defaultList) {
    setBooleansToTrue(BoolField.DEFAULT, defaultList);
  }

  public void setAllOnAsDefault() {
    setAllBooleansToTrue(BoolField.DEFAULT);
  }

  public String getDefaultAsList() {
    return getNamesOfLeavesWithBoolean(BoolField.DEFAULT, true);
  }

  private void setBooleansToTrue(BoolField fieldId, List<String> names) {
    for (TreeNode node : _childNodes) {
      if (node.getIsLeaf()) {
        if (names.contains(node.getName())) {
          node.setBoolField(fieldId, true);
        }
      } else {
        node.setBooleansToTrue(fieldId, names);
      }
    }
  }

  private void setAllBooleansToTrue(BoolField fieldId) {
    for (TreeNode node : _childNodes) {
      if (node.getIsLeaf()) {
        node.setBoolField(fieldId, true);
      } else {
        node.setAllBooleansToTrue(fieldId);
      }
    }
  }

  private String getNamesOfLeavesWithBoolean(BoolField fieldId, boolean value) {
    StringBuilder str = new StringBuilder();
    for (TreeNode node : _childNodes) {
      if (node.getIsLeaf()) {
        if (node.getBoolField(fieldId) == value) {
          str.append(",'").append(node.getName().replace("'", "\\'")).append(
              "'");
        }
      } else {
        String namesFromChild = node.getNamesOfLeavesWithBoolean(fieldId, value);
        if (namesFromChild.length() > 0) {
          str.append(",").append(namesFromChild);
        }
      }
    }
    String all = str.toString();
    if (all.length() > 0) {
      return all.substring(1, all.length());
    }
    return all;
  }

  private List<TreeNode> getLeafNodesWithBoolean(BoolField fieldId, boolean value) {
    List<TreeNode> nodeList = new ArrayList<TreeNode>();
    if (getIsLeaf()) {
      if (getBoolField(fieldId) == value) {
        nodeList.add(this);
      }
      return nodeList;
    }
    for (TreeNode child : _childNodes) {
      nodeList.addAll(child.getLeafNodesWithBoolean(fieldId, value));
    }
    return nodeList;
  }
  
  public boolean getIsAllSelected() {
    for (TreeNode node : _childNodes) {
      if (node.getIsLeaf()) {
        if (!node.getSelected()) {
          return false;
        }
      } else {
        if (!node.getIsAllSelected()) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public String toString() {
    if (getIsLeaf()) {
      return leafToString();
    }
    return toString("");
  }

  /**
   * Finds first node in this tree with the passed name and returns it
   * 
   * @param name name of desired node
   * @return found node or null if not found
   */
  public TreeNode find(String name) {
    if (_name.equals(name)) {
      return this;
    }
    for (TreeNode node : _childNodes) {
      TreeNode found = node.find(name);
      if (found != null) return found;
    }
    return null;
  }

  /**
   * Finds first leaf node in this tree with the passed name and returns it
   * 
   * @param name name of desired leaf node
   * @return found leaf node or null if not found
   */
  public TreeNode findLeaf(String name) {
    if (getIsLeaf() && _name.equals(name)) {
      return this;
    }
    for (TreeNode node : _childNodes) {
      TreeNode found = node.findLeaf(name);
      if (found != null) return found;
    }
    return null;
  }

  /**
   * Removes any subtrees with the passed name.
   * 
   * @param name name of the subtree to be removed
   * @return number of subtrees removed
   */
  public int remove(String name) {
    int numRemoved = 0;
    for (int i = 0; i < _childNodes.size(); i++) {
      if (_childNodes.get(i).getName().equals(name)) {
        _childNodes.remove(i);
        numRemoved++;
        i--; // reuse the current index, now pointing to the next node
      }
      else {
        numRemoved += _childNodes.get(i).remove(name);
      }
    }
    return numRemoved;
  }

  public String toString(String indentation) {
    String IND = indentation;
    String NL = System.getProperty("line.separator");
    StringBuilder str = new StringBuilder().append(IND).append("TreeNode {").append(
        NL).append(IND).append("  Name: ").append(_name).append(NL).append(IND).append(
        "  DisplayName: ").append(_displayName).append(NL).append(IND).append(
        "  Leaves:").append(NL);
    for (TreeNode node : _childNodes) {
      if (node.getIsLeaf()) {
        str.append(IND).append("    ").append(node).append(NL);
      }
    }
    str.append(IND).append("  Children {").append(NL);
    for (TreeNode child : _childNodes) {
      if (!child.getIsLeaf()) {
        str.append(child.toString(IND + "    "));
      }
    }
    str.append(IND).append("  }").append(NL).append(IND).append("}").append(NL);
    return str.toString();
  }

  public String leafToString() {
    StringBuilder str = new StringBuilder().append("Leaf { name: \"").append(
        _name).append("\", displayName: \"").append(_displayName).append(
        "\", help: ").append(_help == null ? "null" : _help.length() + " chars").append(
        ", bools: ");
    boolean first = true;
    for (BoolField field : BoolField.values()) {
      str.append(first ? "{ " : ", ");
      first = false;
      str.append(field.name().toLowerCase()).append(": ").append(
          _booleanFields[field.ordinal()]);
    }
    str.append(" } }");
    return str.toString();
  }
}
