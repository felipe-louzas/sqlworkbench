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
package workbench.gui.dialogs.dataimport;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.importer.TextImportOptions;

import workbench.gui.dialogs.QuoteEscapeSelector;

import workbench.util.QuoteEscapeType;
import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class TextOptionsPanel
  extends JPanel
  implements TextImportOptions, PropertyChangeListener
{
  public TextOptionsPanel()
  {
    super();
    initComponents();
    decode.addPropertyChangeListener(this);
    headerIncluded.addPropertyChangeListener(this);

    Font font = decimalCharTextField.getFont();
    if (font != null)
    {
      FontMetrics fm = decimalCharTextField.getFontMetrics(font);
      if (fm != null)
      {
        int width = fm.charWidth('M');
        int height = fm.getHeight();
        Dimension min = new Dimension(width * 3, height);
        decimalCharTextField.setMinimumSize(min);
        delimiter.setMinimumSize(min);
        quoteChar.setMinimumSize(min);
      }
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
  }

  public void saveSettings()
  {
    saveSettings("text");
  }

  public void saveSettings(String key)
  {
    Settings s = Settings.getInstance();
    s.setProperty("workbench.import."  + key + ".containsheader", this.getContainsHeader());
    s.setProperty("workbench.import." + key + ".decode", this.getDecode());
    s.setDelimiter("workbench.import." + key + ".fielddelimiter", getTextDelimiter());
    s.setProperty("workbench.import." + key + ".quotechar", this.getTextQuoteChar());
    s.setProperty("workbench.import." + key + ".decimalchar", this.getDecimalChar());
    s.setProperty("workbench.import." + key + ".quote.escape", getQuoteEscaping().toString());
    s.setProperty("workbench.import." + key + ".quotealways", this.getQuoteAlways());
    s.setProperty("workbench.import." + key + ".nullstring", this.getNullString());
    s.setProperty("workbench.import." + key + ".emptystringnull", this.getEmptyStringIsNull());
  }

  public void restoreSettings()
  {
    restoreSettings("text");
  }

  public void restoreSettings(String key)
  {
    Settings s = Settings.getInstance();
    this.setContainsHeader(s.getBoolProperty("workbench.import." + key + ".containsheader", true));
    this.setDecode(s.getBoolProperty("workbench.import." + key + ".decode", false));
    this.setTextQuoteChar(s.getProperty("workbench.import." + key + ".quotechar", s.getQuoteChar()));
    this.quoteAlways.setSelected(s.getBoolProperty("workbench.import." + key + ".quotealways", false));
    this.setTextDelimiter(s.getDelimiter("workbench.import." + key + ".fielddelimiter", "\\t", true));
    this.setDecimalChar(s.getProperty("workbench.import." + key + ".decimalchar", "."));
    this.setNullString(s.getProperty("workbench.import." + key + ".nullstring", ""));
    this.setEmptyStringIsNull(s.getBoolProperty("workbench.import." + key + ".emptystringsnull", false));
    String quote = s.getProperty("workbench.import." + key + ".quote.escape", "none");
    QuoteEscapeType escape = null;
    try
    {
      escape = QuoteEscapeType.valueOf(quote);
    }
    catch (Exception e)
    {
      escape = QuoteEscapeType.none;
    }
    ((QuoteEscapeSelector)escapeSelect).setEscapeType(escape);
  }

  public void fromOptions(TextImportOptions options)
  {
    if (options == null) return;

    quoteAlways.setSelected(options.getQuoteAlways());
    setTextQuoteChar(options.getTextQuoteChar());
    setContainsHeader(options.getContainsHeader());
    setDecode(options.getDecode());
    setDecimalChar(options.getDecimalChar());
    setTextDelimiter(options.getTextDelimiter());
    ((QuoteEscapeSelector)escapeSelect).setEscapeType(options.getQuoteEscaping());
    setNullString(options.getNullString());
    setEmptyStringIsNull(options.getEmptyStringIsNull());
  }

  @Override
  public void setEmptyStringIsNull(boolean flag)
  {
    emptyStringIsNull.setSelected(flag);
  }

  @Override
  public boolean getEmptyStringIsNull()
  {
    return emptyStringIsNull.isSelected();
  }

  @Override
  public boolean getQuoteAlways()
  {
    return this.quoteAlways.isSelected();
  }

  @Override
  public QuoteEscapeType getQuoteEscaping()
  {
    return ((QuoteEscapeSelector)escapeSelect).getEscapeType();
  }

  @Override
  public boolean getDecode()
  {
    return this.decode.isSelected();
  }

  @Override
  public void setDecode(boolean flag)
  {
    this.decode.setSelected(flag);
  }

  @Override
  public boolean getContainsHeader()
  {
    return this.headerIncluded.isSelected();
  }

  @Override
  public String getTextDelimiter()
  {
    return this.delimiter.getText();
  }

  @Override
  public String getTextQuoteChar()
  {
    return this.quoteChar.getText();
  }

  public void disableHeaderSelection()
  {
    this.headerIncluded.setEnabled(false);
  }
  @Override
  public void setContainsHeader(boolean flag)
  {
    this.headerIncluded.setSelected(flag);
  }

  @Override
  public void setTextDelimiter(String delim)
  {
    this.delimiter.setText(delim);
  }

  @Override
  public void setTextQuoteChar(String quote)
  {
    this.quoteChar.setText(quote);
  }

  @Override
  public String getDecimalChar()
  {
    String s = this.decimalCharTextField.getText();
    if (StringUtil.isBlank(s)) return ".";
    return s.trim();
  }

  @Override
  public void setDecimalChar(String s)
  {
    this.decimalCharTextField.setText(s);
  }

  @Override
  public String getNullString()
  {
    return nullString.getText();
  }

  @Override
  public void setNullString(String value)
  {
    nullString.setText(StringUtil.trim(value));
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    java.awt.GridBagConstraints gridBagConstraints;

    delimiterLabel = new javax.swing.JLabel();
    delimiter = new javax.swing.JTextField();
    headerIncluded = new javax.swing.JCheckBox();
    quoteCharLabel = new javax.swing.JLabel();
    quoteChar = new javax.swing.JTextField();
    decode = new javax.swing.JCheckBox();
    decimalCharLabel = new javax.swing.JLabel();
    decimalCharTextField = new javax.swing.JTextField();
    quoteAlways = new javax.swing.JCheckBox();
    jLabel1 = new javax.swing.JLabel();
    escapeSelect = new QuoteEscapeSelector();
    emptyStringIsNull = new javax.swing.JCheckBox();
    nullString = new javax.swing.JTextField();
    jLabel2 = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());

    delimiterLabel.setText(ResourceMgr.getString("LblFieldDelimiter")); // NOI18N
    delimiterLabel.setToolTipText(ResourceMgr.getString("d_LblFieldDelimiter")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 4, 0, 4);
    add(delimiterLabel, gridBagConstraints);

    delimiter.setPreferredSize(new java.awt.Dimension(32, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 4);
    add(delimiter, gridBagConstraints);

    headerIncluded.setText(ResourceMgr.getString("LblImportIncludeHeaders")); // NOI18N
    headerIncluded.setToolTipText(ResourceMgr.getString("d_LblImportIncludeHeaders")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    add(headerIncluded, gridBagConstraints);

    quoteCharLabel.setText(ResourceMgr.getString("LblQuoteChar")); // NOI18N
    quoteCharLabel.setToolTipText(ResourceMgr.getString("d_LblQuoteChar")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 4, 0, 4);
    add(quoteCharLabel, gridBagConstraints);

    quoteChar.setPreferredSize(new java.awt.Dimension(32, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 4);
    add(quoteChar, gridBagConstraints);

    decode.setText(ResourceMgr.getString("LblImportDecode")); // NOI18N
    decode.setToolTipText(ResourceMgr.getString("d_LblImportDecode")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    add(decode, gridBagConstraints);

    decimalCharLabel.setText(ResourceMgr.getString("LblImportDecimalChar")); // NOI18N
    decimalCharLabel.setToolTipText(ResourceMgr.getString("d_LblImportDecimalChar")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(3, 4, 0, 4);
    add(decimalCharLabel, gridBagConstraints);

    decimalCharTextField.setToolTipText(ResourceMgr.getDescription("LblImportDecimalChar"));
    decimalCharTextField.setMinimumSize(new java.awt.Dimension(32, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
    add(decimalCharTextField, gridBagConstraints);

    quoteAlways.setText(ResourceMgr.getString("LblExportQuoteAlways")); // NOI18N
    quoteAlways.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(9, 4, 0, 0);
    add(quoteAlways, gridBagConstraints);

    jLabel1.setText(ResourceMgr.getString("LblQuoteEsc")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 4, 0, 4);
    add(jLabel1, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 4);
    add(escapeSelect, gridBagConstraints);

    emptyStringIsNull.setText(ResourceMgr.getString("LblEmptyStringIsNull")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(emptyStringIsNull, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 4);
    add(nullString, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblNullString")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 4, 0, 4);
    add(jLabel2, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JLabel decimalCharLabel;
  private javax.swing.JTextField decimalCharTextField;
  private javax.swing.JCheckBox decode;
  private javax.swing.JTextField delimiter;
  private javax.swing.JLabel delimiterLabel;
  private javax.swing.JCheckBox emptyStringIsNull;
  private javax.swing.JComboBox escapeSelect;
  private javax.swing.JCheckBox headerIncluded;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JTextField nullString;
  private javax.swing.JCheckBox quoteAlways;
  private javax.swing.JTextField quoteChar;
  private javax.swing.JLabel quoteCharLabel;
  // End of variables declaration//GEN-END:variables

}
