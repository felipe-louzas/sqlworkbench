/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 *
 */
package workbench.gui.dialogs.export;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.exporter.BlobMode;
import workbench.db.exporter.ExportType;

import workbench.gui.components.ColumnSelectorPanel;
import workbench.gui.components.KeyColumnSelectorPanel;

import workbench.storage.ResultInfo;

import workbench.sql.generator.merge.MergeGenerator;

import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class SqlOptionsPanel
  extends JPanel
  implements SqlOptions, ActionListener
{
  private List<String> keyColumns;
  private ColumnSelectorPanel columnSelectorPanel;
  private ResultInfo tableColumns;

  public SqlOptionsPanel(ResultInfo info)
  {
    super();
    initComponents();
    setResultInfo(info);
    List<String> types = Settings.getInstance().getLiteralTypeList();
    ComboBoxModel model = new DefaultComboBoxModel(types.toArray());
    literalTypes.setModel(model);

    List<String> mTypes = MergeGenerator.Factory.getSupportedTypes();
    ComboBoxModel mergeModel = new DefaultComboBoxModel(mTypes.toArray());
    mergeTypes.setModel(mergeModel);

    List<String> bTypes = BlobMode.getTypes();
    bTypes.remove(BlobMode.Base64.getTypeString());
    ComboBoxModel blobModel = new DefaultComboBoxModel(bTypes.toArray());
    blobTypes.setModel(blobModel);
    blobTypes.setSelectedItem(BlobMode.SaveToFile.toString());
  }

  public final void setResultInfo(ResultInfo info)
  {
    this.tableColumns = info;

    boolean hasColumns = tableColumns != null;
    boolean keysPresent = (info == null ? false : info.hasPkColumns());
    this.selectKeys.setEnabled(hasColumns);

    this.setIncludeDeleteInsert(keysPresent);
    this.setIncludeUpdate(keysPresent);

    if (info != null)
    {
      TableIdentifier table = info.getUpdateTable();
      if (table != null)
      {
        this.alternateTable.setText(table.getTableName());
      }
      else
      {
        this.alternateTable.setText("target_table");
      }
    }
  }

  @Override
  public boolean getUseMultiRowInserts()
  {
    return this.multiRowInserts.isSelected();
  }

  public void saveSettings(String type)
  {
    Settings s = Settings.getInstance();
    s.setProperty("workbench." + type+ ".sql.commitevery", this.getCommitEvery());
    s.setProperty("workbench." + type+ ".sql.insert.multirow", getUseMultiRowInserts());
    s.setProperty("workbench." + type+ ".sql.createtable", this.getCreateTable());
    s.setProperty("workbench." + type+ ".sql.ignoreidentity", this.ignoreIdentityColumns());
    s.setProperty("workbench." + type+ ".sql.saveas.dateliterals", this.getDateLiteralType());
    s.setProperty("workbench." + type+ ".sql.saveas.blobliterals", this.getBlobMode().getTypeString());
  }

  public void restoreSettings(String type)
  {
    Settings s = Settings.getInstance();
    this.setCommitEvery(s.getIntProperty("workbench." + type + ".sql.commitevery", 0));
    this.setCreateTable(s.getBoolProperty("workbench." + type + ".sql.createtable"));
    String def = s.getProperty("workbench." + type + ".sql.default.dateliterals", "dbms");
    String dateLiterals = s.getProperty("workbench." + type + ".sql.saveas.dateliterals", def);
    this.literalTypes.setSelectedItem(dateLiterals);

    String blobMode = s.getProperty("workbench." + type + ".sql.saveas.blobliterals", BlobMode.SaveToFile.getTypeString());
    this.blobTypes.setSelectedItem(blobMode);

    boolean ignore = s.getBoolProperty("workbench." + type + ".sql.ignoreidentity", Settings.getInstance().getGenerateInsertIgnoreIdentity());
    ignoreIdentity.setSelected(ignore);

    boolean multiRow = s.getBoolProperty("workbench." + type + ".sql.insert.multirow", false);
    multiRowInserts.setSelected(multiRow);
  }

  @Override
  public boolean ignoreIdentityColumns()
  {
    return ignoreIdentity.isSelected();
  }

  @Override
  public String getMergeType()
  {
    return (String)mergeTypes.getSelectedItem();
  }

  @Override
  public void setBlobMode(BlobMode mode)
  {
    if (mode != null)
    {
      blobTypes.setSelectedItem(mode.getTypeString());
    }
  }

  @Override
  public BlobMode getBlobMode()
  {
    String type = (String)blobTypes.getSelectedItem();
    BlobMode mode = BlobMode.getMode(type);
    return mode;
  }

  @Override
  public String getDateLiteralType()
  {
    return (String)literalTypes.getSelectedItem();
  }

  @Override
  public String getAlternateUpdateTable()
  {
    String s = alternateTable.getText();
    if (StringUtil.isNotBlank(s)) return s.trim();
    return null;
  }

  @Override
  public void setAlternateUpdateTable(String table)
  {
    this.alternateTable.setText((table == null ? "" : table.trim()));
  }

  @Override
  public int getCommitEvery()
  {
    int result = -1;
    try
    {
      String value = this.commitCount.getText();
      if (value != null && value.length() > 0)
      {
        result = Integer.parseInt(value);
      }
      else
      {
        result = 0;
      }
    }
    catch (Exception e)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Could not retrieve commit frequency", e);
    }
    return result;
  }

  public void setDbId(String dbid)
  {
    String currentType = MergeGenerator.Factory.getTypeForDBID(dbid);
    mergeTypes.setSelectedItem(currentType);
  }

  private void removeSyntaxType(String type)
  {
    syntaxType.removeItem(type);
  }

  private int getSyntaxTypeIndex(String type)
  {
    int count = syntaxType.getItemCount();
    for (int i=0; i < count; i++)
    {
      String item = (String)syntaxType.getItemAt(i);
      if (item.equals(type)) return i;
    }
    return -1;
  }

  private void addSyntaxType(String type)
  {
    DefaultComboBoxModel model = (DefaultComboBoxModel)syntaxType.getModel();
    int index = getSyntaxTypeIndex(type);

    if (type.equals("MERGE") &&  index == -1)
    {
      // merge always goes to the end
      model.addElement(type);
    }

    if (type.equals("UPDATE") && index == -1)
    {
      int insertIndex = getSyntaxTypeIndex("INSERT");
      model.insertElementAt(type, insertIndex + 1);
    }

    if (type.equals("DELETE/INSERT") && index == -1)
    {
      int updateIndex = getSyntaxTypeIndex("UPDATE");
      model.insertElementAt(type, updateIndex + 1);
    }
  }

  public void setIncludeMerge(boolean flag)
  {
    if (flag)
    {
      addSyntaxType("MERGE");
      mergeTypes.setEnabled(true);
      mergeTypesLabel.setEnabled(true);
    }
    else
    {
      removeSyntaxType("MERGE");
      mergeTypes.setEnabled(false);
      mergeTypesLabel.setEnabled(false);
    }
  }

  public void setIncludeUpdate(boolean flag)
  {
    if (flag)
    {
      addSyntaxType("UPDATE");
    }
    else
    {
      removeSyntaxType("UPDATE");
    }
  }

  public void setIncludeDeleteInsert(boolean flag)
  {
    if (flag)
    {
      addSyntaxType("DELETE/INSERT");
      addSyntaxType("DELETE");
    }
    else
    {
      removeSyntaxType("DELETE/INSERT");
      removeSyntaxType("DELETE");
    }
  }

  private String getSelectedSyntaxType()
  {
    return (String)syntaxType.getSelectedItem();
  }

  @Override
  public ExportType getExportType()
  {
    String type = getSelectedSyntaxType();
    if (type.equals("UPDATE"))
    {
      return ExportType.SQL_UPDATE;
    }
    if (type.equals("DELETE"))
    {
      return ExportType.SQL_DELETE;
    }
    if (type.equals("DELETE/INSERT"))
    {
      return ExportType.SQL_DELETE_INSERT;
    }
    if (type.equals("MERGE"))
    {
      return ExportType.SQL_MERGE;
    }
    return ExportType.SQL_INSERT;
  }

  @Override
  public boolean getCreateTable()
  {
    return createTable.isSelected();
  }

  @Override
  public void setCommitEvery(int value)
  {
    if (value > 0)
    {
      this.commitCount.setText(Integer.toString(value));
    }
    else
    {
      this.commitCount.setText("");
    }
  }

  @Override
  public void setExportType(ExportType type)
  {
    switch (type)
    {
      case SQL_DELETE:
        syntaxType.setSelectedItem("DELETE");
        break;
      case SQL_DELETE_INSERT:
        syntaxType.setSelectedItem("DELETE/INSERT");
        break;
      case SQL_UPDATE:
        syntaxType.setSelectedItem("UPDATE");
        break;
      case SQL_MERGE:
        syntaxType.setSelectedItem("MERGE");
        break;
      default:
        syntaxType.setSelectedItem("INSERT");
    }
    checkMultiRow();
  }

  private void checkMultiRow()
  {
    multiRowInserts.setEnabled(getExportType() == ExportType.SQL_INSERT);
  }

  @Override
  public void setCreateTable(boolean flag)
  {
    this.createTable.setSelected(flag);
  }

  @Override
  public List<String> getKeyColumns()
  {
    return keyColumns;
  }

  private void selectColumns()
  {
    if (this.tableColumns == null) return;

    if (this.columnSelectorPanel == null)
    {
      this.columnSelectorPanel = new KeyColumnSelectorPanel(tableColumns);
    }
    else
    {
      this.columnSelectorPanel.selectColumns(this.keyColumns);
    }

    int choice = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this), this.columnSelectorPanel, ResourceMgr.getString("MsgSelectKeyColumnsWindowTitle"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

    if (choice == JOptionPane.OK_OPTION)
    {
      this.keyColumns = null;

      List selected = this.columnSelectorPanel.getSelectedColumns();
      int size = selected.size();
      this.keyColumns = new ArrayList<>(size);
      for (int i=0; i < size; i++)
      {
        ColumnIdentifier col = (ColumnIdentifier)selected.get(i);
        this.keyColumns.add(col.getColumnName());
      }

      boolean keysPresent = (size > 0);
      this.setIncludeUpdate(keysPresent);
      this.setIncludeDeleteInsert(keysPresent);
      this.setIncludeMerge(keysPresent);
    }
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    GridBagConstraints gridBagConstraints;

    typeGroup = new ButtonGroup();
    selectKeys = new JButton();
    jPanel2 = new JPanel();
    jPanel4 = new JPanel();
    literalTypesLabel = new JLabel();
    literalTypes = new JComboBox();
    mergeTypesLabel = new JLabel();
    mergeTypes = new JComboBox();
    jLabel2 = new JLabel();
    syntaxType = new JComboBox();
    blobTypesLabel = new JLabel();
    blobTypes = new JComboBox();
    extOptionsPanel = new JPanel();
    jLabel1 = new JLabel();
    alternateTable = new JTextField();
    commitLabel = new JLabel();
    commitCount = new JTextField();
    ignoreIdentity = new JCheckBox();
    multiRowInserts = new JCheckBox();
    createTable = new JCheckBox();

    setLayout(new GridBagLayout());

    selectKeys.setText(ResourceMgr.getString("LblSelectKeyColumns")); // NOI18N
    selectKeys.setToolTipText(ResourceMgr.getString("d_LblSelectKeyColumns")); // NOI18N
    selectKeys.addActionListener(this);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(7, 0, 6, 0);
    add(selectKeys, gridBagConstraints);

    jPanel2.setLayout(new BorderLayout(10, 0));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(3, 4, 0, 21);
    add(jPanel2, gridBagConstraints);

    jPanel4.setLayout(new GridBagLayout());

    literalTypesLabel.setText(ResourceMgr.getString("LblLiteralType")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 0, 5, 0);
    jPanel4.add(literalTypesLabel, gridBagConstraints);

    literalTypes.setToolTipText(ResourceMgr.getDescription("LblLiteralType"));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 0.5;
    gridBagConstraints.insets = new Insets(0, 4, 5, 0);
    jPanel4.add(literalTypes, gridBagConstraints);

    mergeTypesLabel.setText(ResourceMgr.getString("LblMergeType")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 0, 5, 0);
    jPanel4.add(mergeTypesLabel, gridBagConstraints);

    mergeTypes.setToolTipText(ResourceMgr.getDescription("LblLiteralType"));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 0.5;
    gridBagConstraints.insets = new Insets(0, 4, 5, 11);
    jPanel4.add(mergeTypes, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblSqlExpType")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 0, 5, 0);
    jPanel4.add(jLabel2, gridBagConstraints);

    syntaxType.setModel(new DefaultComboBoxModel(new String[] { "INSERT", "UPDATE", "DELETE/INSERT", "MERGE", "DELETE" }));
    syntaxType.addActionListener(this);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 0.5;
    gridBagConstraints.insets = new Insets(0, 4, 5, 11);
    jPanel4.add(syntaxType, gridBagConstraints);

    blobTypesLabel.setText(ResourceMgr.getString("LblBlobType")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 0, 5, 0);
    jPanel4.add(blobTypesLabel, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 0.5;
    gridBagConstraints.insets = new Insets(0, 4, 5, 0);
    jPanel4.add(blobTypes, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(3, 0, 0, 6);
    add(jPanel4, gridBagConstraints);

    extOptionsPanel.setLayout(new GridBagLayout());

    jLabel1.setText(ResourceMgr.getString("LblUseExportTableName")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    extOptionsPanel.add(jLabel1, gridBagConstraints);

    alternateTable.setMinimumSize(new Dimension(40, 20));
    alternateTable.setPreferredSize(new Dimension(40, 20));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 7, 0, 0);
    extOptionsPanel.add(alternateTable, gridBagConstraints);

    commitLabel.setText(ResourceMgr.getString("LblExportCommitEvery")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    extOptionsPanel.add(commitLabel, gridBagConstraints);

    commitCount.setColumns(6);
    commitCount.setMinimumSize(new Dimension(32, 20));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 7, 5, 0);
    extOptionsPanel.add(commitCount, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 0, 0, 2);
    add(extOptionsPanel, gridBagConstraints);

    ignoreIdentity.setText(ResourceMgr.getString("LblIgnoreIdentity")); // NOI18N
    ignoreIdentity.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(2, 0, 7, 0);
    add(ignoreIdentity, gridBagConstraints);

    multiRowInserts.setText(ResourceMgr.getString("LblMultiRowInsert")); // NOI18N
    multiRowInserts.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(2, 0, 7, 0);
    add(multiRowInserts, gridBagConstraints);

    createTable.setText(ResourceMgr.getString("LblExportIncludeCreateTable")); // NOI18N
    createTable.setToolTipText(ResourceMgr.getString("d_LblExportIncludeCreateTable")); // NOI18N
    createTable.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(2, 0, 7, 0);
    add(createTable, gridBagConstraints);
  }

  // Code for dispatching events from components to event handlers.

  public void actionPerformed(ActionEvent evt)
  {
    if (evt.getSource() == selectKeys)
    {
      SqlOptionsPanel.this.selectKeysActionPerformed(evt);
    }
    else if (evt.getSource() == syntaxType)
    {
      SqlOptionsPanel.this.syntaxTypeActionPerformed(evt);
    }
  }// </editor-fold>//GEN-END:initComponents

private void selectKeysActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectKeysActionPerformed
  selectColumns();
}//GEN-LAST:event_selectKeysActionPerformed

  private void syntaxTypeActionPerformed(ActionEvent evt)//GEN-FIRST:event_syntaxTypeActionPerformed
  {//GEN-HEADEREND:event_syntaxTypeActionPerformed
    checkMultiRow();
  }//GEN-LAST:event_syntaxTypeActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  public JTextField alternateTable;
  public JComboBox blobTypes;
  public JLabel blobTypesLabel;
  public JTextField commitCount;
  public JLabel commitLabel;
  public JCheckBox createTable;
  public JPanel extOptionsPanel;
  public JCheckBox ignoreIdentity;
  public JLabel jLabel1;
  public JLabel jLabel2;
  public JPanel jPanel2;
  public JPanel jPanel4;
  public JComboBox literalTypes;
  public JLabel literalTypesLabel;
  public JComboBox mergeTypes;
  public JLabel mergeTypesLabel;
  public JCheckBox multiRowInserts;
  public JButton selectKeys;
  public JComboBox syntaxType;
  public ButtonGroup typeGroup;
  // End of variables declaration//GEN-END:variables

}
