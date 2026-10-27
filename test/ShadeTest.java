import java.lang.reflect.*;
public class ShadeTest {
  public static void main(String[] a) throws Exception {
    String res = System.getProperty("g3darmy.res", System.getenv("G3DARMY_RES"));
    if (res == null) { throw new IllegalArgumentException("pass -Dg3darmy.res=<hotplugins/resources/G3DArmy>"); }
    Class<?> c = Class.forName("com.saysky.g3darmy.G3DArmyMin");
    Class<?> md = Class.forName("com.badlogic.gdx.graphics.g3d.model.data.ModelData");
    Method m = c.getDeclaredMethod("applyVerticalShade", md); m.setAccessible(true);
    com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader l = new com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader(new com.badlogic.gdx.utils.JsonReader());
    Object data = l.loadModelData(new com.badlogic.gdx.files.FileHandle(new java.io.File(res, "g3d\\foot_euro_ancient.g3dj")), null);
    Object r = m.invoke(null, data);
    System.out.println("shadedMeshes=" + r);
    java.lang.reflect.Field f = data.getClass().getField("meshes");
    com.badlogic.gdx.utils.Array<?> meshes = (com.badlogic.gdx.utils.Array<?>) f.get(data);
    Object mesh0 = meshes.get(0);
    java.lang.reflect.Field fa = mesh0.getClass().getField("attributes");
    java.lang.reflect.Field fv = mesh0.getClass().getField("vertices");
    com.badlogic.gdx.graphics.VertexAttribute[] attrs = (com.badlogic.gdx.graphics.VertexAttribute[]) fa.get(mesh0);
    float[] verts = (float[]) fv.get(mesh0);
    int stride = 0; for (com.badlogic.gdx.graphics.VertexAttribute at : attrs) stride += at.numComponents;
    int verts_n = verts.length / stride;
    float lo = 9f, hi = -9f; int iLo=-1, iHi=-1;
    for (int v = 0; v < verts_n; v++) { float k = verts[v*stride + stride - 4]; if (k < lo) { lo = k; iLo=v; } if (k > hi) { hi = k; iHi=v; } }
    System.out.println("attrs=" + java.util.Arrays.toString(attrs));
    System.out.println("stride=" + stride + " verts=" + verts_n);
    System.out.println("color min=" + lo + " (v" + iLo + ") max=" + hi + " (v" + iHi + ")");
  }
}
