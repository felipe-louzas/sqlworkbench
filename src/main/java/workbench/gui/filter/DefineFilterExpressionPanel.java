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
package workbench.gui.filter;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import workbench.interfaces.ValidatingComponent;
import workbench.interfaces.ValueProvider;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ExtensionFileFilter;
import workbench.gui.components.FlatButton;
import workbench.gui.components.ValidatingDialog;
import workbench.gui.components.WbFileChooser;
import workbench.gui.components.WbTable;
import workbench.gui.components.WbToolbar;
import workbench.gui.sql.EditorPanel;

import workbench.storage.DataStore;
import workbench.storage.DataStoreValueProvider;
import workbench.storage.ResultInfo;
import workbench.storage.filter.AndExpression;
import workbench.storage.filter.ColumnComparator;
import workbench.storage.filter.ComplexExpression;
import workbench.storage.filter.ExpressionValue;
import workbench.storage.filter.FilterExpression;
import workbench.storage.filter.OrExpression;
import workbench.storage.filter.SQLFilterGenerator;

import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A Panel to display a filter dialog for a {@link workbench.storage.DataStore}
 * @author Thomas Kellerer
 */
public class DefineFilterExpressionPanel
  extends JPanel
  implements ActionListener, ValidatingComponent
{
  private final ValueProvider data;
  private final List<PanelEntry> panels = new ArrayList<>();
  private final JButton addLineButton;
  private final JRadioButton andButton;
  private final JRadioButton orButton;
  private final JPanel expressions;
  private final JScrollPane scroll;
  private final JButton saveButton = new JButton();
  private final JButton loadButton = new JButton();
  private final JButton generateSQL = new JButton();
  private final FilterDefinitionManager filterMgr;
  private final WbConnection dbConnection;

  public DefineFilterExpressionPanel(ValueProvider source, FilterDefinitionManager filterManager, WbConnection conn)
  {
    super();
    data = source;
    expressions = new JPanel();
    this.expressions.setLayout(new GridBagLayout());
    filterMgr = filterManager;
    this.dbConnection = conn;

    this.setLayout(new BorderLayout(0,2));

    Insets emptyInsets = new Insets(0,0,0,0);
    orButton = new JRadioButton(ResourceMgr.getString("LblFilterOrOption"));
    orButton.setToolTipText(ResourceMgr.getDescription("LblFilterOrOption"));
    orButton.setMargin(emptyInsets);
    andButton = new JRadioButton(ResourceMgr.getString("LblFilterAndOption"));
    andButton.setToolTipText(ResourceMgr.getDescription("LblFilterAndOption"));
    andButton.setMargin(emptyInsets);
    ButtonGroup g = new ButtonGroup();
    g.add(orButton);
    g.add(andButton);
    andButton.setSelected(true);

    JPanel radioPanel = new JPanel();
    radioPanel.setLayout(new FlowLayout(FlowLayout.RIGHT,0,0));
    radioPanel.add(andButton);
    radioPanel.add(orButton);

    JPanel p = new JPanel();
    p.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.EAST;
    c.weighty = 0;
    c.weightx = 0;

    WbToolbar bar = new WbToolbar();
    bar.setBorder(WbSwingUtilities.createLineBorder(p));

    loadButton.setIcon(IconMgr.getInstance().getLabelIcon("Open"));
    loadButton.setMargin(emptyInsets);
    loadButton.setToolTipText(ResourceMgr.getDescription("MnuTxtLoadFilter"));

    saveButton.setIcon(IconMgr.getInstance().getLabelIcon(IconMgr.IMG_SAVE));
    saveButton.setMargin(new Insets(0,4,0,4));
    saveButton.setToolTipText(ResourceMgr.getDescription("SaveFilter"));

    //generateSQL.setText(ResourceMgr.getString("LblFilterGenSQL"));
    generateSQL.setIcon(IconMgr.getInstance().getLabelIcon("gen-sql"));
    generateSQL.setToolTipText(ResourceMgr.getDescription("LblFilterGenSQL"));
    generateSQL.setMargin(new Insets(0,4,0,0));

    loadButton.addActionListener(this);
    saveButton.addActionListener(this);
    generateSQL.addActionListener(this);

    bar.add(loadButton);
    bar.add(saveButton);
    bar.addSeparator();
    bar.add(generateSQL);

    p.add(bar, c);

    c.anchor = GridBagConstraints.EAST;
    c.gridx ++;
    c.weightx = 1;
    p.add(radioPanel, c);

    addLineButton = new FlatButton(ResourceMgr.getString("LblFilterAddLine"));
    Dimension d = radioPanel.getPreferredSize();
    int ph = (int)d.getHeight();
    addLineButton.setMinimumSize(new Dimension(25,ph));
    d = addLineButton.getPreferredSize();
    addLineButton.setPreferredSize(new Dimension((int)d.getWidth(),ph));
    addLineButton.addActionListener(this);
    c.gridx++;
    c.anchor = GridBagConstraints.NORTHEAST;
    c.weightx = 1;
    c.insets = new Insets(0,15,0,2);
    p.add(addLineButton);


    this.add(p, BorderLayout.NORTH);
    scroll = new JScrollPane(this.expressions);
    scroll.setBorder(BorderFactory.createEtchedBorder());
    this.add(scroll, BorderLayout.CENTER);
    d = addExpressionPanel();
    scroll.getVerticalScrollBar().setUnitIncrement((int)d.getHeight());
    scroll.getHorizontalScrollBar().setUnitIncrement(25);

    JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JLabel infoLabel = new JLabel(ResourceMgr.getString("MsgFilterHelp"));
    infoPanel.add(infoLabel);
    this.add(infoPanel, BorderLayout.SOUTH);

    double w = d.getWidth() + scroll.getHorizontalScrollBar().getPreferredSize().getWidth();
    this.expressions.setPreferredSize(new Dimension((int)w, (int)(d.height * 3)));
  }

  private void saveFilter()
  {
    FilterExpression filter = this.getExpression();
    if (filter == null)
    {
      WbSwingUtilities.showMessageKey(this, "ErrFilterNotPresent");
      return;
    }

    String lastDir = filterMgr.getLastFilterDir();
    FileFilter ff = ExtensionFileFilter.getXmlFileFilter();
    JFileChooser fc = new WbFileChooser(lastDir);
    fc.setAcceptAllFileFilterUsed(false);
    fc.resetChoosableFileFilters();
    fc.addChoosableFileFilter(ff);
    int answer = fc.showSaveDialog(SwingUtilities.getWindowAncestor(this));
    if (answer == JFileChooser.APPROVE_OPTION)
    {
      String file = fc.getSelectedFile().getAbsolutePath();
      if (!file.toLowerCase().endsWith(".xml"))
      {
        file = file + ".xml";
      }
      String dir = fc.getCurrentDirectory().getAbsolutePath();
      filterMgr.setLastFilterDir(dir);
      try
      {
        WbFile f = new WbFile(file);
        filterMgr.saveFilter(filter, f);
      }
      catch (IOException e)
      {
        String msg = ResourceMgr.getString("ErrLoadingFilter");
        msg = msg + "\n" + ExceptionUtil.getDisplay(e);
        WbSwingUtilities.showErrorMessage(this, msg);
      }
    }
  }

  public static FilterExpression loadFilter(JComponent parent, FilterDefinitionManager filterManager)
  {
    String lastDir = filterManager.getLastFilterDir();
    FileFilter ff = ExtensionFileFilter.getXmlFileFilter();
    JFileChooser fc = new WbFileChooser(lastDir);
    fc.setAcceptAllFileFilterUsed(false);
    fc.resetChoosableFileFilters();
    fc.addChoosableFileFilter(ff);
    int answer = fc.showOpenDialog(SwingUtilities.getWindowAncestor(parent));
    if (answer == JFileChooser.APPROVE_OPTION)
    {
      String file = fc.getSelectedFile().getAbsolutePath();
      String dir = fc.getCurrentDirectory().getAbsolutePath();
      filterManager.setLastFilterDir(dir);
      try
      {
        return filterManager.loadFilter(file);
      }
      catch (Exception e)
      {
        String msg = ResourceMgr.getString("ErrLoadingFilter");
        msg = msg + "\n" + ExceptionUtil.getDisplay(e);
        WbSwingUtilities.showErrorMessage(parent, msg);
      }
    }
    return null;
  }

  private void loadFilter()
  {
    FilterExpression f = loadFilter(this, filterMgr);
    if (f != null)
    {
      this.setFilter(f);
    }
  }

  public void selectColumn(String col)
  {
    if (panels.isEmpty()) return;
    panels.get(0).expressionPanel.selectColumn(col);
  }

  private void removeAllPanels()
  {
    this.panels.clear();
    this.expressions.removeAll();
  }

  public void setFilter(FilterExpression filter)
  {
    if (filter == null) return;

    if (filter instanceof AndExpression)
    {
      this.andButton.setSelected(true);
    }
    else if (filter instanceof OrExpression)
    {
      this.orButton.setSelected(true);
    }
    else
    {
      return;
    }
    removeAllPanels();
    ComplexExpression cExp = (ComplexExpression) filter;
    List<FilterExpression> expList = cExp.getExpressions();
    int count = expList.size();
    int height = 0;
    int addHeight = 0;
    for (int i=0; i < count; i++)
    {
      try
      {
        ExpressionValue exp = (ExpressionValue)expList.get(i);
        Dimension panelSize = this.addExpressionPanel(exp);
        PanelEntry item = this.panels.get(this.panels.size() - 1);
        ColumnExpressionPanel panel = item.expressionPanel;
        panel.setExpressionValue(exp);
        addHeight = panelSize.height;
        if (i <= 15)
        {
          height += panelSize.height;
        }
      }
      catch (ClassCastException e)
      {
        // ignore this as we cannot handle other expressions anyway...
      }
    }
    // add two empty lines
    height += (addHeight * 2);
    Dimension preferred = new Dimension(expressions.getPreferredSize().width, (int)(height * 1.15));
    this.expressions.setPreferredSize(preferred);
    WbSwingUtilities.repaintLater(this);
  }

  @Override
  public boolean validateInput()
  {
    for (PanelEntry entry : panels)
    {
      ColumnComparator comp = entry.expressionPanel.getComparator();
      if (comp == null)
      {
        String msg = ResourceMgr.getString("ErrFilterNoComparator");
        WbSwingUtilities.showErrorMessage(this, msg);
        return false;
      }

      if (!entry.expressionPanel.validateInput())
      {
        String msg = ResourceMgr.getString("ErrFilterWrongValue");
        msg = StringUtil.replace(msg, "%value%", entry.expressionPanel.getInputValue());
        msg = StringUtil.replace(msg, "%op%", comp.getOperator());
        WbSwingUtilities.showErrorMessage(this, msg);
        return false;
      }
    }
    return true;
  }

  private void showSQLCondition()
  {
    String sql = createSQLExpression();
    if (sql == null) return;

    WbSwingUtilities.invokeLater(() ->
    {
      EditorPanel editor = EditorPanel.createSqlEditor();
      editor.setText(sql);
      Dialog dlg = (Dialog)SwingUtilities.getWindowAncestor(this);
      ValidatingDialog d = new ValidatingDialog(dlg, "SQL", editor, new String[] { ResourceMgr.getString("LblClose") }, false);
      d.setCancelOption(0);
      if (!Settings.getInstance().restoreWindowSize(d, "workbench.filterexpression.sql.dialog"))
      {
        d.setSize(500, 350);
      }
      WbSwingUtilities.center(d,dlg);
      d.setVisible(true);
      Settings.getInstance().storeWindowSize(d, "workbench.filterexpression.sql.dialog");
    });
  }

  private String createSQLExpression()
  {
    List<String> conditions = new ArrayList<>();
    SQLFilterGenerator generator = new SQLFilterGenerator(dbConnection);

    DBID id = DBID.fromConnection(dbConnection);
    List<ColumnIdentifier> columns = this.data.getResultInfo().getColumnList();
    for (PanelEntry entry : panels)
    {
      ExpressionValue expression = entry.expressionPanel.getExpressionValue();
      String filterColumn = expression.getColumnName();
      if ("*".equals(filterColumn))
      {
        String sql = generator.getSQLConditionForAll(expression.getComparator(), columns, expression.getFilterValue(), expression.isIgnoreCase());
        if (StringUtil.isNotBlank(sql))
        {
          conditions.add(sql);
        }
      }
      else
      {
        ColumnIdentifier column = ColumnIdentifier.findColumnInList(columns, expression.getColumnName());
        String sql = generator.getSQLCondition(expression.getComparator(), column, expression.getFilterValue(), expression.isIgnoreCase());
        if (StringUtil.isNotBlank(sql))
        {
          conditions.add(sql);
        }
        else
        {
          conditions.add(
            "-- No SQL could be generated for column " +  column.getColumnName()
            + " and operator " + expression.getComparator().getUserDisplay() + " (" + id.getProductName() + ")");
        }
      }
    }

    String operator = andButton.isSelected() ? "\nAND " : "\nOR ";
    String result = conditions.
      stream().
      collect(Collectors.joining(operator));
    return result;
  }

  public FilterExpression getExpression()
  {
    if (panels.isEmpty()) return null;

    ComplexExpression exp = andButton.isSelected() ? new AndExpression() : new OrExpression();
    for (PanelEntry entry : panels)
    {
      FilterExpression f = (FilterExpression)entry.expressionPanel.getExpressionValue();
      if (f != null)
      {
        exp.addExpression(f);
      }
    }
    return exp;
  }

  private Dimension addExpressionPanel()
  {
    return addExpressionPanel(null);
  }

  private Dimension addExpressionPanel(ExpressionValue filter)
  {
    final ColumnExpressionPanel exp = new ColumnExpressionPanel(data, filter);
    JButton b = new FlatButton(IconMgr.getInstance().getLabelIcon("delete"));
    b.setPreferredSize(new Dimension(21,21));
    b.addActionListener(this);
    JPanel p = new JPanel(new GridBagLayout());
    GridBagConstraints c1 = new GridBagConstraints();
    c1.gridx = 0;
    c1.gridy = 0;
    c1.fill = GridBagConstraints.HORIZONTAL;
    c1.anchor = GridBagConstraints.WEST;
    c1.weighty = 0.0;
    c1.weightx = 1.0;
    p.add(exp, c1);

    c1.gridx++;
    c1.weightx = 0.0;
    c1.fill = GridBagConstraints.NONE;
    p.add(b, c1);

    PanelEntry item = new PanelEntry(p, exp);
    b.putClientProperty("panel", item);
    this.panels.add(item);
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = GridBagConstraints.RELATIVE;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.weighty = 1.0;
    c.weightx = 1.0;

    GridBagLayout l = (GridBagLayout)expressions.getLayout();
    for (PanelEntry entry : this.panels)
    {
      GridBagConstraints cons = l.getConstraints(entry.container);
      cons.weighty = 0;
      l.setConstraints(entry.container, cons);
    }
    this.expressions.add(p,c);
    //this.invalidate();
    this.validate();
    WbSwingUtilities.repaintLater(this);

    Dimension ps = exp.getPreferredSize();
    Dimension bs = b.getPreferredSize();
    Dimension prefSize = new Dimension((int)(ps.getWidth() + bs.getWidth()), (int)ps.getHeight());
    return prefSize;
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == saveButton)
    {
      saveFilter();
    }
    else if (e.getSource() == loadButton)
    {
      loadFilter();
    }
    else if (e.getSource() == addLineButton)
    {
      addExpressionPanel();
    }
    else if (e.getSource() == generateSQL)
    {
      showSQLCondition();
    }
    else if (e.getSource() instanceof JButton)
    {
      JButton button = (JButton)e.getSource();
      PanelEntry entry = (PanelEntry)button.getClientProperty("panel");
      //entry.expressionPanel.removeChangeListener(this);
      this.panels.remove(entry);
      this.expressions.remove(entry.container);
      GridBagLayout l = (GridBagLayout)expressions.getLayout();
      int count = this.panels.size();
      for (int i=0; i < count; i++)
      {
        entry = panels.get(i);
        GridBagConstraints cons = l.getConstraints(entry.container);
        if (i < count - 1)
          cons.weighty = 0;
        else
          cons.weighty = 1.0;
        l.setConstraints(entry.container, cons);
      }
      this.invalidate();
      this.validate();
      this.repaint();
    }
  }

  public static void showDialog(WbTable source, FilterDefinitionManager filterMgr)
  {
    DataStore ds = source.getDataStore();
    if (ds == null) return;
    ResultInfo info = ds.getResultInfo();
    if (info == null) return;

    ValueProvider data = new DataStoreValueProvider(ds);
    WbSwingUtilities.invoke(() ->
    {
      DefineFilterExpressionPanel panel = new DefineFilterExpressionPanel(data, filterMgr, ds.getOriginalConnection());
      int col = source.getSelectedColumn();

      FilterExpression lastFilter = source.getLastFilter();
      if (lastFilter != null)
      {
        panel.setFilter(lastFilter);
        panel.doLayout();
      }
      else if (col > -1)
      {
        String colname = info.getColumnName(col);
        panel.selectColumn(colname);
      }
      String title = ResourceMgr.getString("MsgFilterWindowTitle");
      boolean ok = ValidatingDialog.showConfirmDialog(SwingUtilities.getWindowAncestor(source), panel, title);
      if (ok)
      {
        FilterExpression filter = panel.getExpression();
        source.applyFilter(filter);
      }
    });
  }

  public void stateChanged(javax.swing.event.ChangeEvent changeEvent)
  {
    FilterExpression e = this.getExpression();
    this.saveButton.setEnabled(e != null);
  }

  @Override
  public void componentDisplayed()
  {
    if (this.panels.isEmpty()) return;
    PanelEntry entry = panels.get(0);
    if (entry == null || entry.expressionPanel == null) return;
    entry.expressionPanel.setFocusToColumn();
  }

  @Override
  public void componentWillBeClosed()
  {
    // nothing to do
  }
}

class PanelEntry
{
  final JPanel container;
  final ColumnExpressionPanel expressionPanel;
  PanelEntry(JPanel p, ColumnExpressionPanel ep)
  {
    container = p;
    expressionPanel = ep;
  }
}

