package com.saysky.g3darmy;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.lang.reflect.Method;

/** v4.5.0: civilization 段结构(unitSelect/shipSelect + 旧写法兼容)与挂件 level 规则离线自测。 */
public final class ConfigSchemaTest {

    private static Method civSection;
    private static Method selectNode;
    private static Method unitGroupCombo;
    private static int fail = 0;
    private static int total = 0;

    public static void main(String[] args) throws Exception {
        civSection = G3DArmyPlugin.class.getDeclaredMethod("civSection",
                JsonValue.class, String.class, boolean.class);
        civSection.setAccessible(true);
        selectNode = G3DArmyPlugin.class.getDeclaredMethod("selectNode",
                JsonValue.class, int.class, int.class, int.class, String.class, int.class, String.class, int.class);
        selectNode.setAccessible(true);
        unitGroupCombo = G3DArmyPlugin.class.getDeclaredMethod("resolveUnitGroupCombo",
                JsonValue.class, int.class, int.class, int.class, String.class, int.class, int.class, String.class, int.class);
        unitGroupCombo.setAccessible(true);

        // 新结构:loadSelect.unitSelect / shipSelect
        JsonValue nested = parse("{\"unitSelect\":{\"1:Spearmen\":{}},\"shipSelect\":\"ship_ger\"}");
        expect(Boolean.TRUE, same(section(nested, "unitSelect", true), nested.get("unitSelect")), "新结构取 unitSelect");
        expect(Boolean.TRUE, same(section(nested, "shipSelect", false), nested.get("shipSelect")), "新结构取 shipSelect");

        // 旧写法:整段直接是 unitSelect 规则表
        JsonValue flat = parse("{\"1:Spearmen\":{\"default\":\"x\"}}");
        expect(Boolean.TRUE, same(section(flat, "unitSelect", true), flat), "旧写法整段当 unitSelect");
        expect(Boolean.TRUE, section(flat, "shipSelect", false) == null, "旧写法不产生 shipSelect");

        // 只有 shipSelect 的新结构:unitSelect 不应把整段当规则
        JsonValue shipOnly = parse("{\"shipSelect\":\"s\"}");
        expect(Boolean.TRUE, section(shipOnly, "unitSelect", true) == null, "有 shipSelect 时整段不算 unitSelect");

        // 挂件规则 level:byId > group > level > default
        JsonValue rule = parse("{\"byId\":{\"2\":[\"BY_ID\"]},"
                + "\"group\":{\"German\":[\"GROUP\"]},"
                + "\"level\":{\"0-3\":[\"LOW\"],\"4+\":[\"HIGH\"]},"
                + "\"default\":[\"DEF\"]}");
        expect(first(rule(rule, 2, "German", 2, "x")), "BY_ID", "byId 优先");
        expect(first(rule(rule, 2, "German", 0, "x")), "GROUP", "group 覆盖 level");
        expect(first(rule(rule, 2, "", 0, "x")), "LOW", "level 0-3 命中");
        expect(first(rule(rule, 9, "", 0, "x")), "HIGH", "level 4+ 命中");
        expect(first(rule(rule, -1, "", 0, "x")), "DEF", "level 不中 → default");

        // 船模挂件规则:直接一条规则
        JsonValue ship = parse("{\"level\":{\"0-3\":[\"S_LOW\"],\"4+\":[\"S_HIGH\"]},\"default\":[\"S_DEF\"]}");
        expect(first(rule(ship, 1, "", 0, "x")), "S_LOW", "shipSelect 直写规则 level");
        expect(first(rule(ship, 8, "", 0, "x")), "S_HIGH", "shipSelect 直写规则 4+");

        // 真实 loadSelect.json:unitSelect 空数组,靠 unitgroup 兜底
        // 资源目录用 -Dg3darmy.res=<hotplugins/resources/G3DArmy> 或 G3DARMY_RES 指定(缺省跳过)
        String resDir = System.getProperty("g3darmy.res", System.getenv("G3DARMY_RES"));
        if (resDir == null) {
            System.out.println("[skip] real loadSelect.json (set -Dg3darmy.res=... to enable)");
        } else try {
            String text = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(resDir, "loadSelect.json")), "UTF-8");
            JsonValue real = parse(text);
            expect(real.get("unitSelect") != null && real.get("unitSelect").isArray(), Boolean.TRUE, "真实配置 unitSelect = 数组");
            expect(real.get("unitgroup") != null, Boolean.TRUE, "真实配置含 unitgroup");
            expect(groupCombo(real.get("unitgroup"), 5, -1, -1, "European", 0, "x"), "foot_euro_medieval",
                    "真实配置 European 0:Warior 等级 5");
            expect(groupCombo(real.get("unitgroup"), 5, -1, -1, "Arabian", 0, "x"), "foot_arab_medieval",
                    "真实配置 Arabian 文化覆盖");
            expect(groupCombo(real.get("unitgroup"), 5, -1, -1, "Zombie", 4, "x"), "artillery_absolutism_mortar",
                    "真实配置 4:Canon 等级 3+");
        } catch (Exception ex) {
            expect(Boolean.TRUE, Boolean.FALSE, "真实配置读取失败: " + ex);
        }

        System.out.println(fail == 0 ? ("ALL PASS (" + total + ")") : ("FAILED " + fail + "/" + total));
        if (fail != 0) {
            System.exit(1);
        }
    }

    private static JsonValue parse(String s) {
        return new JsonReader().parse(s);
    }

    private static JsonValue section(JsonValue node, String name, boolean legacyFlatAsUnit) {
        try {
            return (JsonValue) civSection.invoke(null, node, name, legacyFlatAsUnit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonValue rule(JsonValue e, int level, String group, int aId, String name) {
        try {
            return (JsonValue) selectNode.invoke(null, e, level, -1, -1, group, aId, name, 0);
        } catch (Exception e2) {
            throw new RuntimeException(e2);
        }
    }

    private static String groupCombo(JsonValue ug, int level, int religionID, int religionGroup,
                                     String group, int uid, String name) {
        try {
            return (String) unitGroupCombo.invoke(null, ug, level, religionID, religionGroup, group, uid, 0, name, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean same(JsonValue a, JsonValue b) {
        return a != null && a == b;
    }

    private static String first(JsonValue arr) {
        return arr == null || arr.child == null ? null : arr.child.asString();
    }

    private static void expect(Object actual, Object want, String label) {
        total++;
        boolean ok = want == null ? actual == null : want.equals(actual);
        if (!ok) {
            fail++;
            System.out.println("FAIL " + label + "  want=" + want + " actual=" + actual);
        } else {
            System.out.println("  ok " + label);
        }
    }
}
