package mchorse.chameleon.lib.data.model;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class ModelPolyMesh
{
    public boolean normalizedUvs;
    public final List<Vector3f> positions = new ArrayList<Vector3f>();
    public final List<Vector3f> normals = new ArrayList<Vector3f>();
    public final List<Vector2f> uvs = new ArrayList<Vector2f>();
    public final List<ModelPoly> polys = new ArrayList<ModelPoly>();

    public static class ModelPoly
    {
        public final List<ModelPolyVertex> vertices = new ArrayList<ModelPolyVertex>();
    }

    public static class ModelPolyVertex
    {
        public int positionIndex;
        public int normalIndex;
        public int uvIndex;

        public ModelPolyVertex(int positionIndex, int normalIndex, int uvIndex)
        {
            this.positionIndex = positionIndex;
            this.normalIndex = normalIndex;
            this.uvIndex = uvIndex;
        }
    }
}
