// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.itm;

import infinity.datatype.Bitmap;
import infinity.datatype.DecNumber;
import infinity.datatype.Flag;
import infinity.datatype.PriTypeBitmap;
import infinity.datatype.ProRef;
import infinity.datatype.ResourceRef;
import infinity.datatype.SecTypeBitmap;
import infinity.datatype.SectionCount;
import infinity.datatype.UnsignDecNumber;
import infinity.gui.StructViewer;
import infinity.resource.AbstractAbility;
import infinity.resource.AbstractStruct;
import infinity.resource.AddRemovable;
import infinity.resource.Effect;
import infinity.resource.HasAddRemovable;
import infinity.resource.HasViewerTabs;
import infinity.resource.Profile;
import infinity.resource.ResourceFactory;

import javax.swing.JComponent;

public final class Ability extends AbstractAbility implements AddRemovable, HasAddRemovable, HasViewerTabs
{
  // ITM/Ability-specific field labels (more fields defined in AbstractAbility)
  public static final String ITM_ABIL                     = "Item ability";
  public static final String ITM_ABIL_IDENTIFY_TO_USE     = "Identify to use?";
  public static final String ITM_ABIL_DICE_SIZE_ALT       = "Alternate dice size";
  public static final String ITM_ABIL_LAUNCHER_REQUIRED   = "Launcher required";
  public static final String ITM_ABIL_DICE_COUNT_ALT      = "Alternate # dice thrown";
  public static final String ITM_ABIL_SPEED               = "Speed";
  public static final String ITM_ABIL_DAMAGE_BONUS_ALT    = "Alternate damage bonus";
  public static final String ITM_ABIL_PRIMARY_TYPE        = "Primary type (school)";
  public static final String ITM_ABIL_SECONDARY_TYPE      = "Secondary type";
  public static final String ITM_ABIL_WHEN_DRAINED        = "When drained";
  public static final String ITM_ABIL_FLAGS               = "Flags";
  public static final String ITM_ABIL_ANIM_OVERHAND       = "Animation: Overhand swing %";
  public static final String ITM_ABIL_ANIM_BACKHAND       = "Animation: Backhand swing %";
  public static final String ITM_ABIL_ANIM_THRUST         = "Animation: Thrust %";
  public static final String ITM_ABIL_IS_ARROW            = "Is arrow?";
  public static final String ITM_ABIL_IS_BOLT             = "Is bolt?";
  public static final String ITM_ABIL_IS_BULLET           = "Is bullet?";

  public static final String[] s_noyes = {"No", "Yes"};
  public static final String[] s_drain = {"Item remains", "Item vanishes", "Replace with used up", "Item recharges"};
  public static final String[] s_launcher = {"None", "Bow", "Crossbow", "Sling"};
  public static final String[] s_abilityuse = {"", "Weapon slots", "", "Item slots", "Gem?"};
  public static final String[] s_recharge = {
    "No flags set", "Add strength bonus", "Breakable", "EE: Damage strength bonus",
    "EE: THAC0 strength bonus", "", "", "", "", "", "",
    "Hostile", "Recharge after resting", "", "", "", "", "Bypass armor", "Keen edge", "",
    "", "", "", "", "", "", "Ex: Toggle backstab", "Ex: Cannot target invisible"};

  Ability() throws Exception
  {
    super(null, ITM_ABIL, new byte[56], 0);
  }

  Ability(AbstractStruct superStruct, byte buffer[], int offset, int number) throws Exception
  {
    super(superStruct, ITM_ABIL + " " + number, buffer, offset);
  }

// --------------------- Begin Interface HasAddRemovable ---------------------

  @Override
  public AddRemovable[] getAddRemovables() throws Exception
  {
    return new AddRemovable[]{new Effect()};
  }

// --------------------- End Interface HasAddRemovable ---------------------


//--------------------- Begin Interface AddRemovable ---------------------

  @Override
  public boolean canRemove()
  {
    return true;
  }

//--------------------- End Interface AddRemovable ---------------------


// --------------------- Begin Interface HasViewerTabs ---------------------

  @Override
  public int getViewerTabCount()
  {
    return 1;
  }

  @Override
  public String getViewerTabName(int index)
  {
    return StructViewer.TAB_VIEW;
  }

  @Override
  public JComponent getViewerTab(int index)
  {
    return new ViewerAbility(this);
  }

  @Override
  public boolean viewerTabAddedBefore(int index)
  {
    return true;
  }

// --------------------- End Interface HasViewerTabs ---------------------

  @Override
  public int read(byte buffer[], int offset) throws Exception
  {
    if (Profile.getEngine() == Profile.Engine.BG2 || Profile.isEnhancedEdition()) {
      addField(new Bitmap(buffer, offset, 1, ABILITY_TYPE, s_type));
      addField(new Bitmap(buffer, offset + 1, 1, ITM_ABIL_IDENTIFY_TO_USE, s_noyes));
      addField(new Bitmap(buffer, offset + 2, 1, ABILITY_LOCATION, s_abilityuse));
      addField(new DecNumber(buffer, offset + 3, 1, ITM_ABIL_DICE_SIZE_ALT));
      addField(new ResourceRef(buffer, offset + 4, ABILITY_ICON, "BAM"));
      addField(new Bitmap(buffer, offset + 12, 1, ABILITY_TARGET, s_targettype));
      addField(new UnsignDecNumber(buffer, offset + 13, 1, ABILITY_NUM_TARGETS));
      addField(new DecNumber(buffer, offset + 14, 2, ABILITY_RANGE));
      addField(new Bitmap(buffer, offset + 16, 1, ITM_ABIL_LAUNCHER_REQUIRED, s_launcher));
      addField(new DecNumber(buffer, offset + 17, 1, ITM_ABIL_DICE_COUNT_ALT));
      addField(new DecNumber(buffer, offset + 18, 1, ITM_ABIL_SPEED));
      addField(new DecNumber(buffer, offset + 19, 1, ITM_ABIL_DAMAGE_BONUS_ALT));
      addField(new DecNumber(buffer, offset + 20, 2, ABILITY_HIT_BONUS));
      addField(new DecNumber(buffer, offset + 22, 1, ABILITY_DICE_SIZE));
      addField(new PriTypeBitmap(buffer, offset + 23, 1, ITM_ABIL_PRIMARY_TYPE));
      addField(new DecNumber(buffer, offset + 24, 1, ABILITY_DICE_COUNT));
      addField(new SecTypeBitmap(buffer, offset + 25, 1, ITM_ABIL_SECONDARY_TYPE));
    }
    else {
      addField(new Bitmap(buffer, offset, 1, ABILITY_TYPE, s_type));
      addField(new Bitmap(buffer, offset + 1, 1, ITM_ABIL_IDENTIFY_TO_USE, s_noyes));
      addField(new Bitmap(buffer, offset + 2, 2, ABILITY_LOCATION, s_abilityuse));
      addField(new ResourceRef(buffer, offset + 4, ABILITY_ICON, "BAM"));
      addField(new Bitmap(buffer, offset + 12, 2, ABILITY_TARGET, s_targettype));
      addField(new DecNumber(buffer, offset + 14, 2, ABILITY_RANGE));
      addField(new Bitmap(buffer, offset + 16, 2, ITM_ABIL_LAUNCHER_REQUIRED, s_launcher));
      addField(new DecNumber(buffer, offset + 18, 2, ITM_ABIL_SPEED));
      addField(new DecNumber(buffer, offset + 20, 2, ABILITY_HIT_BONUS));
      addField(new DecNumber(buffer, offset + 22, 2, ABILITY_DICE_SIZE));
      addField(new DecNumber(buffer, offset + 24, 2, ABILITY_DICE_COUNT));
    }
    addField(new DecNumber(buffer, offset + 26, 2, ABILITY_DAMAGE_BONUS));
    addField(new Bitmap(buffer, offset + 28, 2, ABILITY_DAMAGE_TYPE, s_dmgtype));
    addField(new SectionCount(buffer, offset + 30, 2, ABILITY_NUM_EFFECTS, Effect.class));
    addField(new DecNumber(buffer, offset + 32, 2, ABILITY_FIRST_EFFECT_INDEX));
    addField(new DecNumber(buffer, offset + 34, 2, ABILITY_NUM_CHARGES));
    addField(new Bitmap(buffer, offset + 36, 2, ITM_ABIL_WHEN_DRAINED, s_drain));
    addField(new Flag(buffer, offset + 38, 4, ITM_ABIL_FLAGS, s_recharge));
    if (ResourceFactory.resourceExists("PROJECTL.IDS")) {
      addField(new ProRef(buffer, offset + 42, ABILITY_PROJECTILE));
    } else if (Profile.getEngine() == Profile.Engine.PST) {
      addField(new Bitmap(buffer, offset + 42, 2, ABILITY_PROJECTILE, s_proj_pst));
    } else if (Profile.getEngine() == Profile.Engine.IWD || Profile.getEngine() == Profile.Engine.IWD2) {
      addField(new Bitmap(buffer, offset + 42, 2, ABILITY_PROJECTILE, s_proj_iwd));
    } else {
      addField(new Bitmap(buffer, offset + 42, 2, ABILITY_PROJECTILE, s_projectile));
    }
    addField(new DecNumber(buffer, offset + 44, 2, ITM_ABIL_ANIM_OVERHAND));
    addField(new DecNumber(buffer, offset + 46, 2, ITM_ABIL_ANIM_BACKHAND));
    addField(new DecNumber(buffer, offset + 48, 2, ITM_ABIL_ANIM_THRUST));
    addField(new Bitmap(buffer, offset + 50, 2, ITM_ABIL_IS_ARROW, s_noyes));
    addField(new Bitmap(buffer, offset + 52, 2, ITM_ABIL_IS_BOLT, s_noyes));
    addField(new Bitmap(buffer, offset + 54, 2, ITM_ABIL_IS_BULLET, s_noyes));

    return offset + 56;
  }
}

