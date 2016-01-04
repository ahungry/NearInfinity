// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.spl;

import infinity.datatype.EffectType;
import infinity.datatype.ResourceRef;
import infinity.gui.ViewerUtil;
import infinity.resource.AbstractAbility;
import infinity.resource.Effect;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JPanel;

final class ViewerAbility extends JPanel
{
  private static JPanel makeFieldPanel(Ability ability)
  {
    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel fieldPanel = new JPanel(gbl);

    gbc.insets = new Insets(3, 3, 3, 3);
    ViewerUtil.addLabelFieldPair(fieldPanel, ability.getAttribute(AbstractAbility.ABILITY_TYPE), gbl, gbc, true);
    ViewerUtil.addLabelFieldPair(fieldPanel, ability.getAttribute(AbstractAbility.ABILITY_LOCATION), gbl, gbc, true);
    ViewerUtil.addLabelFieldPair(fieldPanel, ability.getAttribute(AbstractAbility.ABILITY_TARGET), gbl, gbc, true);
    ViewerUtil.addLabelFieldPair(fieldPanel, ability.getAttribute(AbstractAbility.ABILITY_RANGE), gbl, gbc, true);
    ViewerUtil.addLabelFieldPair(fieldPanel, ability.getAttribute(Ability.SPL_ABIL_MIN_LEVEL), gbl, gbc, true);
    ViewerUtil.addLabelFieldPair(fieldPanel, ability.getAttribute(Ability.SPL_ABIL_CASTING_SPEED), gbl, gbc, true);
//    ViewerUtil.addLabelFieldPair(fieldPanel, ability.getAttribute("# charges"), gbl, gbc, true);

    return fieldPanel;
  }

  ViewerAbility(Ability ability)
  {
    JPanel fieldPanel = makeFieldPanel(ability);
    JPanel effectsPanel = ViewerUtil.makeListPanel("Effects", ability, Effect.class, EffectType.EFFECT_TYPE);
    JComponent iconPanel = ViewerUtil.makeBamPanel((ResourceRef)ability.getAttribute(AbstractAbility.ABILITY_ICON), 0);

    JPanel mainPanel = new JPanel(new GridLayout(1, 3, 6, 6));
    mainPanel.add(iconPanel);
    mainPanel.add(fieldPanel);
    mainPanel.add(effectsPanel);

    GridBagLayout gbl = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    setLayout(gbl);

    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    gbl.setConstraints(mainPanel, gbc);
    add(mainPanel);
  }
}

