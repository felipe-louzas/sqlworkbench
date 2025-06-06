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
package workbench.gui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.lnf.LnFHelper;

/**
 * Component to be used as tabComponent.
 *
 * Contains a JLabel to show the text and a JButton to close the tab it belongs to
 *
 * @author Thomas Kellerer
 */
public class TabButtonComponent
  extends JPanel
  implements ActionListener, PropertyChangeListener
{
  private final WbTabbedPane pane;
  private final JLabel label;
  private final WbButton closeButton;
  private boolean isJGoodies;

  public TabButtonComponent(String title, final WbTabbedPane tabPane, boolean showButton)
  {
    super(new BorderLayout());
    pane = tabPane;

    label = new JLabel(title)
    {
      @Override
      public Color getBackground()
      {
        return TabButtonComponent.this.getBackground();
      }
      @Override
      public Color getForeground()
      {
        return TabButtonComponent.this.getForeground();
      }
      @Override
      public Insets getInsets(Insets insets)
      {
        return WbSwingUtilities.getEmptyInsets();
      }
      @Override
      public Insets getInsets()
      {
        return WbSwingUtilities.getEmptyInsets();
      }
    };
    label.setOpaque(true);
    label.setBorder(WbSwingUtilities.EMPTY_BORDER);

    int imgSize = WbSwingUtilities.getFontHeight(label);

    BorderLayout layout = (BorderLayout)getLayout();
    layout.setHgap((int)(imgSize / 3));

    Icon img = new CloseIcon((int)(imgSize * 0.9));
    Icon rolloverImg = new CloseIcon(imgSize, Color.WHITE, Color.RED);
    Icon pressedImg = new CloseIcon(imgSize, Color.WHITE, Color.RED.darker());

    closeButton = new WbButton(img)
    {
      @Override
      public Color getBackground()
      {
        return TabButtonComponent.this.getBackground();
      }
      @Override
      public Color getForeground()
      {
        return TabButtonComponent.this.getForeground();
      }
      @Override
      public Insets getInsets(Insets insets)
      {
        return WbSwingUtilities.getEmptyInsets();
      }
      @Override
      public Insets getInsets()
      {
        return WbSwingUtilities.getEmptyInsets();
      }
    };
    closeButton.setOpaque(true);
    closeButton.setContentAreaFilled(false);
    closeButton.setBorderPainted(false);
    closeButton.setRolloverEnabled(true);
    closeButton.setIcon(img);
    closeButton.setRolloverIcon(rolloverImg);
    closeButton.setPressedIcon(pressedImg);

    closeButton.setFocusable(false);
    closeButton.addActionListener(this);

    setupComponents();
    if (!showButton)
    {
      closeButton.setVisible(showButton);
    }

    isJGoodies = LnFHelper.isJGoodies();
    setOpaque(isJGoodies);

    Settings.getInstance().addPropertyChangeListener(this, GuiSettings.PROPERTY_RESULTTAB_CLOSE_BUTTON_RIGHT);
  }


  @Override
  public Insets getInsets()
  {
    return WbSwingUtilities.getEmptyInsets();
  }

  @Override
  public Insets getInsets(Insets insets)
  {
    return WbSwingUtilities.getEmptyInsets();
  }

  @Override
  public Color getBackground()
  {
    if (!isJGoodies && super.getBackground() != null)
    {
      return super.getBackground();
    }

    Color c = null;

    if (pane != null)
    {
      if (pane.getTabComponentAt(pane.getSelectedIndex()) == this)
      {
        c = UIManager.getColor("TabbedPane.selected");
      }
      else
      {
        c = UIManager.getColor("TabbedPane.unselectedBackground");
      }
    }

    if (c == null)
    {
      c = UIManager.getColor("TabbedPane.tabAreaBackground");
    }

    if (c == null)
    {
      c = UIManager.getColor("TabbedPane.background");
    }

    if (c == null)
    {
      super.getBackground();
    }
    return c;
  }


  @Override
  public Color getForeground()
  {
    if (!isJGoodies && super.getForeground() != null)
    {
      return super.getForeground();
    }

    Color c = null;
    if (pane != null)
    {
      c = pane.getForeground();
    }
    if (c == null)
    {
      c = UIManager.getColor("TabbedPane.foreground");
    }
    if (c == null)
    {
      c = super.getForeground();
    }
    return c;
  }

  @Override
  public void setFont(Font font)
  {
    super.setFont(font);
    if (label != null)
    {
      label.setFont(font);
    }
  }

  @Override
  public void removeNotify()
  {
    super.removeNotify();
    Settings.getInstance().removePropertyChangeListener(this);
  }

  @Override
  public final void setOpaque(boolean isOpaque)
  {
    super.setOpaque(isOpaque);
    if (label != null) label.setOpaque(isOpaque);
    if (closeButton != null) closeButton.setOpaque(isOpaque);
  }

  public void setDisplayedMnemonicIndex(int index)
    throws IllegalArgumentException
  {
    label.setDisplayedMnemonicIndex(index);
  }

  public void setDisplayedMnemonic(char aChar)
  {
    label.setDisplayedMnemonic(aChar);
  }

  public void setDisplayedMnemonic(int key)
  {
    label.setDisplayedMnemonic(key);
  }

  public void setButtonVisible(boolean flag)
  {
    closeButton.setVisible(flag);
  }

  @Override
  public void setEnabled(boolean flag)
  {
    closeButton.setEnabled(flag);
  }

  public void setTitle(String title)
  {
    label.setText(title);
  }

  public void setIcon(Icon icon)
  {
    label.setIcon(icon);
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    int i = pane.indexOfTabComponent(this);
    if (i != -1)
    {
      pane.closeButtonClicked(i);
    }
  }

  private void setupComponents()
  {
    boolean buttonOnRight = GuiSettings.getShowCloseButtonOnRightSide();

    remove(label);
    remove(closeButton);

    add(label, BorderLayout.CENTER);

    if (buttonOnRight)
    {
      add(closeButton, BorderLayout.LINE_END);
    }
    else
    {
      add(closeButton, BorderLayout.LINE_START);
    }
    validate();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    setupComponents();
  }

}
