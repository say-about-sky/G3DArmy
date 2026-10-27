import java.lang.reflect.Field;
import java.lang.reflect.Method;
public class Peek {
    public static void main(String[] a) throws Exception {
        Class<?> c = Class.forName("com.saysky.g3darmy.G3DArmyMin");
        String res = System.getProperty("g3darmy.res", System.getenv("G3DARMY_RES"));
        if (res == null) { throw new IllegalArgumentException("pass -Dg3darmy.res=<hotplugins/resources/G3DArmy>"); }
        Field dp = c.getDeclaredField("dataDirPath"); dp.setAccessible(true);
        dp.set(null, res);
        Class<?> md = Class.forName("com.badlogic.gdx.graphics.g3d.model.data.ModelData");
        Method m = c.getDeclaredMethod("mergePartData", md, String.class); m.setAccessible(true);
        com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader l = new com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader(new com.badlogic.gdx.utils.JsonReader());
        Object data = l.loadModelData(new com.badlogic.gdx.files.FileHandle(new java.io.File(res, "g3d\\foot_euro_ancient.g3dj")), null);
        Object r = m.invoke(null, data, "unit_weapon_sword_metal");
        Field note = c.getDeclaredField("comboNote"); note.setAccessible(true);
        System.out.println("result=" + r);
        System.out.println("note=" + note.get(null));
    }
}
