package mchorse.chameleon.metamorph.editor;

import mchorse.chameleon.lib.ChameleonModel;
import mchorse.chameleon.metamorph.ChameleonMorph;
import mchorse.chameleon.metamorph.pose.AnimatedPose;
import mchorse.chameleon.metamorph.pose.AnimatedPoseTransform;
import mchorse.mclib.client.gui.framework.elements.GuiElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiButtonElement;
import mchorse.mclib.client.gui.framework.elements.buttons.GuiToggleElement;
import mchorse.mclib.client.gui.framework.elements.context.GuiContextMenu;
import mchorse.mclib.client.gui.framework.elements.context.GuiSimpleContextMenu;
import mchorse.mclib.client.gui.framework.elements.input.GuiColorElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTexturePicker;
import mchorse.mclib.client.gui.framework.elements.input.GuiTrackpadElement;
import mchorse.mclib.client.gui.framework.elements.input.GuiTransformations;
import mchorse.mclib.client.gui.framework.elements.list.GuiStringListElement;
import mchorse.mclib.client.gui.utils.Icons;
import mchorse.mclib.client.gui.utils.keys.IKey;
import mchorse.mclib.utils.Direction;
import mchorse.mclib.utils.resources.RLUtils;
import mchorse.metamorph.client.gui.editor.GuiAnimation;
import mchorse.metamorph.client.gui.editor.GuiMorphPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import java.io.File;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Custom model morph panel which allows editing custom textures for materials of the custom model morph
 */
public class GuiChameleonMainPanel extends GuiMorphPanel<ChameleonMorph, GuiChameleonMorph> implements IBonePicker
{
    /* Materials */
    public GuiButtonElement skin;
    public GuiTexturePicker picker;

    public GuiButtonElement skinBone;
    public GuiButtonElement makeDefault;
    public GuiButtonElement resetBone;
    public GuiButtonElement resetDefault;

    public GuiButtonElement createPose;
    public GuiStringListElement bones;
    public GuiToggleElement absoluteBrightness;
    public GuiToggleElement shading;
    public GuiTrackpadElement glow;
    public GuiColorElement color;
    public GuiToggleElement fixed;
    public GuiToggleElement animated;
    public GuiPoseTransformations transforms;
    public GuiAnimation animation;
    public GuiToggleElement player;

    public GuiTrackpadElement scale;
    public GuiTrackpadElement scaleGui;

    private IKey createLabel = IKey.lang("chameleon.gui.editor.create_pose");
    private IKey resetLabel = IKey.lang("chameleon.gui.editor.reset_pose");

    private AnimatedPoseTransform transform;

    public static GuiContextMenu createCopyPasteMenu(Runnable copy, Consumer<AnimatedPose> paste)
    {
        GuiSimpleContextMenu menu = new GuiSimpleContextMenu(Minecraft.getMinecraft());
        AnimatedPose pose = null;

        try
        {
            NBTTagCompound tag = JsonToNBT.getTagFromJson(GuiScreen.getClipboardString());
            AnimatedPose loaded = new AnimatedPose();

            loaded.fromNBT(tag);

            pose = loaded;
        }
        catch (Exception e)
        {
        }

        menu.action(Icons.COPY, IKey.lang("chameleon.gui.editor.context.copy"), copy);

        if (pose != null)
        {
            final AnimatedPose innerPose = pose;

            menu.action(Icons.PASTE, IKey.lang("chameleon.gui.editor.context.paste"), () -> paste.accept(innerPose));
        }

        return menu;
    }

    public GuiChameleonMainPanel(Minecraft mc, GuiChameleonMorph editor)
    {
        super(mc, editor);

        /* Materials view */
        this.skin = new GuiButtonElement(mc, IKey.lang("chameleon.gui.editor.pick_skin"), (b) ->
        {
            this.picker.refresh();
            this.picker.fill(this.morph.skin);
            this.picker.callback = (rl) -> this.morph.skin = RLUtils.clone(rl);
            this.add(this.picker);
            this.picker.resize();
        });
        
        this.skinBone = new GuiButtonElement(mc, IKey.lang("chameleon.gui.editor.pick_skin_bone"), (b) ->
        {
            this.picker.refresh();
            ResourceLocation current = this.morph.boneSkins.get(this.editor.chameleonModelRenderer.boneName);
            if (current == null) {
                current = this.morph.skin;
            }
            this.picker.fill(current);
            this.picker.callback = (rl) -> {
                if (rl != null) {
                    this.morph.boneSkins.put(this.editor.chameleonModelRenderer.boneName, RLUtils.clone(rl));
                } else {
                    this.morph.boneSkins.remove(this.editor.chameleonModelRenderer.boneName);
                }
                this.editor.chameleonModelRenderer.boneName = this.editor.chameleonModelRenderer.boneName;
            };
            this.add(this.picker);
            this.picker.resize();
        });

        this.makeDefault = new GuiButtonElement(mc, IKey.lang("chameleon.gui.editor.make_default"), (b) ->
        {
            ResourceLocation current = this.morph.boneSkins.get(this.editor.chameleonModelRenderer.boneName);
            if (current != null) {
                this.saveDefaultBoneSkin(this.editor.chameleonModelRenderer.boneName, current);
            }
        });

        this.resetBone = new GuiButtonElement(mc, IKey.lang("chameleon.gui.editor.reset_bone"), (b) ->
        {
            this.morph.boneSkins.remove(this.editor.chameleonModelRenderer.boneName);
            this.pickBone(this.editor.chameleonModelRenderer.boneName);
        });

        this.resetDefault = new GuiButtonElement(mc, IKey.lang("chameleon.gui.editor.reset_default"), (b) ->
        {
            this.removeDefaultBoneSkin(this.editor.chameleonModelRenderer.boneName);
            this.pickBone(this.editor.chameleonModelRenderer.boneName);
        });

        this.picker = new GuiTexturePicker(mc, (rl) -> this.morph.skin = RLUtils.clone(rl));

        this.createPose = new GuiButtonElement(mc, this.createLabel, this::createResetPose);
        this.bones = new GuiStringListElement(mc, this::pickBone);
        this.bones.background().context(() -> createCopyPasteMenu(this::copyCurrentPose, this::pastePose));
        this.absoluteBrightness = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.absolute_brightness"), this::toggleAbsoluteBrightness);
        this.shading = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.shading"), this::toggleShading);
        this.glow = new GuiTrackpadElement(mc, this::setGlow).limit(0, 1).values(0.01, 0.1, 0.001);
        this.glow.tooltip(IKey.lang("chameleon.gui.editor.glow"));
        this.color = new GuiColorElement(mc, this::setColor).direction(Direction.RIGHT);
        this.color.tooltip(IKey.lang("chameleon.gui.editor.color"));
        this.color.picker.editAlpha();
        this.fixed = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.fixed"), this::toggleFixed);
        this.animated = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.animated"), this::toggleAnimated);
        this.transforms = new GuiPoseTransformations(mc);
        this.animation = new GuiAnimation(mc, false);
        this.player = new GuiToggleElement(mc, IKey.lang("chameleon.gui.editor.player"), this::togglePlayer);

        this.scale = new GuiTrackpadElement(mc, (value) -> this.morph.scale = value.floatValue());
        this.scale.tooltip(IKey.lang("chameleon.gui.editor.scale"));
        this.scaleGui = new GuiTrackpadElement(mc, (value) -> this.morph.scaleGui = value.floatValue());
        this.scaleGui.tooltip(IKey.lang("chameleon.gui.editor.scale_gui"));

        this.skin.flex().relative(this).set(10, 10, 190, 20);
        this.createPose.flex().relative(this.skin).y(1F, 5).w(1F).h(20);
        
        this.skinBone.flex().relative(this.createPose).y(1F, 5).w(1F).h(20);
        this.makeDefault.flex().relative(this.skinBone).y(1F, 5).w(0.5F, -2).h(20);
        this.resetBone.flex().relative(this.makeDefault).x(1F, 4).w(1F, 0).h(20);
        this.resetDefault.flex().relative(this.makeDefault).y(1F, 5).w(2F, 4).h(20);

        this.picker.flex().relative(this).wh(1F, 1F);

        this.bones.flex().relative(this.resetDefault).y(1F, 5).w(1F).hTo(this.absoluteBrightness.flex(), -10);
        this.animated.flex().relative(this).x(10).y(1F, -10).w(190).anchorY(1);
        this.fixed.flex().relative(this.animated).y(-1F, -5).w(1F);
        this.color.flex().relative(this.fixed).y(-1F, -10).w(1F);
        this.glow.flex().relative(this.color).y(-1F, -10).w(1F);
        this.shading.flex().relative(this.glow).y(-1F, -10).w(1F);
        this.absoluteBrightness.flex().relative(this.shading).y(-1F, -10).w(1F);
        this.transforms.flex().relative(this).set(0, 0, 256, 70).x(0.5F, -128).y(1, -80);
        this.animation.flex().relative(this).x(1F, -130).w(130);

        this.player.flex().relative(this.animation.pickInterpolation).x(0F).y(1F, 5).w(1F);
        this.player.tooltip(IKey.lang("chameleon.gui.editor.player_tooltip"));
        this.animation.addBefore(this.animation.interpolations, this.player);

        GuiSimpleContextMenu abMenu = new GuiSimpleContextMenu(Minecraft.getMinecraft());
        GuiSimpleContextMenu glowMenu = new GuiSimpleContextMenu(Minecraft.getMinecraft());
        GuiSimpleContextMenu colorMenu = new GuiSimpleContextMenu(Minecraft.getMinecraft());
        GuiSimpleContextMenu fixateMenu = new GuiSimpleContextMenu(Minecraft.getMinecraft());

        abMenu.action(IKey.lang("chameleon.gui.editor.context.children"), this.applyToChildren((p, c) -> c.absoluteBrightness = p.absoluteBrightness));
        glowMenu.action(IKey.lang("chameleon.gui.editor.context.children"), this.applyToChildren((p, c) -> c.glow = p.glow));
        colorMenu.action(IKey.lang("chameleon.gui.editor.context.children"), this.applyToChildren((p, c) -> c.color.copy(p.color)));
        fixateMenu.action(IKey.lang("chameleon.gui.editor.context.children"), this.applyToChildren((p, c) -> c.fixed = p.fixed));

        this.absoluteBrightness.context(() -> abMenu);
        this.glow.context(() -> glowMenu);
        this.color.context(() -> colorMenu);
        this.fixed.context(() -> fixateMenu);

        GuiElement lowerBottom = new GuiElement(mc);

        lowerBottom.flex().relative(this).xy(1F, 1F).w(130).anchor(1F, 1F).column(5).vertical().stretch().padding(10);
        lowerBottom.add(this.scale, this.scaleGui);

        this.add(this.skin, this.skinBone, this.makeDefault, this.resetBone, this.resetDefault, this.createPose, this.animated, this.fixed, this.color, this.glow, this.shading, this.absoluteBrightness, this.bones, this.transforms, this.animation, lowerBottom);
    }

    private void copyCurrentPose()
    {
        GuiScreen.setClipboardString(this.morph.pose.toNBT().toString());
    }

    private void pastePose(AnimatedPose pose)
    {
        this.morph.pose.copy(pose);
        this.transforms.set(this.transforms.trans);
    }

    private Runnable applyToChildren(BiConsumer<AnimatedPoseTransform, AnimatedPoseTransform> apply)
    {
        return () ->
        {
            String bone = this.bones.getCurrentFirst();
            AnimatedPoseTransform anim = this.morph.pose.bones.get(bone);
            List<String> children = this.morph.getModel().getChildren(bone);

            for (String child : children)
            {
                AnimatedPoseTransform childAnim = this.morph.pose.bones.get(child);

                apply.accept(anim, childAnim);
            }
        };
    }

    private void createResetPose(GuiButtonElement button)
    {
        if (this.morph.pose == null)
        {
            AnimatedPose pose = new AnimatedPose();
            List<String> bones = this.morph.getModel().getBoneNames();

            for (String bone : bones)
            {
                pose.bones.put(bone, new AnimatedPoseTransform(bone));
            }

            this.morph.pose = pose;
        }
        else
        {
            this.morph.pose = null;
            this.editor.chameleonModelRenderer.boneName = "";
        }

        this.setPoseEditorVisible();
    }

    private void pickBone(List<String> bone)
    {
        this.pickBone(bone.get(0));
    }

    @Override
    public void pickBone(String bone)
    {
        if (this.morph.pose == null)
        {
            return;
        }

        this.transform = this.morph.pose.bones.get(bone);

        if (this.transform == null)
        {
            this.transform = new AnimatedPoseTransform(bone);
            this.morph.pose.bones.put(bone, this.transform);
        }

        this.bones.setCurrentScroll(bone);
        this.animated.toggled(this.morph.pose.animated == AnimatedPoseTransform.ANIMATED);
        this.fixed.toggled(this.transform.fixed == AnimatedPoseTransform.FIXED);
        this.color.picker.setColor(this.transform.color.getRGBAColor());
        this.absoluteBrightness.toggled(this.transform.absoluteBrightness);
        this.shading.toggled(this.transform.shading);
        this.glow.setValue(this.transform.glow);
        this.transforms.set(this.transform);
        this.editor.chameleonModelRenderer.boneName = bone;
    }

    private void toggleAbsoluteBrightness(GuiToggleElement toggle)
    {
        this.transform.absoluteBrightness = toggle.isToggled();
    }

    private void toggleShading(GuiToggleElement toggle)
    {
        this.transform.shading = toggle.isToggled();
    }

    private void setGlow(Double value)
    {
        this.transform.glow = value.floatValue();
    }

    private void setColor(int color)
    {
        this.transform.color.set(color);
    }

    private void toggleFixed(GuiToggleElement toggle)
    {
        this.transform.fixed = toggle.isToggled() ? AnimatedPoseTransform.FIXED : AnimatedPoseTransform.ANIMATED;
    }

    private void toggleAnimated(GuiToggleElement toggle)
    {
        this.morph.pose.animated = toggle.isToggled() ? AnimatedPoseTransform.ANIMATED : AnimatedPoseTransform.FIXED;
    }

    private void togglePlayer(GuiToggleElement toggle)
    {
        this.morph.isActionPlayer = toggle.isToggled();
    }

    @Override
    public void fillData(ChameleonMorph morph)
    {
        super.fillData(morph);

        this.picker.removeFromParent();
        this.setPoseEditorVisible();

        this.animation.fill(morph.animation);
        this.scale.setValue(morph.scale);
        this.scaleGui.setValue(morph.scaleGui);
        this.player.toggled(morph.isActionPlayer);
    }

    @Override
    public void finishEditing()
    {
        this.picker.close();
    }

    private void setPoseEditorVisible()
    {
        ChameleonModel model = this.morph.getModel();
        AnimatedPose pose = this.morph.pose;

        this.createPose.setVisible(model != null && !model.getBoneNames().isEmpty());
        this.createPose.label = pose == null ? this.createLabel : this.resetLabel;
        this.bones.setVisible(model != null && pose != null);
        this.skinBone.setVisible(model != null && pose != null);
        this.makeDefault.setVisible(model != null && pose != null);
        this.resetBone.setVisible(model != null && pose != null);
        this.resetDefault.setVisible(model != null && pose != null);
        this.absoluteBrightness.setVisible(model != null && pose != null);
        this.shading.setVisible(model != null && pose != null);
        this.glow.setVisible(model != null && pose != null);
        this.color.setVisible(model != null && pose != null);
        this.fixed.setVisible(model != null && pose != null);
        this.animated.setVisible(model != null && pose != null);
        this.transforms.setVisible(model != null && pose != null);

        if (pose == null)
        {
            this.bones.flex().relative(this.createPose);
        }
        else
        {
            this.bones.flex().relative(this.resetDefault);
        }

        if (model != null)
        {
            this.bones.clear();
            this.bones.add(model.getBoneNames());
            this.bones.sort();

            if (this.morph.pose != null)
            {
                this.pickBone(model.getBoneNames().get(0));
            }
        }

        if (this.getParent() != null)
        {
            this.getParent().resize();
        }
    }

    private void saveDefaultBoneSkin(String boneName, ResourceLocation current)
    {
        File folder = new File(mchorse.chameleon.ClientProxy.modelsFile, this.morph.getKey());
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File config = new File(folder, "config.json");
        JsonObject json = new JsonObject();
        if (config.exists()) {
            try (FileReader reader = new FileReader(config)) {
                json = new JsonParser().parse(reader).getAsJsonObject();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        JsonObject textures;
        if (json.has("textures") && json.get("textures").isJsonObject()) {
            textures = json.getAsJsonObject("textures");
        } else {
            textures = new JsonObject();
            json.add("textures", textures);
        }
        
        textures.addProperty(boneName, current.toString());
        
        try (FileWriter writer = new FileWriter(config)) {
            writer.write(json.toString());
            
            // Also update the loaded model in ClientProxy
            ChameleonModel model = this.morph.getModel();
            if (model != null) {
                model.defaultBoneSkins.put(boneName, current);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeDefaultBoneSkin(String boneName)
    {
        File folder = new File(mchorse.chameleon.ClientProxy.modelsFile, this.morph.getKey());
        File config = new File(folder, "config.json");
        if (!config.exists()) {
            return;
        }

        JsonObject json = new JsonObject();
        try (FileReader reader = new FileReader(config)) {
            json = new JsonParser().parse(reader).getAsJsonObject();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (json.has("textures") && json.get("textures").isJsonObject()) {
            JsonObject textures = json.getAsJsonObject("textures");
            if (textures.has(boneName)) {
                textures.remove(boneName);
                
                if (textures.entrySet().isEmpty()) {
                    json.remove("textures");
                }

                try (FileWriter writer = new FileWriter(config)) {
                    if (json.entrySet().isEmpty()) {
                        writer.close();
                        config.delete();
                    } else {
                        writer.write(json.toString());
                    }
                    
                    // Also update the loaded model in ClientProxy
                    ChameleonModel model = this.morph.getModel();
                    if (model != null) {
                        model.defaultBoneSkins.remove(boneName);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class GuiPoseTransformations extends GuiTransformations
    {
        public AnimatedPoseTransform trans;

        public GuiPoseTransformations(Minecraft mc)
        {
            super(mc);
        }

        @Override
        protected void localTranslate(double x, double y, double z)
        {
            this.trans.addTranslation(x, y, z, GuiStaticTransformOrientation.getOrientation());

            this.fillT(this.trans.x, this.trans.y, this.trans.z);
        }

        public void set(AnimatedPoseTransform trans)
        {
            this.trans = trans;

            if (trans != null)
            {
                this.fillT(-trans.x, trans.y, trans.z);
                this.fillS(trans.scaleX, trans.scaleY, trans.scaleZ);
                this.fillR(trans.rotateX / (float) Math.PI * 180, trans.rotateY / (float) Math.PI * 180, trans.rotateZ / (float) Math.PI * 180);
            }
        }

        @Override
        public void setT(double x, double y, double z)
        {
            this.trans.x = (float) -x;
            this.trans.y = (float) y;
            this.trans.z = (float) z;
        }

        @Override
        public void setS(double x, double y, double z)
        {
            this.trans.scaleX = (float) x;
            this.trans.scaleY = (float) y;
            this.trans.scaleZ = (float) z;
        }

        @Override
        public void setR(double x, double y, double z)
        {
            /* That was a bad idea... */
            this.trans.rotateX = (float) (x / 180F * (float) Math.PI);
            this.trans.rotateY = (float) (y / 180F * (float) Math.PI);
            this.trans.rotateZ = (float) (z / 180F * (float) Math.PI);
        }
    }
}
