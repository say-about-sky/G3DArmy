package com.saysky.g3darmy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import aoc.kingdoms.lukasz.menu_element.MenuElement;

/**
 * 兵模渲染代理元素(1×1)。
 *
 * <p>为什么 1×1:菜单触摸分发"命中任何元素即吞噬" → 元素占位 = 触摸死区。
 * 真身是每帧算出的多个兵模小矩形(绘制不受元素边界限制,只受全屏菜单裁剪)。</p>
 *
 * <p>v1.2:在<b>绘制帧内</b>计算部队列表(与地图同帧的 pos/scale,零延迟跟随),
 * 并把每部队朝向/动作状态(停留/移动/战斗)传入渲染器。</p>
 */
public final class ArmyModelElement extends MenuElement {

    /** 自旋角(弧度;由插件每帧推进)。 */
    public float angle = 0f;

    public ArmyModelElement() {
        setPosX(0);
        setPosY(0);
        setWidth(1);
        setHeight(1);
        setClickable(false);
    }

    @Override
    public void draw(SpriteBatch oSB, int iTranslateX, int iTranslateY, boolean isActive, boolean scrollableY) {
        // 绘制帧内更新部队列表(与地图绘制同一帧的数据 → 跟随零延迟)
        G3DArmyPlugin.updateArmyList();
        if (!G3DArmyPlugin.hasModels()) {
            return;
        }
        if (!G3DArmyMin.isReady() && !G3DArmyMin.init()) {
            return;
        }
        boolean ended = false;
        try {
            oSB.end();
            ended = true;
            G3DArmyMin.renderAll(angle, G3DArmyPlugin.rectBuf, G3DArmyPlugin.relBuf,
                    G3DArmyPlugin.facingBuf, G3DArmyPlugin.stateBuf, G3DArmyPlugin.kindBuf,
                    G3DArmyPlugin.keyBuf, G3DArmyPlugin.morphBuf, G3DArmyPlugin.comboBuf,
                    G3DArmyPlugin.attachBuf, G3DArmyPlugin.tintBuf, G3DArmyPlugin.comboShipBuf,
                    G3DArmyPlugin.attachShipBuf, G3DArmyPlugin.modelCount);
        } finally {
            if (ended) {
                oSB.setColor(Color.WHITE);
                oSB.begin();
            }
        }
    }

    /** 渲染资源是否就绪。 */
    public boolean inited() {
        return G3DArmyMin.isReady();
    }

    /** 最近一次错误(空串 = 正常)。 */
    public String error() {
        return G3DArmyMin.lastError;
    }
}
