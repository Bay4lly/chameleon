package mchorse.chameleon.lib.parsing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelCube;
import mchorse.chameleon.lib.data.model.ModelPolyMesh;
import mchorse.chameleon.lib.data.model.ModelUV;
import net.minecraft.client.util.JsonException;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelParser
{
    public static Model parse(JsonObject object) throws JsonException
    {
        Model model = new Model();

        object = object.get("minecraft:geometry").getAsJsonArray().get(0).getAsJsonObject();

        if (object.has("description"))
        {
            parseDescription(model, object.get("description").getAsJsonObject());
        }

        if (object.has("bones"))
        {
            parseBones(model, object.get("bones").getAsJsonArray());
        }

        return model;
    }

    private static void parseDescription(Model model, JsonObject object)
    {
        if (object.has("identifier"))
        {
            model.id = object.get("identifier").getAsString();
        }

        if (object.has("texture_width"))
        {
            model.textureWidth = object.get("texture_width").getAsInt();
        }

        if (object.has("texture_height"))
        {
            model.textureHeight = object.get("texture_height").getAsInt();
        }
    }

    private static void parseBones(Model model, JsonArray bones)
    {
        Map<String, List<String>> hierarchy = new HashMap<String, List<String>>();
        Map<String, ModelBone> flatBones = new HashMap<String, ModelBone>();

        for (JsonElement element : bones)
        {
            JsonObject boneElement = element.getAsJsonObject();
            ModelBone bone = new ModelBone(boneElement.get("name").getAsString());

            /* Fill hierarchy information */
            String parent = boneElement.has("parent") ? boneElement.get("parent").getAsString() : "";
            List<String> list = hierarchy.computeIfAbsent(parent, (a) -> new ArrayList<String>());

            list.add(bone.id);

            /* Setup initial transformations */
            if (boneElement.has("pivot"))
            {
                parseVector(boneElement.get("pivot"), bone.initial.translate);
            }

            if (boneElement.has("scale"))
            {
                parseVector(boneElement.get("scale"), bone.initial.scale);
            }

            if (boneElement.has("rotation"))
            {
                parseVector(boneElement.get("rotation"), bone.initial.rotation);

                bone.initial.rotation.x *= -1;
                bone.initial.rotation.y *= -1;
            }

            bone.initial.translate.x *= -1;

            /* Setup cubes */
            if (boneElement.has("cubes"))
            {
                parseCubes(model, bone, boneElement.get("cubes").getAsJsonArray());
            }

            if (boneElement.has("poly_mesh"))
            {
                parsePolyMesh(model, bone, boneElement.get("poly_mesh").getAsJsonObject());
            }

            flatBones.put(bone.id, bone);
        }

        /* Setup hierarchy */
        for (Map.Entry<String, List<String>> entry : hierarchy.entrySet())
        {
            if (entry.getKey().isEmpty())
            {
                continue;
            }

            ModelBone bone = flatBones.get(entry.getKey());

            for (String child : entry.getValue())
            {
                bone.children.add(flatBones.get(child));
            }
        }

        List<String> topLevel = hierarchy.get("");

        if (topLevel != null)
        {
            for (String topLevelBone : topLevel)
            {
                model.bones.add(flatBones.get(topLevelBone));
            }
        }
    }

    private static void parseCubes(Model model, ModelBone bone, JsonArray cubes)
    {
        for (JsonElement element : cubes)
        {
            bone.cubes.add(parseCube(model, element.getAsJsonObject()));
        }
    }

    private static void parsePolyMesh(Model model, ModelBone bone, JsonObject object)
    {
        ModelPolyMesh mesh = new ModelPolyMesh();

        mesh.normalizedUvs = object.has("normalized_uvs") && object.get("normalized_uvs").getAsBoolean();

        if (object.has("positions"))
        {
            for (JsonElement element : object.get("positions").getAsJsonArray())
            {
                Vector3f vector = new Vector3f();

                parseVector(element, vector);
                vector.x *= -1;
                vector.scale(1 / 16F);
                mesh.positions.add(vector);
            }
        }

        if (object.has("uvs"))
        {
            float tw = 1F / model.textureWidth;
            float th = 1F / model.textureHeight;

            for (JsonElement element : object.get("uvs").getAsJsonArray())
            {
                Vector2f vector = new Vector2f();

                parseVector(element, vector);

                if (!mesh.normalizedUvs)
                {
                    vector.x *= tw;
                    vector.y *= th;
                }
                
                vector.y = 1.0f - vector.y;

                mesh.uvs.add(vector);
            }
        }

        if (object.has("polys"))
        {
            for (JsonElement element : object.get("polys").getAsJsonArray())
            {
                ModelPolyMesh.ModelPoly poly = new ModelPolyMesh.ModelPoly();
                JsonArray vertexArrayJson = element.getAsJsonArray();
                int size = vertexArrayJson.size();

                Vector3f normal = new Vector3f(0, 1, 0);

                if (size >= 3)
                {
                    int idx0 = vertexArrayJson.get(size - 1).getAsJsonArray().get(0).getAsInt();
                    int idx1 = vertexArrayJson.get(size - 2).getAsJsonArray().get(0).getAsInt();
                    int idx2 = vertexArrayJson.get(size - 3).getAsJsonArray().get(0).getAsInt();

                    Vector3f p0 = mesh.positions.get(idx0);
                    Vector3f p1 = mesh.positions.get(idx1);
                    Vector3f p2 = mesh.positions.get(idx2);

                    Vector3f v1 = new Vector3f(p1);
                    v1.sub(p0);
                    Vector3f v2 = new Vector3f(p2);
                    v2.sub(p0);

                    normal.cross(v2, v1);

                    if (normal.lengthSquared() > 0)
                    {
                        normal.normalize();
                    }
                    else
                    {
                        normal.set(0, 1, 0);
                    }
                }

                int normalIndex = mesh.normals.size();
                mesh.normals.add(normal);

                for (int i = size - 1; i >= 0; i--)
                {
                    JsonArray vertexArray = vertexArrayJson.get(i).getAsJsonArray();

                    poly.vertices.add(new ModelPolyMesh.ModelPolyVertex(
                        vertexArray.get(0).getAsInt(),
                        normalIndex,
                        vertexArray.get(2).getAsInt()
                    ));
                }

                mesh.polys.add(poly);
            }
        }

        bone.polyMesh = mesh;
    }

    private static ModelCube parseCube(Model model, JsonObject object)
    {
        ModelCube cube = new ModelCube();

        if (object.has("inflate"))
        {
            cube.inflate = object.get("inflate").getAsFloat();
        }

        parseVector(object.get("origin"), cube.origin);
        parseVector(object.get("size"), cube.size);

        if (object.has("pivot"))
        {
            parseVector(object.get("pivot"), cube.pivot);
        }
        else
        {
            cube.pivot.set(cube.origin);
        }

        cube.origin.x *= -1;
        cube.pivot.x *= -1;

        if (object.has("rotation"))
        {
            parseVector(object.get("rotation"), cube.rotation);

            cube.rotation.x *= -1;
            cube.rotation.y *= -1;
        }

        if (object.has("uv"))
        {
            boolean mirror = object.has("mirror") && object.get("mirror").getAsBoolean();

            parseUV(cube, object.get("uv"), mirror);
        }

        cube.generateQuads(model);

        return cube;
    }

    private static void parseUV(ModelCube cube, JsonElement element, boolean mirror)
    {
        if (element.isJsonArray())
        {
            Vector2f boxUV = new Vector2f();

            parseVector(element.getAsJsonArray(), boxUV);
            cube.setupBoxUV(boxUV, mirror);
        }
        else if (element.isJsonObject())
        {
            JsonObject sides = element.getAsJsonObject();

            if (sides.has("north")) cube.north = parseUVSide(sides.get("north").getAsJsonObject());
            if (sides.has("east")) cube.east = parseUVSide(sides.get("east").getAsJsonObject());
            if (sides.has("south")) cube.south = parseUVSide(sides.get("south").getAsJsonObject());
            if (sides.has("west")) cube.west = parseUVSide(sides.get("west").getAsJsonObject());
            if (sides.has("up")) cube.up = parseUVSide(sides.get("up").getAsJsonObject());
            if (sides.has("down")) cube.down = parseUVSide(sides.get("down").getAsJsonObject());
        }
    }

    private static ModelUV parseUVSide(JsonObject uvSide)
    {
        ModelUV uv = new ModelUV();

        parseVector(uvSide.get("uv"), uv.origin);
        parseVector(uvSide.get("uv_size"), uv.size);

        return uv;
    }

    private static void parseVector(JsonElement element, Vector3f vector)
    {
        JsonArray array = element.getAsJsonArray();

        vector.x = array.get(0).getAsFloat();
        vector.y = array.get(1).getAsFloat();
        vector.z = array.get(2).getAsFloat();
    }

    private static void parseVector(JsonElement element, Vector2f vector)
    {
        JsonArray array = element.getAsJsonArray();

        vector.x = array.get(0).getAsFloat();
        vector.y = array.get(1).getAsFloat();
    }
}