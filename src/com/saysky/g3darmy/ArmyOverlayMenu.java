package com.saysky.g3darmy;

import java.util.ArrayList;
import java.util.List;

import aoc.kingdoms.lukasz.jakowski.CFG;
import aoc.kingdoms.lukasz.menu.Menu;
import aoc.kingdoms.lukasz.menu_element.MenuElement;

/**
 * 全屏无框覆盖层(同地图锚定版):标题 null + 不可关闭 = 无边框无背景;
 * 元素仅一个:1×1 兵模代理(渲染发生在其 draw 里)。
 * v1.6.0:移除右上角「关闭 3D 兵模」按钮 —— 开关改由 army.json 的 "enabled" 控制。
 */
public final class ArmyOverlayMenu extends Menu {

    public final ArmyModelElement models;

    public ArmyOverlayMenu() {
        models = new ArmyModelElement();
        List<MenuElement> elements = new ArrayList<MenuElement>();
        elements.add(models);
        // v1.6.0:不再创建「关闭 3D 兵模」按钮(关闭 = army.json "enabled": false)
        initMenu(null, 0, 0, CFG.GAME_WIDTH, CFG.GAME_HEIGHT, elements, true, false);
        setVisible(false);
    }
}
