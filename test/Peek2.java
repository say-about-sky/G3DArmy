import java.lang.reflect.*;
import java.util.*;
public class Peek2 {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("com.saysky.g3darmy.G3DArmyMin");
        String res = System.getProperty("g3darmy.res", System.getenv("G3DARMY_RES"));
        if (res == null) { throw new IllegalArgumentException("pass -Dg3darmy.res=<hotplugins/resources/G3DArmy>"); }
        Field dp = c.getDeclaredField("dataDirPath"); dp.setAccessible(true);
        dp.set(null, res);
        Method idx = c.getDeclaredMethod("ensurePartsIndex"); idx.setAccessible(true);
        idx.invoke(null);
        Field pf = c.getDeclaredField("partsIndex"); pf.setAccessible(true);
        Map<?,?> map = (Map<?,?>) pf.get(null);
        System.out.println("partsIndex=" + map.size());
        Object entry = map.get("unit_weapon_sword_metal");
        System.out.println("entry=" + (entry == null ? "null" : "ok"));
        Field cpd = c.getDeclaredField("confPartsDir"); cpd.setAccessible(true);
        System.out.println("confPartsDir=" + cpd.get(null));
        com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader l = new com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader(new com.badlogic.gdx.utils.JsonReader());
        com.badlogic.gdx.graphics.g3d.model.data.ModelData pd = l.loadModelData(new com.badlogic.gdx.files.FileHandle(new java.io.File(res, "parts\\unit_weapon_sword_metal.g3dj")), null);
        System.out.println("part nodes=" + pd.nodes.size + " meshes=" + pd.meshes.size + " mats=" + pd.materials.size);
        com.badlogic.gdx.graphics.g3d.model.data.ModelNode n0 = pd.nodes.get(0);
        System.out.println("n0=" + n0.id + " parts=" + (n0.parts == null ? "null" : n0.parts.length));
        if (n0.parts != null) for (com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart p : n0.parts) {
            System.out.println("  mp=" + p.meshPartId + " mat=" + p.materialId + " bones=" + (p.bones == null ? "null" : p.bones.size));
            if (p.bones != null) for (int i=0;i<p.bones.size;i++) System.out.println("    bone=" + p.bones.getKeyAt(i) + " mat=" + p.bones.getValueAt(i));
        }
    }
}
