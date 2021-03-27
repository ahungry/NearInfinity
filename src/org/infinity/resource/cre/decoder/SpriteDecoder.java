// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2021 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.cre.decoder;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.infinity.datatype.IsNumeric;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.cre.decoder.util.AnimationInfo;
import org.infinity.resource.cre.decoder.util.ColorInfo;
import org.infinity.resource.cre.decoder.util.CreatureInfo;
import org.infinity.resource.cre.decoder.util.CycleDef;
import org.infinity.resource.cre.decoder.util.DecoderAttribute;
import org.infinity.resource.cre.decoder.util.DirDef;
import org.infinity.resource.cre.decoder.util.Direction;
import org.infinity.resource.cre.decoder.util.FrameInfo;
import org.infinity.resource.cre.decoder.util.ItemInfo;
import org.infinity.resource.cre.decoder.util.SegmentDef;
import org.infinity.resource.cre.decoder.util.SeqDef;
import org.infinity.resource.cre.decoder.util.Sequence;
import org.infinity.resource.cre.decoder.util.SpriteUtils;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.PseudoBamDecoder;
import org.infinity.resource.graphics.BamV1Decoder.BamV1Control;
import org.infinity.resource.graphics.BlendingComposite;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IniMap;
import org.infinity.util.IniMapSection;
import org.infinity.util.Misc;
import org.infinity.util.tuples.Couple;

/**
 * Specialized BAM decoder for creature animation sprites.
 */
public abstract class SpriteDecoder extends PseudoBamDecoder
{
  // List of general creature animation attributes
  public static final DecoderAttribute KEY_ANIMATION_TYPE     = DecoderAttribute.with("animation_type", DecoderAttribute.DataType.USERDEFINED);
  public static final DecoderAttribute KEY_ANIMATION_SECTION  = DecoderAttribute.with("animation_section", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_MOVE_SCALE         = DecoderAttribute.with("move_scale", DecoderAttribute.DataType.DECIMAL);
  public static final DecoderAttribute KEY_ELLIPSE            = DecoderAttribute.with("ellipse", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_COLOR_BLOOD        = DecoderAttribute.with("color_blood", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_COLOR_CHUNKS       = DecoderAttribute.with("color_chunks", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_SOUND_FREQ         = DecoderAttribute.with("sound_freq", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_SOUND_DEATH        = DecoderAttribute.with("sound_death", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_PERSONAL_SPACE     = DecoderAttribute.with("personal_space", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_CAST_FRAME         = DecoderAttribute.with("cast_frame", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_HEIGHT_OFFSET      = DecoderAttribute.with("height_offset", DecoderAttribute.DataType.INT);
  public static final DecoderAttribute KEY_BRIGHTEST          = DecoderAttribute.with("brightest", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_MULTIPLY_BLEND     = DecoderAttribute.with("multiply_blend", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_LIGHT_SOURCE       = DecoderAttribute.with("light_source", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_NEW_PALETTE        = DecoderAttribute.with("new_palette", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_SOUND_REF          = DecoderAttribute.with("sound_ref", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_0     = DecoderAttribute.with("combat_round_0", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_1     = DecoderAttribute.with("combat_round_1", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_2     = DecoderAttribute.with("combat_round_2", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_3     = DecoderAttribute.with("combat_round_3", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_COMBAT_ROUND_4     = DecoderAttribute.with("combat_round_4", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_WALK_SOUND         = DecoderAttribute.with("walk_sound", DecoderAttribute.DataType.STRING);
  // List of commonly used attributes specific to creature animation types
  public static final DecoderAttribute KEY_RESREF             = DecoderAttribute.with("resref", DecoderAttribute.DataType.STRING);
  public static final DecoderAttribute KEY_DETECTED_BY_INFRAVISION  = DecoderAttribute.with("detected_by_infravision", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_FALSE_COLOR        = DecoderAttribute.with("false_color", DecoderAttribute.DataType.BOOLEAN);
  public static final DecoderAttribute KEY_TRANSLUCENT        = DecoderAttribute.with("translucent", DecoderAttribute.DataType.BOOLEAN);

  /**
   * A default operation that can be passed to the
   * {@link #createAnimation(SeqDef, List, BeforeSourceBam, BeforeSourceFrame, AfterSourceFrame, AfterDestFrame)}
   * method. It is called once per source BAM resource.
   * Performed actions: palette replacement, shadow color fix, false color replacement, translucency
   */
  protected final BeforeSourceBam FN_BEFORE_SRC_BAM = new BeforeSourceBam() {
    @Override
    public void accept(BamV1Control control, SegmentDef sd)
    {
      if (isPaletteReplacementEnabled() && sd.getSpriteType() == SegmentDef.SpriteType.AVATAR) {
        int[] palette = getNewPaletteData(sd.getEntry());
        if (palette != null) {
          SpriteUtils.applyNewPalette(control, palette);
        }
      }

      SpriteUtils.fixShadowColor(control, isTransparentShadow());

      if (isPaletteReplacementEnabled() && isFalseColor()) {
        applyFalseColors(control, sd);
      }

      if (isTranslucencyEnabled() && isTranslucent()) {
        applyTranslucency(control);
      }
    }
  };

  /**
   * A default operation that can be passed to the
   * {@link #createAnimation(SeqDef, List, BeforeSourceBam, BeforeSourceFrame, AfterSourceFrame, AfterDestFrame)}
   * method. It is called for each source frame (segment) before being applied to the target frame.
   */
  protected final BeforeSourceFrame FN_BEFORE_SRC_FRAME = new BeforeSourceFrame() {
    @Override
    public BufferedImage apply(SegmentDef sd, BufferedImage image, Graphics2D g)
    {
      // nothing to do...
      return image;
    }
  };

  /**
   * A default operation that can be passed to the
   * {@link #createAnimation(SeqDef, List, BeforeSourceBam, BeforeSourceFrame, AfterSourceFrame, AfterDestFrame)}
   * method. It is called for each source frame (segment) after being applied to the target frame.
   */
  protected final AfterSourceFrame FN_AFTER_SRC_FRAME = new AfterSourceFrame() {
    @Override
    public void accept(SegmentDef sd, Graphics2D g)
    {
      // nothing to do...
    }
  };

  /**
   * A default action that can be passed to the
   * {@link #createAnimation(SeqDef, List, BeforeSourceBam, BeforeSourceFrame, AfterSourceFrame, AfterDestFrame)}
   * method. It calculates an eastern direction frame by mirroring it horizontally if needed.
   */
  protected final AfterDestFrame FN_AFTER_DST_FRAME = new AfterDestFrame() {
    @Override
    public void accept(DirDef dd, int frameIdx)
    {
      if (dd.isMirrored()) {
        flipImageHorizontal(frameIdx);
      }
    }
  };

  private final CreatureInfo creInfo;
  private final IniMap ini;
  /** Storage for associations between directions and cycle indices. */
  private final EnumMap<Direction, Integer> directionMap;
  /** Cache for creature animation attributes. */
  private final TreeMap<DecoderAttribute, Object> attributesMap;

  private Sequence currentSequence;
  private boolean showCircle;
  private boolean selectionCircleBitmap;
  private boolean showPersonalSpace;
  private boolean showBoundingBox;
  private boolean transparentShadow;
  private boolean translucencyEnabled;
  private boolean paletteReplacementEnabled;
  private boolean renderSpriteAvatar;
  private boolean renderSpriteWeapon;
  private boolean renderSpriteHelmet;
  private boolean renderSpriteShield;
  private boolean animationChanged;
  private boolean autoApplyChanges;

  /**
   * Convenience method for loading the animation of the specified CRE resource.
   * @param cre The CRE resource instance.
   * @return A {@code SpriteDecoder} instance with processed animation data.
   * @throws Exception if the specified resource could not be processed.
   */
  public static SpriteDecoder importSprite(CreResource cre) throws Exception
  {
    Objects.requireNonNull(cre, "CRE resource cannot be null");
    int animationId = ((IsNumeric)cre.getAttribute(CreResource.CRE_ANIMATION)).getValue();
    Class<? extends SpriteDecoder> spriteClass =
        Objects.requireNonNull(SpriteUtils.detectAnimationType(animationId), "Could not determine animation type");
    try {
      Constructor<? extends SpriteDecoder> ctor =
          Objects.requireNonNull(spriteClass.getConstructor(CreResource.class), "No matching constructor found");
      return ctor.newInstance(cre);
    } catch (InvocationTargetException ite) {
      throw (ite.getCause() instanceof Exception) ? (Exception)ite.getCause() : ite;
    }
  }

  /**
   * Instances creates with this constructor are only suited for identification purposes.
   * @param type the animation type
   * @param animationId specific animation id
   * @param sectionName INI section name for animation-specific data
   * @param ini the INI file with creature animation attributes
   * @throws Exception
   */
  protected SpriteDecoder(AnimationInfo.Type type, int animationId, IniMap ini) throws Exception
  {
    Objects.requireNonNull(type, "Animation type cannot be null");
    Objects.requireNonNull(ini, "No INI data available for animation id: " + animationId);
    this.attributesMap = new TreeMap<>();
    this.directionMap = new EnumMap<>(Direction.class);
    setAttribute(KEY_ANIMATION_TYPE, type);
    setAttribute(KEY_ANIMATION_SECTION, type.getSectionName());
    this.creInfo = new CreatureInfo(this, SpriteUtils.getPseudoCre(animationId, null, null));
    this.ini = ini;
    this.currentSequence = Sequence.NONE;
    init();
    if (!isMatchingAnimationType()) {
      throw new IllegalArgumentException("Animation id is incompatible with animation type: " + type.toString());
    }
  }

  /**
   * This constructor creates an instance that can be used to render animation sequences.
   * @param type the animation type
   * @param cre the CRE resource instance.
   * @throws Exception
   */
  protected SpriteDecoder(AnimationInfo.Type type, CreResource cre) throws Exception
  {
    Objects.requireNonNull(type, "Animation type cannot be null");
    this.attributesMap = new TreeMap<>();
    this.directionMap = new EnumMap<>(Direction.class);
    setAttribute(KEY_ANIMATION_TYPE, type);
    setAttribute(KEY_ANIMATION_SECTION, type.getSectionName());
    this.creInfo = new CreatureInfo(this, cre);
    this.ini = Objects.requireNonNull(SpriteUtils.getAnimationInfo(getAnimationId()),
                                      "No INI data available for animation id: " + getAnimationId());
    this.currentSequence = Sequence.NONE;
    this.showCircle = false;
    this.selectionCircleBitmap = (Profile.getGame() == Profile.Game.PST) || (Profile.getGame() == Profile.Game.PSTEE);
    this.showPersonalSpace = false;
    this.showBoundingBox = false;
    this.transparentShadow = true;
    this.translucencyEnabled = true;
    this.paletteReplacementEnabled = true;
    this.renderSpriteAvatar = true;
    this.renderSpriteWeapon = true;
    this.renderSpriteShield = true;
    this.renderSpriteHelmet = true;
    this.autoApplyChanges = true;
    SpriteUtils.updateRandomPool();
    init();
  }

  /**
   * Returns the data associated with the specified attribute name.
   * @param key the attribute name.
   * @return attribute data in the type inferred from the method call.
   *                   Returns {@code null} if data is not available for the inferred type.
   */
  @SuppressWarnings("unchecked")
  public <T> T getAttribute(DecoderAttribute att)
  {
    T retVal = null;
    if (att == null) {
      return retVal;
    }

    Object data = attributesMap.getOrDefault(att, att.getDefaultValue());
    if (data != null) {
      try {
        retVal = (T)data;
      } catch (ClassCastException e) {
        // e.printStackTrace();
      }
    }
    return retVal;
  }

  /**
   * Stores the attribute key and value along with the autodetected data type.
   * @param key the attribute name.
   * @param value the value in one of the data types covered by {@link KeyType}.
   */
  protected void setAttribute(DecoderAttribute att, Object value)
  {
    if (att == null) {
      return;
    }
    attributesMap.put(att, value);
  }

  /** Returns an iterator over the attribute keys. */
  public Iterator<DecoderAttribute> getAttributeIterator()
  {
    return attributesMap.keySet().iterator();
  }

  /** Returns the type of the current creature animation. */
  public AnimationInfo.Type getAnimationType()
  {
    return getAttribute(KEY_ANIMATION_TYPE);
  }

  /**
   * Returns the INI section name for the current animation type.
   * Returns {@code null} if the name could not be determined.
   */
  public String getAnimationSectionName()
  {
    return getAttribute(KEY_ANIMATION_SECTION);
  }

  /**
   * Returns a list of BAM filenames associated with the current animation type.
   * @param essential if set returns only essential files required for the animation.
   * @return list of BAM filenames associated with the current animation type.
   *         Returns {@code null} if files could not be determined.
   */
  public abstract List<String> getAnimationFiles(boolean essential);

  /** Recreates the creature animation based on the current creature resource. */
  public void reset() throws Exception
  {
    Direction[] directions = getDirectionMap().keySet().toArray(new Direction[getDirectionMap().keySet().size()]);
    discard();
    // recreating current sequence
    if (getCurrentSequence() != Sequence.NONE) {
      createSequence(getCurrentSequence(), directions);
    }
  }

  /** Removes the currently loaded animation sequence. */
  protected void discard()
  {
    frameClear();
    directionMap.clear();
  }

  /**
   * Loads the specified sequence if available. Discards the currently active sequence.
   * Call {@code reset()} instead to enforce reloading the same sequence with different
   * creature attributes.
   * @param seq the animation sequence to load. Specifying {@code Sequence.None} only discards the current sequence.
   * @return whether the sequence was successfully loaded.
   */
  public boolean loadSequence(Sequence seq) throws Exception
  {
    return loadSequence(seq, null);
  }

  /**
   * Loads selected directions of the specified sequence if available. Discards the currently active sequence.
   * Call {@code reset()} instead to enforce reloading the same sequence with different
   * creature attributes.
   * @param seq the animation sequence to load. Specifying {@code Sequence.None} only discards the current sequence.
   * @param directions array with directions allowed to be created. Specify {@code null} to create animations
   *                   for all directions.
   * @return whether the sequence was successfully loaded.
   */
  public boolean loadSequence(Sequence seq, Direction[] directions) throws Exception
  {
    boolean retVal = true;

    if (getCurrentSequence() != Objects.requireNonNull(seq, "Animation sequence cannot be null")) {
      // discarding current sequence
      discard();

      try {
        createSequence(seq, directions);
        currentSequence = seq;
      } catch (NullPointerException e) {
        retVal = (seq != Sequence.NONE);
      } catch (Exception e) {
        e.printStackTrace();
        retVal = (seq != Sequence.NONE);
      }
    }

    return retVal;
  }

  /** Returns the currently active sequence. */
  public Sequence getCurrentSequence()
  {
    return currentSequence;
  }

  /** Returns whether the specified animation sequence is available for the current creature animation. */
  public abstract boolean isSequenceAvailable(Sequence seq);

  /**
   * Returns the closest available direction to the specified direction.
   * @param dir the requested direction
   * @return an available {@code Direction} that is closest to the specified direction.
   *         Returns {@code null} if no direction is available.
   */
  public Direction getExistingDirection(Direction dir)
  {
    Direction retVal = null;

    if (dir == null) {
      return retVal;
    }
    if (getDirectionMap().containsKey(dir)) {
      return dir;
    }
    SeqDef sd = getSequenceDefinition(getCurrentSequence());
    if (sd == null || sd.isEmpty()) {
      return retVal;
    }

    int dirIdx = dir.getValue();
    int dirLen = Direction.values().length;
    int maxRange = dirLen / 2;
    for (int range = 1; range <= maxRange; range++) {
      int dist = (dirIdx + range + dirLen) % dirLen;
      Direction distDir = Direction.from(dist);
      if (getDirectionMap().containsKey(distDir)) {
        retVal = distDir;
        break;
      }
      dist = (dirIdx - range + dirLen) % dirLen;
      distDir = Direction.from(dist);
      if (getDirectionMap().containsKey(distDir)) {
        retVal = distDir;
        break;
      }
    }

    return retVal;
  }

  /** Provides access to the {@link CreatureInfo} instance associated with the sprite decoder. */
  public CreatureInfo getCreatureInfo()
  {
    return creInfo;
  }

  /** Returns the {@code CreResource} instance of the current CRE resource. */
  public CreResource getCreResource()
  {
    return creInfo.getCreResource();
  }

  /** Returns the numeric animation id of the current CRE resource. */
  public int getAnimationId()
  {
    return creInfo.getAnimationId();
  }

  /** Returns a INI structure with creature animation info. */
  public IniMap getAnimationInfo()
  {
    return ini;
  }

  /** Returns whether the selection circle for the creature is drawn. */
  public boolean isSelectionCircleEnabled()
  {
    return showCircle;
  }

  /** Sets whether the selection circle for the creature is drawn. */
  public void setSelectionCircleEnabled(boolean b)
  {
    if (showCircle != b) {
      showCircle = b;
      selectionCircleChanged();
    }
  }

  /** Returns whether the space occupied by the creature is visualized. */
  public boolean isPersonalSpaceVisible()
  {
    return showPersonalSpace;
  }

  /** Sets whether the space occupied by the creature is visualized. */
  public void setPersonalSpaceVisible(boolean b)
  {
    if (showPersonalSpace != b) {
      showPersonalSpace = b;
      personalSpaceChanged();
    }
  }

  /** Returns whether a bounding box is drawn around sprites (or quadrants) and secondary overlays. */
  public boolean isBoundingBoxVisible()
  {
    return showBoundingBox;
  }

  /** Sets whether a bounding box is drawn around sprites (or quadrants) and secondary overlays. */
  public void setBoundingBoxVisible(boolean b)
  {
    if (showBoundingBox != b) {
      showBoundingBox = b;
      spriteChanged();
    }
  }

  /** Returns whether the avatar sprite should be rendered. */
  public boolean getRenderAvatar()
  {
    return renderSpriteAvatar;
  }

  /** Sets whether the avatar sprite should be rendered. */
  public void setRenderAvatar(boolean b)
  {
    if (renderSpriteAvatar != b) {
      renderSpriteAvatar = b;
      spriteChanged();
    }
  }

  /** Returns whether the weapon overlay should be rendered. This option affects only specific animation types. */
  public boolean getRenderWeapon()
  {
    return renderSpriteWeapon;
  }

  /** Sets whether the weapon overlay should be rendered. This option affects only specific animation types. */
  public void setRenderWeapon(boolean b)
  {
    if (renderSpriteWeapon != b) {
      renderSpriteWeapon = b;
      spriteChanged();
    }
  }

  /**
   * Returns whether the shield (or left-handed weapon) overlay should be rendered.
   * This option affects only specific animation types.
   */
  public boolean getRenderShield()
  {
    return renderSpriteShield;
  }

  /**
   * Sets whether the shield (or left-handed weapon) overlay should be rendered.
   * This option affects only specific animation types.
   */
  public void setRenderShield(boolean b)
  {
    if (renderSpriteShield != b) {
      renderSpriteShield = b;
      spriteChanged();
    }
  }

  /**  Returns whether the helmet overlay should be rendered. This option affects only specific animation types. */
  public boolean getRenderHelmet()
  {
    return renderSpriteHelmet;
  }

  /** Sets whether the helmet overlay should be rendered. This option affects only specific animation types. */
  public void setRenderHelmet(boolean b)
  {
    if (renderSpriteHelmet != b) {
      renderSpriteHelmet = b;
      spriteChanged();
    }
  }

  /** Returns whether translucency effect is applied to the creature animation. */
  public boolean isTranslucencyEnabled()
  {
    return translucencyEnabled;
  }

  /** Sets whether translucency effect is applied to the creature animation. */
  public void setTranslucencyEnabled(boolean b)
  {
    if (translucencyEnabled != b) {
      translucencyEnabled = b;
      if (isTranslucent()) {
        SpriteUtils.clearBamCache();
        spriteChanged();
      }
    }
  }

  /** Returns whether any kind of palette replacement (full palette or false colors) is enabled. */
  public boolean isPaletteReplacementEnabled()
  {
    return paletteReplacementEnabled;
  }

  /** Sets whether palette replacement (full palette or false colors) is enabled. */
  public void setPaletteReplacementEnabled(boolean b)
  {
    if (paletteReplacementEnabled != b) {
      paletteReplacementEnabled = b;
      SpriteUtils.clearBamCache();
      spriteChanged();
    }
  }

  /**
   * Returns {@code true} if a bitmap is used to render the selection circle.
   * Returns {@code false} if a colored circle is drawn instead.
   */
  public boolean isSelectionCircleBitmap()
  {
    return selectionCircleBitmap;
  }

  /**
   * Specify {@code true} if a bitmap should be used to render the selection circle.
   * Specify {@code false} if a colored circle should be drawn instead.
   */
  public void setSelectionCircleBitmap(boolean b)
  {
    if (selectionCircleBitmap != b) {
      selectionCircleBitmap = b;
      selectionCircleChanged();
    }
  }

  /** Returns the moving speed of the creature animation. */
  public double getMoveScale()
  {
    return getAttribute(KEY_MOVE_SCALE);
  }

  /** Sets the moving speed of the creature animation. */
  protected void setMoveScale(double value)
  {
    setAttribute(KEY_MOVE_SCALE, value);
  }

  /** Returns the selection circle size of the creature animation. */
  public int getEllipse()
  {
    return getAttribute(KEY_ELLIPSE);
  }

  /** Sets the selection circle size of the creature animation. */
  public void setEllipse(int value)
  {
    if (getEllipse() != value) {
      setAttribute(KEY_ELLIPSE, value);
      selectionCircleChanged();
    }
  }

  /** Returns the map space (in search map units) reserved exclusively for the creature animation*/
  public int getPersonalSpace()
  {
    return getAttribute(KEY_PERSONAL_SPACE);
  }

  /** Sets the map space (in search map units) reserved exclusively for the creature animation*/
  public void setPersonalSpace(int value)
  {
    if (getPersonalSpace() != value) {
      setAttribute(KEY_PERSONAL_SPACE, value);
      personalSpaceChanged();
    }
  }

  /** Returns the resref (prefix) for the associated animation files. */
  public String getAnimationResref()
  {
    return getAttribute(KEY_RESREF);
  }

  /** Sets the resref (prefix) for the associated animation files. */
  protected void setAnimationResref(String resref)
  {
    setAttribute(KEY_RESREF, resref);
  }

  /** Returns the replacement palette for the creature animation. Returns empty string if no replacement palette exists. */
  public String getNewPalette()
  {
    return getAttribute(KEY_NEW_PALETTE);
  }

  /** Sets the replacement palette for the creature animation. */
  public void setNewPalette(String resref)
  {
    resref = (resref != null) ? resref.trim() : "";
    if (!getNewPalette().equalsIgnoreCase(resref)) {
      setAttribute(KEY_NEW_PALETTE, resref);
      paletteChanged();
    }
  }

  /** Loads the replacement palette associated with the specified BAM resource. */
  protected int[] getNewPaletteData(ResourceEntry bamRes)
  {
    // Note: method argument is irrelevant for base implementation
    return SpriteUtils.loadReplacementPalette(getNewPalette());
  }

  /**
   * Returns whether blending mode "brightest" is enabled.
   * <p>Blending modes and their effects:
   * <li>Brightest only: Use {@code GL_ONE_MINUS_DST_COLOR}
   * <li>MultiplyBlend only: Use {@code GL_DST_COLOR}
   * <li>Brightest and MultiplyBlend: Use {@code GL_SRC_COLOR}
   */
  public boolean isBrightest()
  {
    return getAttribute(KEY_BRIGHTEST);
  }

  /** Sets blending mode "brightest". */
  protected void setBrightest(boolean b)
  {
    setAttribute(KEY_BRIGHTEST, b);
  }

  /**
   * Returns whether blending mode "multiply_blend" is enabled.
   * <p>Blending modes and their effects:
   * <li>Brightest only: Use {@code GL_ONE_MINUS_DST_COLOR}
   * <li>MultiplyBlend only: Use {@code GL_DST_COLOR}
   * <li>Brightest and MultiplyBlend: Use {@code GL_SRC_COLOR}
   */
  public boolean isMultiplyBlend()
  {
    return getAttribute(KEY_MULTIPLY_BLEND);
  }

  /** Sets blending mode "multiply_blend". */
  protected void setMultiplyBlend(boolean b)
  {
    setAttribute(KEY_MULTIPLY_BLEND, b);
  }

  /** Returns whether sprite is affected by environmental lighting. */
  public boolean isLightSource()
  {
    return getAttribute(KEY_LIGHT_SOURCE);
  }

  /** Sets whether sprite is affected by environmental lighting. */
  protected void setLightSource(boolean b)
  {
    setAttribute(KEY_LIGHT_SOURCE, b);
  }

  /** Returns whether a red tint is applied to the creature if detected by infravision. */
  public boolean isDetectedByInfravision()
  {
    return getAttribute(KEY_DETECTED_BY_INFRAVISION);
  }

  /** Sets whether a red tint is applied to the creature if detected by infravision. */
  protected void setDetectedByInfravision(boolean b)
  {
    setAttribute(KEY_DETECTED_BY_INFRAVISION, b);
  }

  /** Returns whether palette range replacement is enabled. */
  public boolean isFalseColor()
  {
    return getAttribute(KEY_FALSE_COLOR);
  }

  /** Sets whether palette range replacement is enabled. */
  protected void setFalseColor(boolean b)
  {
    setAttribute(KEY_FALSE_COLOR, b);
  }

  /** Returns whether creature animation is translucent.  */
  public boolean isTranslucent()
  {
    return getCreatureInfo().getEffectiveTranslucency() > 0;
  }

  /** Sets whether creature animation is translucent.  */
  protected void setTranslucent(boolean b)
  {
    setAttribute(KEY_TRANSLUCENT, b);
  }

  /** Call this method whenever the visibility of the selection circle has been changed. */
  public void selectionCircleChanged()
  {
    setAnimationChanged();
  }

  /** Call this method whenever the visibility of personal space has been changed. */
  public void personalSpaceChanged()
  {
    setAnimationChanged();
  }

  /** Call this method whenever the visibility of any sprite types has been changed. */
  public void spriteChanged()
  {
    setAnimationChanged();
  }

  /** Call this method whenever the allegiance value has been changed. */
  public void allegianceChanged()
  {
    setAnimationChanged();
  }

  /**
   * Call this method whenever the creature palette has changed.
   * False color is processed by {@link #falseColorChanged()}.
   */
  public void paletteChanged()
  {
    // Note: PST false color palette may also contain true color regions.
    if (!isFalseColor() ||
        Profile.getGame() == Profile.Game.PSTEE ||
        Profile.getEngine() == Profile.Engine.PST) {
      setAnimationChanged();
    }
  }

  /**
   * Call this method whenever the false color palette of the creature has changed.
   * Conventional palette changes are processed by {@link #paletteChanged()}. */
  public void falseColorChanged()
  {
    if (isFalseColor()) {
      setAnimationChanged();
    }
  }

  /** This method reloads the creature animation if any relevant changes have been made. */
  public void applyAnimationChanges()
  {
    if (hasAnimationChanged()) {
      resetAnimationChanged();
      try {
        reset();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /** Returns whether changes to the creature animation should be applied immediately. */
  public boolean isAutoApplyChanges()
  {
    return autoApplyChanges;
  }

  /** Sets whether changes to the creature animation should be applied immediately. */
  public void setAutoApplyChanges(boolean b)
  {
    if (autoApplyChanges != b) {
      autoApplyChanges = b;
      if (autoApplyChanges) {
        applyAnimationChanges();
      }
    }
  }

  /**
   * Returns whether a creature animation reload has been requested.
   * Call {@link #applyPendingChanges()} to apply the changes.
   */
  public boolean hasAnimationChanged()
  {
    return animationChanged;
  }

  /** Call to request a creature animation reload by the method {@link #applyPendingChanges()}. */
  public void setAnimationChanged()
  {
    animationChanged = true;
    if (isAutoApplyChanges()) {
      applyAnimationChanges();
    }
  }

  /** Call to cancel the request of a creature animation reload by the method {@link #applyPendingChanges()}. */
  public void resetAnimationChanged()
  {
    animationChanged = false;
  }

  /**
   * Returns the preferred compositor for rendering the sprite on the target surface.
   */
  public Composite getComposite()
  {
    int blending = ((isBrightest() ? 1 : 0) << 0) | ((isMultiplyBlend() ? 1 : 0) << 1);
    switch (blending) {
      case 1:   // brightest
        return BlendingComposite.Brightest;
      case 2:   // multiply
        return BlendingComposite.Multiply;
      case 3:   // brightest + multiply
        return BlendingComposite.BrightestMultiply;
      default:
        return AlphaComposite.SrcOver;
    }
  }

  /**
   * Returns the BAM cycle associated with the specified direction.
   * Returns -1 if entry not found.
   */
  public int getCycleIndex(Direction dir)
  {
    int retVal = -1;
    Integer value = directionMap.get(dir);
    if (value != null) {
      retVal = value.intValue();
    }

    return retVal;
  }

  /**
   * Returns a copy of the map containing associations between animation directions and bam sequence numbers.
   */
  public EnumMap<Direction, Integer> getDirectionMap()
  {
    return directionMap.clone();
  }

  /** Creates the BAM structure for the creature animation. */
  protected abstract void init() throws Exception;

  /**
   * Initializes general data for the creature animation.
   * @param ini The INI map containing creature animation data.
   */
  protected void initDefaults(IniMap ini) throws Exception
  {
    IniMapSection section = getGeneralIniSection(Objects.requireNonNull(ini, "INI object cannot be null"));
    Objects.requireNonNull(section.getAsString("animation_type"), "animation_type required");
    Misc.requireCondition(getAnimationType().contains(getAnimationId()),
                          String.format("Animation slot (%04X) is not compatible with animation type (%s)",
                                        getAnimationId(), getAnimationType().toString()));

    setMoveScale(section.getAsDouble("move_scale", 0.0));
    setEllipse(section.getAsInteger("ellipse", 16));
    setPersonalSpace(section.getAsInteger("personal_space", 3));
    setBrightest(section.getAsInteger("brightest", 0) != 0);
    setMultiplyBlend(section.getAsInteger("multiply_blend", 0) != 0);
    setLightSource(section.getAsInteger("light_source", 0) != 0);

    String s = section.getAsString("new_palette", "");
    setNewPalette(s);

    // getting first available "resref" definition
    for (Iterator<IniMapSection> iter = getAnimationInfo().iterator(); iter.hasNext(); ) {
      section = iter.next();
      s = section.getAsString("resref", "");
      if (!s.isEmpty()) {
        setAnimationResref(s);
        break;
      }
    }
    Misc.requireCondition(!getAnimationResref().isEmpty(), "Animation resource prefix required");
  }

  /**
   * Returns the general INI map section defined for all supported creature animation types from the specified
   * {@code IniMap} instance. Returns an empty {@code IniMapSection} instance if section could not be determined.
   */
  protected IniMapSection getGeneralIniSection(IniMap ini)
  {
    final String sectionName = "general";
    IniMapSection retVal = null;
    if (ini != null) {
      retVal = ini.getSection(sectionName);
    }

    if (retVal == null) {
      retVal = new IniMapSection(sectionName, 0, null);
    }

    return retVal;
  }

  /**
   * Returns the INI map section responsible for animation-type-specific attributes.
   * Returns an empty {@code IniMapSection} instance if section could not be determined.
   */
  protected IniMapSection getSpecificIniSection()
  {
    IniMapSection retVal = null;
    IniMap ini = getAnimationInfo();
    if (ini != null) {
      retVal = ini.getSection(getAnimationSectionName());
    }

    if (retVal == null) {
      retVal = new IniMapSection(getAnimationSectionName(), 0, null);
    }

    return retVal;
  }

  /**
   * Assigns a cycle index to the specified BAM sequence and direction.
   * @param seq the sequence type for identification purposes.
   * @param dir the direction type
   * @param cycleIndex the cycle index associated with the specified sequence and direction.
   * @return The previous BAM cycle index if available. -1 otherwise.
   */
  protected int addDirection(Direction dir, int cycleIndex)
  {
    int retVal = -1;
    dir = Objects.requireNonNull(dir, "Creature direction required");
    Integer value = directionMap.get(dir);
    if (value != null) {
      retVal = value.intValue();
    }
    directionMap.put(dir, cycleIndex);

    return retVal;
  }

  /**
   * Generates definitions for the specified animation sequence.
   * @param seq the requested animation sequence.
   * @return a fully initialized {@code SeqDef} object if sequence is supported, {@code null} otherwise.
   */
  protected abstract SeqDef getSequenceDefinition(Sequence seq);

  /**
   * Loads the specified animation sequence into the SpriteDecoder.
   * @param seq the sequence to load.
   * @throws NullPointerException if specified sequence is not available.
   */
  protected void createSequence(Sequence seq) throws Exception
  {
    SeqDef sd = Objects.requireNonNull(getSequenceDefinition(seq), "Sequence not available: " + (seq != null ? seq : "(null)"));
    createAnimation(sd, null, FN_BEFORE_SRC_BAM, FN_BEFORE_SRC_FRAME, FN_AFTER_SRC_FRAME, FN_AFTER_DST_FRAME);
  }

  /**
   * Loads the specified animation sequence into the SpriteDecoder.
   * Only directions listed in the given {@code Direction} array will be considered.
   * @param seq the sequence to load.
   * @param directions an array of {@code Direction} values. Only directions listed in the array
   *                   are considered by the creation process. Specify {@code null} to allow all directions.
   * @throws NullPointerException if specified sequence is not available.
   */
  protected void createSequence(Sequence seq, Direction[] directions) throws Exception
  {
    SeqDef sd = Objects.requireNonNull(getSequenceDefinition(seq), "Sequence not available: " + (seq != null ? seq : "(null)"));
    if (directions == null) {
      directions = Direction.values();
    }
    createAnimation(sd, Arrays.asList(directions), FN_BEFORE_SRC_BAM, FN_BEFORE_SRC_FRAME, FN_AFTER_SRC_FRAME, FN_AFTER_DST_FRAME);
  }

  protected void createAnimation(SeqDef definition, List<Direction> directions,
                                 BeforeSourceBam beforeSrcBam,
                                 BeforeSourceFrame beforeSrcFrame,
                                 AfterSourceFrame afterSrcFrame,
                                 AfterDestFrame afterDstFrame)
  {
    PseudoBamControl dstCtrl = createControl();
    BamV1Control srcCtrl = null;
    ResourceEntry entry = null;
    definition = Objects.requireNonNull(definition, "Sequence definition cannot be null");

    if (directions == null) {
      directions = Arrays.asList(Direction.values());
    }
    if (directions.isEmpty()) {
      return;
    }

    for (final DirDef dd : definition.getDirections()) {
      if (!directions.contains(dd.getDirection())) {
        continue;
      }
      CycleDef cd = dd.getCycle();
      int cycleIndex = dstCtrl.cycleAdd();
      addDirection(dd.getDirection(), cycleIndex);

      cd.reset();
      int frameCount = cd.getMaximumFrames();
      final ArrayList<FrameInfo> frameInfo = new ArrayList<>();
      for (int frame = 0; frame < frameCount; frame++) {
        frameInfo.clear();
        for (final SegmentDef sd : cd.getCycles()) {
          // checking visibility of sprite types
          boolean skip = (sd.getSpriteType() == SegmentDef.SpriteType.AVATAR) && !getRenderAvatar();
          skip |= (sd.getSpriteType() == SegmentDef.SpriteType.WEAPON) && !getRenderWeapon();
          skip |= (sd.getSpriteType() == SegmentDef.SpriteType.SHIELD) && !getRenderShield();
          skip |= (sd.getSpriteType() == SegmentDef.SpriteType.HELMET) && !getRenderHelmet();
          if (skip) {
            continue;
          }

          entry = sd.getEntry();
          srcCtrl = Objects.requireNonNull(SpriteUtils.loadBamController(entry));
          srcCtrl.cycleSet(sd.getCycleIndex());

          if (sd.getCurrentFrame() >= 0) {
            if (beforeSrcBam != null) {
              beforeSrcBam.accept(srcCtrl, sd);
            }
            frameInfo.add(new FrameInfo(srcCtrl, sd));
          }
          sd.advance();
        }

        int frameIndex = createFrame(frameInfo.toArray(new FrameInfo[frameInfo.size()]), beforeSrcFrame, afterSrcFrame);
        if (afterDstFrame != null) {
          afterDstFrame.accept(dd, frameIndex);
        }
        dstCtrl.cycleAddFrames(cycleIndex, new int[] {frameIndex});
      }
    }
  }

  /**
   * Creates a single creature animation frame from the given array of source frame segments
   * and adds it to the BAM frame list. Each source frame segment can be processed by the specified lambda function
   * before it is drawn onto to the target frame.
   * @param sourceFrames array of source frame segments to compose.
   * @param beforeSrcFrame optional function that is executed before a source frame segment is drawn onto the
   *                       target frame.
   * @param afterSrcFrame optional method that is executed right after a source frame segment has been drawn onto the
   *                      target frame.
   * @return the absolute target BAM frame index.
   */
  protected int createFrame(FrameInfo[] sourceFrames, BeforeSourceFrame beforeSrcFrame, AfterSourceFrame afterSrcFrame)
  {
    Rectangle rect;
    if (Objects.requireNonNull(sourceFrames, "Source frame info objects required").length > 0) {
      rect = SpriteUtils.getTotalFrameDimension(sourceFrames);
    } else {
      rect = new Rectangle(0, 0, 1, 1);
    }

    // include personal space region in image size
    if (isPersonalSpaceVisible()) {
      rect = SpriteUtils.updateFrameDimension(rect, getPersonalSpaceSize(true));
    }

    // include selection circle in image size
    float circleStrokeSize = getSelectionCircleStrokeSize();
    if (isSelectionCircleEnabled()) {
      Dimension dim = getSelectionCircleSize();
      rect = SpriteUtils.updateFrameDimension(rect, new Dimension(2 * (dim.width + (int)circleStrokeSize),
                                                                  2 * (dim.height + (int)circleStrokeSize)));
    }

    // creating target image
    BufferedImage image;
    if (rect.width > 0 && rect.height > 0) {
      image = ColorConvert.createCompatibleImage(rect.width, rect.height, Transparency.TRANSLUCENT);
      Graphics2D g = image.createGraphics();
      try {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        Point center = new Point(-rect.x, -rect.y);

        if (isPersonalSpaceVisible()) {
          // Drawing personal space region
          drawPersonalSpace(g, center, null, 0.5f);
        }

        if (isSelectionCircleEnabled()) {
          // Drawing selection circle
          drawSelectionCircle(g, center, circleStrokeSize);
        }

        // drawing source frames to target image
        for (final FrameInfo fi : sourceFrames) {
          BamV1Control ctrl = fi.getController();
          ctrl.cycleSet(fi.getCycle());
          int frameIdx = fi.getFrame();
          ctrl.cycleSetFrameIndex(frameIdx);
          BufferedImage srcImage = (BufferedImage)ctrl.cycleGetFrame();
          if (beforeSrcFrame != null) {
            srcImage = beforeSrcFrame.apply(fi.getSegmentDefinition(), srcImage, g);
          }
          FrameEntry entry = ctrl.getDecoder().getFrameInfo(ctrl.cycleGetFrameIndexAbsolute());
          int x = -rect.x - entry.getCenterX();
          int y = -rect.y - entry.getCenterY();

          if (isBoundingBoxVisible() && entry.getWidth() > 2 && entry.getHeight() > 2) {
            // drawing bounding box around sprite elements
            Stroke oldStroke = g.getStroke();
            Color oldColor = g.getColor();
            Object oldHints = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            try {
              g.setStroke(FrameInfo.STROKE_BOUNDING_BOX);
              g.setColor(FrameInfo.SPRITE_COLOR.getOrDefault(fi.getSegmentDefinition().getSpriteType(), FrameInfo.SPRITE_COLOR_DEFAULT));
              g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
              g.drawRect(x, y, entry.getWidth() - 1, entry.getHeight() - 1);
            } finally {
              g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, (oldHints != null) ? oldHints : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
              if (oldColor != null) {
                g.setColor(oldColor);
              }
              if (oldStroke != null) {
                g.setStroke(oldStroke);
              }
            }
          }

          g.drawImage(srcImage, x, y, entry.getWidth(), entry.getHeight(), null);

          if (afterSrcFrame != null) {
            afterSrcFrame.accept(fi.getSegmentDefinition(), g);
          }
          ctrl = null;
        }
      } finally {
        g.dispose();
        g = null;
      }
    } else {
      // dummy graphics
      image = ColorConvert.createCompatibleImage(1, 1, Transparency.TRANSLUCENT);
    }

    // setting center point
    int cx = -rect.x;
    int cy = -rect.y;

    return frameAdd(image, new Point(cx, cy));
  }

  /**
   * Calculates the total size of the personal space region.
   * @param scaled whether dimension should be scaled according to search map unit size.
   */
  protected Dimension getPersonalSpaceSize(boolean scaled)
  {
    int size = (Math.max(1, getPersonalSpace()) - 1) | 1;
    if (scaled) {
      return new Dimension(size * 16, size * 12);
    } else {
      return new Dimension(size, size);
    }
  }

  /**
   * Draws the personal space region onto the specified graphics object.
   * @param g the {@code Graphics2D} instance of the image.
   * @param center center position of the personal space.
   * @param color the fill color of the drawn region. Specify {@code null} to use a default color.
   * @param alpha alpha transparency in range [0.0, 1.0] where 0.0 is fully transparent (invisible) and 1.0 is fully opaque.
   */
  protected void drawPersonalSpace(Graphics2D g, Point center, Color color, float alpha)
  {
    if (g != null) {
      BufferedImage image = createPersonalSpace(color, alpha);
      g.drawImage(image, center.x - (image.getWidth() / 2), center.y - (image.getHeight() / 2), null);
    }
  }

  /** Creates a bitmap with the personal space tiles. */
  protected BufferedImage createPersonalSpace(Color color, float alpha)
  {
    // preparations
    if (color == null) {
      color = new Color(224, 0, 224);
    }
    alpha = Math.max(0.0f, Math.min(1.0f, alpha));  // clamping alpha to [0.0, 1.0]
    color = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * alpha));

    // creating personal space pattern (unscaled)
    Dimension dim = getPersonalSpaceSize(false);
    BufferedImage image = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
    int[] bitmap = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
    int cx = dim.width / 2;
    int cy = dim.height / 2;
    int c = color.getRGB();
    int maxDist = dim.width * dim.width / 4;
    for (int y = 0; y < dim.height; y++) {
      for (int x = 0; x < dim.width; x++) {
        int ofs = y * dim.width + x;
        int dx = (cx - x) * (cx - x);
        int dy = (cy - y) * (cy - y);
        if (dx + dy < maxDist) {
          bitmap[ofs] = c;
        }
      }
    }

    // scaling up to search map unit size
    dim = getPersonalSpaceSize(true);
    BufferedImage retVal = new BufferedImage(dim.width, dim.height, image.getType());
    Graphics2D g = retVal.createGraphics();
    try {
      g.setComposite(AlphaComposite.Src);
      Object oldHints = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      g.drawImage(image, 0, 0, dim.width, dim.height, null);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                         (oldHints != null) ? oldHints : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    } finally {
      g.dispose();
      g = null;
    }

    return retVal;
  }

  /** Calculates the horizontal and vertical radius of the selection circle (ellipse). */
  protected Dimension getSelectionCircleSize()
  {
    Dimension dim = new Dimension();
    dim.width = Math.max(0, getEllipse());
    if (isSelectionCircleBitmap()) {
      dim.width += 4; // compensation for missing stroke size
    }
    dim.height = dim.width * 4 / 7;   // ratio 1.75
    if (dim.height % 7 > 3) {
      // rounding up
      dim.height++;
    }
    return dim;
  }

  /** Determines a circle stroke size relative to the circle size. Empty circles or bitmap circles have no stroke size. */
  protected float getSelectionCircleStrokeSize()
  {
    float circleStrokeSize = 0.0f;
    if (!isSelectionCircleBitmap() && getEllipse() > 0) {
      // thickness relative to circle size
      circleStrokeSize = Math.max(1.0f, (float)(Math.floor(Math.sqrt(getEllipse()) / 2.0)));
    }

    return circleStrokeSize;
  }

  /**
   * Draws a selection circle onto the specified graphics object.
   * @param g the {@code Graphics2D} instance of the image.
   * @param center center position of the circle.
   * @param color the circle color. Specify {@code null} to use global defaults.
   * @param strokeSize the thickness of the selection circle.
   */
  protected void drawSelectionCircle(Graphics2D g, Point center, float strokeSize)
  {
    if (g != null) {
      Dimension dim = getSelectionCircleSize();

      if (isSelectionCircleBitmap()) {
        Image image = getCreatureInfo().isStatusPanic() ? SpriteUtils.getAllegianceImage(-1)
                                                        : SpriteUtils.getAllegianceImage(getCreatureInfo().getAllegiance());
        Object oldHints = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, center.x - dim.width, center.y - dim.height, 2 * dim.width, 2 * dim.height, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, (oldHints != null) ? oldHints : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
      } else {
        Color color = getCreatureInfo().isStatusPanic() ? SpriteUtils.getAllegianceColor(-1)
                                                        : SpriteUtils.getAllegianceColor(getCreatureInfo().getAllegiance());
        Object oldHints = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeSize));
        g.drawOval(center.x - dim.width, center.y - dim.height, 2 * dim.width, 2 * dim.height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, (oldHints != null) ? oldHints : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
      }
    }
  }

  /** Returns whether creature shadow is semi-transparent. */
  protected boolean isTransparentShadow()
  {
    return transparentShadow;
  }

  /** Sets whether creature shadow is semi-transparent. */
  protected void setTransparentShadow(boolean b)
  {
    if (transparentShadow != b) {
      transparentShadow = b;
      spriteChanged();
    }
  }

  /**
   * Translates the specified color location index into a palette color offset.
   * @param locationIndex the location to translate.
   * @return the resulting palette color offset. Returns -1 if location is not supported.
   */
  protected int getColorOffset(int locationIndex)
  {
    int retVal = -1;
    if (locationIndex >= 0 && locationIndex < 7) {
      retVal = 4 + locationIndex * 12;
    }
    return retVal;
  }

  /**
   * Returns the palette data for the specified color entry.
   * @param colorIndex the color entry.
   * @return palette data as int array. Returns {@code null} if palette data could not be determined.
   */
  protected int[] getColorData(int colorIndex, boolean allowRandom)
  {
    int[] retVal = null;
    try {
      retVal = SpriteUtils.getColorGradient(colorIndex, allowRandom);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return retVal;
  }

  /**
   * Replaces false colors with color ranges defined in the associated CRE resource.
   * @param control the BAM controller.
   */
  protected void applyFalseColors(BamV1Control control, SegmentDef sd)
  {
    if (control == null || sd == null) {
      return;
    }

    // preparations
    final Map<Integer, int[]> colorRanges = new HashMap<Integer, int[]>();
    for (int loc = 0; loc < 7; loc++) {
      int ofs = getColorOffset(loc);
      Couple<Integer, Boolean> colorInfo = getCreatureInfo().getEffectiveColorValue(sd.getSpriteType(), loc);
      int colIdx = colorInfo.getValue0().intValue();
      boolean allowRandom = colorInfo.getValue1().booleanValue();
      if (ofs > 0 && colIdx >= 0) {
        int[] range = getColorData(colIdx, allowRandom);
        if (range != null) {
          colorRanges.put(ofs, range);
        }
      }
    }

    // Special: Off-hand weapon uses weapon colors
    if (sd.getSpriteType() == SegmentDef.SpriteType.SHIELD) {
      ItemInfo itemInfo = getCreatureInfo().getEquippedShield();
      if (itemInfo != null && itemInfo.getSlotType() == ItemInfo.SlotType.WEAPON) {
        ColorInfo colorInfo = itemInfo.getColorInfo();
        for (int loc = 0; loc < 7; loc++) {
          int ofs = getColorOffset(loc);
          int colIdx = colorInfo.getValue(SegmentDef.SpriteType.WEAPON, loc);
          if (ofs > 0 && colIdx >= 0) {
            int[] range = getColorData(colIdx, false);
            if (range != null) {
              colorRanges.put(ofs, range);
            }
          }
        }
      }
    }

    // applying colors
    int[] palette = control.getCurrentPalette();
    for (final Integer ofs : colorRanges.keySet()) {
      // replacing base ranges
      final int[] range = colorRanges.get(ofs);
      palette = SpriteUtils.replaceColors(palette, range, ofs.intValue(), range.length, false);
    }

    if (getAnimationType() != AnimationInfo.Type.MONSTER_PLANESCAPE) {
      // preparing offset array
      final int srcOfs = 4;
      final int dstOfs = 88;
      final int srcLen = 12;
      final int dstLen = 8;
      final int[] offsets = new int[colorRanges.size()];
      for (int i = 0; i < offsets.length; i++) {
        offsets[i] = srcOfs + i * srcLen;
      }

      // calculating mixed ranges
      int k = 0;
      for (int i = 0; i < offsets.length - 1; i++) {
        int ofs1 = offsets[i];
        for (int j = i + 1; j < offsets.length; j++, k++) {
          int ofs2 = offsets[j];
          int ofs3 = dstOfs + k * dstLen;
          palette = SpriteUtils.interpolateColors(palette, ofs1, ofs2, srcLen, ofs3, dstLen, false);
        }
      }

      // fixing special palette entries
      palette[2] = 0xFF000000;
      palette[3] = 0xFF000000;
    }

    control.setExternalPalette(palette);
  }

  /**
   * The specified frame is mirrored horizontally. Both pixel data and center point are adjusted.
   * @param frameIndex absolute frame index in the BAM frame list.
   */
  protected void flipImageHorizontal(int frameIndex)
  {
    PseudoBamFrameEntry frame = getFrameInfo(frameIndex);
    // flipping image horizontally
    BufferedImage image = frame.getFrame();
    AffineTransform at = AffineTransform.getScaleInstance(-1, 1);
    at.translate(-image.getWidth(), 0);
    AffineTransformOp op = new AffineTransformOp(at, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
    image = op.filter(image, null);
    // updating frame data
    frame.setFrame(image);
    frame.setCenterX(frame.getWidth() - frame.getCenterX() - 1);
  }

  /**
   * Applies translucency to the specified paletted image.
   * @param control the BAM controller.
   */
  protected void applyTranslucency(BamV1Control control)
  {
    if (control != null) {
      int alpha = getCreatureInfo().getEffectiveTranslucency();
      int[] palette = control.getCurrentPalette();

      // shadow color (alpha relative to semi-transparency of shadow)
      int alphaShadow = 255 - (palette[1] >>> 24);
      alphaShadow = alpha * alphaShadow / 255;
      alphaShadow <<= 24; // setting alpha mask
      palette[1] = alphaShadow | (palette[1] & 0x00ffffff);

      // creature colors
      alpha <<= 24; // setting alpha mask
      for (int i = 2; i < palette.length; i++) {
        palette[i] = alpha | (palette[i] & 0x00ffffff);
      }

      control.setExternalPalette(palette);
    }
  }

  /**
   * Returns whether the current CRE resource provides an animation that is compatible with the
   * {@code SpriteDecoder} class.
   * @return {@code true} if the animation type of the CRE is compatible with this {@code SpriteDecoder} instance.
   *         Returns {@code false} otherwise.
   */
  protected boolean isMatchingAnimationType()
  {
    boolean retVal = false;

    List<String> names = getAnimationFiles(true);
    if (!names.isEmpty()) {
      retVal = names.parallelStream().allMatch(ResourceFactory::resourceExists);
    }

    return retVal;
  }

  @Override
  public int hashCode()
  {
    int hash = super.hashCode();
    hash = 31 * hash + ((creInfo == null) ? 0 : creInfo.hashCode());
    hash = 31 * hash + ((ini == null) ? 0 : ini.hashCode());
    hash = 31 * hash + ((directionMap == null) ? 0 : directionMap.hashCode());
    hash = 31 * hash + ((attributesMap == null) ? 0 : attributesMap.hashCode());
    hash = 31 * hash + ((currentSequence == null) ? 0 : currentSequence.hashCode());
    hash = 31 * hash + Boolean.valueOf(showCircle).hashCode();
    hash = 31 * hash + Boolean.valueOf(selectionCircleBitmap).hashCode();
    hash = 31 * hash + Boolean.valueOf(showPersonalSpace).hashCode();
    hash = 31 * hash + Boolean.valueOf(showBoundingBox).hashCode();
    hash = 31 * hash + Boolean.valueOf(transparentShadow).hashCode();
    hash = 31 * hash + Boolean.valueOf(translucencyEnabled).hashCode();
    hash = 31 * hash + Boolean.valueOf(paletteReplacementEnabled).hashCode();
    hash = 31 * hash + Boolean.valueOf(renderSpriteAvatar).hashCode();
    hash = 31 * hash + Boolean.valueOf(renderSpriteWeapon).hashCode();
    hash = 31 * hash + Boolean.valueOf(renderSpriteHelmet).hashCode();
    hash = 31 * hash + Boolean.valueOf(renderSpriteShield).hashCode();
    hash = 31 * hash + Boolean.valueOf(animationChanged).hashCode();
    hash = 31 * hash + Boolean.valueOf(autoApplyChanges).hashCode();
    return hash;
  }

  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof SpriteDecoder)) {
      return false;
    }
    boolean retVal = super.equals(o);
    if (retVal) {
      SpriteDecoder other = (SpriteDecoder)o;
      retVal &= (this.creInfo == null && other.creInfo == null) ||
                (this.creInfo != null && this.creInfo.equals(other.creInfo));
      retVal &= (this.ini == null && other.ini == null) ||
                (this.ini != null && this.ini.equals(other.ini));
      retVal &= (this.directionMap == null && other.directionMap == null) ||
                (this.directionMap != null && this.directionMap.equals(other.directionMap));
      retVal &= (this.attributesMap == null && other.attributesMap == null) ||
                (this.attributesMap != null && this.attributesMap.equals(other.attributesMap));
      retVal &= (this.currentSequence == null && other.currentSequence == null) ||
                (this.currentSequence != null && this.currentSequence.equals(other.currentSequence));
      retVal &= (this.showCircle == other.showCircle);
      retVal &= (this.selectionCircleBitmap == other.selectionCircleBitmap);
      retVal &= (this.showPersonalSpace == other.showPersonalSpace);
      retVal &= (this.showBoundingBox == other.showBoundingBox);
      retVal &= (this.transparentShadow == other.transparentShadow);
      retVal &= (this.translucencyEnabled == other.translucencyEnabled);
      retVal &= (this.paletteReplacementEnabled == other.paletteReplacementEnabled);
      retVal &= (this.renderSpriteAvatar == other.renderSpriteAvatar);
      retVal &= (this.renderSpriteWeapon == other.renderSpriteWeapon);
      retVal &= (this.renderSpriteHelmet == other.renderSpriteHelmet);
      retVal &= (this.renderSpriteShield == other.renderSpriteShield);
      retVal &= (this.animationChanged == other.animationChanged);
      retVal &= (this.autoApplyChanges == other.autoApplyChanges);
    }
    return retVal;
  }

//-------------------------- INNER CLASSES --------------------------

  /**
   * Represents an operation that is called once per source BAM resource when creating a creature animation.
   */
  public interface BeforeSourceBam
  {
    /**
     * Performs this operation on the given arguments.
     * @param control the {@code BamV1Control} instance of the source BAM
     * @param sd the {@code SegmentDef} instance describing the source BAM.
     */
    void accept(BamV1Control control, SegmentDef sd);
  }

  /**
   * Represents a function that is called for each source frame before it is drawn onto the destination image.
   */
  public interface BeforeSourceFrame
  {
    /**
     * Performs this function on the given arguments.
     * @param sd the {@code SegmentDef} structure describing the given source frame.
     * @param srcImage {@code BufferedImage} object of the the source frame
     * @param g the {@code Graphics2D} object of the destination image.
     * @return the updated source frame image
     */
    BufferedImage apply(SegmentDef sd, BufferedImage srcImage, Graphics2D g);
  }

  /**
   * Represents an operation that is called for each source frame after it has been drawn onto the destination image.
   * It can be used to clean up modifications made to the {@code Graphics2D} instance
   * in the {@code BeforeSourceFrame} function.
   */
  public interface AfterSourceFrame
  {
    /**
     * Performs this operation on the given arguments.
     * @param sd the {@code SegmentDef} structure describing the current source frame.
     * @param g the {@code Graphics2D} object of the destination image.
     */
    void accept(SegmentDef sd, Graphics2D g);
  }

  /**
   * Represents an operation that is called for each destination frame after it has been created.
   */
  public interface AfterDestFrame
  {
    /**
     * Performs this operation on the given arguments.
     * @param dd the {@code DirDef} object defining the current destination cycle
     * @param frameIdx the absolute destination BAM frame index.
     */
    void accept(DirDef dd, int frameIdx);
  }
}
