package com.saysky.g3darmy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart;
import com.badlogic.gdx.utils.JsonReader;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** v4.0.1 离线自测:ModelData 级挂件合并(不改顶点,只追加 mesh/material/nodePart)。 */
public final class MergeTest {

    private static int fail = 0;
    private static int total = 0;

    public static void main(String[] args) throws Exception {
        String res = args.length > 0 ? args[0] : System.getenv("G3DARMY_RES");
        if (res == null) { throw new IllegalArgumentException("usage: MergeTest <hotplugins/resources/G3DArmy>"); }
        Field f = G3DArmyMin.class.getDeclaredField("dataDirPath");
        f.setAccessible(true);
        f.set(null, res);
        Method merge = G3DArmyMin.class.getDeclaredMethod("mergePartData", ModelData.class, String.class);
        merge.setAccessible(true);

        File body = new File(res, "g3d\\foot_euro_ancient.g3dj");
        G3dModelLoader loader = new G3dModelLoader(new JsonReader());
        ModelData data = loader.loadModelData(new FileHandle(body), null);
        ModelNode host = firstHost(data);
        int partsBefore = host.parts == null ? 0 : host.parts.length;
        System.out.println("body parts = " + partsBefore + "  meshes=" + data.meshes.size + " materials=" + data.materials.size);

        String[] tokens = {"unit_weapon_sword_metal", "unit_weapon_shield_viking_round"};
        int ok = 0;
        for (String t : tokens) {
            Object r = merge.invoke(null, data, t);
            expect(Boolean.TRUE.equals(r), "merge " + t);
            if (Boolean.TRUE.equals(r)) {
                ok++;
            }
        }
        int partsAfter = host.parts.length;
        expect(partsAfter == partsBefore + ok, "宿主部件数 " + partsBefore + " -> " + partsAfter);

        // 新部件的 meshPartId / materialId 必须能在 data 里找到
        Set<String> meshParts = new HashSet<String>();
        for (ModelMesh m : data.meshes) {
            if (m.parts != null) {
                for (ModelMeshPart p : m.parts) {
                    meshParts.add(p.id);
                }
            }
        }
        Set<String> matIds = new HashSet<String>();
        for (int i = 0; i < data.materials.size; i++) {
            matIds.add(data.materials.get(i).id);
        }
        for (int i = partsBefore; i < partsAfter; i++) {
            ModelNodePart p = host.parts[i];
            expect(meshParts.contains(p.meshPartId), "meshPart 存在: " + p.meshPartId);
            expect(p.materialId == null || matIds.contains(p.materialId), "material 存在: " + p.materialId);
            expect(p.bones != null && p.bones.size == 1, "bones 条目=1: " + (p.bones == null ? "null" : p.bones.size));
            if (p.bones != null && p.bones.size == 1) {
                String bone = p.bones.getKeyAt(0);
                expect("Unit_Wea_Right_Hand01".equals(bone) || "Unit_Base_Rig_LeftHand".equals(bone),
                        "挂点骨正确: " + bone);
            }
        }

        // 贴图必须已改成绝对路径
        boolean absOk = true;
        for (int i = 0; i < data.materials.size; i++) {
            if (data.materials.get(i).textures == null) {
                continue;
            }
            for (int t = 0; t < data.materials.get(i).textures.size; t++) {
                String fn = data.materials.get(i).textures.get(t).fileName;
                if (fn != null && fn.contains("#") == false && new File(fn).isAbsolute() == false) {
                    absOk = false;
                    System.out.println("  非绝对路径贴图: " + fn);
                }
            }
        }
        expect(absOk, "贴图路径均为绝对路径");

        System.out.println(fail == 0 ? ("ALL PASS (" + total + ")") : ("FAILED " + fail + "/" + total));
        if (fail != 0) {
            System.exit(1);
        }
    }

    private static ModelNode firstHost(ModelData d) {
        for (int i = 0; i < d.nodes.size; i++) {
            ModelNode h = hostOf(d.nodes.get(i));
            if (h != null) {
                return h;
            }
        }
        throw new IllegalStateException("no host");
    }

    private static ModelNode hostOf(ModelNode n) {
        if (n == null) {
            return null;
        }
        if (n.parts != null && n.parts.length > 0) {
            return n;
        }
        if (n.children != null) {
            for (ModelNode c : n.children) {
                ModelNode h = hostOf(c);
                if (h != null) {
                    return h;
                }
            }
        }
        return null;
    }

    private static void expect(boolean cond, String label) {
        total++;
        if (!cond) {
            fail++;
            System.out.println("FAIL " + label);
        } else {
            System.out.println("  ok " + label);
        }
    }
}
