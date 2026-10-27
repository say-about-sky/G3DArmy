package com.saysky.g3darmy;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

import aoc.kingdoms.lukasz.jakowski.CFG;
import aoc.kingdoms.lukasz.jakowski.FileManager;
import aoc.kingdoms.lukasz.jakowski.Game;
import aoc.kingdoms.lukasz.jakowski.Game_Calendar;
import aoc.kingdoms.lukasz.map.army.ArmyDivision;
import aoc.kingdoms.lukasz.map.army.ArmyManager;
import aoc.kingdoms.lukasz.map.army.ArmyRegiment;
import aoc.kingdoms.lukasz.map.battles.Battle;
import aoc.kingdoms.lukasz.map.ReligionManager;
import aoc.kingdoms.lukasz.map.civilization.Civilization;
import aoc.kingdoms.lukasz.map.diplomacy.DiplomacyManager;
import aoc.kingdoms.lukasz.map.moveUnits.MoveUnits;
import aoc.kingdoms.lukasz.map.province.Province;
import aoc.kingdoms.lukasz.map.province.ProvinceDrawArmy;
import aoc.kingdoms.lukasz.menu.Menu;
import aoc.kingdoms.lukasz.textures.ImageManager;
import aoc.kingdoms.lukasz.textures.Images;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.saysky.hotplugloader.api.HotContext;
import com.saysky.hotplugloader.api.HotEvents;
import com.saysky.hotplugloader.api.HotListener;
import com.saysky.hotplugloader.api.HotPlugin;
import com.saysky.hotplugloader.api.HotPluginInfo;

/**
 * G3D 兵模扩展测试插件 v1.0.0 —— 把地图上的部队方框扩展成 3D 兵模。
 *
 * <p><b>原版事实(源码核实):</b></p>
 * <ul>
 *   <li>部队显示三种随缩放的策略:① <b>scale ≥ Game.DRAW_ARMY_MIN_SCALE</b>(地图配置:
 *       EarthM 0.4 / Earth3 0.5;代码默认 0.5;设置项 DrawArmyScale 可调 0.05~1.0)= 完整显示方框;
 *       ② <b>0.3 ≤ scale < 阈值</b>(ProvinceDrawArmy.DRAW_ARMY_MIN_SCALE_ANIMATION = 0.3)= 淡出/淡入过渡;
 *       ③ <b>scale < 0.3</b> = 隐藏,但**框选模式(selectMode)**下强制全亮显示;</li>
 *   <li>玩家操控的部队是**绿色方框**:Civ.iArmyImgID → 玩家走 updateArmyImgID_Player() →
 *       Images.armyPlayer0..14(由 Game.player.playerData.armyImgID 选择,默认 0 = 绿);
 *       敌=armyEnemy(红)、友=armyAlly(蓝)、其他=army;方框左端还有士气色条(高士气=绿);</li>
 *   <li>部队屏幕坐标 = ProvinceDrawArmy.getArmyPosX/Y(省中心 + 省份位移 + 部队偏移,内部含
 *       scale 变换)—— 直接调用游戏函数即为权威位置。</li>
 * </ul>
 *
 * <p><b>本插件显示逻辑:</b>与方框同款固定屏幕尺寸(不随缩放变化);
 * 可见性对齐①②:scale ≥ 阈值或框选模式时显示;在绘制帧内取坐标(零延迟);
 * 配色按关系(玩家绿/敌红/友蓝/中立);默认只画玩家部队(scope=player)。</p>
 *
 * <p><b>v1.1 新增:</b></p>
 * <ul>
 *   <li><b>上移</b>:兵模底边位于方框顶部上方 liftPx 像素(不再与方框重叠);</li>
 *   <li><b>转向</b>:按行军路径方向转向 —— 移动线“当前路段目标省”
 *       (MoveUnits.getToProvinceID)省中心与部队屏幕位置求方向;移动中带步伐起伏;</li>
 *   <li><b>用户资源(模型/动画公开)</b>:dataDir 下放 <b>model.g3dj / model.g3db</b>
 *       即替换兵模;模型自带动画自动播放(animation/animSpeed 可调);
 *       内置模型会导出 soldier-template.g3dj 作格式示例(附带可加载自检)。</li>
 * </ul>
 *
 * <p><b>v1.2 新增:</b></p>
 * <ul>
 *   <li><b>全部部队</b>:默认 scope=all(玩家/敌/友/中立全部绘制,配色不变);</li>
 *   <li><b>三态动画</b>:停留/移动/战斗 —— 用户模型按名称自动匹配(可 animationIdle/Move/Battle 指定,
 *       animation 作回退),某状态缺动画时用内置程序化动作(呼吸/步伐/前冲突刺)兜底;</li>
 *   <li><b>战斗表现</b>:交战部队不再隐藏 —— 按游戏判定分左右(进攻方左/防守方右,
 *       Battle.attackingArmy/defendingArmy.isInBattleArmy),双方面对面朝向,间距 battleSplitPx。</li>
 * </ul>
 *
 * <p><b>v1.3 新增:</b></p>
 * <ul>
 *   <li><b>战斗兵模锚定战斗横幅</b>:游戏在交战时画的是「进攻|⚔|防守」合并横幅;
 *       两侧兵模改为对齐横幅左半/右半区、同一基准线(修复“左侧兵模偏下”);</li>
 *   <li><b>堆叠节流</b>:同省多部队重叠时默认只显示<b>首个(最上)静止兵牌</b>的兵模;
 *       该部队开始移动后,下一个静止兵牌自动接替显示(依次显示,防重叠/防卡顿);
 *       移动中的部队始终显示;配置 stackFirstOnly 可关;</li>
 *   <li><b>选中优先(v1.3.1)</b>:玩家选中的军牌(单选/多选)始终显示其兵模 ——
 *       即使它不是堆叠中的首个(按 Game.activeArmy 匹配,为多兵种兵牌铺路);</li>
 *   <li><b>撤退态(v1.4)</b>:第四种动作状态(状态值 3)—— `a.inRetreat`(兵牌白旗同源)时驱动;
 *       内置模型:小碎步+前倾+手中白旗;用户模型:animationRetreat / 关键词 retreat/flee/run…;
 *       并收窄兵模与兵牌间距(视线中心下移,脚下空区 23%→~10%)。</li>
 *   <li><b>用户模型修正(v1.4.1~1.4.4)</b>:①贴图按模型目录<b>绝对路径</b>加载(Gdx.files.absolute,安卓不再丢贴图);
 *       ②自动适配(modelAutoFit):按包围盒缩放至视口约 85%、水平居中、脚底贴地 ——
 *       任意尺寸/原点的模型(如骑士原始身高 12.2、脚底 y=-8.85)开箱即用;
 *       ③均匀顶点色(转换器哨兵值,实测 0x7FC00000)自动剥离;
 *       ④灰白自发光(如骑士 cccccc)自动归零——修复“模型发白”。</li>
 *   <li><b>朝向来源修正(v1.4.5)</b>:朝向改取「当前路段两端省中心连线方向」(= 行军直线/黄引导线)。
 *       旧版用「部队精灵位置→下一站中心」会被精灵自身状态污染 —— 堆叠站位偏移、到达并入新堆的
 *       纵向弹跳(extraArmyY)、行军摆动动画都让小范围方向漂移,表现为过地块时的小幅乱转。</li>
 *   <li><b>v1.7.0 显示优化</b>:①只在默认「政治」地图模式渲染(外交/宗教等模式不显示兵模);
 *       ②迷雾门控与兵牌同源(Province.getFogDrawArmy;关闭迷雾时恒可见)—— 看不到兵牌的地块也看不到兵模;
 *       ③只收集当前视角内(兵牌在屏 ±200px)的部队;
 *       ④同地块堆叠 + 同行军段(同文明同起终点)+ 同战斗同侧只显示一个兵模
 *       (v1.7.1:堆叠组锚定“整叠最上方兵牌”的正上方;v1.7.2:行军组粘性跟随同一兵牌,
 *       兵种速度不一不再来回闪);
 *       ⑤显示优先级:选中 > 我方绿 > 敌方红 > 盟友蓝 > 其他灰,同级取离视角中心近者;
 *       ⑥同屏上限默认 24(可调 maxModels,上限 128)—— 修复兵多时移动动画被截断不播的问题。</li>
 *   <li><b>v1.8.0 海面船模</b>:①陆地部队=陆军模型,<b>下海(海洋地块)=船模</b>
 *       (上岸自动换回;逐地块判定 = Province.getSeaProvince);②海面兵牌在游戏的
 *       “海面在视省”列表绘制 —— 已同步采集,海战与陆地同逻辑(战斗横幅锚定/同侧节流/迷雾);
 *       ③船模文件 ship.g3dj(+贴图)放资源目录即生效,无船模文件时海面沿用陆军模型;
 *       ④船适配按长边缩放(不裁切),shipYaw 可修船头朝向。</li>
 *   <li><b>v1.9.2 陆↔海过渡(绑定行军进度)</b>:部队在跨陆/海的路段行进时 ——
 *       0→50% 当前模型缩到最小、50%→100% 换成另一种模型放大(到达时正好恢复全尺寸);
 *       进度 = 与兵牌位置完全同源的插值:(done/current/fCurrentMovingPercentage)×路段宽;
 *       ⚠ 不能直接用 fCurrentMovingPercentage —— 它每游戏刻被重置(会导致每刻重播)。
 *       纯视口缩放实现:不增加任何绘制次数,缩小时像素更少,不影响流畅度;
 *       无路段数据时兜底为“到站后过渡”(时长 morphMs;morphMove=false 可强制该模式)。</li>
 *   <li><b>v2.0.0 拼装兵模支持</b>:多部件拼装模型(如 44 骨的躯干件)超过 DefaultShader 默认
 *       numBones=12 会抛"too many bones" → 建批次前按已加载模型的**单部件最大骨骼数**自动配置
 *       (army.json numBones 可手调;状态行显示实际值)。</li>
 * </ul>
 */
@HotPluginInfo(id = "com.saysky.g3darmy", name = "G3D 兵模(G3DArmy)", version = "4.9.14", trusted = true,
        description = "拼装兵模+海面船模+挂件;unitSelect=宗教块数组,unitgroup=文化组兜底,civilization 顶层 unitSelect,level 可任意层级;4.9.14 modelLift(窗口随俯角相对兵牌定位)+上下限全部改为 [min,max] 数组;近大远小与地图局部缩放同源")
public final class G3DArmyPlugin implements HotPlugin, HotListener {

    /* ---- 配置(army.json 可改;静态 = 绘制线程可读) ---- */
    /** v1.6.0:总开关 —— false = 完全不挂载/不渲染(取代原「关闭 3D 兵模」按钮;改后「全部重载」生效)。 */
    private static boolean enabled = true;
    private static float modelPx = 170f;          // 兵模屏幕尺寸(px;与方框一样固定,不随缩放)
    /* ---- v3.0.1:渲染窗口尺寸(高/宽可分开;马/大型模型头尾被剪切时调大) ---- */
    private static float modelPxW = 0f;           // 兵模窗口宽(px;0=用 modelPx)
    private static float modelPxH = 0f;           // 兵模窗口高(px;0=用 modelPx)
    /** v4.9.13:渲染窗口像素上下限(近大远小缩放后的最终窗口长边;0=不限)。 */
    private static float modelPxMin = 0f;
    private static float modelPxMax = 0f;
    /** v4.9.13:模型尺寸倍率上下限(相对默认大小 1.0;默认等同旧硬编码 0.5~2)。 */
    private static float modelMulMin = 0.5f;
    private static float modelMulMax = 2.0f;
    /**
     * v4.9.14:渲染窗口相对兵牌的位置 [平视端, 俯视端](px;负值 = 压到兵牌基准线下方)。
     * 未配置(空数组/缺键)→ 自动模式:按俯角补偿,让「脚」与兵牌保持 `liftPx` 的固定间距
     * (俯视时脚会向模型中心收拢,旧版不补偿 → 看起来脚离兵牌越来越远)。
     */
    private static float modelLiftMin = 0f;
    private static float modelLiftMax = 0f;
    private static boolean modelLiftManual = false;
    /** 本帧生效的抬升(px;= 窗口底边在兵牌顶上方多高,负值可压到基准线下方)。 */
    private static float liftPxEff = 8f;
    /** 本帧缩放归一值(0=兵模刚出现/地图最俯视, 1=拉到最近/最平视;viewPitch 与 modelLift 共用)。 */
    private static float viewPitchU = 1f;
    private static float modelFitPct = 0.85f;     // 自动适配占比(模型最长边/高度占视口比例)
    private static String modelFitBy = "height";  // 适配维度:"height"=只按高(旧行为)/"max"=按最大边(长模型不裁切)
    private static float liftPx = 8f;             // 兵模底边相对方框顶部的抬升(px;避免遮挡)
    private static float battleSplitPx = 55f;     // 战斗时双方兵模左右分离(px;进攻方左/防守方右)
    private static boolean stackFirstOnly = true; // true=同省堆叠只显示首个静止兵牌的兵模(依次接替)
    private static String modelFile = "model";    // 用户模型文件名(不含扩展名;"builtin"/空=内置)
    private static String shipModelFile = "ship"; // v1.8.0:船模文件名(不含扩展名;海面部队;""/"-"/"builtin"=不用船模)
    /* ---- v2.1.0:兵模拼接(loadSelect.json 的 unitSelect / shipSelect) ---- */
    private static boolean comboMode = true;         // true=按兵牌主兵种/等级/文化选 combo;false=一律用 model
    /** v4.2.0:资源目录结构(setting.json / loadSelect.json / models/…;旧版 army.json/parts.json/g3d 仍兼容)。 */
    private static final String SETTING_FILE = "setting.json";
    private static final String LEGACY_SETTING_FILE = "army.json";
    private static final String LOAD_SELECT_FILE = "loadSelect.json";
    private static final String LEGACY_LOAD_SELECT_FILE = "parts.json";
    private static final String OVERRIDE_FILE = "loadSelect.override.json";
    private static final String LEGACY_OVERRIDE_FILE = "unitSelect.override.json";
    private static final String MODELS_DIR = "models";
    private static final String ATTACH_SUB = "attachments.json";
    private static final String ASSEMBLY_SUB = "assembly.json";
    private static final String ANIM_EVENTS_FILE = "anim_events.json";
    private static final String CIV_DIR = "civilization";
    private static String partsFileName = LOAD_SELECT_FILE; // 装载配置文件名(放资源目录)
    private static JsonValue unitSelectNode;          // loadSelect.json → unitSelect(数组=宗教块 / 对象=旧格式)
    private static JsonValue unitGroupNode;           // loadSelect.json → unitgroup(文化组兜底)
    private static JsonValue shipSelectNode;          // loadSelect.json → shipSelect
    /** v3.1.0:选型日志去重(避免每帧刷屏)。 */
    private static final HashMap<String, String> comboLogSeen = new HashMap<String, String>();

    /* ---- v4.0.0:运行时挂件(武器/配饰) ---- */
    /** v4.5.0:attachments.json → unitSelect(按兵种类别;旧字段 rules 兼容)。 */
    private static JsonValue attachUnitSelectNode;
    /** v4.7.0:attachments.json → unitgroup(文化组兜底,可选)。 */
    private static JsonValue attachUnitGroupNode;
    /** v4.5.0:attachments.json → shipSelect(船模挂件;规则条目或按兵种类别)。 */
    private static JsonValue attachShipSelectNode;
    private static String attachFileName = "attachments.json";
    /** 需剥离静态件的 combo 前缀与关键词。 */
    private static String[] stripCombosArr;
    private static String[] stripTokensArr;
    /** 上次计算船模时的时代(时代变了重算 shipSelect)。 */
    private static int lastShipAge = Integer.MIN_VALUE;

    /* ---- v4.3.0:资源目录运行时接入(models/assembly.json / anim_events.json / civilization/) ---- */
    /** assembly.combos[comboId].animSet 的第一项(骑乘 = sword_r 这类主集)。 */
    private static final HashMap<String, String> comboAnimSet = new HashMap<String, String>();
    /** assembly.combos[comboId].group(foot/mounted/artillery/ship…)。 */
    private static final HashMap<String, String> comboGroup = new HashMap<String, String>();
    /** v4.4.0:assembly.combos[comboId].rig(foot/mounted/artillery…)。 */
    private static final HashMap<String, String> comboRig = new HashMap<String, String>();
    /** v4.4.0:assembly.animDonors 拍平成 "<rig>.<武器族>" → donor combo。 */
    private static final HashMap<String, String> animDonorMap = new HashMap<String, String>();
    /** 文明 tag(小写)→ civilization/<tag>.json 节点。 */
    private static final HashMap<String, JsonValue> civOverrides = new HashMap<String, JsonValue>();
    /** anim_events.json 根(已推给 G3DArmyMin 解析)。 */
    private static JsonValue animEventsNode;
    /** 粒子开关(anim_events.enabled)。 */
    private static boolean particlesEnabled = true;
    /** 粒子池上限。 */
    private static int particleMax = 256;
    /** 资源索引校验结果(启动日志用)。 */
    private static String resValidateNote = "";
    private static String animationName = "";     // 动画基础名(""=自动第一个;"-"=禁用所有动画)
    private static String animIdleName = "";      // 停留动画名(""=自动按名称匹配)
    private static String animMoveName = "";      // 移动动画名(""=自动按名称匹配)
    private static String animBattleName = "";    // 战斗动画名(""=自动按名称匹配)
    private static String animRetreatName = "";   // 撤退动画名(""=自动按名称匹配;缺省回退移动动画)
    private static float animSpeed = 1f;          // 动画速度倍率
    private static float worldH = 2.2f;           // 3D 视口世界高度(模型大小不匹配时调)
    private static String tintMode = "civ";         // v4.1.0:染色方式 "civ"(文明色,默认)/"relation"(关系色)/"none"
    private static float tintStrength = 0.45f;     // 染色强度(0..1;与原贴图色插值)
    private static float tintLift = 0.22f;         // 保底亮度(过暗的国家色会被提亮,避免全黑)
    private static String tintSkip = "head,skin"; // 跳过的材质关键词(逗号分隔;脸/皮肤不染)
    private static float feetDark = 0.5f;         // v4.1.1:脚底亮度(0..1;1=关闭;越小底部越暗、接地感越强)
    private static float shadowScale = 1f;         // v4.1.1:接触阴影尺寸倍数(0.5~2;按模型尺寸自动算后再乘)
    private static boolean shadowAnchor = true;    // v4.1.4:阴影自动对准脚底接触几何(不同模型不再各飞各的)
    private static float shadowShiftZ = 0f;        // v4.1.4:阴影前后固定偏移(世界单位;负=屏幕上方/远处)
    private static float shadowShiftPct = 0f;      // v4.1.4:阴影前后比例偏移(占模型高度;大小模型观感一致)
    private static float shadowCore = 0.6f;       // v4.1.2:叠加的接触核心阴影缩放(0=关)
    private static boolean shadowBake = true;      // v4.9.2:核心烘焙进贴图(=阴影只画一层;false=旧双层)
    private static boolean modelAutoFit = true;   // 用户模型自动适配(按包围盒缩放至视口 85%+贴地+居中;v1.4.1)
    private static float modelScale = 0f;         // 手动缩放倍率(>0 覆盖自动适配缩放)
    private static float modelOffsetY = 0f;       // 额外 Y 偏移(世界单位,叠加在自动贴地之上)
    private static float modelYaw = 0f;           // 基础朝向修正(度;模型背对行军方向时设 180)
    private static float shipYaw = 0f;            // v1.8.0:船模朝向修正(度;船头背对航向时设 180)
    private static float morphMs = 550f;          // v1.9.0:陆↔海过渡时长(ms;模型“缩小→换模→放大”;0=关)
    private static boolean morphMove = true;      // v1.9.1:true=过渡绑定行军进度(默认);false=到站后才播放
    private static int numBones = 0;              // v2.0.0:着色器骨骼上限(0=自动按模型统计;拼装兵模 torso 44 骨)
    private static int comboCacheSize = 32;       // v4.9.7:20 → 32(兵种多时反复淘汰重载=缩放来回模型回退)
    private static int shipCacheSize = 12;        // v4.9.7:8 → 12(同上,船模挂件组合)
    private static float turnTau = 0.2f;          // 转向平滑时间常数(秒;v1.4.6;越小越快,0=瞬间转向)
    private static float tiltStrength = 1f;       // v3.0.0:伪3D地图(Pseudo3DMap)适配强度(0=关闭;1=标准;实时生效)
    /* ---- v4.9.8:伪3D 地图的两项表现适配(setting.json) ---- */
    /** 近大远小比例(0=关闭;1=标准;>1 更夸张)。有效深度系数 = tiltStrength × nearFar。 */
    private static float nearFar = 1f;
    /** 视角随地图缩放:俯仰角区间 [最小, 最大](度;最小=地图拉到最近(最平视)时,最大=缩到兵模刚出现时)。 */
    private static float viewPitchMin = 12f;
    private static float viewPitchMax = 55f;
    /** 视角随缩放是否启用(false = 固定基准俯角 21.78°,与旧版一致)。 */
    private static boolean viewPitchOn = true;
    /** 俯仰角映射:兵模可见阈值的快速兑底錨点 + 对数跨度(ln 6 → 锚点一直算到放大 6 倍)。 */
    private static final float VIEW_PITCH_SCALE_MIN = 0.6f;
    private static final float VIEW_PITCH_LOG_SPAN = 1.7917595f;
    private static boolean scopePlayerOnly = false; // false=全部部队(默认);true=只画玩家部队
    private static int maxModels = 24;            // 同屏兵模上限(v1.7.0:10→24;上限 128)
    private static float spinSpeed = 0f;          // 自旋(弧度/秒;0=静立)
    /** v4.9.0:兵模出现的最小地图缩放 = `Game.DRAW_ARMY_MIN_SCALE × 本倍率` —— 兵牌先显示,再放大一点才画模型。 */
    private static float modelMinScaleMul = 1.25f;
    /** v4.9.3:兵模出现的最小地图缩放(绝对値;>0 时优先生效,0 = 用倍率×兵牌阈值)。 */
    private static float modelMinScale = 0f;
    /** v4.9.0:5 档最高速时隐藏「移动中」部队的兵模(多兵牌同时移动时省算力/减闪烁)。 */
    private static boolean hideMovingAtMaxSpeed = true;
    /** v4.9.2:屏外裁剪边距(px;兵牌完全在屏幕外这么远时不采采集/不画模型)。 */
    private static float cullMarginPx = 96f;
    /** v4.9.6:combo 加载失败后的重试间隔基数(帧;默认 600≈10s,实际间隔随失败次数递增)。 */
    private static int comboRetryFrames = 600;
    /** v4.9.7:最多重试次数(0=不限,一直退避重试;>0=达到后视为永久失败)。 */
    private static int comboRetryMax = 0;
    /** v4.9.6:单帧 combo 加载耗时预算(ms;超出则后续几帧暂停加载,避免连续掉帧)。 */
    private static float comboLoadBudgetMs = 6f;

    /* ---- 每帧部队列表(绘制线程写入,element 读取) ---- */
    static final int MAX_BUF = 128;
    static final float[] rectBuf = new float[MAX_BUF * 4];
    static final int[] relBuf = new int[MAX_BUF];
    static final float[] facingBuf = new float[MAX_BUF];
    /** 每部队动作状态:0=停留 1=移动 2=战斗 3=撤退。 */
    static final int[] stateBuf = new int[MAX_BUF];
    /** v1.8.0:每部队模型类型:0=陆军 1=船(海面地块)。 */
    static final int[] kindBuf = new int[MAX_BUF];
    /** v1.9.0:每部队兵牌 key(与 rectBuf 同序;陆↔海过渡检测用)。 */
    static final String[] keyBuf = new String[MAX_BUF];
    /** v1.9.1:每部队行程过渡进度(0..1 = 行程进度;-1 = 无过渡(不变/走时间兜底))。 */
    static final float[] morphBuf = new float[MAX_BUF];
    /** v2.1.0:每部队兵模 combo id(按主兵种/等级/文化解析;null/空 = 用默认模型)。 */
    static final String[] comboBuf = new String[MAX_BUF];
    /** v4.0.0:每部队运行时挂件签名(`件名@骨;…`;空 = 不挂)。 */
    static final String[] attachBuf = new String[MAX_BUF];
    /** v4.8.0:每部队船模 combo id(海面/过渡放大段用;不能和陆军 combo 混用)。 */
    static final String[] comboShipBuf = new String[MAX_BUF];
    /** v4.8.0:每部队船模挂件签名。 */
    static final String[] attachShipBuf = new String[MAX_BUF];
    /** v4.1.0:每部队染色颜色(0xRRGGBB;-1 = 不染)。 */
    static final int[] tintBuf = new int[MAX_BUF];
    static volatile int modelCount = 0;
    /** 本帧列表中的战斗部队数(诊断)。 */
    static volatile int battleCount = 0;

    /* ---- v1.7.0 候选池(采集阶段;去重/排序/截断在 finalizeSelection) ---- */
    private static final long GROUP_STATIONARY = 1L << 56;       // 同地块堆叠组
    private static final long GROUP_MOVING = 2L << 56;           // 同行军段组(同文明同起终点)
    private static final long GROUP_BATTLE = 3L << 56;           // 同战斗同侧组
    private static final long[] cGroupKey = new long[MAX_BUF];   // 去重键(<0 = 独立,不参与合并)
    private static final float[] cAnchorX = new float[MAX_BUF];  // 判定锚点(兵牌中心;算离视角中心距离)
    private static final float[] cAnchorY = new float[MAX_BUF];
    private static final float[] cRectX = new float[MAX_BUF];
    private static final float[] cRectY = new float[MAX_BUF];
    private static final float[] cSize = new float[MAX_BUF];
    private static final float[] cFacing = new float[MAX_BUF];
    private static final float[] cDist = new float[MAX_BUF];
    private static final int[] cRel = new int[MAX_BUF];
    private static final int[] cState = new int[MAX_BUF];
    private static final int[] cSel = new int[MAX_BUF];
    private static final int[] cKind = new int[MAX_BUF];   // v1.8.0:0=陆军 1=船模(海面)
    private static final String[] cCombo = new String[MAX_BUF]; // v2.1.0:每候选的兵模 combo id(陆军)
    private static final String[] cAttach = new String[MAX_BUF]; // v4.0.0:每候选的挂件签名(陆军)
    private static final String[] cComboShip = new String[MAX_BUF]; // v4.8.0:每候选的船模 combo id
    private static final String[] cAttachShip = new String[MAX_BUF]; // v4.8.0:每候选的船模挂件签名
    private static final int[] cTint = new int[MAX_BUF];         // v4.1.0:每候选的染色颜色
    private static final boolean[] cFaceFixed = new boolean[MAX_BUF];
    private static final boolean[] cDrop = new boolean[MAX_BUF];
    private static final int[] cOrder = new int[MAX_BUF];
    private static final ArmyDivision[] cArmy = new ArmyDivision[MAX_BUF];
    /** v4.9.3:候选是否真的在行军(游戏标记 or 路线进度推进)。 */
    private static final boolean[] cMoving = new boolean[MAX_BUF];
    private static int candCount = 0;
    /**
     * v1.7.2:行军组粘性赢家(组键 → 兵牌 key)—— 同组各兵牌速度不同时,离屏中心最近者会不断换人
     * (模型在几个兵牌之间来回闪);一旦选定就在本路段内持续跟随,不再重选。上限整表清理。
     */
    private static final HashMap<Long, String> moveWinner = new HashMap<Long, String>();

    /** 每部队朝向缓存:key = civID|armyKey → 上次朝向(度)。 */
    private static final HashMap<String, Float> facingCache = new HashMap<String, Float>();
    /** v4.9.3:行军进度观测:armyKey → {上次路段进度, 上次“有推进”的毫秒时间}。 */
    private static final HashMap<String, float[]> marchSeen = new HashMap<String, float[]>();
    /** v4.9.3:进度保活窗(ms)—— 游戏按刻推进行军,帧间进度可能不变,窗口内仍视为行军。 */
    private static final long MARCH_HOLD_MS = 400L;
    /** facingOf 的附加输出:本帧是否移动中(1=是)。 */
    private static float tmpMoving = 0f;
    /** 相机俯角 sin(≈21.8°):屏幕纵向位移 → 世界 Z 位移换算(转向用)。 */
    private static final float SIN_TILT = 0.3714f;

    /* ---- v3.0.0:伪3D地图(Pseudo3DMap)适配 ---- */
    /** Game.getTiltK() 反射缓存(null = 该方法缺失 = 未装伪3D补丁/桌面原版)。 */
    private static java.lang.reflect.Method tiltKMethod;
    private static boolean tiltKResolved = false;
    /** 本帧倾斜系数 ks = getTiltK()×currentScale×tiltStrength(0=未装/关闭;updateArmyList 每帧刷新)。 */
    private static float tiltKS = 0f;
    /** v4.9.12:本帧「近大远小」实际用到的尺寸系数区间(诊断;未生效时都是 1)。 */
    private static float nearFarFMin = 1f;
    private static float nearFarFMax = 1f;

    private static HotContext ctx;
    private static String lastListErr = "";
    private static float spinAngle = 0f;

    private ArmyOverlayMenu overlay;
    private long frames = 0;
    private boolean mounted = false;
    private boolean staleChecked = false;
    private boolean diagLogged = false;
    private boolean disabledLogged = false;
    private String lastLoggedErr = "";

    @Override
    public void init(HotContext context) {
        ctx = context;
        HotEvents.register(this, HotEvents.FRAME);
        try {
            Gdx.app.addLifecycleListener(new LifecycleListener() {
                @Override
                public void pause() {
                }

                @Override
                public void resume() {
                    G3DArmyMin.invalidate();
                    log("回前台:GL 上下文可能已重建,兵模资源将在下次绘制时重建");
                }

                @Override
                public void dispose() {
                }
            });
        } catch (Throwable ignored) {
        }
        log("已加载 v4.9.14 G3DArmy(宿主 " + context.hostVersion() + ");近大远小=与地图局部缩放同源(1/D²);相机绕模型中心环绕,俯角随缩放(拉近=更平视);单帧加载预算");
        loadConfig();
    }

    /** 读取/生成设置文件(setting.json;旧版 army.json 兼容)。 */
    private void loadConfig() {
        try {
            // v4.9.1:配置/文明/装配资源可能变化 → 选型缓存整体作废(重载后不会用旧结果)
            selectCache.clear();
            File f = new File(ctx.dataDir(), SETTING_FILE);
            if (!f.exists()) {
                File legacy = new File(ctx.dataDir(), LEGACY_SETTING_FILE);
                if (legacy.isFile()) {
                    f = legacy;   // v4.2.0:老版本资源目录
                }
            }
            if (!f.exists()) {
                File legacy2 = new File(new File(ctx.dataDir(), "_legacy"), LEGACY_SETTING_FILE);
                if (legacy2.isFile()) {
                    f = legacy2;
                }
            }
            if (!f.exists()) {
                FileWriter w = new FileWriter(f);
                w.write("{\n");
                w.write("  \"enabled\": " + enabled + ",\n");
                w.write("  \"modelPx\": " + modelPx + ",\n");
                w.write("  \"modelPxW\": " + modelPxW + ",\n");
                w.write("  \"modelPxH\": " + modelPxH + ",\n");
                w.write("  \"modelPxLimit\": [" + modelPxMin + ", " + modelPxMax + "],\n");
                w.write("  \"modelMulLimit\": [" + modelMulMin + ", " + modelMulMax + "],\n");
                w.write("  \"modelLift\": [],\n");
                w.write("  \"modelFitPct\": " + modelFitPct + ",\n");
                w.write("  \"modelFitBy\": \"" + modelFitBy + "\",\n");
                w.write("  \"liftPx\": " + liftPx + ",\n");
                w.write("  \"battleSplitPx\": " + battleSplitPx + ",\n");
                w.write("  \"stackFirstOnly\": " + stackFirstOnly + ",\n");
                w.write("  \"model\": \"" + modelFile + "\",\n");
                w.write("  \"shipModel\": \"" + shipModelFile + "\",\n");
                w.write("  \"comboMode\": " + comboMode + ",\n");
                w.write("  \"partsFile\": \"" + partsFileName + "\",\n");
                w.write("  \"animation\": \"" + animationName + "\",\n");
                w.write("  \"animationIdle\": \"" + animIdleName + "\",\n");
                w.write("  \"animationMove\": \"" + animMoveName + "\",\n");
                w.write("  \"animationBattle\": \"" + animBattleName + "\",\n");
                w.write("  \"animationRetreat\": \"" + animRetreatName + "\",\n");
                w.write("  \"animSpeed\": " + animSpeed + ",\n");
                w.write("  \"worldH\": " + worldH + ",\n");
                w.write("  \"tint\": \"" + tintMode + "\",\n");
                w.write("  \"tintStrength\": " + tintStrength + ",\n");
                w.write("  \"tintLift\": " + tintLift + ",\n");
                w.write("  \"tintSkip\": \"" + tintSkip + "\",\n");
                w.write("  \"feetDark\": " + feetDark + ",\n");
                w.write("  \"shadowScale\": " + shadowScale + ",\n");
                w.write("  \"shadowAnchor\": " + shadowAnchor + ",\n");
                w.write("  \"shadowShiftZ\": " + shadowShiftZ + ",\n");
                w.write("  \"shadowShiftPct\": " + shadowShiftPct + ",\n");
                w.write("  \"shadowCore\": " + shadowCore + ",\n");
                w.write("  \"shadowBake\": " + shadowBake + ",\n");
                w.write("  \"modelAutoFit\": " + modelAutoFit + ",\n");
                w.write("  \"modelScale\": " + modelScale + ",\n");
                w.write("  \"modelOffsetY\": " + modelOffsetY + ",\n");
                w.write("  \"modelYaw\": " + modelYaw + ",\n");
                w.write("  \"shipYaw\": " + shipYaw + ",\n");
                w.write("  \"morphMs\": " + morphMs + ",\n");
                w.write("  \"morphMove\": " + morphMove + ",\n");
                w.write("  \"numBones\": " + numBones + ",\n");
                w.write("  \"turnTau\": " + turnTau + ",\n");
                w.write("  \"tiltStrength\": " + tiltStrength + ",\n");
                w.write("  \"nearFar\": " + nearFar + ",\n");
                w.write("  \"viewPitch\": [" + viewPitchMin + ", " + viewPitchMax + "],\n");
                w.write("  \"scope\": \"" + (scopePlayerOnly ? "player" : "all") + "\",\n");
                w.write("  \"maxModels\": " + maxModels + ",\n");
                w.write("  \"modelMinScaleMul\": " + modelMinScaleMul + ",\n");
                w.write("  \"modelMinScale\": " + modelMinScale + ",\n");
                w.write("  \"hideMovingAtMaxSpeed\": " + hideMovingAtMaxSpeed + ",\n");
                w.write("  \"cullMarginPx\": " + cullMarginPx + ",\n");
                w.write("  \"comboRetryFrames\": " + comboRetryFrames + ",\n");
                w.write("  \"comboRetryMax\": " + comboRetryMax + ",\n");
                w.write("  \"comboLoadBudgetMs\": " + comboLoadBudgetMs + ",\n");
                w.write("  \"spinSpeed\": " + spinSpeed + "\n");
                w.write("}\n");
                w.close();
                log("已生成默认配置:" + f.getAbsolutePath());
            } else {
                String text = readAll(f);
                enabled = getBool(text, "enabled", enabled);
                modelPx = getFloat(text, "modelPx", modelPx);
                modelPxW = getFloat(text, "modelPxW", modelPxW);
                modelPxH = getFloat(text, "modelPxH", modelPxH);
                // v4.9.14:上下限统一用数组 [min, max];旧标量键仍兼容
                float[] pxLim = getFloatArray(text, "modelPxLimit", null);
                if (pxLim != null && pxLim.length >= 2) {
                    modelPxMin = pxLim[0];
                    modelPxMax = pxLim[1];
                } else {
                    modelPxMin = getFloat(text, "modelPxMin", modelPxMin);
                    modelPxMax = getFloat(text, "modelPxMax", modelPxMax);
                }
                float[] mulLim = getFloatArray(text, "modelMulLimit", null);
                if (mulLim != null && mulLim.length >= 2) {
                    modelMulMin = mulLim[0];
                    modelMulMax = mulLim[1];
                } else {
                    modelMulMin = getFloat(text, "modelMulMin", modelMulMin);
                    modelMulMax = getFloat(text, "modelMulMax", modelMulMax);
                }
                // v4.9.14:窗口相对兵牌位置 [平视端, 俯视端](空数组/缺键 = 自动补偿)
                float[] liftArr = getFloatArray(text, "modelLift", null);
                modelLiftManual = liftArr != null && liftArr.length >= 2;
                if (modelLiftManual) {
                    modelLiftMin = liftArr[0];
                    modelLiftMax = liftArr[1];
                }
                modelFitPct = getFloat(text, "modelFitPct", modelFitPct);
                modelFitBy = getStr(text, "modelFitBy", modelFitBy);
                liftPx = getFloat(text, "liftPx", liftPx);
                battleSplitPx = getFloat(text, "battleSplitPx", battleSplitPx);
                stackFirstOnly = getBool(text, "stackFirstOnly", stackFirstOnly);
                modelFile = getStr(text, "model", modelFile);
                shipModelFile = getStr(text, "shipModel", shipModelFile);
                comboMode = getBool(text, "comboMode", comboMode);
                partsFileName = getStr(text, "partsFile", partsFileName);
                animationName = getStr(text, "animation", animationName);
                animIdleName = getStr(text, "animationIdle", animIdleName);
                animMoveName = getStr(text, "animationMove", animMoveName);
                animBattleName = getStr(text, "animationBattle", animBattleName);
                animRetreatName = getStr(text, "animationRetreat", animRetreatName);
                animSpeed = getFloat(text, "animSpeed", animSpeed);
                worldH = getFloat(text, "worldH", worldH);
                String tintVal = getStr(text, "tint", tintMode);
                if ("true".equalsIgnoreCase(tintVal)) {
                    tintMode = "relation";
                } else if ("false".equalsIgnoreCase(tintVal)) {
                    tintMode = "none";
                } else {
                    tintMode = tintVal;
                }
                tintStrength = getFloat(text, "tintStrength", tintStrength);
                tintLift = getFloat(text, "tintLift", tintLift);
                tintSkip = getStr(text, "tintSkip", tintSkip);
                feetDark = getFloat(text, "feetDark", feetDark);
                shadowScale = getFloat(text, "shadowScale", shadowScale);
                shadowAnchor = getBool(text, "shadowAnchor", shadowAnchor);
                shadowShiftZ = getFloat(text, "shadowShiftZ", shadowShiftZ);
                shadowShiftPct = getFloat(text, "shadowShiftPct", shadowShiftPct);
                shadowCore = getFloat(text, "shadowCore", shadowCore);
                shadowBake = getBool(text, "shadowBake", shadowBake);
                modelAutoFit = getBool(text, "modelAutoFit", modelAutoFit);
                modelScale = getFloat(text, "modelScale", modelScale);
                modelOffsetY = getFloat(text, "modelOffsetY", modelOffsetY);
                modelYaw = getFloat(text, "modelYaw", modelYaw);
                shipYaw = getFloat(text, "shipYaw", shipYaw);
                morphMs = getFloat(text, "morphMs", morphMs);
                morphMove = getBool(text, "morphMove", morphMove);
                numBones = (int) getFloat(text, "numBones", numBones);
                comboCacheSize = (int) getFloat(text, "comboCache", comboCacheSize);
                shipCacheSize = (int) getFloat(text, "shipCache", shipCacheSize);
                turnTau = getFloat(text, "turnTau", turnTau);
                tiltStrength = getFloat(text, "tiltStrength", tiltStrength);
                nearFar = getFloat(text, "nearFar", nearFar);
                float[] vp = getFloatArray(text, "viewPitch", null);
                if (vp != null && vp.length >= 2) {
                    viewPitchMin = vp[0];
                    viewPitchMax = vp[1];
                }
                String scope = getStr(text, "scope", scopePlayerOnly ? "player" : "all");
                scopePlayerOnly = !"all".equalsIgnoreCase(scope);
                maxModels = (int) getFloat(text, "maxModels", maxModels);
                modelMinScaleMul = getFloat(text, "modelMinScaleMul", modelMinScaleMul);
                modelMinScale = getFloat(text, "modelMinScale", modelMinScale);
                hideMovingAtMaxSpeed = getBool(text, "hideMovingAtMaxSpeed", hideMovingAtMaxSpeed);
                cullMarginPx = getFloat(text, "cullMarginPx", cullMarginPx);
                comboRetryFrames = (int) getFloat(text, "comboRetryFrames", comboRetryFrames);
                comboRetryMax = (int) getFloat(text, "comboRetryMax", comboRetryMax);
                comboLoadBudgetMs = getFloat(text, "comboLoadBudgetMs", comboLoadBudgetMs);
                spinSpeed = getFloat(text, "spinSpeed", spinSpeed);
                log("已读取配置:" + f.getAbsolutePath());
            }
        } catch (Throwable t) {
            log("配置读取失败(用默认值):" + t);
        }
        if (maxModels < 1) {
            maxModels = 1;
        }
        if (maxModels > MAX_BUF) {
            maxModels = MAX_BUF;
        }
        // v4.9.13:渲染窗口像素上下限(0=不限;两者都给了但反了就交换)
        if (modelPxMin < 0f) {
            modelPxMin = 0f;
        } else if (modelPxMin > 2048f) {
            modelPxMin = 2048f;
        }
        if (modelPxMax < 0f) {
            modelPxMax = 0f;
        } else if (modelPxMax > 4096f) {
            modelPxMax = 4096f;
        }
        if (modelPxMax > 0f && modelPxMin > modelPxMax) {
            float tmp = modelPxMin;
            modelPxMin = modelPxMax;
            modelPxMax = tmp;
        }
        // v4.9.13:模型尺寸倍率上下限(默认 0.5~2 = 旧硬编码;允许 0.1~4)
        if (modelMulMin < 0.1f) {
            modelMulMin = 0.1f;
        } else if (modelMulMin > 4f) {
            modelMulMin = 4f;
        }
        if (modelMulMax < 0.1f) {
            modelMulMax = 0.1f;
        } else if (modelMulMax > 4f) {
            modelMulMax = 4f;
        }
        if (modelMulMax < modelMulMin) {
            float tmp = modelMulMin;
            modelMulMin = modelMulMax;
            modelMulMax = tmp;
        }
        // v4.9.14:窗口相对兵牌位置 [平视端, 俯视端](允许负值 = 压到兵牌基准线下方)
        if (modelLiftManual) {
            if (modelLiftMin < -2048f) {
                modelLiftMin = -2048f;
            } else if (modelLiftMin > 2048f) {
                modelLiftMin = 2048f;
            }
            if (modelLiftMax < -2048f) {
                modelLiftMax = -2048f;
            } else if (modelLiftMax > 2048f) {
                modelLiftMax = 2048f;
            }
        }
        updateLift();
        // v4.9.0:兵模出现缩放的倍率夹取(1=与兵牌同时出现;越大=需放大更多才画模型)
        if (modelMinScaleMul < 1f) {
            modelMinScaleMul = 1f;
        } else if (modelMinScaleMul > 4f) {
            modelMinScaleMul = 4f;
        }
        // v4.9.3:绝对缩放值夹取(0=自动;0~1 也允许,便于部署后酌情微调)
        if (modelMinScale < 0f) {
            modelMinScale = 0f;
        } else if (modelMinScale > 4f) {
            modelMinScale = 4f;
        }
        // v4.9.2:屏外裁剪边距(0~512)
        if (cullMarginPx < 0f) {
            cullMarginPx = 0f;
        } else if (cullMarginPx > 512f) {
            cullMarginPx = 512f;
        }
        // v2.1.0:读取模型库配置(loadSelect.json 的 unitSelect / shipSelect)
        loadPartsConfig();
        // v4.6.0:Religions.json 原始英文名(ID = 数组下标;用于 religion 选择器)
        loadReligionNames();
        // v4.0.0:读取运行时挂件配置(models/attachments.json)
        loadAttachConfig();
        // v4.3.0:装配配置(combo → animSet)、文明变体、动画事件/状态机
        loadAssemblyConfig();
        loadCivilizationConfig();
        loadAnimEvents();
        shipModelFile = resolveShipCombo(currentAge());
        if (tiltStrength < 0f) {
            tiltStrength = 0f;
        } else if (tiltStrength > 2f) {
            tiltStrength = 2f;
        }
        // v4.9.8:近大远小比例(0=关闭;上限 4)
        if (nearFar < 0f) {
            nearFar = 0f;
        } else if (nearFar > 4f) {
            nearFar = 4f;
        }
        // v4.9.8:视角俯仰角区间(自动排序;限幅 1~85°;两点重合/非法 = 关闭 → 固定基准俯角)
        {
            float lo = Math.min(viewPitchMin, viewPitchMax);
            float hi = Math.max(viewPitchMin, viewPitchMax);
            if (lo < 1f) {
                lo = 1f;
            } else if (lo > 85f) {
                lo = 85f;
            }
            if (hi < 1f) {
                hi = 1f;
            } else if (hi > 85f) {
                hi = 85f;
            }
            viewPitchMin = lo;
            viewPitchMax = hi;
            viewPitchOn = (hi - lo) > 0.5f;
        }
        G3DArmyMin.spinSpeed = spinSpeed;
        G3DArmyMin.confModel = modelFile;
        G3DArmyMin.confAnimation = animationName;
        G3DArmyMin.confAnimIdle = animIdleName;
        G3DArmyMin.confAnimMove = animMoveName;
        G3DArmyMin.confAnimBattle = animBattleName;
        G3DArmyMin.confAnimRetreat = animRetreatName;
        G3DArmyMin.confAnimSpeed = animSpeed;
        G3DArmyMin.confWorldH = worldH;
        G3DArmyMin.confFitPct = modelFitPct;
        G3DArmyMin.confFitByMax = "max".equalsIgnoreCase(modelFitBy);
        G3DArmyMin.confTint = !"none".equalsIgnoreCase(tintMode);
        G3DArmyMin.confTintStrength = Math.max(0f, Math.min(1f, tintStrength));
        G3DArmyMin.confTintSkip = splitCsv(tintSkip);
        G3DArmyMin.confFeetDark = Math.max(0.05f, Math.min(1f, feetDark));
        G3DArmyMin.confShadowScale = Math.max(0.3f, Math.min(2.5f, shadowScale));
        G3DArmyMin.confShadowAnchor = shadowAnchor;
        G3DArmyMin.confShadowShiftZ = Math.max(-1.0f, Math.min(1.0f, shadowShiftZ));
        G3DArmyMin.confShadowShiftPct = Math.max(-0.5f, Math.min(0.5f, shadowShiftPct));
        G3DArmyMin.confShadowCore = Math.max(0f, Math.min(1.5f, shadowCore));
        G3DArmyMin.confShadowBake = shadowBake;
        G3DArmyMin.confModelAutoFit = modelAutoFit;
        G3DArmyMin.confModelScale = modelScale;
        G3DArmyMin.confModelOffsetY = modelOffsetY;
        G3DArmyMin.confModelYaw = modelYaw;
        G3DArmyMin.confShipModel = shipModelFile;
        G3DArmyMin.confShipYaw = shipYaw;
        G3DArmyMin.confMorphMs = morphMs;
        G3DArmyMin.confNumBones = numBones;
        // v4.9.5:模型缓存容量(默认 20/8;太小 = 反复淘汰重载,每次 80~100ms)
        if (comboCacheSize < 4) {
            comboCacheSize = 4;
        } else if (comboCacheSize > G3DArmyMin.MAX_COMBO_SETS_CAP) {
            comboCacheSize = G3DArmyMin.MAX_COMBO_SETS_CAP;
        }
        if (shipCacheSize < 2) {
            shipCacheSize = 2;
        } else if (shipCacheSize > G3DArmyMin.MAX_SHIP_SETS_CAP) {
            shipCacheSize = G3DArmyMin.MAX_SHIP_SETS_CAP;
        }
        G3DArmyMin.maxComboSets = comboCacheSize;
        G3DArmyMin.maxShipSets = shipCacheSize;
        // v4.9.6:失败重试 / 加载预算(0=关闭预算)
        if (comboRetryFrames < 60) {
            comboRetryFrames = 60;
        } else if (comboRetryFrames > 36000) {
            comboRetryFrames = 36000;
        }
        if (comboRetryMax < 0) {
            comboRetryMax = 0;      // v4.9.7:0 = 不限重试(未定失败总能自愈)
        } else if (comboRetryMax > 20) {
            comboRetryMax = 20;
        }
        if (comboLoadBudgetMs < 0f) {
            comboLoadBudgetMs = 0f;
        } else if (comboLoadBudgetMs > 50f) {
            comboLoadBudgetMs = 50f;
        }
        G3DArmyMin.comboRetryFrames = comboRetryFrames;
        G3DArmyMin.comboRetryMax = comboRetryMax;
        G3DArmyMin.comboLoadBudgetMs = comboLoadBudgetMs;
        G3DArmyMin.dataDirPath = ctx.dataDir() == null ? null : ctx.dataDir().getAbsolutePath();
        // v4.2.0:扫资源目录(models/<类>/{g3d,png};旧 g3d/png/parts 兼容)
        G3DArmyMin.scanResources();
        // v4.3.0:按 index.json 做一次启动校验(仅日志,不阻断)
        validateResourceIndexes();
        log("配置:开关=" + (enabled ? "开" : "关") + " 兵模尺寸=" + modelPx + "px 抬升=" + liftPx + "px(位置="
                + (modelLiftManual ? (modelLiftMin + "~" + modelLiftMax) : "自动") + ") 战斗分离=" + battleSplitPx + "px 堆叠首个="
                + (stackFirstOnly ? "是" : "否") + " 范围="
                + (scopePlayerOnly ? "仅玩家部队" : "全部部队")
                + " 上限=" + maxModels + " 自旋=" + spinSpeed
                + " 模型=" + modelFile + " 船模=" + shipModelFile + " 过渡=" + (morphMove ? "随行军进度" : (morphMs > 0f ? ((int) morphMs + "ms") : "关"))
                + " 动画=" + ("".equals(animationName) ? "自动" : animationName)
                + " 视口高=" + worldH + " 模型适配=" + (modelAutoFit ? ("自动(" + ("max".equalsIgnoreCase(modelFitBy) ? "最大边" : "高度") + " " + modelFitPct + ")") : "关")
                + " 窗口=" + ((modelPxW > 0f ? (int) modelPxW : (int) modelPx) + "x" + ((modelPxH > 0f ? (int) modelPxH : (int) modelPx)) + "px")
                + " 窗口限=" + (modelPxMin > 0f ? String.valueOf((int) modelPxMin) : "-") + "~" + (modelPxMax > 0f ? String.valueOf((int) modelPxMax) : "-")
                + "px 倍率限=" + modelMulMin + "~" + modelMulMax
                + (modelScale > 0f ? " 手动缩放=" + modelScale : "")
                + (modelYaw != 0f ? " 朝向修正=" + modelYaw : "")
                + " 转向τ=" + turnTau + "s 骨骼=" + (numBones > 0 ? String.valueOf(numBones) : "自动")
                + " 缓存=" + comboCacheSize + "/" + shipCacheSize
                + " 模型选择=" + (unitSelectNode != null ? (partsFileName + "(主兵种)") : (comboMode ? "未读到配置" : "关"))
                + " 伪3D强度=" + tiltStrength
                + " 近大远小=" + nearFar
                + " 视角俯仰=" + (viewPitchOn ? (viewPitchMin + "~" + viewPitchMax + "°(随缩放)") : "固定")                + " 脚底暗=" + feetDark + " 阴影×" + shadowScale + "(" + (shadowAnchor ? "脚底锚点" : "固定原点")
                + " 偏移" + shadowShiftZ + "+" + shadowShiftPct + "×高/核心" + shadowCore + ")");
        writeReadmeIfMissing();
    }

    private static String readAll(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) {
                    break;
                }
                off += r;
            }
            return new String(buf, 0, off, "UTF-8");
        } finally {
            in.close();
        }
    }

    /** 读取 `"key": <字面量>` 的原始文本(截到 , } 换行 为止;找不到返回 null)。 */
    private static String rawValue(String text, String key) {
        int i = text.indexOf("\"" + key + "\"");
        if (i < 0) {
            return null;
        }
        int c = text.indexOf(':', i);
        if (c < 0) {
            return null;
        }
        int e = c + 1;
        while (e < text.length()) {
            char ch = text.charAt(e);
            if (ch == ',' || ch == '}' || ch == '\n' || ch == '\r') {
                break;
            }
            e++;
        }
        return text.substring(c + 1, e).trim();
    }

    private static float getFloat(String text, String key, float def) {
        try {
            String v = rawValue(text, key);
            return v == null ? def : Float.parseFloat(v);
        } catch (Throwable t) {
            return def;
        }
    }

    private static boolean getBool(String text, String key, boolean def) {
        try {
            String v = rawValue(text, key);
            return v == null ? def : Boolean.parseBoolean(v);
        } catch (Throwable t) {
            return def;
        }
    }

    private static String getStr(String text, String key, String def) {
        try {
            int i = text.indexOf("\"" + key + "\"");
            if (i < 0) {
                return def;
            }
            int q1 = text.indexOf('"', text.indexOf(':', i));
            if (q1 < 0) {
                return def;
            }
            int q2 = text.indexOf('"', q1 + 1);
            if (q2 < 0) {
                return def;
            }
            return text.substring(q1 + 1, q2);
        } catch (Throwable t) {
            return def;
        }
    }

    @Override
    public void onEvent(String type, Object[] args) {
        if (!HotEvents.FRAME.equals(type)) {
            return;
        }
        try {
            frame(args);
        } catch (Throwable ignored) {
        }
    }

    private void frame(Object[] args) {
        frames++;

        // 旧实例覆盖层清理要先行:即便本次以 enabled=false 启动,也要收掉重载前残留的覆盖层
        if (!staleChecked && frames >= 3L) {
            staleChecked = true;
            hideStaleWindows();
        }

        // v1.6.0:总开关(取代原「关闭 3D 兵模」按钮)——false 时完全不挂载/不渲染
        if (!enabled) {
            if (!disabledLogged) {
                disabledLogged = true;
                log("3D 兵模已按 army.json 关闭(enabled=false;改回 true 后「全部重载」生效)");
            }
            return;
        }

        float dt = 0.016f;
        if (args != null && args.length > 1 && args[1] instanceof Float) {
            dt = (Float) args[1];
            if (dt <= 0f || dt > 0.25f) {
                dt = 0.016f;
            }
        }
        spinAngle += dt * spinSpeed;

        if (!mounted) {
            tryMount();
        }
        if (overlay == null) {
            return;
        }
        overlay.models.angle = spinAngle;

        // GL 诊断(首次资源就绪时打一条;v1.4.7 联动「启用日志」:关闭则不采集/不输出)
        if (logEnabled() && !diagLogged && overlay.models.inited()) {
            diagLogged = true;
            log("GL 信息: " + G3DArmyMin.diag);
            log("模型来源: " + G3DArmyMin.modelSource
                    + (G3DArmyMin.modelNote != null && G3DArmyMin.modelNote.length() > 0
                            ? " | " + G3DArmyMin.modelNote : ""));
        }

        // 错误变化上报
        String err = overlay.models.error();
        if (err == null) {
            err = "";
        }
        if (!err.equals(lastLoggedErr)) {
            if (err.length() > 0) {
                log("渲染错误: " + err);
            } else if (lastLoggedErr.length() > 0) {
                log("渲染已恢复正常");
            }
            lastLoggedErr = err;
        }

        // 状态读数(v1.4.7:日志关闭时连字符串都不拼)
        if (logEnabled() && (frames == 140L || frames % 900L == 0L)) {
            logStatus();
        }
    }

    /** v4.9.7:预加载节流计数(兵模不可见期间使用)。 */
    private static int prefetchFrame = 0;

    /**
     * 绘制帧内采集兵模候选(v1.7.0):迷雾门控(与兵牌同源)+ 仅默认「政治」地图模式 +
     * 视角内筛选 → (采集完后)组内去重 → 优先级排序 → 截断到 maxModels → 写出缓冲。
     */
    static void updateArmyList() {
        modelCount = 0;
        battleCount = 0;
        candCount = 0;
        // v2.1.0:时代变化 → 重算船模 combo(shipSelect.byAge)
        int ageID = currentAge();
        if (ageID != lastShipAge) {
            lastShipAge = ageID;
            String sc = resolveShipCombo(ageID);
            if (sc != null && !sc.equals(G3DArmyMin.confShipModel)) {
                G3DArmyMin.confShipModel = sc;
                // v4.8.0:只重建船模资源(旧版 invalidate() 会连陆军 combo 一起清空 →
                // 换时代后所有兵模逐帧重载,期间全是默认模型)
                G3DArmyMin.invalidateShip();
                log("时代=" + ageID + " → 船模=" + sc + "(仅重建船模资源)");
            }
        }
        try {
            if (Game.mapScale == null || Game.mapCoords == null || Game.player == null || Game.mapTouchManager == null) {
                return;
            }
            // v1.7.0:只在默认「政治」地图模式显示(外交/宗教/招募选择等其它模式不渲染兵模)
            if (Game.mapModes == null || Game.mapModes.iActiveMapModeID != Game.mapModes.MODE_DEFAULT) {
                return;
            }
            float scale = Game.mapScale.getCurrentScale();
            // v3.0.0:伪3D适配系数(ks = getTiltK×scale×强度;未装伪3D补丁/地图倾斜关闭 → 0 = 原行为)
            float kTilt = tiltK();
            tiltKS = tiltStrength * kTilt * scale;
            // 原版显示策略①(完整显示)+ ③的框选模式例外;淡出档(0.3~阈值)不画兵模(原版是渐隐)
            // v4.9.0:兵模比兵牌更晚出现 —— 兵牌在 DRAW_ARMY_MIN_SCALE 就显示,模型要再放大
            //          modelMinScaleMul 倍才画(多兵模同屏时减少“小尺寸密集模型”的负担)
            // v4.9.3:setting.json 的 modelMinScale 可直接给绝对值(>0 时优先,0=按倍率自动算)
            float modelMinScaleEff = modelMinScale > 0f
                    ? modelMinScale : (Game.DRAW_ARMY_MIN_SCALE * modelMinScaleMul);
            // v4.9.10:渲染窗口视角随地图缩放(以「兵模出现的缩放」为锚点 → 配置区间完整可用)
            updateViewPitch(scale, modelMinScaleEff);
            // v4.9.14:窗口相对兵牌的位置(俯视时脚会向中心收拢 → 自动下移窗口保持脚与兵牌的间距)
            updateLift();
            boolean show = scale >= modelMinScaleEff || Game.mapTouchManager.selectMode;
            if (!show) {
                // v4.9.7:兵模不可见(缩小到阈值以下)——旧版直接 return,导致加载队列在
                // 缩小期间完全不消费;放大回来只能 1 帧加载 1 个 combo,期间全是无枪兜底
                // 模型(用户看到的“缩放视角重新显示时模型不更新”)。
                if (prefetchDue()) {
                    collectArmies();
                    prefetchCandidates();
                }
                return;
            }
            collectArmies();
            finalizeSelection();
        } catch (Throwable t) {
            String s = String.valueOf(t);
            if (!s.equals(lastListErr)) {
                lastListErr = s;
                log("部队列表读取失败:" + s);
            }
        }
    }

    /** 采集视角内三张省列表的部队(陆 / 海 / 额外)。 */
    private static void collectArmies() {
        for (int k = 0; k < Game.NUM_OF_PROVINCES_IN_VIEW && candCount < MAX_BUF; k++) {
            addProvinceArmies(Game.getProvinceInViewID(k));
        }
        // v1.8.0:海面地块(其兵牌由游戏自身的“海面在视省”列表绘制;下海=船模)
        for (int k = 0; k < Game.NUM_OF_SEA_PROVINCES_IN_VIEW && candCount < MAX_BUF; k++) {
            addProvinceArmies(Game.getSeaProvinceInViewID(k));
        }
        for (int k = 0; k < Game.NUM_OF_EXTRA_PROVINCES_IN_VIEW && candCount < MAX_BUF; k++) {
            addProvinceArmies(Game.getExtraProvinceInViewID(k));
        }
    }

    /** v4.9.7:预加载节流(每 6 帧一次;缩小期间后台把 combo 排进加载队列)。 */
    private static boolean prefetchDue() {
        prefetchFrame++;
        return (prefetchFrame % 6) == 0;
    }

    /** v4.9.7:把本帧采集到的部队用到的 combo 全部预加载(不画、不排序)。 */
    private static void prefetchCandidates() {
        try {
            // 与可见时的显示上限对齐:只预热可能马上要画的那一批,避免把队列塞满
            int budget = maxModels > 0 ? maxModels : 24;
            for (int i = 0; i < candCount && budget > 0; i++, budget--) {
                G3DArmyMin.prefetch(cCombo[i], cAttach[i], cComboShip[i], cAttachShip[i]);
            }
        } catch (Throwable ignored) {
        }
        candCount = 0;
    }

    /**
     * v1.7.0 采集:把该省(迷雾可见)内所有部队写入候选池 —— 战斗锚定横幅、普通部队用游戏
     * 自身的屏幕坐标;不设上限,去重/排序/截断在 finalizeSelection。
     */
    private static void addProvinceArmies(int pid) {
        try {
            Province p = Game.getProvince(pid);
            if (p == null) {
                return;
            }
            // v1.7.0 迷雾门控:与游戏兵牌绘制同源(ProvinceDrawArmy.updateDrawArmy 仅在
            // getFogDrawArmy() 时画兵牌;关闭迷雾时全图恒 true)→ 看不到兵牌的地块也看不到兵模
            if (!p.getFogDrawArmy()) {
                return;
            }
            // v1.8.0:海洋地块(下海)= 船模;陆地 = 陆军模型(上岸自动换回)
            int kind = 0;
            try {
                if (p.getSeaProvince()) {
                    kind = 1;
                }
            } catch (Throwable ignored) {
            }
            int n = p.getArmySize();
            // v1.7.1:堆叠节流时,组模型锚定到“整叠最上方静止兵牌”的正上方
            //(修复:优先级选中的兵牌若位于堆叠中段,模型会盖住上方的兵牌)
            float topSX = 0f;
            float topSY = 0f;
            float topW = 0f;
            boolean topValid = false;
            if (stackFirstOnly) {
                for (int i = 0; i < n; i++) {
                    ArmyDivision a2 = p.getArmy(i);
                    if (a2 == null || a2.inBattle || a2.inMovement) {
                        continue;
                    }
                    if (scopePlayerOnly && a2.civID != Game.player.iCivID) {
                        continue;
                    }
                    float sy2 = ProvinceDrawArmy.getArmyPosY(pid, i);
                    if (!topValid || sy2 < topSY) {
                        topValid = true;
                        topSX = ProvinceDrawArmy.getArmyPosX(pid, i);
                        topSY = sy2;
                        topW = ProvinceDrawArmy.getArmyWidth(a2.iArmyWidth);
                    }
                }
            }
            for (int i = 0; i < n && candCount < MAX_BUF; i++) {
                ArmyDivision a = p.getArmy(i);
                if (a == null) {
                    continue;
                }
                if (scopePlayerOnly && a.civID != Game.player.iCivID) {
                    continue;
                }
                float size = modelPx;
                int sel = isSelectedArmy(a.key) ? 1 : 0;

                // ---- ① 战斗:锚定战斗横幅(游戏绘制的“进攻|⚔|防守”合并横幅) ----
                if (a.inBattle) {
                    Battle bt = findBattleForArmy(pid, a.key);
                    // v4.7.2:战斗已判定结束、但游戏线程尚未 removeBattle 的那一帧,直接按非战斗状态渲染,
                    // 避免战斗结束后还残留一帧攻击动画。
                    if (bt != null && bt.attackingArmy != null && bt.defendingArmy != null
                            && !bt.endOfBattle() && !bt.endOfBattle_NoAttacks()) {
                        int side = 3;
                        if (bt.attackingArmy != null && bt.attackingArmy.isInBattleArmy(a.key)) {
                            side = 1;
                        } else if (bt.defendingArmy != null && bt.defendingArmy.isInBattleArmy(a.key)) {
                            side = 2;
                        }
                        if (addBattleCandidate(bt, pid, a, side, size, sel, kind)) {
                            continue;
                        }
                    }
                }

                // ---- ②③ 非战斗:坐标 = 游戏自身的部队屏幕坐标(与方框绘制同源,含缩放变换) ----
                // v4.9.3:不能只看 `a.inMovement` —— 行军被高防御地块/模组逻辑阻断后会把这个标记清掉,
                // 恢复移动时又不一定置回 true(兵牌已经在走,标记仍是 false)→ 动画永远停在 idle。
                // 这里改为“游戏标记 or 行军路线进度在推进”双源判定(进度与兵牌位置完全同源)。
                MoveUnits muNow = findMoveUnits(a.civID, a.key);
                boolean moving = a.inMovement || isMarchingProgress(a, muNow);
                // v4.9.0:5 档最高速 + 多兵牌移动 → 移动中的部队不画模型
                //(每刻位置跳变,模型本来也跟不上;这段是缩放/换模/动画最集中的开销)
                if (moving && hideMovingAtMaxSpeed && isMaxGameSpeed()) {
                    continue;
                }
                float sx = ProvinceDrawArmy.getArmyPosX(pid, i);
                float sy = ProvinceDrawArmy.getArmyPosY(pid, i);
                float w = ProvinceDrawArmy.getArmyWidth(a.iArmyWidth);
                float rx = sx + w * 0.5f - size * 0.5f;
                // v4.9.14:用本帧生效抬升(自动/手动随俯角变;负值可压到兵牌基准线下方)
                float ry = sy - liftPxEff - size;
                // v1.7.0:只收集当前视角中能看到兵牌的部队(带可配余量;v4.9.2:200px → cullMarginPx)
                if (rx + size < -cullMarginPx || ry + size < -cullMarginPx
                        || rx > CFG.GAME_WIDTH + cullMarginPx || ry > CFG.GAME_HEIGHT + cullMarginPx) {
                    continue;
                }
                int state = a.inRetreat ? 3 : (moving ? 1 : 0);   // 撤退 > 移动 > 停留
                long gk;
                if (!stackFirstOnly) {
                    gk = -(candCount + 1);                        // 关闭节流 → 全部显示(不去重)
                } else if (moving) {
                    gk = moveGroupKey(a.civID, a.key, muNow);     // 同行军段(同文明同起终点)只显示一个
                } else {
                    gk = GROUP_STATIONARY | (pid & 0xFFFFL);      // 同地块堆叠只显示一个
                }
                // v1.7.1:堆叠组统一锚定“整叠最上方兵牌”的正上方(去重后该组只留一个候选)
                float ax = sx + w * 0.5f;
                float ay = sy + 16f;
                if (!moving && topValid) {
                    ax = topSX + topW * 0.5f;
                    ay = topSY + 16f;
                    rx = topSX + topW * 0.5f - size * 0.5f;
                    ry = topSY - liftPxEff - size;
                }
                addCandidate(gk, ax, ay, rx, ry, size,
                        relationOf(a.civID), 0f, state, sel, false, a, kind, moving);
            }
        } catch (Throwable t) {
            String s = String.valueOf(t);
            if (!s.equals(lastListErr)) {
                lastListErr = s;
                log("部队读取失败:" + s);
            }
        }
    }

    /** 写入一个候选(采集阶段;v4.9.3 增 moving = 真实行军状态)。 */
    private static void addCandidate(long gk, float ax, float ay, float rx, float ry, float size,
                                     int rel, float facing, int state, int sel, boolean faceFixed, ArmyDivision a,
                                     int kind, boolean moving) {
        int i = candCount++;
        cMoving[i] = moving;
        cGroupKey[i] = gk;
        cKind[i] = kind;
        cAnchorX[i] = ax;
        cAnchorY[i] = ay;
        cRectX[i] = rx;
        cRectY[i] = ry;
        cSize[i] = size;
        cRel[i] = rel;
        cFacing[i] = facing;
        cState[i] = state;
        cSel[i] = sel;
        cFaceFixed[i] = faceFixed;
        cArmy[i] = a;
        // v4.8.0:陆/海两套签名都必须算 —— 陆↔海过渡的两半段分别用它们。
        // 旧版只按当前地块类型算一套,过渡放大段就会拿“船模 id 当陆军模型”(或反之),
        // 查不到资源 → 回退默认兵模/默认船模(用户看到的“放大时先变回默认模型”)。
        // v4.9.1(B1):改走「主兵种签名」缓存 —— 签名不变时不重复做 JSON 选型解析。
        ResolvedSelect rs = resolveSelect(a);
        cCombo[i] = rs.landCombo;      // v2.1.0:主兵种(数量最多)→ combo
        cAttach[i] = rs.landAttach;    // v4.0.0:主兵种 → 运行时挂件
        cComboShip[i] = rs.shipCombo;  // v4.5.0:文明覆盖 > 全局 shipSelect
        cAttachShip[i] = rs.shipAttach; // v4.5.0:船模挂件(shipSelect)
        cTint[i] = tintOf(a);          // v4.1.0:文明/关系颜色
        float dcx = ax - CFG.GAME_WIDTH * 0.5f;
        float dcy = ay - CFG.GAME_HEIGHT * 0.5f;
        cDist[i] = dcx * dcx + dcy * dcy;   // 离视角中心的距离²(同级优先级的裁决依据)
    }

    /** v4.9.3:可复用已查到的 MoveUnits(避免每帧重复扫描文明行军表)。 */
    private static long moveGroupKey(int civID, String key, MoveUnits mu) {
        try {
            if (mu == null) {
                mu = findMoveUnits(civID, key);
            }
            if (mu != null) {
                int from = mu.getFromProvinceID();
                int dest = mu.getToProvinceID();
                if (from >= 0 && dest >= 0) {
                    return GROUP_MOVING | ((long) (civID & 0xFFFF) << 32)
                            | ((long) (from & 0xFFFF) << 16) | (dest & 0xFFFF);
                }
            }
        } catch (Throwable ignored) {
        }
        return -(candCount + 1);
    }

    /**
     * v1.7.0 收尾:组内择优(选中 > 我方绿0 > 敌方红1 > 盟友蓝2 > 其他灰3 > 离视角中心近
     * > 采集顺序)→ 全局排序 → 截断到 maxModels → 写出缓冲。
     * v1.7.2:行军组用粘性赢家(选中 > 上帧同组赢家 > 离屏中心近),防兵种速度差导致模型来回闪。
     */
    private static void finalizeSelection() {
        for (int i = 0; i < candCount; i++) {
            cDrop[i] = false;
        }
        // ---- v1.7.2:行军组粘性赢家 ----
        for (int i = 0; i < candCount; i++) {
            long gi = cGroupKey[i];
            if (gi < 0 || (gi >>> 56) != 2L) {
                continue;   // 非行军组(独立/堆叠/战斗组由下方通用去重处理)
            }
            boolean firstOfGroup = true;
            for (int j = 0; j < i; j++) {
                if (cGroupKey[j] == gi) {
                    firstOfGroup = false;
                    break;
                }
            }
            if (!firstOfGroup) {
                continue;
            }
            int winner = -1;
            for (int j = i; j < candCount; j++) {          // ① 选中者优先
                if (cGroupKey[j] == gi && cSel[j] == 1) {
                    winner = j;
                    break;
                }
            }
            if (winner < 0) {                              // ② 上一帧赢家(仍在组内则继续跟随)
                String prev = moveWinner.get(Long.valueOf(gi));
                if (prev != null) {
                    for (int j = i; j < candCount; j++) {
                        if (cGroupKey[j] == gi && cArmy[j] != null && prev.equals(cArmy[j].key)) {
                            winner = j;
                            break;
                        }
                    }
                }
            }
            if (winner < 0) {                              // ③ 首次出现:离视角中心近者
                for (int j = i; j < candCount; j++) {
                    if (cGroupKey[j] != gi) {
                        continue;
                    }
                    if (winner < 0 || compareCand(j, winner) < 0) {
                        winner = j;
                    }
                }
            }
            for (int j = i; j < candCount; j++) {          // 同组其余全部淘汰
                if (cGroupKey[j] == gi && j != winner) {
                    cDrop[j] = true;
                }
            }
            if (winner >= 0 && cArmy[winner] != null && cArmy[winner].key != null) {
                moveWinner.put(Long.valueOf(gi), cArmy[winner].key);
                if (moveWinner.size() > 256) {
                    moveWinner.clear();
                }
            }
        }
        // ---- 通用去重(堆叠组/战斗组;行军组已在上面处理) ----
        for (int i = 0; i < candCount; i++) {
            long gi = cGroupKey[i];
            if (gi < 0 || (gi >>> 56) == 2L) {
                continue;   // 独立候选 / 行军组
            }
            for (int j = 0; j < candCount; j++) {
                if (j == i || cGroupKey[j] != gi) {
                    continue;
                }
                if (compareCand(j, i) < 0) {   // j 更优 → 淘汰 i
                    cDrop[i] = true;
                    break;
                }
            }
        }
        int m = 0;
        for (int i = 0; i < candCount; i++) {
            if (!cDrop[i]) {
                cOrder[m++] = i;
            }
        }
        for (int a = 1; a < m; a++) {          // 插入排序(每帧≤128,开销可忽略)
            int v = cOrder[a];
            int b = a - 1;
            while (b >= 0 && compareCand(v, cOrder[b]) < 0) {
                cOrder[b + 1] = cOrder[b];
                b--;
            }
            cOrder[b + 1] = v;
        }
        int keep = Math.min(m, maxModels);
        // v4.9.8:「近大远小」比例在这里叠加(tiltStrength 管伪3D总适配,nearFar 管远近尺寸差)
        float ks = tiltKS * nearFar;
        float fMin = 9f;
        float fMax = 0f;
        for (int k = 0; k < keep; k++) {
            int i = cOrder[k];
            int b = k * 4;
            // v3.0.1:窗口可单独拉宽/拉高(底边与水平中心保持原兵牌位置不变)
            float fw = modelPxW > 0f ? modelPxW : cSize[i];
            float fh = modelPxH > 0f ? modelPxH : cSize[i];
            float fx = cRectX[i] + cSize[i] * 0.5f - fw * 0.5f;
            float fy = cRectY[i] + cSize[i] - fh;
            if (ks > 0.0005f) {
                // v4.9.12 近大远小:与伪3D地图的<b>局部纵向缩放</b>同源。
                //   地图公式 D = 1 + ks − ks·Y/H(screenY = Y/D),雅可比 scaleY ∝ (1+ks)/D²
                //   → 同一屏幕 Y 处地图内容的放大率 ∝ 1/D²。
                //   以屏幕中线为 1 归一 → 屏幕下方(近)更大、上方(远)更小,平均尺寸不变;
                //   只用兵牌底边的屏幕 Y(y 向下, 0=最上)算一次,每模型 O(1)。
                float u = (fy + fh) / CFG.GAME_HEIGHT;      // 0=屏幕最上, 1=屏幕最下
                if (u < 0f) {
                    u = 0f;
                } else if (u > 1f) {
                    u = 1f;
                }
                float dMid = 1f + ks * 0.5f;
                float d = 1f + ks * (1f - u);
                float f = (dMid * dMid) / (d * d);
                // v4.9.13:倍率上下限改为可配(modelMulMin/Max;默认 0.5~2 = 旧行为)
                if (f < modelMulMin) {
                    f = modelMulMin;
                } else if (f > modelMulMax) {
                    f = modelMulMax;
                }
                if (f < fMin) {
                    fMin = f;
                }
                if (f > fMax) {
                    fMax = f;
                }
                float bottom = fy + fh;                     // 原底边(锚点)
                float nw = fw * f;
                float nh = fh * f;
                // v4.9.13:窗口像素上下限(0=不限;按长边夹取,等比缩放 → 
                // 与 3D 环绕的自动拉远兼容:宽高比不变,包围盒/zoom 计算全部维持)。
                if (modelPxMin > 0f || modelPxMax > 0f) {
                    float adj = pxLimitAdj(nw, nh);
                    if (adj != 1f) {
                        nw *= adj;
                        nh *= adj;
                    }
                }
                fx += fw * 0.5f - nw * 0.5f;
                fy = bottom - nh;                        // 底边固定,向上长
                fw = nw;
                fh = nh;
            }
            rectBuf[b] = fx;
            rectBuf[b + 1] = fy;
            rectBuf[b + 2] = fw;
            rectBuf[b + 3] = fh;
            relBuf[k] = cRel[i];
            facingBuf[k] = cFaceFixed[i] ? cFacing[i] : facingOf(cArmy[i]);
            stateBuf[k] = cState[i];
            kindBuf[k] = cKind[i];
            keyBuf[k] = cArmy[i].key;
            comboBuf[k] = cCombo[i];
            attachBuf[k] = cAttach[i];
            comboShipBuf[k] = cComboShip[i];
            attachShipBuf[k] = cAttachShip[i];
            tintBuf[k] = cTint[i];
            morphBuf[k] = -1f;
            applyMoveMorph(k, cArmy[i], cMoving[i]);
            if (cState[i] == 2) {
                battleCount++;
            }
        }
        // v4.9.13:渲染窗口像素上下限统一再做一遍(近大远小未生效时也要夹;已夹过的 adj=1 无变化)
        if (keep > 0 && (modelPxMin > 0f || modelPxMax > 0f)) {
            for (int k = 0; k < keep; k++) {
                int b = k * 4;
                float w = rectBuf[b + 2];
                float hh = rectBuf[b + 3];
                float adj = pxLimitAdj(w, hh);
                if (adj != 1f) {
                    float nw = w * adj;
                    float nh = hh * adj;
                    rectBuf[b] += w * 0.5f - nw * 0.5f;
                    rectBuf[b + 1] = (rectBuf[b + 1] + hh) - nh;   // 底边固定,向上长
                    rectBuf[b + 2] = nw;
                    rectBuf[b + 3] = nh;
                }
            }
        }
        modelCount = keep;
        // v4.9.12:近大远小诊断(本帧实际用到的尺寸系数区间;ks=0 时全 1)
        nearFarFMin = keep > 0 && fMax > 0f ? fMin : 1f;
        nearFarFMax = keep > 0 && fMax > 0f ? fMax : 1f;
    }

    /**
     * v4.9.13:渲染窗口像素上下限的等比修正系数(1 = 不变)。
     *
     * <p>按窗口**长边**与 `modelPxMin`/`modelPxMax` 比较(0 = 该侧不限),返回统一乘到宽高上的系数,
     * 保持宽高比不变 —— 这样 3D 环绕的「按投影包围自动拉远」(`pw/ph` 决定 aspect、
     * 判定用世界单位)完全不受影响。</p>
     */
    private static float pxLimitAdj(float w, float h) {
        float longest = w > h ? w : h;
        if (longest <= 0.01f) {
            return 1f;
        }
        if (modelPxMax > 0f && longest > modelPxMax) {
            return modelPxMax / longest;
        }
        if (modelPxMin > 0f && longest < modelPxMin) {
            return modelPxMin / longest;
        }
        return 1f;
    }

    /** 候选优劣:<0 = x 更优(选中 > 关系色绿0红1蓝2灰3 > 离视角中心近 > 采集顺序)。 */
    private static int compareCand(int x, int y) {
        if (cSel[x] != cSel[y]) {
            return cSel[y] - cSel[x];
        }
        if (cRel[x] != cRel[y]) {
            return cRel[x] - cRel[y];
        }
        if (cDist[x] != cDist[y]) {
            return cDist[x] < cDist[y] ? -1 : 1;
        }
        return x - y;
    }

    /** v4.9.0:是否处于最高游戏速度(5 档;此时大地图上多兵牌移动,模型开销需要让路)。 */
    private static boolean isMaxGameSpeed() {
        try {
            return Game.gameThread != null && Game.gameThread.playSpeed >= 5;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * v4.9.3:部队是否“真的在走”—— 行军路线进度推进过就视为行军。
     *
     * <p>背景:行军被高防御地块/模组逻辑阻断后,游戏会把 `ArmyDivision.inMovement` 清掉
     * (动画正确变 idle);但恢复移动时有些路径<b>不会</b>把该标记置回 true(兵牌已经重新在走),
     * 于是兵模一直停在 idle 跟随兵牌移动。这里观测与兵牌位置完全同源的
     * `legProgressOf`(done + (current-done)×刻内百分比)是否在推进,并用 400ms 保活窗
     * 避免“游戏刻 vs 渲染帧”不同步造成的闪烁。</p>
     *
     * @param mu 已查到的行军数据(可为 null → 视为不在走)
     */
    private static boolean isMarchingProgress(ArmyDivision a, MoveUnits mu) {
        if (a == null || mu == null) {
            if (a != null && a.key != null) {
                marchSeen.remove(a.key);
            }
            return false;
        }
        try {
            float prog = legProgressOf(mu);
            if (prog < 0f) {
                return a.inMovement;      // 路段宽不可用 → 退回游戏标记
            }
            long now = System.currentTimeMillis();
            float[] rec = marchSeen.get(a.key);
            if (rec == null) {
                if (marchSeen.size() > 512) {
                    marchSeen.clear();
                }
                marchSeen.put(a.key, new float[]{prog, 0f});
                return a.inMovement;      // 首帧先信游戏标记
            }
            if (Math.abs(prog - rec[0]) > 1e-5f) {
                rec[0] = prog;
                rec[1] = (float) now;     // 进度推进 → 记为“本帧在走”
                return true;
            }
            return now - (long) rec[1] < MARCH_HOLD_MS;
        } catch (Throwable ignored) {
            return a.inMovement;
        }
    }

    /** 与原版方框配色一致:0=玩家(绿) 1=敌(红) 2=友(蓝) 3=中立。 */
    private static int relationOf(int civID) {
        try {
            if (civID >= 0 && civID == Game.player.iCivID) {
                return 0;
            }
            if (civID >= 0 && DiplomacyManager.isAtWar(Game.player.iCivID, civID)) {
                return 1;
            }
            if (civID >= 0 && DiplomacyManager.isAlly(Game.player.iCivID, civID)) {
                return 2;
            }
        } catch (Throwable ignored) {
        }
        return 3;
    }

    /* ================= v2.1.0:兵模 combo 选择(parts.json 的 unitSelect / shipSelect) ================= */

    /** 文化分组名(下标 = Civilization.iGroupID;与 game/groups/civsGroups.txt 同序)。 */
    private static final String[] CIV_GROUPS = {
            "European", "Chinese", "Arabian", "Turkish", "Indian", "Japanese", "Papal",
            "German", "African", "Nordic", "Asian", "Native", "Zombie"};

    /** v4.6.0:Religions.json 原始顺序的英文名(ID = 下标;运行时 Name 已本地化,配置键用这里)。 */
    private static String[] religionRawNames = new String[0];

    /** 当前时代 ID(船型按时代选)。 */
    private static int currentAge() {
        try {
            return Game_Calendar.CURRENT_AGEID;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 读取装载配置(资源目录下的 loadSelect.json;旧版 parts.json 兼容);只取 unitSelect / shipSelect。 */
    private static void loadPartsConfig() {
        unitSelectNode = null;
        shipSelectNode = null;
        if (!comboMode) {
            return;
        }
        try {
            // v4.2.0:优先 loadSelect.json;setting.json 里若还写着旧的 parts.json 也能兜底
            File f = new File(ctx.dataDir(), LOAD_SELECT_FILE);
            if (!f.isFile() && partsFileName != null && partsFileName.length() > 0
                    && !LOAD_SELECT_FILE.equalsIgnoreCase(partsFileName)) {
                f = new File(ctx.dataDir(), partsFileName);
            }
            if (!f.isFile()) {
                File legacy = new File(ctx.dataDir(), LEGACY_LOAD_SELECT_FILE);
                if (legacy.isFile()) {
                    f = legacy;
                }
            }
            if (!f.isFile()) {
                File legacy2 = new File(new File(ctx.dataDir(), "_legacy"), LEGACY_LOAD_SELECT_FILE);
                if (legacy2.isFile()) {
                    f = legacy2;
                }
            }
            if (!f.isFile()) {
                log("装载配置缺失:" + f.getAbsolutePath() + "(回退到 setting.json 的 model)");
                return;
            }
            JsonValue root = new JsonReader().parse(readAll(f));
            unitSelectNode = root.get("unitSelect");
            unitGroupNode = root.get("unitgroup");
            shipSelectNode = root.get("shipSelect");
            log("装载配置:" + f.getName() + " unitSelect=" + unitNodeLabel(unitSelectNode)
                    + (unitGroupNode != null ? " + unitgroup" : "")
                    + (shipSelectNode != null ? " + 船型表" : ""));
            // v3.1.0:合并覆盖文件(byId 等手写规则写这里,重建模型库不会丢)
            File ov = new File(ctx.dataDir(), OVERRIDE_FILE);
            if (!ov.isFile()) {
                File legacyOv = new File(ctx.dataDir(), LEGACY_OVERRIDE_FILE);
                if (legacyOv.isFile()) {
                    ov = legacyOv;
                }
            }
            if (ov.isFile()) {
                JsonValue oroot = new JsonReader().parse(readAll(ov));
                JsonValue ou = oroot.get("unitSelect");
                if (ou != null) {
                    unitSelectNode = mergeUnitConfig(unitSelectNode, ou);
                }
                JsonValue og = oroot.get("unitgroup");
                if (og != null) {
                    if (unitGroupNode == null) {
                        unitGroupNode = og;
                    } else {
                        mergeJson(unitGroupNode, og);
                    }
                }
                JsonValue os = oroot.get("shipSelect");
                if (os != null) {
                    if (shipSelectNode == null) {
                        shipSelectNode = os;
                    } else {
                        mergeJson(shipSelectNode, os);
                    }
                }
                log("已合并覆盖文件:" + ov.getName());
            }
        } catch (Throwable t) {
            log("模型库配置读取失败(回退到 army.json 的 model):" + t);
        }
    }

    /** v3.1.0:递归合并 JSON 对象(over 覆盖 base 的同名子键)。 */
    private static void mergeJson(JsonValue base, JsonValue over) {
        if (base == null || over == null || !base.isObject() || !over.isObject()) {
            return;
        }
        for (JsonValue c = over.child; c != null; c = c.next) {
            String name = c.name;
            if (name == null) {
                continue;
            }
            JsonValue ex = base.get(name);
            if (ex != null && ex.isObject() && c.isObject()) {
                mergeJson(ex, c);
            } else {
                base.remove(name);
                base.addChild(name, c);
            }
        }
    }

    /** unitSelect 可能是数组(宗教块)或对象(旧格式);覆盖文件优先匹配。 */
    private static JsonValue mergeUnitConfig(JsonValue base, JsonValue over) {
        if (base == null) {
            return over;
        }
        if (over == null) {
            return base;
        }
        if (base.isArray() && over.isArray()) {
            JsonValue out = new JsonValue(JsonValue.ValueType.array);
            for (JsonValue c = over.child; c != null; c = c.next) {
                out.addChild(c);
            }
            for (JsonValue c = base.child; c != null; c = c.next) {
                out.addChild(c);
            }
            return out;
        }
        if (base.isArray() && over.isObject()) {
            JsonValue out = new JsonValue(JsonValue.ValueType.array);
            out.addChild(over);
            for (JsonValue c = base.child; c != null; c = c.next) {
                out.addChild(c);
            }
            return out;
        }
        if (base.isObject() && over.isArray()) {
            JsonValue out = new JsonValue(JsonValue.ValueType.array);
            for (JsonValue c = over.child; c != null; c = c.next) {
                out.addChild(c);
            }
            out.addChild(base);
            return out;
        }
        mergeJson(base, over);
        return base;
    }

    /** unitSelect/unitgroup 的条目摘要(日志用)。 */
    private static String unitNodeLabel(JsonValue node) {
        if (node == null) {
            return "无";
        }
        if (node.isArray()) {
            return "数组(" + node.size + "块)";
        }
        return "对象(" + countNamedChildren(node) + ")";
    }

    /* ================= v4.9.1(B1):选型结果缓存 ================= */

    /** 一次「陆/海两形态」的模型+挂件签名(缓存值)。 */
    private static final class ResolvedSelect {
        String landCombo;
        String landAttach;
        String shipCombo;
        String shipAttach;
    }

    /**
     * v4.9.1:选型缓存 —— 键 = <b>影响结果的签名</b>(文明/主兵种/等级/宗教/时代/comboMode)。
     *
     * <p>旧实现每帧对每支部队都走一遍 JSON 树(文明变体 → 宗教块 → unitgroup → level…),
     * 同屏多兵牌 + 多模型时是纯 CPU 浪费。选型结果只取决于:文明的 tag(→文化簇)、
     * 主兵团的 uID/aID/等级、文明宗教(宗教块选择)、时代(船型)、comboMode;
     * 这些不变 → 结果必然不变。缓存按签名而不是按部队,所以同类部队天然共享。</p>
     */
    private static final HashMap<String, ResolvedSelect> selectCache = new HashMap<String, ResolvedSelect>();

    /** v4.9.1:按签名缓存解析「陆军 combo/挂件 + 船模 combo/挂件」。 */
    private static ResolvedSelect resolveSelect(ArmyDivision a) {
        String sig = null;
        boolean provisional = true;
        try {
            if (a != null) {
                ArmyRegiment best = mainRegiment(a);
                int uid = best != null ? best.uID : -1;
                int aID = best != null ? best.aID : 0;
                int level = best != null ? unitLevelOf(uid, aID) : 0;
                String tag = civTagOf(a.civID);
                int group = civGroupOf(a.civID);
                String uname = best != null ? unitNameOf(uid, aID) : "";
                sig = a.civID + "|" + uid + "|" + aID + "|" + level + "|"
                        + religionIDOf(a.civID) + "|" + currentAge() + "|" + (comboMode ? 1 : 0)
                        + "|" + tag + "|" + group;
                // v4.9.6:回合中新建的文明,当帧可能还没 tag / iGroupID —— 此时解析出来的
                // 只是"临时兜底"结果,绝不能写进缓存(旧版签名不含 tag/群组 → 兜底被永久缓存,
                // 即使之后文明/宗教/地区对照都就绪也不更新,只能重启/回前台)。
                // v4.9.7:兵种数据(lArmy / 兵团名)未就绪时 byId 匹配会失败 → 同样不能缓存,
                // 否则该兵种永远停在“无枪默认模型”(缩放重新显示也不更新)。
                provisional = tag.length() == 0 || group < 0 || (best != null && uname.length() == 0);
            }
        } catch (Throwable ignored) {
            sig = null;
        }
        if (sig != null && !provisional) {
            ResolvedSelect hit = selectCache.get(sig);
            if (hit != null) {
                return hit;
            }
        }
        ResolvedSelect n = new ResolvedSelect();
        try {
            n.landCombo = resolveCombo(a);
            n.landAttach = resolveAttach(a);
            n.shipCombo = resolveShipComboFor(a, currentAge());
            n.shipAttach = resolveShipAttach(a);
        } catch (Throwable t) {
            n.landCombo = modelFile;
            n.landAttach = "";
            n.shipCombo = shipModelFile;
            n.shipAttach = "";
        }
        if (sig != null && !provisional) {
            if (selectCache.size() > 512) {
                selectCache.clear();
            }
            selectCache.put(sig, n);
        }
        return n;
    }

    /**
     * 部队 → 兵模 combo id:取兵牌里<b>数量(num)最多</b>的兵团作主兵种。
     * 优先级:civilization/<tag>.json > 全局 unitSelect(宗教块)> unitgroup(文化组)> setting.model;
     * 条目内 level 可写在任意层级。解析不到 → 回退配置里的 model。
     */
    private static String resolveCombo(ArmyDivision a) {
        if (!comboMode || a == null) {
            return modelFile;
        }
        try {
            ArmyRegiment best = mainRegiment(a);
            if (best == null) {
                return modelFile;
            }
            int uid = best.uID;
            int level = unitLevelOf(uid, best.aID);
            String group = groupNameOf(a.civID);
            int religionID = religionIDOf(a.civID);
            int religionGroup = religionGroupOf(religionID);
            String uname = unitNameOf(uid, best.aID);
            // v4.3.0:文明变体优先(civilization/<tag>.json → unitSelect),未命中再走全局
            String combo = null;
            String civNote = "";
            JsonValue ov = civOverrideOf(a.civID);
            if (ov != null) {
                // v4.7.0:顶层 unitSelect(数组/对象);旧写法 loadSelect.unitSelect / loadSelect 兼容
                JsonValue civUnit = civUnitSelectOf(ov);
                if (civUnit != null) {
                    combo = resolveComboFromRoot(civUnit, level, religionID, religionGroup, group,
                            uid, best.aID, uname, 0);
                    if (combo != null && combo.length() > 0) {
                        civNote = " [civ:" + ov.getString("civ", "?") + "]";
                    }
                }
            }
            if (combo == null || combo.length() == 0) {
                combo = resolveComboFromRoot(unitSelectNode, level, religionID, religionGroup, group,
                        uid, best.aID, uname, 0);
            }
            if (combo == null || combo.length() == 0) {
                // v4.7.0:宗教块没填/没命中 → unitgroup(文化组)兜底
                combo = resolveUnitGroupCombo(unitGroupNode, level, religionID, religionGroup, group,
                        uid, best.aID, uname, 0);
            }
            if (combo == null || combo.length() == 0) {
                // v4.9.7:未命中任何配置 → 先记一条日志(否则只能看到“模型不对”但不知道
                // 是配置没覆盖到该兵种/等级/宗教/文化,还是加载失败)
                if (logEnabled()) {
                    String usig = "F:" + uid + ":" + best.aID + "|" + group + "|" + religionID + "|" + level;
                    if (!comboLogSeen.containsKey(usig)) {
                        comboLogSeen.put(usig, modelFile);
                        if (comboLogSeen.size() > 256) {
                            comboLogSeen.clear();
                        }
                        log("选型兜底: " + typeNameOf(uid) + "[" + best.aID + "]"
                                + (uname != null && uname.length() > 0 ? "(" + uname + ")" : "")
                                + " 等级=" + level + " 宗教=" + religionID + "(" + religionGroup + ")"
                                + " 文化=" + (group.length() > 0 ? group : "-")
                                + " → 默认模型 " + modelFile + "(配置未命中)");
                    }
                }
                return modelFile;
            }
            if (logEnabled()) {
                String sig = uid + ":" + best.aID + "|" + group + "|" + religionID + "|" + level + "|" + uname;
                if (!comboLogSeen.containsKey(sig)) {
                    comboLogSeen.put(sig, combo);
                    if (comboLogSeen.size() > 256) {
                        comboLogSeen.clear();
                    }
                    String relName = religionNameOf(religionID);
                    log("选型: " + typeNameOf(uid) + "[" + best.aID + "]"
                            + (uname != null && uname.length() > 0 ? "(" + uname + ")" : "")
                            + " 等级=" + level
                            + " 宗教=" + (relName.length() > 0 ? relName : (religionID >= 0 ? String.valueOf(religionID) : "-"))
                            + "(" + (religionGroup >= 0 ? religionGroup : "-") + ")"
                            + " 文化=" + (group.length() > 0 ? group : "-")
                            + " → " + combo + civNote);
                }
            }
            return combo;
        } catch (Throwable t) {
            return modelFile;
        }
    }

    /** 主兵种 = 兵牌里数量最多的兵团(同数量取靠前的)。 */
    private static ArmyRegiment mainRegiment(ArmyDivision a) {
        List<ArmyRegiment> list = a.lArmyRegiment;
        if (list == null || list.isEmpty()) {
            return null;
        }
        ArmyRegiment best = null;
        for (int i = 0, n = list.size(); i < n; i++) {
            ArmyRegiment r = list.get(i);
            if (r == null) {
                continue;
            }
            if (best == null || r.num > best.num) {
                best = r;
            }
        }
        return best;
    }

    /** 兵团等级(UnitLevel;超出范围退回 0)。 */
    private static int unitLevelOf(int uid, int aID) {
        try {
            return ArmyManager.lArmy.get(uid).get(aID).UnitLevel;
        } catch (Throwable t) {
            try {
                return ArmyManager.lArmy.get(uid).get(0).UnitLevel;
            } catch (Throwable t2) {
                return 0;
            }
        }
    }

    /** 兵种类型名(Units.json 里的 File 去掉 .json,如 Warior / Cavalry)。 */
    private static String typeNameOf(int uid) {
        try {
            String f = ArmyManager.lUnitsTypes.get(uid).File;
            if (f != null && f.toLowerCase().endsWith(".json")) {
                f = f.substring(0, f.length() - 5);
            }
            return f == null ? "" : f;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * v4.6.0:读取 game/Religions.json 的原始宗教名(ID = 数组下标)。
     * 运行时 Religion.Name 已被 Game.lang 本地化,配置里建议用这里的英文名或数字 ID;
     * 同时仍兼容运行时本地化名。
     */
    private static void loadReligionNames() {
        religionRawNames = new String[0];
        try {
            FileHandle f = FileManager.loadFile("game/Religions.json");
            if (f == null) {
                log("宗教表缺失( religion 选择器仅支持数字 ID )");
                return;
            }
            JsonValue root = new JsonReader().parse(f.readString());
            JsonValue data = root == null ? null : root.get("Data");
            if (data == null || !data.isArray()) {
                log("宗教表格式异常( religion 选择器仅支持数字 ID )");
                return;
            }
            java.util.ArrayList<String> names = new java.util.ArrayList<String>();
            for (JsonValue it = data.child; it != null; it = it.next) {
                names.add(it.getString("Name", ""));
            }
            religionRawNames = names.toArray(new String[names.size()]);
            log("宗教表:" + religionRawNames.length + " 个(ID 0~" + (religionRawNames.length - 1) + ")");
        } catch (Throwable t) {
            log("宗教表读取失败( religion 选择器仅支持数字 ID ):" + t);
        }
    }

    /** 文明的文化分组名(iGroupID → civsGroups 名称)。 */
    private static String groupNameOf(int civID) {
        try {
            Civilization c = Game.getCiv(civID);
            int g = c == null ? -1 : c.iGroupID;
            return (g >= 0 && g < CIV_GROUPS.length) ? CIV_GROUPS[g] : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** v4.9.6:文明 tag(选型缓存签名用;独立 try 避免一处异常拖垮整个签名)。 */
    private static String civTagOf(int civID) {
        try {
            Civilization c = Game.getCiv(civID);
            String tag = c == null ? null : c.getCivTag();
            return tag == null ? "" : tag;
        } catch (Throwable t) {
            return "";
        }
    }

    /** v4.9.6:文明文化组下标(选型缓存签名用;失败 = -1)。 */
    private static int civGroupOf(int civID) {
        try {
            Civilization c = Game.getCiv(civID);
            return c == null ? -1 : c.iGroupID;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 文明当前宗教 ID(Religions.json 下标;失败 = -1)。 */
    private static int religionIDOf(int civID) {
        try {
            return Game.getCiv(civID).getReligionID();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 宗教大组 ID(ReligionGroupID;失败 = -1)。 */
    private static int religionGroupOf(int religionID) {
        if (religionID < 0) {
            return -1;
        }
        try {
            ReligionManager rm = Game.religionManager;
            if (rm != null && religionID < rm.getReligionsSize()) {
                ReligionManager.Religion r = rm.getReligion(religionID);
                return r == null ? -1 : r.ReligionGroupID;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /** 宗教原始英文名(用于日志/配置键)。 */
    private static String religionNameOf(int religionID) {
        if (religionID >= 0 && religionID < religionRawNames.length) {
            return religionRawNames[religionID];
        }
        return "";
    }

    /** 在 religion 表里按「数字 ID → 原始英文名 → 本地化名」依次查找。 */
    private static JsonValue religionValueOf(JsonValue map, int religionID) {
        if (map == null || !map.isObject() || religionID < 0) {
            return null;
        }
        JsonValue v = map.get(String.valueOf(religionID));
        if (v != null) {
            return v;
        }
        String raw = religionNameOf(religionID);
        if (raw.length() > 0) {
            v = map.get(raw);
            if (v == null) {
                v = findChildIgnoreCase(map, raw);
            }
            if (v != null) {
                return v;
            }
        }
        try {
            ReligionManager rm = Game.religionManager;
            if (rm != null && religionID < rm.getReligionsSize()) {
                ReligionManager.Religion r = rm.getReligion(religionID);
                String loc = r == null ? null : r.Name;
                if (loc != null && loc.length() > 0) {
                    v = map.get(loc);
                    if (v == null) {
                        v = findChildIgnoreCase(map, loc);
                    }
                    if (v != null) {
                        return v;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** JSON 对象子键大小写不敏感查找。 */
    private static JsonValue findChildIgnoreCase(JsonValue obj, String name) {
        if (obj == null || !obj.isObject() || name == null || name.length() == 0) {
            return null;
        }
        for (JsonValue c = obj.child; c != null; c = c.next) {
            if (c.name != null && c.name.equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /** v4.3.0:在指定 unitSelect 根(全局或文明覆盖)里取类型条目。 */
    private static JsonValue typeEntry(JsonValue root, int uid) {
        if (root == null || !root.isObject()) {
            return null;
        }
        String name = typeNameOf(uid);
        if (name != null && name.length() > 0) {
            JsonValue e = root.get(uid + ":" + name);
            if (e != null) {
                return e;
            }
            e = root.get(name);
            if (e != null) {
                return e;
            }
        } else {
            // Units.json 还没就绪时,按 "<uid>:" 前缀找第一个键
            String prefix = uid + ":";
            for (JsonValue c = root.child; c != null; c = c.next) {
                if (c.name != null && c.name.startsWith(prefix)) {
                    return c;
                }
            }
        }
        return root.get(String.valueOf(uid));
    }

    /** 兵团名称(游戏已本地化,如“精锐长矛兵”;拿不到返回空串)。 */
    private static String unitNameOf(int uid, int aID) {
        try {
            String n = ArmyManager.lArmy.get(uid).get(aID).Name;
            return n == null ? "" : n;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * v4.6.0:统一选择器(模型/挂件通用)。
     * 外层先查 civilization 文件,所以整体优先级:
     * civilization 文件 > religion > religionGroup > group > level > default。
     * 任意层级的值都可以再写 level(等级段)对象 → level 可在任何兵种模型配置里使用。
     * byId(具体兵团)仍是条目内最具体的覆盖。
     */
    private static JsonValue selectNode(JsonValue e, int level, int religionID, int religionGroup,
                                        String group, int aId, String unitName, int depth) {
        if (e == null || depth > 8) {
            return null;
        }
        if (!e.isObject()) {
            return e;   // 终端值:模型字符串 / 挂件数组
        }
        // ① byId:具体兵团(aID 或兵团名)
        JsonValue byId = e.get("byId");
        if (byId != null) {
            JsonValue v = byId.get(String.valueOf(aId));
            if (v == null && unitName != null && unitName.length() > 0) {
                v = byId.get(unitName);
                if (v == null) {
                    v = findChildIgnoreCase(byId, unitName);
                }
            }
            if (v != null) {
                JsonValue r = selectNode(v, level, religionID, religionGroup, group, aId, unitName, depth + 1);
                if (r != null) {
                    return r;
                }
            }
        }
        // ② religion:具体宗教(数字 ID / 原始英文名 / 本地化名)
        JsonValue rel = e.get("religion");
        if (rel != null) {
            JsonValue v = religionValueOf(rel, religionID);
            if (v != null) {
                JsonValue r = selectNode(v, level, religionID, religionGroup, group, aId, unitName, depth + 1);
                if (r != null) {
                    return r;
                }
            }
        }
        // ③ religionGroup:宗教大组(ReligionGroupID)
        JsonValue rg = e.get("religionGroup");
        if (rg != null && religionGroup >= 0) {
            JsonValue v = rg.get(String.valueOf(religionGroup));
            if (v != null) {
                JsonValue r = selectNode(v, level, religionID, religionGroup, group, aId, unitName, depth + 1);
                if (r != null) {
                    return r;
                }
            }
        }
        // ④ group:文化簇(civsGroups.txt / Civilization.iGroupID)
        JsonValue gmap = e.get("group");
        if (gmap != null && group != null && group.length() > 0) {
            JsonValue v = gmap.get(group);
            if (v == null) {
                v = findChildIgnoreCase(gmap, group);
            }
            if (v != null) {
                JsonValue r = selectNode(v, level, religionID, religionGroup, group, aId, unitName, depth + 1);
                if (r != null) {
                    return r;
                }
            }
        }
        // ⑤ level:等级段(可出现在任意层级)
        JsonValue lm = e.get("level");
        if (lm != null) {
            for (JsonValue it = lm.child; it != null; it = it.next) {
                if (levelInRange(it.name, level)) {
                    JsonValue r = selectNode(it, level, religionID, religionGroup, group, aId, unitName, depth + 1);
                    if (r != null) {
                        return r;
                    }
                }
            }
        }
        // ⑥ default
        JsonValue d = e.get("default");
        if (d != null) {
            return selectNode(d, level, religionID, religionGroup, group, aId, unitName, depth + 1);
        }
        return null;
    }

    /**
     * v4.7.0:在 unitSelect 节点(数组=宗教块 / 对象=旧格式)里找兵种条目并解析。
     * 数组块可用 religion / religionGroup / group 作为块条件;无条件的块(文明文件)直接匹配。
     */
    private static JsonValue resolveUnitNode(JsonValue node, int level, int religionID, int religionGroup,
                                             String group, int uid, int aId, String unitName, int depth) {
        if (node == null || depth > 8) {
            return null;
        }
        if (node.isArray()) {
            for (JsonValue block = node.child; block != null; block = block.next) {
                if (block == null || !block.isObject() || !blockMatches(block, religionID, religionGroup, group)) {
                    continue;
                }
                JsonValue entry = typeEntry(block, uid);
                if (entry == null) {
                    continue;
                }
                JsonValue r = selectNode(entry, level, religionID, religionGroup, group, aId, unitName, depth + 1);
                if (r != null) {
                    return r;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        JsonValue entry = typeEntry(node, uid);
        if (entry == null) {
            return null;
        }
        return selectNode(entry, level, religionID, religionGroup, group, aId, unitName, depth + 1);
    }

    /** 数组块条件:religion / religionGroup / group 全部命中才匹配(未写 = 通配)。 */
    private static boolean blockMatches(JsonValue block, int religionID, int religionGroup, String group) {
        JsonValue rel = block.get("religion");
        if (rel != null && !religionMatches(rel, religionID)) {
            return false;
        }
        JsonValue rg = block.get("religionGroup");
        if (rg != null && !religionGroupMatches(rg, religionGroup)) {
            return false;
        }
        JsonValue gp = block.get("group");
        if (gp != null && !groupMatches(gp, group)) {
            return false;
        }
        return true;
    }

    /** religion 条件:字符串(名称/ID)、数组、或 {键:值} 表(兼容旧写法)。 */
    private static boolean religionMatches(JsonValue v, int religionID) {
        if (v == null) {
            return true;
        }
        if (v.isString()) {
            return religionNameMatches(v.asString(), religionID);
        }
        if (v.isNumber()) {
            return religionID >= 0 && v.asInt() == religionID;
        }
        if (v.isArray()) {
            for (JsonValue c = v.child; c != null; c = c.next) {
                if (religionMatches(c, religionID)) {
                    return true;
                }
            }
            return false;
        }
        if (v.isObject()) {
            return religionValueOf(v, religionID) != null;
        }
        return false;
    }

    /** 宗教名称/ID 匹配(原始英文名、本地化名、数字 ID)。 */
    private static boolean religionNameMatches(String name, int religionID) {
        if (name == null || religionID < 0) {
            return false;
        }
        String n = name.trim();
        if (n.length() == 0) {
            return false;
        }
        if (n.equals(String.valueOf(religionID))) {
            return true;
        }
        String raw = religionNameOf(religionID);
        if (raw.length() > 0 && raw.equalsIgnoreCase(n)) {
            return true;
        }
        try {
            ReligionManager rm = Game.religionManager;
            if (rm != null && religionID < rm.getReligionsSize()) {
                ReligionManager.Religion r = rm.getReligion(religionID);
                if (r != null && r.Name != null && r.Name.equalsIgnoreCase(n)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** religionGroup 条件:数字/数字字符串/数组/表键。 */
    private static boolean religionGroupMatches(JsonValue v, int religionGroup) {
        if (v == null) {
            return true;
        }
        if (v.isNumber()) {
            return religionGroup >= 0 && v.asInt() == religionGroup;
        }
        if (v.isString()) {
            try {
                return religionGroup >= 0 && Integer.parseInt(v.asString().trim()) == religionGroup;
            } catch (Throwable t) {
                return false;
            }
        }
        if (v.isArray()) {
            for (JsonValue c = v.child; c != null; c = c.next) {
                if (religionGroupMatches(c, religionGroup)) {
                    return true;
                }
            }
            return false;
        }
        if (v.isObject()) {
            return religionGroup >= 0 && v.get(String.valueOf(religionGroup)) != null;
        }
        return false;
    }

    /** group 条件:字符串/数组/表键。 */
    private static boolean groupMatches(JsonValue v, String group) {
        if (v == null) {
            return true;
        }
        if (group == null || group.length() == 0) {
            return false;
        }
        if (v.isString()) {
            return v.asString().equalsIgnoreCase(group);
        }
        if (v.isArray()) {
            for (JsonValue c = v.child; c != null; c = c.next) {
                if (groupMatches(c, group)) {
                    return true;
                }
            }
            return false;
        }
        if (v.isObject()) {
            return v.get(group) != null || findChildIgnoreCase(v, group) != null;
        }
        return false;
    }

    /** 模型解析入口(处理 @fallback_foot / @fallback_none)。 */
    private static String resolveComboFromRoot(JsonValue root, int level, int religionID, int religionGroup,
                                               String group, int uid, int aId, String unitName, int depth) {
        JsonValue v = resolveUnitNode(root, level, religionID, religionGroup, group, uid, aId, unitName, depth);
        if (v == null || !v.isString()) {
            return null;
        }
        String combo = v.asString();
        if (combo == null) {
            return null;
        }
        if (combo.startsWith("@fallback_foot")) {
            // 用同一根节点解析步兵(0:Warior),保持宗教/文化上下文
            return resolveComboFromRoot(root, level, religionID, religionGroup, group, 0, aId, unitName, depth + 1);
        }
        if (combo.startsWith("@")) {
            return null;   // @fallback_none 等 = 没有对应模型
        }
        return combo;
    }

    /** unitgroup 兜底:先查文化组专属表,再查旧式“单位类型直写”表。 */
    private static String resolveUnitGroupCombo(JsonValue ug, int level, int religionID, int religionGroup,
                                                String group, int uid, int aId, String unitName, int depth) {
        if (ug == null || !ug.isObject() || depth > 8) {
            return null;
        }
        if (group != null && group.length() > 0) {
            JsonValue g = ug.get(group);
            if (g == null) {
                g = findChildIgnoreCase(ug, group);
            }
            if (g != null && g.isObject()) {
                String c = resolveComboFromRoot(g, level, religionID, religionGroup, group, uid, aId, unitName, depth + 1);
                if (c != null && c.length() > 0) {
                    return c;
                }
            }
        }
        return resolveComboFromRoot(ug, level, religionID, religionGroup, group, uid, aId, unitName, depth + 1);
    }

    /** unitgroup 兜底(挂件数组版,逻辑同 resolveUnitGroupCombo)。 */
    private static JsonValue resolveUnitGroupValue(JsonValue ug, int level, int religionID, int religionGroup,
                                                   String group, int uid, int aId, String unitName, int depth) {
        if (ug == null || !ug.isObject() || depth > 8) {
            return null;
        }
        if (group != null && group.length() > 0) {
            JsonValue g = ug.get(group);
            if (g == null) {
                g = findChildIgnoreCase(ug, group);
            }
            if (g != null && g.isObject()) {
                JsonValue v = resolveUnitNode(g, level, religionID, religionGroup, group, uid, aId, unitName, depth + 1);
                if (v != null) {
                    return v;
                }
            }
        }
        return resolveUnitNode(ug, level, religionID, religionGroup, group, uid, aId, unitName, depth + 1);
    }

    /** 等级段匹配:"0-2" 区间 / "7+" 及以上 / "3" 精确。 */
    private static boolean levelInRange(String key, int level) {
        if (key == null) {
            return false;
        }
        String k = key.trim();
        try {
            if (k.endsWith("+")) {
                return level >= Integer.parseInt(k.substring(0, k.length() - 1).trim());
            }
            int dash = k.indexOf('-');
            if (dash > 0) {
                int lo = Integer.parseInt(k.substring(0, dash).trim());
                int hi = Integer.parseInt(k.substring(dash + 1).trim());
                return level >= lo && level <= hi;
            }
            return level == Integer.parseInt(k);
        } catch (Throwable t) {
            return false;
        }
    }

    /* ================= v4.0.0:运行时挂件(武器/配饰) ================= */

    /**
     * 读取 models/attachments.json:
     * { strip:{combos,tokens}, unitSelect:{ "<uid>:<Type>": {byId/group/level/default:[...] } },
     *   shipSelect:{ byId/group/level/default:[...] } 或 { "<uid>:<Type>": {...} } }
     * 旧字段 rules 仍按 unitSelect 读取。
     */
    private static void loadAttachConfig() {
        attachUnitSelectNode = null;
        attachUnitGroupNode = null;
        attachShipSelectNode = null;
        stripCombosArr = null;
        stripTokensArr = null;
        try {
            File f = new File(new File(ctx.dataDir(), MODELS_DIR), ATTACH_SUB);
            if (!f.isFile()) {
                File legacy = new File(ctx.dataDir(), attachFileName);
                if (legacy.isFile()) {
                    f = legacy;
                }
            }
            if (!f.isFile()) {
                File legacy2 = new File(new File(ctx.dataDir(), "_legacy"), attachFileName);
                if (legacy2.isFile()) {
                    f = legacy2;
                }
            }
            if (!f.isFile()) {
                log("挂件配置缺失:" + f.getName() + "(不挂件)");
                G3DArmyMin.confStripCombos = null;
                G3DArmyMin.confStripTokens = null;
                return;
            }
            JsonValue root = new JsonReader().parse(readAll(f));
            JsonValue unit = root.get("unitSelect");
            if (unit == null) {
                unit = root.get("rules");   // v4.5.0:旧字段兼容
            }
            attachUnitSelectNode = unit;
            attachUnitGroupNode = root.get("unitgroup");
            attachShipSelectNode = root.get("shipSelect");
            JsonValue strip = root.get("strip");
            if (strip != null) {
                stripCombosArr = jsonStringArray(strip.get("combos"));
                stripTokensArr = jsonStringArray(strip.get("tokens"));
            }
            int ns = countNamedChildren(attachShipSelectNode);
            G3DArmyMin.confPartsDir = "models/parts";
            G3DArmyMin.confStripCombos = stripCombosArr;
            G3DArmyMin.confStripTokens = stripTokensArr;
            log("挂件配置:" + f.getName() + " unitSelect=" + unitNodeLabel(attachUnitSelectNode)
                    + (attachUnitGroupNode != null ? " + unitgroup" : "")
                    + (attachShipSelectNode != null ? " + 船型规则=" + ns : "")
                    + (stripTokensArr != null ? " 剥离(" + joinArr(stripCombosArr) + "):" + joinArr(stripTokensArr) : ""));
        } catch (Throwable t) {
            log("挂件配置读取失败(不挂件):" + t);
            attachUnitSelectNode = null;
            attachUnitGroupNode = null;
            attachShipSelectNode = null;
            G3DArmyMin.confStripCombos = null;
            G3DArmyMin.confStripTokens = null;
        }
    }

    /** JSON 对象里非 "_" 开头的子键数量。 */
    private static int countNamedChildren(JsonValue node) {
        if (node == null || !node.isObject()) {
            return 0;
        }
        int n = 0;
        for (JsonValue v = node.child; v != null; v = v.next) {
            if (v.name != null && !v.name.startsWith("_")) {
                n++;
            }
        }
        return n;
    }

    /* ================= v4.3.0:assembly / civilization / anim_events ================= */

    /** 读 models/assembly.json:combo → animSet(可能多个)与 group(artillery/ship 兜底)。 */
    private static void loadAssemblyConfig() {
        comboAnimSet.clear();
        comboGroup.clear();
        comboRig.clear();
        animDonorMap.clear();
        try {
            File f = new File(new File(ctx.dataDir(), MODELS_DIR), ASSEMBLY_SUB);
            if (!f.isFile()) {
                log("装配配置缺失:" + f.getAbsolutePath());
                G3DArmyMin.setComboAnimSets(null, null);
                G3DArmyMin.setComboRigs(null);
                G3DArmyMin.setAnimDonors(null);
                return;
            }
            JsonValue root = new JsonReader().parse(readAll(f));
            JsonValue combos = root.get("combos");
            if (combos != null) {
                for (JsonValue c = combos.child; c != null; c = c.next) {
                    String id = c.name;
                    if (id == null || id.startsWith("_")) {
                        continue;
                    }
                    String group = c.getString("group", null);
                    if (group != null && group.length() > 0) {
                        comboGroup.put(id, group);
                    }
                    String rig = c.getString("rig", null);
                    if (rig == null) {
                        JsonValue rigs = c.get("rigs");
                        if (rigs != null && rigs.child != null) {
                            rig = rigs.child.asString();
                        }
                    }
                    if (rig != null && rig.length() > 0) {
                        comboRig.put(id, rig);
                    }
                    JsonValue aset = c.get("animSet");
                    String joined = null;
                    if (aset != null) {
                        if (aset.isString()) {
                            joined = aset.asString();
                        } else if (aset.isArray()) {
                            StringBuilder sb = new StringBuilder();
                            for (JsonValue v = aset.child; v != null; v = v.next) {
                                if (v.isString() && v.asString() != null && v.asString().length() > 0) {
                                    if (sb.length() > 0) {
                                        sb.append(',');
                                    }
                                    sb.append(v.asString());
                                }
                            }
                            joined = sb.length() > 0 ? sb.toString() : null;
                        }
                    }
                    if (joined != null) {
                        comboAnimSet.put(id, joined);
                    }
                }
            }
            G3DArmyMin.setComboAnimSets(comboAnimSet, comboGroup);
            // v4.4.0:武器 → 动画 donor(同 rig 借动画)
            JsonValue donors = root.get("animDonors");
            if (donors != null) {
                for (JsonValue rigN = donors.child; rigN != null; rigN = rigN.next) {
                    if (rigN.name == null || rigN.name.startsWith("_") || !rigN.isObject()) {
                        continue;
                    }
                    for (JsonValue fam = rigN.child; fam != null; fam = fam.next) {
                        if (fam.name == null || fam.name.startsWith("_") || !fam.isString()) {
                            continue;
                        }
                        animDonorMap.put(rigN.name + "." + fam.name, fam.asString());
                    }
                }
            }
            G3DArmyMin.setComboRigs(comboRig);
            G3DArmyMin.setAnimDonors(animDonorMap);
            log("装配配置:" + f.getName() + " combo=" + comboGroup.size() + " animSet=" + comboAnimSet.size()
                    + " rig=" + comboRig.size() + " 动画借位=" + animDonorMap.size());
        } catch (Throwable t) {
            log("装配配置读取失败:" + t);
            G3DArmyMin.setComboAnimSets(null, null);
            G3DArmyMin.setComboRigs(null);
            G3DArmyMin.setAnimDonors(null);
        }
    }

    /**
     * v4.7.0:文明文件的 unitSelect(顶层优先;旧结构 loadSelect.unitSelect / 扁平 loadSelect 兼容)。
     */
    private static JsonValue civUnitSelectOf(JsonValue ov) {
        if (ov == null) {
            return null;
        }
        JsonValue u = ov.get("unitSelect");
        if (u != null) {
            return u;
        }
        return civSection(ov.get("loadSelect"), "unitSelect", true);
    }

    /** v4.7.0:文明文件的 shipSelect(顶层优先;旧结构 loadSelect.shipSelect 兼容)。 */
    private static JsonValue civShipSelectOf(JsonValue ov) {
        if (ov == null) {
            return null;
        }
        JsonValue s = ov.get("shipSelect");
        if (s != null) {
            return s;
        }
        return civSection(ov.get("loadSelect"), "shipSelect", false);
    }

    /**
     * v4.5.0:从 civilization 的 loadSelect / attachments 段取 unitSelect / shipSelect。
     * 新结构 = 段内分 unitSelect / shipSelect;旧结构 = 整段直接就是 unitSelect 规则表
     * (legacyFlatAsUnit=true 时才这样兜底)。
     */
    private static JsonValue civSection(JsonValue node, String name, boolean legacyFlatAsUnit) {
        if (node == null) {
            return null;
        }
        JsonValue v = node.get(name);
        if (v != null) {
            return v;
        }
        if (!legacyFlatAsUnit) {
            return null;
        }
        if (node.get("unitSelect") != null || node.get("shipSelect") != null) {
            return null;    // 已是新结构,只是没写这一项
        }
        return node;        // 旧写法:整段就是 unitSelect
    }

    /** 文明变体:按文明 tag 取覆盖节点(去掉旧的 group 职能)。 */
    private static void loadCivilizationConfig() {
        civOverrides.clear();
        try {
            File dir = new File(ctx.dataDir(), CIV_DIR);
            if (!dir.isDirectory()) {
                return;
            }
            File[] fs = dir.listFiles();
            if (fs == null) {
                return;
            }
            for (int i = 0; i < fs.length; i++) {
                File f = fs[i];
                if (!f.isFile() || !f.getName().toLowerCase().endsWith(".json")) {
                    continue;
                }
                String base = f.getName().substring(0, f.getName().lastIndexOf('.'));
                JsonValue root = new JsonReader().parse(readAll(f));
                if (root == null) {
                    continue;
                }
                if (root.getBoolean("enabled", true) == false) {
                    continue;
                }
                String civ = root.getString("civ", base);
                if (civ != null && civ.length() > 0) {
                    civOverrides.put(civ.toLowerCase(), root);
                }
            }
            log("文明变体:" + civOverrides.size() + " 个 tag");
        } catch (Throwable t) {
            log("文明变体读取失败:" + t);
        }
    }

    /** 读 anim_events.json(武器事件表/预设/状态机),转成 Java 结构交给 G3DArmyMin。 */
    private static void loadAnimEvents() {
        animEventsNode = null;
        particlesEnabled = true;
        try {
            File f = new File(ctx.dataDir(), ANIM_EVENTS_FILE);
            if (!f.isFile()) {
                log("动画事件配置缺失:" + f.getAbsolutePath() + "(粒子关闭)");
                particlesEnabled = false;
                G3DArmyMin.setAnimEvents(null, false, 1f, 1f, 256, "weapon_tip");
                return;
            }
            JsonValue root = new JsonReader().parse(readAll(f));
            if (root == null) {
                particlesEnabled = false;
                return;
            }
            animEventsNode = root;
            particlesEnabled = root.getBoolean("enabled", true);
            float scale = root.getFloat("particleScale", 1f);
            float rate = root.getFloat("particleRate", 1f);
            particleMax = Math.max(16, Math.min(1024, root.getInt("particleMax", 256)));
            String locator = root.getString("locatorFallback", "weapon_tip");
            G3DArmyMin.setAnimEvents(root, particlesEnabled, scale, rate, particleMax, locator);
            JsonValue w = root.get("weapons");
            JsonValue pr = root.get("presets");
            log("动画事件:" + f.getName() + " 武器=" + (w == null ? 0 : w.size)
                    + " 预设=" + (pr == null ? 0 : pr.size)
                    + " 状态机=" + (root.get("stateMachine") != null)
                    + " 粒子=" + (particlesEnabled ? "开" : "关"));
        } catch (Throwable t) {
            particlesEnabled = false;
            log("动画事件读取失败(粒子关闭):" + t);
            G3DArmyMin.setAnimEvents(null, false, 1f, 1f, 256, "weapon_tip");
        }
    }

    /** v4.3.0:按主兵种所属文明取变体覆盖节点(只用具体 civ tag;v4.5.0 去掉文化簇兜底)。 */
    private static JsonValue civOverrideOf(int civID) {
        if (civOverrides.isEmpty()) {
            return null;
        }
        try {
            Civilization c = Game.getCiv(civID);
            if (c == null) {
                return null;
            }
            String tag = null;
            try {
                tag = c.getCivTag();
            } catch (Throwable ignored) {
            }
            if (tag != null && tag.length() > 0) {
                JsonValue v = civOverrides.get(tag.toLowerCase());
                if (v != null) {
                    return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** v4.3.0:启动校验 models/main/index.json 与 models/parts/index.json(仅日志)。 */
    private static void validateResourceIndexes() {
        try {
            int[] a = checkModelIndex(new File(new File(new File(ctx.dataDir(), MODELS_DIR), "main"), "index.json"));
            int[] b = checkPartIndex(new File(new File(new File(ctx.dataDir(), MODELS_DIR), "parts"), "index.json"));
            resValidateNote = "资源校验: main=" + a[0] + "(缺失 " + a[1] + ") parts=" + b[0] + "(缺失 " + b[1] + ")";
            log(resValidateNote);
        } catch (Throwable t) {
            resValidateNote = "资源校验失败: " + t;
        }
    }

    private static int[] checkModelIndex(File idx) {
        int[] out = new int[2];
        try {
            if (!idx.isFile()) {
                out[0] = -1;
                return out;
            }
            JsonValue root = new JsonReader().parse(readAll(idx));
            JsonValue models = root.get("models");
            if (models == null) {
                return out;
            }
            File base = idx.getParentFile();
            File g3d = new File(base, "g3d");
            File png = new File(base, "png");
            for (JsonValue m = models.child; m != null; m = m.next) {
                out[0]++;
                String file = m.getString("file", null);
                if (file != null && !new File(g3d, file).isFile()) {
                    out[1]++;
                }
                JsonValue tex = m.get("textures");
                if (tex != null) {
                    for (JsonValue t = tex.child; t != null; t = t.next) {
                        String n = t.asString();
                        if (n != null && !new File(png, n).isFile() && !new File(g3d, n).isFile()) {
                            out[1]++;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static int[] checkPartIndex(File idx) {
        int[] out = new int[2];
        try {
            if (!idx.isFile()) {
                out[0] = -1;
                return out;
            }
            JsonValue root = new JsonReader().parse(readAll(idx));
            JsonValue parts = root.get("parts");
            if (parts == null) {
                return out;
            }
            File base = idx.getParentFile();
            File g3d = new File(base, "g3d");
            File png = new File(base, "png");
            for (JsonValue p = parts.child; p != null; p = p.next) {
                out[0]++;
                String file = p.getString("file", null);
                if (file != null && !new File(g3d, file).isFile()) {
                    out[1]++;
                }
            }
            if (out[1] > 0) {
                log("挂件缺失文件数: " + out[1] + "(共 " + out[0] + ")");
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** JSON 字符串数组 → String[];非数组返回 null。 */
    private static String[] jsonStringArray(JsonValue arr) {
        if (arr == null || !arr.isArray()) {
            return null;
        }
        List<String> out = new java.util.ArrayList<String>();
        for (JsonValue v = arr.child; v != null; v = v.next) {
            if (v.isString()) {
                String s = v.asString();
                if (s != null && s.length() > 0) {
                    out.add(s);
                }
            }
        }
        return out.isEmpty() ? null : out.toArray(new String[out.size()]);
    }

    private static String joinArr(String[] a) {
        if (a == null || a.length == 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(a[i]);
        }
        return sb.toString();
    }

    /** 逗号分隔字符串 → String[](去空白/空项)。 */
    private static String[] splitCsv(String s) {
        if (s == null || s.trim().length() == 0) {
            return null;
        }
        String[] raw = s.split(",");
        List<String> out = new java.util.ArrayList<String>();
        for (int i = 0; i < raw.length; i++) {
            String t = raw[i].trim();
            if (t.length() > 0) {
                out.add(t);
            }
        }
        return out.isEmpty() ? null : out.toArray(new String[out.size()]);
    }

    /** 与原版方框一致的 4 种关系色(0 玩家/1 敌/2 友/3 中立),relation 模式用。 */
    private static final float[][] REL_RGB = {
            {0.33f, 0.58f, 0.30f},
            {0.62f, 0.26f, 0.22f},
            {0.32f, 0.46f, 0.66f},
            {0.62f, 0.58f, 0.50f}};

    /**
     * v4.1.0:部队染色颜色(0xRRGGBB;−1 = 不染)。
     * civ 模式取文明色(Civilization.civColor),按 tintLift 把过暗的颜色提亮(避免近黑色兵模)。
     */
    private static int tintOf(ArmyDivision a) {
        if (a == null || tintMode == null || "none".equalsIgnoreCase(tintMode)) {
            return -1;
        }
        float r;
        float g;
        float b;
        if ("relation".equalsIgnoreCase(tintMode)) {
            float[] c = REL_RGB[Math.max(0, Math.min(3, relationOf(a.civID)))];
            r = c[0];
            g = c[1];
            b = c[2];
        } else {
            try {
                Civilization civ = Game.getCiv(a.civID);
                Color cc = civ == null ? null : civ.civColor;
                if (cc == null) {
                    return -1;
                }
                r = cc.r;
                g = cc.g;
                b = cc.b;
            } catch (Throwable t) {
                return -1;
            }
        }
        float lum = 0.299f * r + 0.587f * g + 0.114f * b;
        if (lum < 1e-4f) {
            r = tintLift;
            g = tintLift;
            b = tintLift;
        } else if (lum < tintLift) {
            float k = tintLift / lum;
            r *= k;
            g *= k;
            b *= k;
        }
        r = Math.min(1f, Math.max(0f, r));
        g = Math.min(1f, Math.max(0f, g));
        b = Math.min(1f, Math.max(0f, b));
        return (((int) (r * 255f)) << 16) | (((int) (g * 255f)) << 8) | ((int) (b * 255f));
    }

    /** 部队 → 挂件签名(`件名@骨;…`;空串 = 不挂)。 */
    private static String resolveAttach(ArmyDivision a) {
        return resolveAttachImpl(a, false);
    }

    /** v4.5.0:海面部队 → 船模挂件签名(attachments.json → shipSelect)。 */
    private static String resolveShipAttach(ArmyDivision a) {
        return resolveAttachImpl(a, true);
    }

    /**
     * 挂件签名解析(陆军 = unitSelect / old rules;船 = shipSelect)。
     * 文件层:civilization 文件 > 全局;层内:religion 块 > unitgroup(文化组) > 空。
     * unitSelect 支持数组(宗教块)与旧对象格式;值必须是数组。
     */
    private static String resolveAttachImpl(ArmyDivision a, boolean ship) {
        JsonValue global = ship ? attachShipSelectNode : attachUnitSelectNode;
        JsonValue globalGroup = ship ? null : attachUnitGroupNode;
        if (global == null && globalGroup == null && civOverrides.isEmpty()) {
            return "";
        }
        if (a == null) {
            return "";
        }
        try {
            ArmyRegiment best = mainRegiment(a);
            if (best == null) {
                return "";
            }
            int uid = best.uID;
            int religionID = religionIDOf(a.civID);
            int religionGroup = religionGroupOf(religionID);
            int lv = unitLevelOf(uid, best.aID);
            String grp = groupNameOf(a.civID);
            String uname = unitNameOf(uid, best.aID);

            JsonValue list = null;
            // ① 文明文件
            JsonValue ov = civOverrideOf(a.civID);
            if (ov != null) {
                JsonValue at = ov.get("attachments");
                if (at != null) {
                    JsonValue civRules;
                    if (ship) {
                        civRules = at.get("shipSelect");
                    } else {
                        civRules = at.isArray() ? at : civSection(at, "unitSelect", true);
                    }
                    list = resolveAttachValue(civRules, ship, lv, religionID, religionGroup, grp, uid, best.aID, uname, 0);
                    if (list == null && !ship) {
                        JsonValue atGroup = at.get("unitgroup");
                        if (atGroup != null) {
                            list = resolveUnitGroupValue(atGroup, lv, religionID, religionGroup, grp, uid, best.aID, uname, 0);
                        }
                    }
                }
            }
            // ② 全局 unitSelect/shipSelect
            if (list == null) {
                list = resolveAttachValue(global, ship, lv, religionID, religionGroup, grp, uid, best.aID, uname, 0);
            }
            // ③ 全局 unitgroup 兜底(仅陆军)
            if (list == null && !ship && globalGroup != null) {
                list = resolveUnitGroupValue(globalGroup, lv, religionID, religionGroup, grp, uid, best.aID, uname, 0);
            }
            return (list != null && list.isArray()) ? attachSignatureOf(list) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** 挂件节点解析:船规则条目用 selectNode;数组/兵种表用 resolveUnitNode。 */
    private static JsonValue resolveAttachValue(JsonValue rules, boolean ship, int level, int religionID,
                                                int religionGroup, String group, int uid, int aId,
                                                String unitName, int depth) {
        if (rules == null || depth > 8) {
            return null;
        }
        if (ship && isRuleEntry(rules)) {
            return selectNode(rules, level, religionID, religionGroup, group, aId, unitName, depth + 1);
        }
        return resolveUnitNode(rules, level, religionID, religionGroup, group, uid, aId, unitName, depth + 1);
    }

    /** 是否是「一条规则」(byId/religion/religionGroup/group/level/default 任一存在)。 */
    private static boolean isRuleEntry(JsonValue v) {
        return v != null && v.isObject()
                && (v.get("byId") != null || v.get("religion") != null || v.get("religionGroup") != null
                || v.get("group") != null || v.get("level") != null || v.get("default") != null);
    }

    /** 数组 → 挂件签名(`件名@骨;…`);空/非法 = 空串。 */
    private static String attachSignatureOf(JsonValue list) {
        if (list == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonValue it = list.child; it != null; it = it.next) {
            String tok = null;
            if (it.isString()) {
                tok = it.asString();
            } else if (it.isObject()) {
                String part = it.getString("part", null);
                String bone = it.getString("bone", null);
                if (part != null && part.length() > 0) {
                    tok = (bone == null || bone.length() == 0) ? part : (part + "@" + bone);
                }
            }
            if (tok != null && tok.length() > 0) {
                if (sb.length() > 0) {
                    sb.append(';');
                }
                sb.append(tok);
            }
        }
        return sb.toString();
    }

    /** 船模 combo:shipSelect.byAge 命中时代 → galley;否则 default.galley。 */
    private static String resolveShipCombo(int ageID) {
        return resolveShipComboFrom(shipSelectNode, ageID, shipModelFile);
    }

    /**
     * v4.5.0:从任意 shipSelect 节点解析船模(全局 loadSelect.json 或 civilization 覆盖)。
     * 支持:字符串(固定船模 id)、byAge 列表、default(字符串或 {galley:...})。
     */
    private static String resolveShipComboFrom(JsonValue node, int ageID, String fallback) {
        if (!comboMode || node == null) {
            return fallback;
        }
        try {
            if (node.isString()) {
                String s = node.asString();
                return (s == null || s.length() == 0) ? fallback : s;
            }
            JsonValue byAge = node.get("byAge");
            if (byAge != null) {
                for (JsonValue it = byAge.child; it != null; it = it.next) {
                    JsonValue ag = it.get("age");
                    int lo = ag != null ? ag.getInt("min", Integer.MIN_VALUE) : Integer.MIN_VALUE;
                    int hi = ag != null ? ag.getInt("max", Integer.MAX_VALUE) : Integer.MAX_VALUE;
                    if (ageID >= lo && ageID <= hi) {
                        JsonValue v = it.get("galley");
                        if (v != null) {
                            String s = v.asString();
                            if (s != null && s.length() > 0) {
                                return s;
                            }
                        }
                    }
                }
            }
            JsonValue def = node.get("default");
            JsonValue v = def != null ? def.get("galley") : null;
            if (v != null) {
                String s = v.asString();
                if (s != null && s.length() > 0) {
                    return s;
                }
            }
            if (def != null && def.isString()) {
                String s = def.asString();
                if (s != null && s.length() > 0) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    /** v4.5.0:该部队的船模(文明覆盖 > 全局 shipSelect > 设置船模)。 */
    private static String resolveShipComboFor(ArmyDivision a, int ageID) {
        if (a != null && !civOverrides.isEmpty()) {
            JsonValue ov = civOverrideOf(a.civID);
            if (ov != null) {
                JsonValue ss = civShipSelectOf(ov);
                if (ss != null) {
                    String c = resolveShipComboFrom(ss, ageID, null);
                    if (c != null && c.length() > 0) {
                        return c;
                    }
                }
            }
        }
        return resolveShipCombo(ageID);
    }

    /* ================= v1.1:路径转向 ================= */

    /**
     * 计算部队当前应朝向(模型 Y 轴角度,度;0=朝屏幕下方即面向观察者)。
     *
     * <p>v1.4.5 方向改为取<b>当前路段两端省中心的连线</b>(= 行军直线/黄引导线方向):
     * 行军位移正是 A.center→B.center 的直线插值(MoveUnits.update 同源),
     * 而旧版用「部队精灵位置→目标中心」会被精灵自身状态污染 ——
     * 堆叠站位偏移(iShiftX/Y)、到达并入新堆的纵向弹跳(extraArmyY)、行军摆动动画
     * 都会让方向在小范围内漂移,表现为「过地块时莫名小幅乱转」。
     * 省中心差在整段行程内恒定,过地块时精确切换到下一段方向。移动中置 tmpMoving=1。</p>
     */
    private static float facingOf(ArmyDivision a) {
        tmpMoving = 0f;
        String k = a.civID + "|" + a.key;
        Float prev = facingCache.get(k);
        boolean known = prev != null;
        float facing = known ? prev.floatValue() : 0f;
        float target = facing;
        boolean hasTarget = false;
        try {
            MoveUnits mu = findMoveUnits(a.civID, a.key);
            if (mu != null) {
                tmpMoving = 1f;                // 在途 = 移动中(步伐起伏)
                int from = mu.getFromProvinceID();
                int dest = mu.getToProvinceID();
                if (from >= 0 && dest >= 0) {
                    float dx = ProvinceDrawArmy.getDetailsPosX_2(dest) - ProvinceDrawArmy.getDetailsPosX_2(from);
                    float dy = ProvinceDrawArmy.getDetailsPosY_2(dest) - ProvinceDrawArmy.getDetailsPosY_2(from);
                    if (dx * dx + dy * dy > 400f) { // 路段过短(<20px)保持上一朝向,防乱转
                        target = (float) Math.toDegrees(Math.atan2(dx, dy / SIN_TILT));
                        hasTarget = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (hasTarget) {
            if (!known || turnTau <= 0f) {
                facing = target;                       // 首次出现/关闭平滑:直接到位
            } else {
                // v1.4.6:转身过渡 —— 向目标角缓动靠拢(指数平滑,帧率无关)
                float delta = target - facing;
                delta -= 360f * Math.round(delta / 360f);            // 最近角差(-180,180]
                float dt = 0.016f;
                try {
                    float g = Gdx.graphics.getDeltaTime();
                    if (g > 0f && g < 0.1f) {
                        dt = g;
                    }
                } catch (Throwable ignored) {
                }
                facing += delta * (1f - (float) Math.exp(-dt / turnTau));
                facing -= 360f * Math.round(facing / 360f);          // 归一化,防漂
            }
        }
        facingCache.put(k, facing);
        if (facingCache.size() > 512) {
            facingCache.clear();
            facingCache.put(k, facing);
        }
        return facing;
    }

    /** 是否玩家选中的军牌(Game.activeArmy 按 key 匹配;支持多选)。 */
    private static boolean isSelectedArmy(String key) {
        try {
            if (key == null || Game.activeArmySize <= 0) {
                return false;
            }
            return Game.isAlreadyActiveArmy(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 找该部队所在战斗(游戏权威):battleManager.isArmyInBattle → getArmyBattleKey → getBattle。
     * 找不到返回 null(调用方回退普通显示)。
     */
    private static Battle findBattleForArmy(int pid, String key) {
        try {
            if (Game.battleManager == null || key == null) {
                return null;
            }
            if (!Game.battleManager.isArmyInBattle(pid, key)) {
                return null;
            }
            String bk = Game.battleManager.getArmyBattleKey(pid, key);
            if (bk == null || bk.length() == 0) {
                return null;
            }
            return Game.battleManager.getBattle(bk);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 把战斗部队写入候选池 —— <b>锚定战斗横幅</b>(修复 v1.2“左侧兵模偏下”):
     * 双方共用横幅基准线与左右半区,进攻方=左半(面右)、防守方=右半(面左);
     * v1.7.0:同战斗同侧只保留一个(组内去重,选中者优先)。
     *
     * <p>横幅几何与 Battle.drawBattle_Just 同源:优先用其已算好的 posX/posY/battleWidth
     * (每帧地图绘制时更新,菜单层绘制在其后 → 本帧即最新),未画过则按同公式现算。</p>
     *
     * @return true=已处理(写入候选或视口外跳过);false=无法处理(回退普通显示)
     */
    private static boolean addBattleCandidate(Battle bt, int pid, ArmyDivision a, int side, float size, int sel, int kind) {
        try {
            float flagW = ImageManager.getImage(Images.armyFlag).getWidth();
            float iconW = ImageManager.getImage(Images.battleIcon0).getWidth();
            float bw;
            float bpx;
            float bpy;
            if (bt.battleWidth > 0) {
                bw = bt.battleWidth;
                bpx = bt.posX;
                bpy = bt.posY;
            } else {
                bw = bt.getBattleWidth();
                bpx = Battle.getArmyPosX(pid) - bw * 0.5f;
                bpy = Battle.getArmyPosY(pid);
            }
            float atkW = bt.attackingArmy != null ? bt.attackingArmy.textW : bw * 0.25f;
            float defW = bt.defendingArmy != null ? bt.defendingArmy.textW : bw * 0.25f;
            float atkSec = flagW + 5f + atkW + 10f + iconW * 0.5f;    // 进攻方半区(旗+数字+半个⚔)
            float defSec = defW + 10f + iconW * 0.5f + flagW + 5f;    // 防守方半区
            float cx;
            float face;
            if (side == 1) {          // 进攻方:横幅左半中心,再向左分离;面向右(朝防守方)
                cx = bpx + atkSec * 0.5f - battleSplitPx;
                face = 90f;
            } else if (side == 2) {   // 防守方:横幅右半中心,再向右分离;面向左(朝进攻方)
                cx = bpx + bw - defSec * 0.5f + battleSplitPx;
                face = -90f;
            } else {                  // 侧别未知:横幅中点,保持朝向
                cx = bpx + bw * 0.5f;
                face = facingOf(a);
            }
            float rx = cx - size * 0.5f;
            float ry = bpy - liftPxEff - size;   // 双方同一基准线(横幅顶上方)
            if (rx + size < -cullMarginPx || ry + size < -cullMarginPx
                    || rx > CFG.GAME_WIDTH + cullMarginPx || ry > CFG.GAME_HEIGHT + cullMarginPx) {
                return true;
            }
            long gk = GROUP_BATTLE | ((pid & 0xFFFFL) << 8) | (side & 0xF);
            addCandidate(gk, cx, bpy + 16f, rx, ry, size, relationOf(a.civID), face, 2, sel, true, a, kind, false);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 在文明的移动线列表里按 key 找 MoveUnits(行军路径数据来源)。 */
    private static MoveUnits findMoveUnits(int civID, String key) {
        try {
            if (key == null || civID < 0) {
                return null;
            }
            int n = Game.getCiv(civID).getMoveUnitsSize();
            for (int j = 0; j < n; j++) {
                MoveUnits mu = Game.getCiv(civID).getMoveUnits(j);
                if (mu != null && key.equals(mu.key)) {
                    return mu;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * v1.9.1:行程进度过渡 —— 移动中的部队按「当前路段」决定实际显示的模型类型/进度:
     * <ul>
     *   <li>路段两端同类(陆→陆 / 海→海):显示路段起点省的类型(跨海段完成后持续为船模,
     *       直到到站并入海省列表 —— 与游戏逐段转移的行为对齐);</li>
     *   <li>路段两端陆↔海不同:显示目标类型 + 进度 p(0→0.5 旧模型缩小,0.5→1 新模型长回),
     *       由渲染器按 p 缩放 —— 行进到 100% 时正好恢复全尺寸,不再等到站后播放。</li>
     * </ul>
     * 数据源 = 路段真实进度(与兵牌位置公式完全同源,同帧同步;见 legProgressOf)。
     */
    private static void applyMoveMorph(int k, ArmyDivision a) {
        applyMoveMorph(k, a, a != null && a.inMovement);
    }

    /** v4.9.3:moving = 真实行军状态(游戏标记 or 路线进度推进),模组恢复移动也能正常过渡。 */
    private static void applyMoveMorph(int k, ArmyDivision a, boolean moving) {
        try {
            if (!morphMove || a == null || !moving) {
                return;
            }
            MoveUnits mu = findMoveUnits(a.civID, a.key);
            if (mu == null || mu.iRouteSize < 2) {
                return;
            }
            int fromID = mu.getFromProvinceID();
            int toID = mu.getToProvinceID();
            boolean fromSea = Game.getProvince(fromID).getSeaProvince();
            boolean toSea = Game.getProvince(toID).getSeaProvince();
            if (fromSea != toSea) {
                float p = legProgressOf(mu);
                if (p < 0f) {
                    return;                    // 路段宽不可用 → 不干预(走时间兜底)
                }
                if (p > 1f) {
                    p = 1f;
                }
                kindBuf[k] = toSea ? 1 : 0;   // 目标模型(过渡后半段显示)
                morphBuf[k] = p;              // 路段真实进度(0..1)
            } else {
                kindBuf[k] = fromSea ? 1 : 0; // 路段同类:显示“当前所在省”的类型(跨海后保持船模)
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 路段真实进度(0..1)= 与部队位置插值**完全同源**的表达式:
     * (doneMovementProgressWidth + (currentMovementProgressWidth − doneMovementProgressWidth)
     *  × fCurrentMovingPercentage) / 路段宽(iWidth.get(0));宽不可用时返回 -1。
     * <p>⚠ 绝不能直接用 fCurrentMovingPercentage —— 它是“本游戏刻内的时间比例”:
     * 每刻开头被重置为 0(再置 0.01 重新爬升),直接拿它当进度会把过渡
     * 变成每刻重播一次 0→0.5→1 的往返闪切(模型来回切换的真因)。</p>
     */
    private static float legProgressOf(MoveUnits mu) {
        try {
            int w = mu.iWidth.get(0);
            if (w <= 0) {
                return -1f;
            }
            float lerp = mu.doneMovementProgressWidth
                    + (mu.currentMovementProgressWidth - mu.doneMovementProgressWidth) * mu.fCurrentMovingPercentage;
            return lerp / (float) w;
        } catch (Throwable ignored) {
        }
        return -1f;
    }

    /* ================= v1.1:用户资源说明 ================= */

    private static final String README_TEXT =
            "【G3D 兵模扩展测试 · 用户资源说明】(v4.9.14)\n"
            + "\n"
            + "■ 目录结构(资源目录 = hotplugins\\resources\\G3DArmy\\)\n"
            + "  setting.json        总体渲染设置(原 army.json;旧文件仍兼容)\n"
            + "  loadSelect.json     装载配置:兵种/船型 → 模型(unitSelect / unitgroup / shipSelect)\n"
            + "  anim_events.json    动画事件/状态机(攻击粒子时间线,预留)\n"
            + "  civilization\\       指定国家的变体(顶层 unitSelect 数组 + attachments)\n"
            + "  models\\assembly.json      运行时装配配置(rigs/animSets/combos 等)\n"
            + "  models\\attachments.json   挂件(武器/盾)规则:unitSelect / unitgroup / shipSelect(旧字段 rules = unitSelect)\n"
            + "  models\\main\\{g3d,png}     完整模型 + 贴图(combo 成品)\n"
            + "  models\\parts\\{g3d,png}    运行时挂件 + 贴图\n"
            + "  models\\body|head|feet\\{g3d,png}  预留拆分部件\n"
            + "  每个模型目录的 index.json 记录模型清单/挂点基准。\n"
            + "  v4.7.0:loadSelect.json 的 unitSelect 是宗教块数组(religion),unitgroup 为文化组兜底;\n"
            + "  civilization/<tag>.json 的 unitSelect 是顶层数组,不需要 religion/default;\n"
            + "  level 可写在任意层级;religion 可用英文名或数字 ID,religionGroup 用 ReligionGroupID(0~5)。\n"
            + "\n"
            + "■ 替换兵模(模型/动画文件公开)\n"
            + "  1. 把模型文件放到 models\\main\\g3d\\ 子目录,命名为  model.g3dj  或  model.g3db\n"
            + "     (libGDX g3d 格式;可用 fbx-conv 从 Blender/FBX 转换导出)。\n"
            + "  2. 重启游戏生效。模型要求:Y 轴朝上;尺寸/原点不限(自动适配:缩放至视口约 85%、贴地、居中)。\n"
            + "  3. 动画分四态,自动按名称匹配:停留 idle(idle/stand/stay…)、移动 move(walk/run/march…)、\n"
            + "     战斗 battle(attack/battle/fight…)、撤退 retreat(retreat/flee/run/escape…);\n"
            + "     可用 setting.json 的 animationIdle / animationMove / animationBattle / animationRetreat 指定,\n"
            + "     基础键 \"animation\" 作回退(\"\"=第一个;\"-\"=禁用动画),animSpeed 调速;\n"
            + "     某状态无动画时自动用内置程序化动作(呼吸/步伐/突刺/撤退前倾+白旗)。\n"
            + "  4. 模型会被染成与方框一致的关系色(玩家绿/敌红/友蓝/中立);\n"
            + "     setting.json \"tint\": false 可关闭染色,保留模型原色。\n"
            + "  5. 想恢复内置几何兵模:删除 model.g3dj/g3db,或把 \"model\" 设为 \"builtin\"。\n"
            + "  6. soldier-template.g3dj 是内置兵模自动导出的格式示例(models\\main\\g3d)\\n"
            + "     (可改名为 model.g3dj 先做链路测试,再用自制模型替换)。\n"
            + "  7. 贴图:模型引用的贴图文件(如 Knight.png)放到同级的 png\\ 子目录即可\n"
            + "     (models\\main\\png;v4.2.0 起也支持 models\\<类>\\g3d 旁、旧 png\\ 目录);\n"
            + "     v1.4.1 起按模型目录的绝对路径加载(桌面/安卓均可用),缺贴图时纯色占位。\n"
            + "  8. 顶点色:导出的顶点色若「全网格同一值」(转换器哨兵值,与贴图相乘会毁色)→ 自动剥离;\n"
            + "     顶点色有变化的模型保留原样。\n"
            + "  9. 脚下阴影:用户模型自动渲染柔和接触阴影(径向渐变圆盘,随部队移动/转身);\n"
            + "     内置几何兵模自带硬边阴影盘。\n"
            + "\n"
            + "■ 战斗表现\n"
            + "  交战(两军同格)时:兵模锚定游戏战斗横幅(进攻|⚔|防守)——\n"
            + "  进攻方对齐横幅左半、防守方对齐右半,同一基准线、面对面;\n"
            + "  左右间距 = battleSplitPx(px,默认 55)。\n"
            + "\n"
            + "■ 同格多部队(堆叠)\n"
            + "  默认只显示首个(最上)静止兵牌的兵模;该部队开始移动后,\n"
            + "  下一个静止兵牌的兵模自动接替(依次显示,防重叠/防卡顿);\n"
            + "  移动中的部队始终显示。setting.json \"stackFirstOnly\": false 可关;\n"
            + "  玩家选中的军牌(单选/多选)始终显示其兵模(即使不是首个)。\n"
            + "\n"
            + "■ 位置与尺寸(setting.json)\n"
            + "  modelPx       兵模屏幕尺寸(px)\n"
            + "  modelPxW/H    渲染窗口宽/高(px;0=用 modelPx)—— 默认窗口大小\n"
            + "  modelPxLimit  渲染窗口像素上下限 [min, max](0=不限;按窗口长边夹取,等比缩放→宽高比不变)\n"
            + "  modelMulLimit 模型尺寸倍率上下限 [min, max](默认 [0.5, 2];即「近大远小」尺寸系数的夹取区间)\n"
            + "  modelLift     渲染窗口相对兵牌位置 [平视端, 俯视端](px;留空 []=自动):\n"
            + "                自动 → 随俯角把窗口下移,让脚与兵牌保持 liftPx 间距(俯视时脚不会越离越远);\n"
            + "                手写 [a, b] → 两端插值;负值 = 窗口压到兵牌基准线下方\n"
            + "  liftPx        兵模底边与方框顶部的间距(px,默认 8;调大 = 更靠上;自动位置模式下的基准间距)\n"
            + "  battleSplitPx 战斗时进攻/防守左右分离距离(px,默认 55)\n"
            + "  worldH        3D 视口世界高度(默认 2.2;模型显得过大/被裁时调大,如 4~6)\n"
            + "  modelAutoFit  用户模型自动适配(true=默认:按包围盒缩放至视口约 85%、贴地、居中)\n"
            + "  modelScale    手动缩放倍率(>0 覆盖自动缩放;0=自动)\n"
            + "  modelOffsetY  额外 Y 偏移(世界单位,叠加在自动贴地之上)\n"
            + "  modelYaw      基础朝向修正(度;模型背对行军方向时设 180)\n"
            + "  shipModel     船模文件名(不含扩展名;默认 \"ship\";\"\"/\"-\"/\"builtin\"=不用船模)\n"
            + "  shipYaw       船模朝向修正(度;船头背对航向时设 180)\n"
            + "  morphMs       到站兜底过渡时长(毫秒,默认 550;0=关闭该兜底)\n"
            + "  morphMove     true=过渡绑定行军进度(默认)/ false=到站后才播放过渡\n"
            + "  numBones      着色器骨骼上限(0=自动按模型统计;拼装兵模 torso 44 骨需 ≥44)\n"
            + "  turnTau       转身平滑时间常数(秒,默认 0.2;越小转得越快,0=瞬间转向)\n"
            + "  tiltStrength  伪3D地图适配强度(0=关闭;1=标准;上限 2;未装伪3D补丁时无效)\n"
            + "  nearFar       近大远小比例(小数;0=关;1=标准;越大近处越放大/远处越小。\n"
            + "                尺寸系数 = 中线归一的地图局部缩放比 1/D² (D = 1+tiltK·scale·nearFar·(1−Y/H)):\n"
            + "                屏幕下方(近)更大、上方(远)更小,平均尺寸不变;仅地图倾斜开启时生效)\n"
            + "  viewPitch     渲染窗口相机以模型中心为圆心做 3D 环绕,俯仰角区间 [最小, 最大](度,默认 [12, 55]);\n"
            + "                直接随【地图缩放】变化(与地图倾斜开关无关),以“兵模出现的缩放”为锚点、锚点×6 到最大:\n"
            + "                地图拉近(伪3D越接近“平视”)→ 俯角趋向最小值 → 兵模也更平视;\n"
            + "                缩到最小(地图接近俯视)→ 趋向最大值 → 兵模也从上方往下看;\n"
            + "                相机自动拉远保证环绕全程模型/阴影不超出窗口;两值相等 = 关闭(固定基准俯角 21.78°)。\n"
            + "\n"
            + "■ 显示规则(v1.7.0)\n"
            + "  1. 只在默认「政治」地图模式显示;外交/宗教等其它模式不渲染。\n"
            + "  2. 迷雾:看不到兵牌的地块也看不到兵模(与兵牌同源判定)。\n"
            + "  3. 只渲染当前视角中能看到兵牌的部队;同地块堆叠、同行军段(同文明同起终点)、\n"
            + "     同战斗同侧均只显示一个兵模(选中者优先;堆叠组锚定整叠最上方兵牌,不遮挡兵牌;\n"
            + "     v1.7.2 行军组粘性跟随同一兵牌,兵种速度不一也不来回闪)。\n"
            + "  4. 数量太多时优先显示:选中 > 我方(绿)> 敌方(红)> 盟友(蓝)> 其他(灰),\n"
            + "     同级取离视角中心更近者(默认上限 24,setting.json maxModels 可调,最大 128)。\n"
            + "  5. 海面:下海的部队换为船模(上岸自动换回);海战与陆地同逻辑。\n"
            + "  6. 陆↔海过渡(绑定行军进度):跨陆/海路段 0→50% 旧模型缩到最小、50%→100%\n"
            + "     换成新模型放大(走到 100% 时正好恢复全尺寸);morphMove=false 改回“到站后过渡”\n"
            + "     (时长 morphMs,默认 550ms;morphMs=0 且 morphMove=false = 关闭)。\n"
            + "  7. v4.9.0:兵模比兵牌更晚出现 —— 缩放 ≥ DRAW_ARMY_MIN_SCALE×modelMinScaleMul 才画\n"
            + "     (默认 1.25 倍);5 档最高速时不画“移动中”部队的模型(hideMovingAtMaxSpeed)。\n"
            + "     也可在 setting.json 直接写 modelMinScale(绝对缩放,>0 优先;0=按倍率自动算)。\n"
            + "\n"
            + "■ 其它(setting.json)\n"
            + "  enabled   true=正常显示(默认)/ false=不显示 3D 兵模\n"
            + "            (v1.6.0 起取代原右上角「关闭 3D 兵模」按钮;改后「全部重载」生效)\n"
            + "  scope     \"all\"=全部部队(默认)/ \"player\"=只画玩家部队\n"
            + "  stackFirstOnly 同格堆叠/同行军段只显示一个兵模(true=默认;选中者优先)\n"
            + "  maxModels 同屏兵模上限(默认 24,最大 128)\n"
            + "  modelMinScaleMul 兵模出现缩放 = 兵牌阈值×本值(默认 1.25;1=与兵牌同时出现,\n"
            + "            越大=兵牌出现后还需再放大更多才画模型)\n"
            + "  modelMinScale 兵模出现的最小地图缩放(绝对值;默认 0=用上面倍率自动算,\n"
            + "            >0 直接生效,部署后可酌情微调,例如 0.62)\n"
            + "  hideMovingAtMaxSpeed 5 档最高速时隐藏移动中部队的兵模(默认 true)\n"
            + "  cullMarginPx 屏外裁剪边距(px,默认 96;越小越省,太小会让边缘模型衔接到得早/晚)\n"
            + "  comboRetryFrames 兵模加载失败后重试间隔(帧,默认 600;新建文明第一次加载失败可自动重试)\n"
            + "  comboRetryMax 最多重试次数(默认 0=不限;0 以外的值达到后视为永久失败,固定画兜底)\n"
            + "  comboLoadBudgetMs 单帧加载耗时预算(ms,默认 6;超过后后续几帧暂停加载,避免连续掉帧;0=不限)\n"
            + "  spinSpeed 自旋速度(弧度/秒;0=静立)\n"
            + "  删除 setting.json 后重启 = 恢复默认配置并重新生成。\n";

    /** 首次运行写一份用户资源说明(模型/动画替换方法)。 */
    private static void writeReadmeIfMissing() {
        try {
            File f = new File(ctx.dataDir(), "README-用户资源说明.txt");
            if (f.exists()) {
                return;
            }
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(fos, "UTF-8");
            try {
                w.write(README_TEXT);
            } finally {
                w.close();
            }
            log("已生成用户资源说明:" + f.getAbsolutePath());
        } catch (Throwable t) {
            log("说明文件写入失败:" + t);
        }
    }

    static boolean hasModels() {
        return modelCount > 0;
    }

    /* ================= v3.0.0:伪3D(Pseudo3DMap)适配 ================= */

    /**
     * 倾斜系数:反射调用 Game.getTiltK()(Pseudo3DMap 补丁新增;MAP_TILT 关闭时其返回 0)。
     * 游戏无该方法(未装补丁/桌面原版)→ 0 → 完全原行为。仅在首帧解析一次方法,之后每帧一次 invoke。
     */
    private static float tiltK() {
        if (!tiltKResolved) {
            tiltKResolved = true;
            try {
                tiltKMethod = Game.class.getMethod("getTiltK");
            } catch (Throwable t) {
                tiltKMethod = null;
            }
            log(tiltKMethod != null
                    ? "伪3D适配:检测到 Pseudo3DMap(Game.getTiltK;适配强度=" + tiltStrength + ")"
                    : "伪3D适配:未检测到 Pseudo3DMap(getTiltK 缺失)→ 按原版固定尺寸显示");
        }
        if (tiltKMethod == null) {
            return 0f;
        }
        try {
            Object r = tiltKMethod.invoke(null);
            return (r instanceof Float) ? ((Float) r).floatValue() : 0f;
        } catch (Throwable t) {
            return 0f;
        }
    }

    /**
     * v4.9.10:渲染窗口视角(俯仰角)随地图缩放变化。
     *
     * <p>直接用地图缩放 `currentScale` 做归一(不看地图倾斜开关、也不看 getTiltK —— 旧版
     * 要求伪3D补丁生效才动,结果地图未开倾斜(MAP_MODE≠1)时完全没反应)。
     * 锚点 = **兵模开始出现的缩放**(`modelMinScale` 或 `DRAW_ARMY_MIN_SCALE×modelMinScaleMul`),
     * 于是不论用户怎么调可见阈值,配置的 [最小,最大] 俯角都能在真正看得到兵模的缩放区间内走完:
     * `u = clamp01( ln(scale/锚点) / ln(6) )`(即锚点 → 锚点×6)。</p>
     *
     * <p>方向与地图一致:地图拉得越近(伪3D 倾斜越强、越接近“平视”)→ 模型俯角越小,兵模也跟着变得更平视;
     * 缩到最小(地图接近“俯视”)→ 用最大俯角,兵模也从上往下看。</p>
     *
     * @param scale           当前地图缩放
     * @param modelMinScaleEff 兵模开始出现的缩放阈值(<=0 = 用默认 0.6)
     */
    private static void updateViewPitch(float scale, float modelMinScaleEff) {
        float anchor = modelMinScaleEff > 0.05f ? modelMinScaleEff : VIEW_PITCH_SCALE_MIN;
        float s = scale > 0.01f ? scale : 0.01f;
        float u = (float) (Math.log(s / anchor) / VIEW_PITCH_LOG_SPAN);
        if (u < 0f) {
            u = 0f;
        } else if (u > 1f) {
            u = 1f;
        }
        viewPitchU = u;
        if (!viewPitchOn) {
            G3DArmyMin.viewPitchDeg = -1f;
            return;
        }
        // u=0(刚能看到兵模/地图最俯视)→ 最大俯角;u=1(拉到最远近/最平视)→ 最小俯角
        G3DArmyMin.viewPitchDeg = viewPitchMax + (viewPitchMin - viewPitchMax) * u;
    }

    /**
     * v4.9.14:本帧「渲染窗口相对兵牌的位置」(= 窗口底边在兵牌顶上方多少 px,负值可压到基准线下方)。
     *
     * <p><b>为什么要随俯角变</b>:3D 环绕时模型中心固定在窗口中心,俯视时脚(模型底边)
     * 会向中心收拢 → 脚与兵牌的间距变大(旧版窗口底边固定 → “俯视时脚离兵牌越来越远”)。</p>
     *
     * <p>两种模式:
     * <ul>
     *   <li><b>自动</b>(`modelLift` 留空/缺键,推荐):保持「脚」距兵牌始终为 `liftPx` ——
     *       脚在窗口内的位置比例 `fb = 0.5 − cy·cosθ/(worldH·zoom)`,以 cy≈0.4·worldH、
     *       基准俯角 21.78°(cos=0.9286) 为基准反推窗口应下移多少;</li>
     *   <li><b>手动</b>(`modelLift: [平视端, 俯视端]`):直接给两端位置,与 `viewPitch` 用同一个
     *       缩放归一值 `u` 线性插值(pitch 关闭时 `u` 仍然按缩放算)。</li>
     * </ul></p>
     */
    private static void updateLift() {
        float fh = (modelPxH > 0f ? modelPxH : modelPx) ;
        if (fh < 1f) {
            fh = 170f;
        }
        float lift;
        if (modelLiftManual) {
            // u=1(平视端) → modelLiftMin;u=0(俯视端) → modelLiftMax
            lift = modelLiftMax + (modelLiftMin - modelLiftMax) * viewPitchU;
        } else {
            float deg = G3DArmyMin.viewPitchDeg > 0.5f ? G3DArmyMin.viewPitchDeg : 21.78f;
            float cs = (float) Math.cos(deg * 0.017453292f);
            // 脚在窗口内的比例 fb = 0.5 − 0.4·cosθ;基准(21.78°)时 0.1286
            lift = liftPx - 0.4f * fh * (0.9286f - cs);
        }
        if (lift < -fh) {
            lift = -fh;
        } else if (lift > fh) {
            lift = fh;
        }
        liftPxEff = lift;
    }

    /** v4.9.8:读取 `"key": [a, b, ...]` 形式的浮点数组(缺失/非法 → def)。 */
    private static float[] getFloatArray(String text, String key, float[] def) {
        try {
            int i = text.indexOf("\"" + key + "\"");
            if (i < 0) {
                return def;
            }
            int colon = text.indexOf(':', i);
            int open = text.indexOf('[', i);
            if (open < 0 || (colon >= 0 && colon < open)) {
                return def;
            }
            int close = text.indexOf(']', open);
            if (close < 0) {
                return def;
            }
            String body = text.substring(open + 1, close).trim();
            if (body.length() == 0) {
                return new float[0];
            }
            String[] parts = body.split(",");
            float[] out = new float[parts.length];
            for (int k = 0; k < parts.length; k++) {
                out[k] = Float.parseFloat(parts[k].trim());
            }
            return out;
        } catch (Throwable t) {
            return def;
        }
    }

    /** 当前 FPS(诊断用;取不到时返回 ?)。 */
    private static String fpsText() {
        try {
            return String.valueOf(Gdx.graphics.getFramesPerSecond());
        } catch (Throwable t) {
            return "?";
        }
    }

    /** 内存快照(诊断用):Java 已用/上限 MB + 原生堆 MB。 */
    private static String memText() {
        try {
            Runtime r = Runtime.getRuntime();
            long used = (r.totalMemory() - r.freeMemory()) >> 20;
            long max = r.maxMemory() >> 20;
            long nativeHeap = -1L;
            try {
                nativeHeap = Gdx.app.getNativeHeap() >> 20;
            } catch (Throwable ignored) {
            }
            return used + "/" + max + "MB(native=" + nativeHeap + "MB)";
        } catch (Throwable t) {
            return "?";
        }
    }

    /** dex 补丁 LateGamePerf 的诊断串(反射读取;补丁未安装时返回 "-")。 */
    private static java.lang.reflect.Method latePerfMethod;
    private static boolean latePerfResolved;

    private static String latePerfStats() {
        try {
            if (!latePerfResolved) {
                latePerfResolved = true;
                try {
                    latePerfMethod = Class.forName("com.saysky.lateperf.LatePerf").getMethod("statsText");
                } catch (Throwable t) {
                    latePerfMethod = null;
                }
            }
            if (latePerfMethod == null) {
                return "-";
            }
            Object v = latePerfMethod.invoke(null);
            return v instanceof String ? (String) v : "-";
        } catch (Throwable t) {
            return "!";
        }
    }

    private void logStatus() {
        try {
            float scale = Game.mapScale.getCurrentScale();
            log("状态: scale=" + scale
                    + " minScale=" + Game.DRAW_ARMY_MIN_SCALE
                    + " 兵模最小缩放=" + (modelMinScale > 0f
                    ? ("绝对" + modelMinScale)
                    : ("倍率" + modelMinScaleMul + "→" + (Game.DRAW_ARMY_MIN_SCALE * modelMinScaleMul)))
                    + " 高速隐藏移动=" + hideMovingAtMaxSpeed + "(当前高速=" + isMaxGameSpeed() + ")"
                    + " selectMode=" + Game.mapTouchManager.selectMode
                    + " 候选=" + candCount + " 列表=" + modelCount + "(战斗=" + battleCount + ") 渲染=" + G3DArmyMin.lastRendered
                    + " 范围=" + (scopePlayerOnly ? "player" : "all")
                    + " 骨骼上限=" + G3DArmyMin.lastNumBones
                    + " 视角=" + Math.round(G3DArmyMin.viewPitchDeg * 10f) / 10f + "°(scale=" + (Math.round(scale * 100f) / 100f) + ")"
                    + " 近大远小ks=" + (Math.round(tiltKS * 1000f) / 1000f) + "×" + nearFar
                    + "(缩放系数=" + (Math.round(nearFarFMin * 100f) / 100f) + "~" + (Math.round(nearFarFMax * 100f) / 100f) + ")"
                    + " 窗口位置=" + (Math.round(liftPxEff * 10f) / 10f) + "px(" + (modelLiftManual ? "手动" : "自动") + ")"
                    + " 阴影=" + (G3DArmyMin.shadowReady ? "开" : "关")
                    + " 模型=" + G3DArmyMin.modelSource
                    + " FPS=" + fpsText()
                    + " 堆=" + memText()
                    + " combo=" + G3DArmyMin.comboCacheCount() + "/" + comboCacheSize
                    + " shipSet=" + G3DArmyMin.shipCacheCount() + "/" + shipCacheSize
                    + " 加载=" + G3DArmyMin.comboLoads + "次/共" + G3DArmyMin.comboLoadMsSum
                    + "ms/最大" + G3DArmyMin.comboLoadMsMax + "ms"
                    + " lateperf=" + latePerfStats());
            G3DArmyMin.resetComboLoadStats();
        } catch (Throwable t) {
            log("状态读取失败:" + t);
        }
    }

    private void tryMount() {
        try {
            if (Game.menuManager == null || Game.menuManager.IN_GAME < 0) {
                // IN_GAME 菜单视图未建立(主菜单阶段值恒为 -1,addNextMenuToView 会 AIOOBE)→ 等待
                return;
            }
            ArmyOverlayMenu m = new ArmyOverlayMenu();
            int slot = Game.menuManager.addNextMenuToView(Game.menuManager.IN_GAME, m);
            Game.menuManager.setOrderOfMenu(slot);
            m.updateMenuElements_IsInView();
            m.setVisible(true);
            overlay = m;
            mounted = true;
            log("兵模覆盖层已挂载(slot=" + slot + ", 全屏无框,观察 IN_GAME 视图)");
        } catch (Throwable t) {
            if (frames % 300L == 0L) {
                log("挂载重试:" + t);
            }
        }
    }

    /** 隐藏“本插件类名前缀”的旧实例覆盖层(覆盖导入/重载后残留)。 */
    private void hideStaleWindows() {
        try {
            if (Game.menuManager == null) {
                return;
            }
            Field f = Game.menuManager.getClass().getDeclaredField("menus");
            f.setAccessible(true);
            Object v = f.get(Game.menuManager);
            if (!(v instanceof List)) {
                return;
            }
            List<?> views = (List<?>) v;
            for (Object viewObj : views) {
                if (!(viewObj instanceof List)) {
                    continue;
                }
                List<?> menus = (List<?>) viewObj;
                for (Object m : menus) {
                    if (!(m instanceof Menu) || m == overlay) {
                        continue;
                    }
                    String cn = m.getClass().getName();
                    if (cn != null && cn.startsWith("com.saysky.g3darmy.")) {
                        try {
                            ((Menu) m).setVisible(false);
                            log("已隐藏旧实例覆盖层:" + cn);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    static void log(String message) {
        if (!logEnabled()) {
            return; // v1.4.7:框架「启用日志」关闭 → 不输出任何日志
        }
        try {
            ctx.log("[G3DArmy] " + message);
        } catch (Throwable ignored) {
        }
    }

    /** 「启用日志」缓存(v1.4.7):每 1 秒刷新一次,帧内调用 = 一次字段读,零开销。 */
    private static volatile boolean logEnabledCache = true;
    private static volatile long logEnabledNextCheck = 0L;

    /**
     * 热加载框架「启用日志」开关联动:关闭 → 插件不做任何诊断采集/日志输出。
     * 取不到配置类时按开启处理。
     */
    static boolean logEnabled() {
        long now = System.nanoTime();
        if (now >= logEnabledNextCheck) {
            logEnabledNextCheck = now + 1000000000L; // 1s
            try {
                logEnabledCache = com.saysky.hotplugloader.LoaderConfig.logEnabled();
            } catch (Throwable t) {
                logEnabledCache = true;
            }
        }
        return logEnabledCache;
    }
}
