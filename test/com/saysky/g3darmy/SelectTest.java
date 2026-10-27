package com.saysky.g3darmy;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.lang.reflect.Method;

/** v3.1.0 选型规则(byId / group / level / default + 覆盖合并)离线自测。 */
public final class SelectTest {

    private static Method selectNode;
    private static Method resolveComboRoot;
    private static Method unitGroupCombo;
    private static Method mergeJson;
    private static int fail = 0;
    private static int total = 0;

    public static void main(String[] args) throws Exception {
        selectNode = G3DArmyPlugin.class.getDeclaredMethod("selectNode",
                JsonValue.class, int.class, int.class, int.class, String.class, int.class, String.class, int.class);
        selectNode.setAccessible(true);
        resolveComboRoot = G3DArmyPlugin.class.getDeclaredMethod("resolveComboFromRoot",
                JsonValue.class, int.class, int.class, int.class, String.class, int.class, int.class, String.class, int.class);
        resolveComboRoot.setAccessible(true);
        unitGroupCombo = G3DArmyPlugin.class.getDeclaredMethod("resolveUnitGroupCombo",
                JsonValue.class, int.class, int.class, int.class, String.class, int.class, int.class, String.class, int.class);
        unitGroupCombo.setAccessible(true);
        mergeJson = G3DArmyPlugin.class.getDeclaredMethod("mergeJson", JsonValue.class, JsonValue.class);
        mergeJson.setAccessible(true);
        // 测试里没有游戏运行时,手动给 religion 名字表
        java.lang.reflect.Field relNames = G3DArmyPlugin.class.getDeclaredField("religionRawNames");
        relNames.setAccessible(true);
        relNames.set(null, new String[]{"Pagan", "Catholic", "", "", "", "Sunni"});

        String json = "{\"unitSelect\":{"
                + "\"1:Spearmen\":{\"byId\":{\"2\":\"SPEAR_ELITE\",\"EliteSpearman\":\"SPEAR_ELITE_NAME\"},"
                + "  \"level\":{\"0-3\":\"LV_LOW\",\"4-6\":\"LV_MID\"},\"default\":\"SPEAR_DEF\"},"
                + "\"0:Warior\":{\"byId\":{\"EliteSwordsman\":\"SWORD_ELITE\",\"\u7cbe\u9510\u5251\u5ba2\":\"SWORD_ELITE_CN\"},"
                + "  \"level\":{\"3-6\":\"LV_MID\"},\"group\":{\"Arabian\":\"ARAB\"},\"default\":\"WAR_DEF\"}"
                + "}}";
        JsonValue root = new JsonReader().parse(json);
        JsonValue us = root.get("unitSelect");

        expect(resolve(entry(us, "1:Spearmen"), 2, "", 2, "\u7cbe\u9510\u957f\u77db\u5175"),
                "SPEAR_ELITE", "byId(aID=2) 命中");
        expect(resolve(entry(us, "1:Spearmen"), 2, "", 99, "EliteSpearman"),
                "SPEAR_ELITE_NAME", "byId(兵种名) 命中");
        expect(resolve(entry(us, "1:Spearmen"), 2, "", 0, "\u6c11\u5175"),
                "LV_LOW", "无 byId → level 命中");
        expect(resolve(entry(us, "1:Spearmen"), 9, "", 0, "x"),
                "SPEAR_DEF", "level 不匹配 → default");
        expect(resolve(entry(us, "0:Warior"), 4, "", 4, "EliteSwordsman"),
                "SWORD_ELITE", "byId(英文名) 命中");
        expect(resolve(entry(us, "0:Warior"), 4, "", 4, "\u7cbe\u9510\u5251\u5ba2"),
                "SWORD_ELITE_CN", "byId(中文名) 命中");
        expect(resolve(entry(us, "0:Warior"), 2, "Arabian", 2, "\u8f7b\u6b65\u5175"),
                "ARAB", "无 byId → group 覆盖 level");
        expect(resolve(entry(us, "0:Warior"), 7, "", 7, "\u7ebf\u5217\u6b65\u5175"),
                "WAR_DEF", "group/level 都不中 → default");

        // v4.6.0 新优先级:byId > religion > religionGroup > group > level > default
        // level 可嵌在 religion / group 等任意层级里
        JsonValue rel = new JsonReader().parse("{\"4:Canon\":{"
                + "\"byId\":{\"2\":\"BY_ID\"},"
                + "\"religion\":{\"Catholic\":{\"level\":{\"0-2\":\"REL_LOW\",\"3+\":\"REL_HIGH\"},"
                + "  \"default\":\"REL_DEF\"},\"5\":\"REL_ID5\"},"
                + "\"religionGroup\":{\"1\":\"REL_GROUP1\",\"3\":\"REL_GROUP3\"},"
                + "\"group\":{\"European\":{\"level\":{\"0-1\":\"GRP_LOW\"},\"default\":\"GRP_DEF\"}},"
                + "\"level\":{\"0\":\"LV0\"},"
                + "\"default\":\"DEF\"}}").get("4:Canon");
        expect(resolve(rel, 5, 1, 1, "European", 2, "x"), "BY_ID", "byId 最具体");
        expect(resolve(rel, 1, 1, 1, "European", 0, "x"), "REL_LOW", "religion 命中 + 内嵌 level");
        expect(resolve(rel, 5, 1, 1, "European", 0, "x"), "REL_HIGH", "religion.level 3+");
        expect(resolve(rel, 5, 5, 1, "European", 0, "x"), "REL_ID5", "religion 数字 ID 命中");
        expect(resolve(rel, 5, -1, 3, "European", 0, "x"), "REL_GROUP3", "religionGroup 命中");
        expect(resolve(rel, 0, -1, -1, "European", 0, "x"), "GRP_LOW", "group 命中 + 内嵌 level");
        expect(resolve(rel, 5, -1, -1, "European", 0, "x"), "GRP_DEF", "group.default");
        expect(resolve(rel, 0, -1, -1, "African", 0, "x"), "LV0", "无宗教/文化 → 根 level");
        expect(resolve(rel, 9, -1, -1, "African", 0, "x"), "DEF", "最后回 default");

        // v4.7.0 数组式 unitSelect:按宗教块划分,unitgroup 兜底
        JsonValue schema = new JsonReader().parse("{"
                + "\"unitSelect\":["
                + "  {\"religion\":\"Catholic\",\"1\":{\"level\":{\"0-3\":\"REL_LOW\",\"4+\":\"REL_HIGH\"}}},"
                + "  {\"religion\":\"Sunni\",\"1\":\"SUNNI\"}"
                + "],"
                + "\"unitgroup\":{\"European\":{\"1\":\"EURO\"},\"1\":\"GENERIC\"}}");
        JsonValue us2 = schema.get("unitSelect");
        expect(comboFromRoot(us2, 1, 1, 1, "European", 1, "x"), "REL_LOW", "数组: religion 块命中");
        expect(comboFromRoot(us2, 5, 1, 1, "European", 1, "x"), "REL_HIGH", "数组: religion 块内 level");
        expect(comboFromRoot(us2, 1, 5, 3, "European", 1, "x"), "SUNNI", "数组: 第二个 religion 块");
        expect(comboFromRoot(us2, 1, -1, -1, "European", 1, "x"), null, "数组: 无宗教块 → null");
        expect(groupCombo(schema.get("unitgroup"), 1, -1, -1, "European", 1, "x"), "EURO", "unitgroup: 文化组命中");
        expect(groupCombo(schema.get("unitgroup"), 1, -1, -1, "African", 1, "x"), "GENERIC", "unitgroup: 通用兜底");

        // 覆盖文件合并:byId 追加,level 覆盖
        JsonValue base = new JsonReader().parse("{\"byId\":{},\"level\":{\"0-3\":\"OLD_LOW\"}}");
        JsonValue over = new JsonReader().parse("{\"byId\":{\"2\":\"OVR_ELITE\"},\"level\":{\"0-3\":\"NEW_LOW\"}}");
        mergeJson.invoke(null, base, over);
        expect(resolve(base, 2, "", 2, "x"), "OVR_ELITE", "merge: byId 新增");
        expect(resolve(base, 1, "", 0, "x"), "NEW_LOW", "merge: level 覆盖");

        // 边界:空/缺失
        expect(resolve(null, 0, "", 0, ""), null, "entry=null → null");
        expect(resolve(new JsonValue(JsonValue.ValueType.object), 0, "", 0, ""), null, "空条目 → null");

        System.out.println(fail == 0 ? ("ALL PASS (" + total + ")") : ("FAILED " + fail + "/" + total));
        if (fail != 0) {
            System.exit(1);
        }
    }

    private static JsonValue entry(JsonValue us, String key) {
        return us.get(key);
    }

    private static String resolve(JsonValue e, int level, String group, int aId, String name) {
        return resolve(e, level, -1, -1, group, aId, name);
    }

    private static String resolve(JsonValue e, int level, int religionID, int religionGroup,
                                  String group, int aId, String name) {
        try {
            Object r = selectNode.invoke(null, e, level, religionID, religionGroup, group, aId, name, 0);
            return r == null ? null : ((JsonValue) r).asString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String comboFromRoot(JsonValue root, int level, int religionID, int religionGroup,
                                        String group, int uid, String name) {
        try {
            return (String) resolveComboRoot.invoke(null, root, level, religionID, religionGroup, group, uid, 0, name, 0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String groupCombo(JsonValue ug, int level, int religionID, int religionGroup,
                                     String group, int uid, String name) {
        try {
            return (String) unitGroupCombo.invoke(null, ug, level, religionID, religionGroup, group, uid, 0, name, 0);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void expect(String actual, String want, String label) {
        total++;
        boolean ok = want == null ? actual == null : want.equals(actual);
        if (!ok) {
            fail++;
            System.out.println("FAIL " + label + "  want=" + want + " actual=" + actual);
        } else {
            System.out.println("  ok " + label + " = " + actual);
        }
    }
}
