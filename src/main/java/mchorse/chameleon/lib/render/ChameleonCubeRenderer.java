package mchorse.chameleon.lib.render;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelCube;
import mchorse.chameleon.lib.data.model.ModelPolyMesh;
import mchorse.chameleon.lib.data.model.ModelQuad;
import mchorse.chameleon.lib.data.model.ModelVertex;
import mchorse.chameleon.lib.utils.MatrixStack;
import mchorse.mclib.utils.Interpolation;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.renderer.Tessellator;
import mchorse.mclib.client.render.VertexBuilder;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.Map;

/**
 * Cube renderer
 *
 * Renders given bones from the model as cubes, fully
 */
@SideOnly(Side.CLIENT)
public class ChameleonCubeRenderer implements IChameleonRenderProcessor
{
    private float r;
    private float g;
    private float b;
    private float a;

    /* Temporary variables to avoid allocating and GC vectors */
    private Vector3f normal = new Vector3f();
    private Vector4f vertex = new Vector4f();

    private ResourceLocation currentSkin;
    private ResourceLocation defaultSkin;
    private Map<String, ResourceLocation> boneSkins;
    private Map<String, ResourceLocation> defaultBoneSkins;

    public void prepare(ResourceLocation skin, Map<String, ResourceLocation> boneSkins, Map<String, ResourceLocation> defaultBoneSkins)
    {
        this.currentSkin = skin;
        this.defaultSkin = skin;
        this.boneSkins = boneSkins;
        this.defaultBoneSkins = defaultBoneSkins;
    }

    @Override
    public boolean renderBone(BufferBuilder builder, MatrixStack stack, ModelBone bone)
    {
        int lightX = (int) OpenGlHelper.lastBrightnessX;
        int lightY = (int) OpenGlHelper.lastBrightnessY;

        this.r = bone.color.r;
        this.g = bone.color.g;
        this.b = bone.color.b;
        this.a = bone.color.a;

        if (bone.absoluteBrightness)
        {
            lightX = 0;
        }

        lightX = (int) Interpolation.LINEAR.interpolate(lightX, 240, bone.glow);

        ResourceLocation boneSkin = null;
        if (this.boneSkins != null && this.boneSkins.containsKey(bone.id)) {
            boneSkin = this.boneSkins.get(bone.id);
        } else if (this.defaultBoneSkins != null && this.defaultBoneSkins.containsKey(bone.id)) {
            boneSkin = this.defaultBoneSkins.get(bone.id);
        }

        ResourceLocation skin = boneSkin != null ? boneSkin : this.defaultSkin;

        if (skin != null && !skin.equals(this.currentSkin)) {
            Tessellator.getInstance().draw();
            Minecraft.getMinecraft().renderEngine.bindTexture(skin);
            builder.begin(GL11.GL_QUADS, VertexBuilder.getFormat(true, true, true, true));
            this.currentSkin = skin;
        }

        for (ModelCube cube : bone.cubes)
        {
            renderCube(builder, stack, cube, lightX, lightY, bone.shading);
        }

        if (bone.polyMesh != null)
        {
            /* Flush the current GL_QUADS batch before switching to GL_TRIANGLES for the mesh */
            ResourceLocation activeSkin = this.currentSkin;
            Tessellator.getInstance().draw();

            renderMesh(stack, bone.polyMesh, lightX, lightY, bone.shading);

            /* Restart GL_QUADS batch for any subsequent cubes/bones */
            if (activeSkin != null) {
                Minecraft.getMinecraft().renderEngine.bindTexture(activeSkin);
            }
            builder.begin(GL11.GL_QUADS, VertexBuilder.getFormat(true, true, true, true));
        }

        return false;
    }

    /**
     * Renders a poly mesh using GL_TRIANGLES with calcTangent after every triangle,
     * matching Blockbuster's OBJ renderer pipeline for correct shader mod support.
     * Each polygon (tri or quad) is triangulated as fan triangles from vertex 0.
     */
    private void renderMesh(MatrixStack stack, ModelPolyMesh mesh, int lightX, int lightY, boolean shading)
    {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.getBuffer();
        builder.begin(GL11.GL_TRIANGLES, VertexBuilder.getFormat(true, true, true, true));

        for (ModelPolyMesh.ModelPoly poly : mesh.polys)
        {
            int size = poly.vertices.size();
            if (size < 3) continue;

            /* Compute face normal in local space, then apply normal matrix — same as renderCube */
            if (shading)
            {
                Vector3f p0 = mesh.positions.get(poly.vertices.get(0).positionIndex);
                Vector3f p1 = mesh.positions.get(poly.vertices.get(1).positionIndex);
                Vector3f p2 = mesh.positions.get(poly.vertices.get(2).positionIndex);

                Vector3f e1 = new Vector3f(p1.x - p0.x, p1.y - p0.y, p1.z - p0.z);
                Vector3f e2 = new Vector3f(p2.x - p0.x, p2.y - p0.y, p2.z - p0.z);

                this.normal.cross(e2, e1);

                if (this.normal.lengthSquared() > 0)
                {
                    this.normal.normalize();
                }
                else
                {
                    this.normal.set(0, 1, 0);
                }

                stack.getNormalMatrix().transform(this.normal);

                if (this.normal.lengthSquared() > 0)
                {
                    this.normal.normalize();
                }
            }
            else
            {
                this.normal.set(0, 1, 0);
            }

            /* Fan-triangulate: (0,1,2), (0,2,3), (0,3,4) ... */
            for (int i = 1; i < size - 1; i++)
            {
                writeVertex(builder, stack, mesh, poly.vertices.get(0), lightX, lightY);
                writeVertex(builder, stack, mesh, poly.vertices.get(i), lightX, lightY);
                writeVertex(builder, stack, mesh, poly.vertices.get(i + 1), lightX, lightY);

                /* calcTangent for shader mod tangent space — identical to Blockbuster OBJ */
                VertexBuilder.calcTangent(builder, false);
            }
        }

        tess.draw();
    }

    private void writeVertex(BufferBuilder builder, MatrixStack stack, ModelPolyMesh mesh,
                             ModelPolyMesh.ModelPolyVertex v, int lightX, int lightY)
    {
        Vector3f position = mesh.positions.get(v.positionIndex);
        Vector2f uv = mesh.uvs.get(v.uvIndex);

        this.vertex.set(position);
        this.vertex.w = 1;
        stack.getModelMatrix().transform(this.vertex);

        builder.pos(this.vertex.x, this.vertex.y, this.vertex.z)
            .color(this.r, this.g, this.b, this.a)
            .tex(uv.x, uv.y)
            .lightmap(lightY, lightX)
            .normal(this.normal.x, this.normal.y, this.normal.z)
            .endVertex();
    }

    private void renderCube(BufferBuilder builder, MatrixStack stack, ModelCube cube, int lightX, int lightY, boolean shading)
    {
        stack.push();
        stack.moveToCubePivot(cube);
        stack.rotateCube(cube);
        stack.moveBackFromCubePivot(cube);

        for (ModelQuad quad : cube.quads)
        {
            if (shading)
            {
                this.normal.set(quad.normal.x, quad.normal.y, quad.normal.z);
                stack.getNormalMatrix().transform(this.normal);

                /* For 0 sized cubes on either axis, to avoid getting dark shading on models
                 * which didn't correctly setup the UV faces.
                 *
                 * For example two wings, first wing uses top face for texturing the flap,
                 * and second wing uses bottom face as a flap. In the end, the second wing
                 * will appear dark shaded without this fix.
                 */
                if (this.normal.getX() < 0 && (cube.size.y == 0 || cube.size.z == 0)) this.normal.x *= -1;
                if (this.normal.getY() < 0 && (cube.size.x == 0 || cube.size.z == 0)) this.normal.y *= -1;
                if (this.normal.getZ() < 0 && (cube.size.x == 0 || cube.size.y == 0)) this.normal.z *= -1;

                if (this.normal.lengthSquared() > 0)
                {
                    this.normal.normalize();
                }
            }
            else
            {
                this.normal.set(0, 1, 0);
            }

            for (ModelVertex vertex : quad.vertices)
            {
                this.vertex.set(vertex.position);
                this.vertex.w = 1;
                stack.getModelMatrix().transform(this.vertex);

                builder.pos(this.vertex.x, this.vertex.y, this.vertex.z)
                    .color(this.r, this.g, this.b, this.a)
                    .tex(vertex.uv.x, vertex.uv.y)
                    .lightmap(lightY, lightX)
                    .normal(this.normal.x, this.normal.y, this.normal.z)
                    .endVertex();
            }
        }

        stack.pop();
    }
}