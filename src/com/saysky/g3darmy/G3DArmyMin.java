package com.saysky.g3darmy;

import java.io.File;
import java.io.FileWriter;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import aoc.kingdoms.lukasz.jakowski.CFG;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.NodeKeyframe;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation;
import com.badlogic.gdx.graphics.g3d.model.data.ModelData;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMesh;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeAnimation;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe;
import com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.UBJsonReader;

/**
 * 兵模渲染器 v1.0:在地图部队方框处绘制 3D"兵模"(士兵几何体)。
 *
 * <p>与地图锚定版(G3DMapMin)的差异:</p>
 * <ul>
 *   <li><b>多实例、多视口一趟渲染</b>:状态捕获/还原每帧只做一次,循环内为每个部队
 *       设置小视口渲染一个兵模(尺寸固定屏幕像素,与方框一致,不随缩放变化);</li>
 *   <li>4 套配色模型:玩家(绿,对应原版绿色方框)/ 敌(红)/ 友(蓝)/ 中立(中性色);</li>
 *   <li>兵模 = 腿+躯干+头 的几何组合 + 脚下接触阴影圆盘(半透明),廉价且轮廓清晰;</li>
 *   <li>正交相机(与地图上帝视角一致,无透视视差);</li>
 *   <li><b>v1.1</b>:兵模绘制在方框上方(不再遮挡);按行军路径自动转向(移动中带步伐起伏);
 *       支持 dataDir 用户模型(model.g3dj/g3db,自动播放模型动画),内置模型自动导出格式示例;</li>
 *   <li><b>v1.2</b>:三态动画(停留/移动/战斗;用户模型按名称自动匹配 idle/move/battle,可配置);
 *       战斗部队按游戏判定分左右(进攻方左/防守方右、面对面),某状态缺动画时用程序化动作兜底。</li>
 * </ul>
 */
public final class G3DArmyMin {

    /** 最近一次错误(初始化/绘制);空串 = 正常。 */
    public static String lastError = "";
    /** 诊断信息(init 时采集,插件打进日志)。 */
    public static String diag = "";
    /** 自旋速度(弧度/秒;0 = 静立,兵模默认静立)。 */
    public static volatile float spinSpeed = 0f;
    /** 最近一帧实际渲染的兵模数量(诊断用)。 */
    public static volatile int lastRendered = 0;

    /* ---- 用户资源/动画配置(插件从 army.json 推入,init 前设置) ---- */
    /** 用户模型文件名(不含扩展名;空/"builtin" = 用内置几何兵模)。 */
    public static volatile String confModel = "model";
    /** 动画基础名("" = 自动第一个;"-" = 禁用所有动画;其他 = 指定名称)。 */
    public static volatile String confAnimation = "";
    /** 停留动画名("" = 自动按名称匹配)。 */
    public static volatile String confAnimIdle = "";
    /** 移动动画名("" = 自动按名称匹配)。 */
    public static volatile String confAnimMove = "";
    /** 战斗动画名("" = 自动按名称匹配)。 */
    public static volatile String confAnimBattle = "";
    /** 撤退动画名("" = 自动按名称匹配;缺省回退到移动动画)。 */
    public static volatile String confAnimRetreat = "";
    /** 动画速度倍率。 */
    public static volatile float confAnimSpeed = 1f;
    /** 视口世界高度(模型显得过大/被裁时调大)。 */
    public static volatile float confWorldH = 2.2f;
    /** v4.9.8:本帧相机俯仰角(度;<=0 = 用基准 21.78°)。插件每帧按伪3D倾斜量+地图缩放写入。 */
    public static volatile float viewPitchDeg = -1f;
    /** v3.0.1:自动适配占比(模型最长边/高度占视口的比例;默认 0.85)。 */
    public static volatile float confFitPct = 0.85f;
    /** v3.0.1:true=按包围盒最大边适配(马/长模型不被裁切);false=只按高度(旧行为)。 */
    public static volatile boolean confFitByMax = false;
    /** 用户模型是否染色(插件按 tintMode 开关;true 时每帧按部队颜色改材质 diffuse)。 */
    public static volatile boolean confTint = true;
    /** v4.1.0:染色强度(0..1;与原贴图颜色的插值比例)。 */
    public static volatile float confTintStrength = 0.45f;
    /** v4.1.0:染色跳过的材质关键词(如 head/skin),避免脸被染成国家色。 */
    public static volatile String[] confTintSkip = {"head", "skin"};
    /** v4.1.1:脚底亮度(0..1;1=关闭)。底部顶点乘这个亮度→ 有「站地上」的接触感。 */
    public static volatile float confFeetDark = 0.5f;
    /** v4.1.1:接触阴影尺寸倍数(按模型包围盒自动算,这里再乘一个系数;1=默认)。 */
    public static volatile float confShadowScale = 1f;
    /** v4.1.4:阴影是否自动对准脚底(true=按模型底面几何定位;false=固定在世界原点)。 */
    public static volatile boolean confShadowAnchor = true;
    /** v4.1.2:阴影前后固定偏移(世界单位;0=不偏移;负值=向远处/屏幕上方)。 */
    public static volatile float confShadowShiftZ = 0f;
    /** v4.1.4:阴影前后比例偏移(占模型世界高度;模型越大偏移越大 → 不同尺寸观感一致)。 */
    public static volatile float confShadowShiftPct = 0f;
    /** v4.1.2:叠加的「接触核心」阴影缩放(相对主阴影;0=关;越小越贴脚)。 */
    public static volatile float confShadowCore = 0.55f;
    /** v4.9.2:核心阴影是否烘焙进贴图(true=阴影只画一层,省 1 次 draw call/模型;false=旧双层画法)。 */
    public static volatile boolean confShadowBake = true;
    /** v4.9.2:核心阴影是否已烘焙进阴影贴图(=true 时只画一层,省 1 次 draw call/模型)。 */
    private static boolean shadowCoreBaked = false;
    /** v4.9.4:接触阴影实例是否就绪(诊断;旧版依赖默认用户模型,模型加载失败时会静默变 false)。 */
    public static volatile boolean shadowReady = false;
    /** 用户模型自动适配(v1.4.1):按包围盒缩放至视口约 85%、贴地、居中(骑士类大模型必需)。 */
    public static volatile boolean confModelAutoFit = true;
    /** 手动缩放倍率(>0 时覆盖自动适配的缩放)。 */
    public static volatile float confModelScale = 0f;
    /** 额外 Y 偏移(世界单位;叠加在自动贴地之上)。 */
    public static volatile float confModelOffsetY = 0f;
    /** 基础朝向修正(度;模型背对行军方向时设 180)。 */
    public static volatile float confModelYaw = 0f;
    /** v1.8.0:船模文件名(不含扩展名;海面部队;<confShipModel>.g3dj/.g3db;""/"-"/"builtin"=不用船模)。 */
    public static volatile String confShipModel = "ship";
    /** v1.8.0:船模朝向修正(度;船头背对航向时设 180)。 */
    public static volatile float confShipYaw = 0f;
    /** v1.9.0:陆↔海过渡总时长(毫秒;“缩小→换模→放大”;≤0=禁用)。 */
    public static volatile float confMorphMs = 550f;
    /** v2.0.0:着色器骨骼上限(0=自动:按已加载模型的单部件最大骨骼数;拼装兵模 torso 实测 44)。 */
    public static volatile int confNumBones = 0;
    /** v2.0.0:按模型自动统计出的骨骼需求(init 时更新)。 */
    private static int detectedBones = 12;
    /** v2.0.0:本次批次实际使用的骨骼上限(诊断用)。 */
    public static volatile int lastNumBones = 12;
    /** v1.9.0:过渡状态表:兵牌 key → [上帧信息(frameId<<1|kind), 起始毫秒(-1=无过渡)]。 */
    private static final HashMap<String, long[]> morphMap = new HashMap<String, long[]>();
    /** 过渡最小缩放系数(缩到该比例时视口<4px 自动跳过绘制 = 视觉上“缩没再长出”)。 */
    private static final float MORPH_MIN = 0.02f;
    /** 插件数据目录(用户资源目录;可 null)。 */
    public static volatile String dataDirPath = null;
    /** 模型来源描述(诊断)。 */
    public static volatile String modelSource = "内置几何兵模";
    /** 资源附加说明(加载失败/模板/动画状态;诊断)。 */
    public static volatile String modelNote = "";
    /** 正交视口高(世界单位):兵模总高≈1.25,留边距防裁剪。 */
    private static float worldH = 2.2f;
    /** v4.9.8:基准俯角(度;= 旧版固定相机,视线中心下移后脚底位于窗口底边上方约 9.9%)。 */
    private static final float BASE_PITCH_DEG = 21.78f;
    /** v4.9.9:俯角缓存(每帧最多算一次 sin/cos;同帧多模型共享)。 */
    private static float rigAngleDeg = Float.NaN;
    private static float rigSn = 0f;
    private static float rigCs = 1f;

    /* ---- v1.4.1:用户模型自动适配(把任意尺寸/原点的模型换算进视口) ---- */
    /** 用户模型实例的基础变换(缩放+贴地+居中;每帧在它之上叠加朝向旋转;等比缩放与旋转可交换)。 */
    private static final Matrix4 userFit = new Matrix4();
    /** 适配缩放(程序化动作位移需按 1/scale 换算回世界单位)。 */
    private static float userFitScale = 1f;
    private static float userInvFitScale = 1f;
    /** 适配平移的 x/z(世界):阴影锚点 = (userFitDx, 0, userFitDz)(适配后脚底平面恒在 y=0)。 */
    private static float userFitDx = 0f;
    private static float userFitDz = 0f;

    private static boolean ready = false;
    private static ModelBatch batch;
    private static OrthographicCamera camera;
    private static final Model[] models = new Model[4];
    private static final ModelInstance[] instances = new ModelInstance[4];
    private static float animT = 0f;
    private static Environment env;

    /* ---- 用户模型多态实例 (rel 0..3) × (state 0停留/1移动/2战斗/3撤退) ---- */
    private static boolean usingUserModel = false;
    private static final ModelInstance[][] uInstances = new ModelInstance[4][4];
    private static final AnimationController[][] uAnims = new AnimationController[4][4];
    /** v4.9.1(B2):上一帧/本帧实际用到的 (rel×state) 位(bit = rel*4+st;-1=全量)。 */
    private static long uPrevMask = -1L;
    private static long uCurMask = -1L;
    private static long sPrevMask = -1L;
    private static long sCurMask = -1L;
    /** v4.1.0:默认用户模型的材质基准色。 */
    private static Array<Color> baseUser;
    /** v4.1.1:默认用户模型的阴影尺寸。 */
    private static float userShadowScale = 1f;
    /** v4.1.4:默认用户模型的阴影锚点(模型空间脚底接触带中心)。 */
    private static float userAnchorX = 0f;
    private static float userAnchorZ = 0f;
    /** v4.1.4:默认用户模型的阴影前后偏移(世界单位;绝对+按高度比例)。 */
    private static float userShadowShift = 0f;
    /** v4.4.0:默认用户模型的本体包围盒(模型空间;粒子锚点用)。 */
    private static float userBbMinX, userBbMinY, userBbMinZ, userBbMaxX, userBbMaxY, userBbMaxZ;
    /** v4.9.9:默认用户模型的环绕中心(世界坐标竖直中心)/竖直半高/水平半宽(含阴影盘;含任意朝向)。 */
    private static float userCenterY = 0.4f, userHalfV = 0.4f, userHalfD = 0.3f;
    /** 每个状态解析出的动画 id(null=该状态无动画 → 程序化动作)。 */
    private static final String[] stateAnimIds = new String[4];

    /* ---- v1.4:撤退白旗(仅内置模型;撤退状态渲染) ---- */
    private static Model retreatFlagModel;
    private static ModelInstance retreatFlagInst;

    /* ---- v1.4.8:用户模型脚下的柔和接触阴影(径向渐变半透明圆盘;内置模型自带几何阴影盘) ---- */
    private static Model userShadowModel;
    private static ModelInstance userShadowInst;

    /* ---- v1.8.0:船模(海面部队;<confShipModel>.g3dj/.g3db;缺文件=海面沿用陆军模型) ---- */
    private static boolean usingShipModel = false;
    /** 仅用于 dispose。 */
    private static Model shipModelRef;
    private static final ModelInstance[][] sInstances = new ModelInstance[4][4];
    private static final AnimationController[][] sAnims = new AnimationController[4][4];
    /** v4.1.0:船模的材质基准色。 */
    private static Array<Color> baseShip;
    /** v4.1.1:船模的阴影尺寸。 */
    private static float shipShadowScale = 1f;
    /** v4.1.4:船模的阴影锚点(模型空间底面中心)。 */
    private static float shipAnchorX = 0f;
    private static float shipAnchorZ = 0f;
    /** v4.1.4:船模的阴影前后偏移(世界单位)。 */
    private static float shipShadowShift = 0f;
    /** v4.4.0:船模的本体包围盒(模型空间;船炮锚点用)。 */
    private static float shipBbMinX, shipBbMinY, shipBbMinZ, shipBbMaxX, shipBbMaxY, shipBbMaxZ;
    /** v4.9.9:船模的环绕中心/竖直半高/水平半宽(世界坐标;含阴影盘)。 */
    private static float shipCenterY = 0.2f, shipHalfV = 0.2f, shipHalfD = 0.3f;
    /** 船模适配(按最大维度缩放:长船占视口约 85% 宽,不裁切)。 */
    private static final Matrix4 shipFit = new Matrix4();
    private static float shipFitScale = 1f;
    private static float shipInvFitScale = 1f;
    private static float shipFitDx = 0f;
    private static float shipFitDz = 0f;

    /* ================= v2.1.0:多 combo(拼装兵模)按需加载 ================= */

    /** 一个 combo(= 一个 g3dj)的全部渲染资源:4 关系色 × 4 状态实例 + 自动适配变换。 */
    static final class ModelSet {
        final String id;
        Model model;
        final ModelInstance[][] inst = new ModelInstance[4][4];
        final AnimationController[][] anim = new AnimationController[4][4];
        final String[] animIds = new String[4];
        final Matrix4 fit = new Matrix4();
        float fitScale = 1f;
        float invFitScale = 1f;
        float dx = 0f;
        float dz = 0f;
        /** v4.1.1:接触阴影尺寸(按模型水平尺寸自动算)。 */
        float shadowScale = 1f;
        /** v4.1.4:阴影锚点(模型空间脚底接触带中心;旋转/适配前)。 */
        float anchorX = 0f;
        float anchorZ = 0f;
        /** v4.1.4:阴影前后偏移(世界单位;绝对偏移 + 按高度比例偏移)。 */
        float shadowShift = 0f;
        /** v4.9.9:环绕中心(世界坐标竖直中心) / 竖直半高 / 水平半宽(含阴影盘与任意朝向)。 */
        float centerY = 0.4f;
        float halfV = 0.4f;
        float halfD = 0.3f;
        /** v4.3.0:装配 animSet(assembly.combos[*].animSet 第一个)与对应武器事件表(null=无)。 */
        String animSet = null;
        WeaponProfile weapon = null;
        /** v4.4.0:借用了哪个 combo 的动画(如 `foot_eastasia_medieval(bow)`;null=用自身)。 */
        String animDonor = null;
        /** v4.4.0:battle 相位实例(rel × PHASE);sh 为 null = 该 combo 无战斗动画。 */
        final ModelInstance[][] battleInst = new ModelInstance[4][PHASE];
        final AnimationController[][] battleAnim = new AnimationController[4][PHASE];
        final float[] battlePrev = new float[4 * PHASE];
        final long[] battleUpd = new long[4 * PHASE];
        final int[] battleFired = new int[4 * PHASE];
        /** v4.4.0:本体包围盒(模型空间;粒子锚点/船侧用)。 */
        float bbMinX, bbMinY, bbMinZ, bbMaxX, bbMaxY, bbMaxZ;
        /** v4.7.3:枪口(挂件网格在挂点骨局部空间的远端顶点;粒子锚点用)。 */
        boolean hasMuzzle = false;
        String muzzleBone = null;
        float muzzleX, muzzleY, muzzleZ;
        long lastUse = 0L;
        /** v4.9.1(B2):上一帧/本帧实际用到的 (rel×state) 位(bit = rel*4+st;-1=全量)。 */
        long prevMask = -1L;
        long curMask = -1L;
        /** v4.1.0:材质基准色(下标与 inst[0][0].materials 对齐;每帧染色/恢复用)。 */
        Array<Color> baseDiff;
        /** v4.0.0:运行时挂载的挂件 Model(随本套资源一起释放)。 */
        final ArrayList<Model> extras = new ArrayList<Model>();

        ModelSet(String id) {
            this.id = id;
        }
    }

    /** combo id → 已加载模型集(默认模型/船模仍走上面的全局资源,不进这里)。 */
    private static final HashMap<String, ModelSet> comboCache = new HashMap<String, ModelSet>();
    /**
     * v4.9.6:失败记录 = key → {尝试次数, 最后一次失败帧}。
     * 旧版是永久 HashSet:加载期任何一次瞬时失败(资源刚就绪/异常)都会让该文明
     * 永远画兜底模型,只有重启游戏/回前台(invalidate 清缓存)才恢复。
     * 现在按 comboRetryFrames 帧后自动重试,最多 comboRetryMax 次。
     */
    private static final HashMap<String, int[]> comboFailed = new HashMap<String, int[]>();
    /** 待加载队列(每帧最多加载 1 个,避免一次性卡顿)。 */
    private static final ArrayList<String> comboQueue = new ArrayList<String>();
    /** 额外 combo 资源上限(LRU 淘汰;每个 combo = 1 个 Model + 16 个实例)。
     *  v4.9.5:10 → 20。后期战场同屏会有 12~20 种 combo(文化×宗教×科技×兵种),
     *  容量小于需求时会不停「淘汰→重载」(每次 80~100ms,每秒几次)= 兵牌移动时明显卡顿。 */
    public static volatile int maxComboSets = 20;
    /** 绝对上限(防手滑配太大吃显存)。 */
    public static final int MAX_COMBO_SETS_CAP = 64;
    private static long comboFrame = 0L;

    /* v4.9.4:combo / 船模加载耗时统计(诊断;插件每 900 帧读取后清零) */
    public static volatile int comboLoads = 0;
    public static volatile long comboLoadMsSum = 0L;
    public static volatile long comboLoadMsMax = 0L;

    public static void resetComboLoadStats() {
        comboLoads = 0;
        comboLoadMsSum = 0L;
        comboLoadMsMax = 0L;
    }

    /* ---- v4.9.6:失败重试 / 加载预算 ---- */
    /** 失败后重试间隔基数(帧;默认 600≈10s,实际间隔 = 基数 × (失败次数+1),最多 8 倍)。 */
    public static volatile int comboRetryFrames = 600;
    /** 最多重试次数(0 = 不限,一直退避重试;>0 = 达到后视为永久失败)。
     *  v4.9.7:默认改为不限 —— 瞬时失败(资源刚就绪/GL 抖动/内存紧张)必须能自愈,
     *  旧版“永久失败”会让兵种永远画无枪兜底模型,只有重启游戏才恢复。 */
    public static volatile int comboRetryMax = 0;
    /** 单帧加载耗时预算(ms;一次加载超预算后暂停若干帧,避免连续掉帧)。 */
    public static volatile float comboLoadBudgetMs = 6f;
    /** 超预算后要跳过的帧数。 */
    private static int comboSleepFrames = 0;

    /** 该 key 现在是否应视为「失败,别再排队」(永久失败或还在冷却期)。 */
    private static boolean comboFailedNow(String key) {
        int[] v = comboFailed.get(key);
        if (v == null) {
            return false;
        }
        if (comboRetryMax > 0 && v[0] >= comboRetryMax) {
            return true;
        }
        // v4.9.7:退避间隔随失败次数递增(最多 8 倍);冷却结束后会自动重试用
        int grow = v[0] + 1;
        if (grow > 8) {
            grow = 8;
        }
        long wait = (long) comboRetryFrames * (long) grow;
        return comboFrame - (long) v[1] < wait;
    }

    /** 记录一次失败(次数 + 帧),供冷却/重试判定。 */
    private static void noteComboFailure(String key) {
        int[] v = comboFailed.get(key);
        if (v == null) {
            v = new int[]{0, 0};
            comboFailed.put(key, v);
        }
        v[0] = v[0] + 1;
        v[1] = (int) comboFrame;
    }

    /** v4.9.6:把一次超预算的加载摊开到后续几帧(减少连续掉帧)。 */
    private static void applyLoadBudget(long elapsedMs) {
        float budget = comboLoadBudgetMs;
        if (budget <= 0f || elapsedMs <= budget) {
            return;
        }
        int skip = (int) (elapsedMs / Math.max(1f, budget));
        if (skip > 6) {
            skip = 6;
        }
        if (skip > comboSleepFrames) {
            comboSleepFrames = skip;
        }
    }

    /* ---- v4.5.0:船模 + 挂件(文明 shipSelect / 船模挂件;适配按长边) ---- */
    /** `船模id|挂件签名` → 已加载模型集(基础船模仍走 sInstances)。 */
    private static final HashMap<String, ModelSet> shipSetCache = new HashMap<String, ModelSet>();
    private static final HashSet<String> shipSetFailed = new HashSet<String>();
    private static final ArrayList<String> shipSetQueue = new ArrayList<String>();
    /** v4.9.5:船模组合缓存上限(4 → 8,可配 shipCache)。 */
    public static volatile int maxShipSets = 8;
    /** 绝对上限。 */
    public static final int MAX_SHIP_SETS_CAP = 24;

    /* ---- v4.9.6:贴图共享缓存(多个 combo/挂件用同一张 png 时不再重复解码) ---- */
    /** 绝对路径 → 带引用计数的贴图。 */
    private static final HashMap<String, SharedTexture> texCache = new HashMap<String, SharedTexture>();

    /**
     * 引用计数贴图:Model.dispose() 只减引用,最后一个使用者释放时才真正 dispose。
     * 旧版每次 combo 加载都 new Texture(...) 重新解码同一张 png,千卡来源之一。
     */
    static final class SharedTexture extends Texture {
        final String path;
        int refs = 1;

        SharedTexture(FileHandle fh, String path) {
            super(fh, false);
            this.path = path;
        }

        @Override
        public void dispose() {
            refs--;
            if (refs <= 0) {
                texCache.remove(path);
                super.dispose();
            }
        }
    }

    /* ---- v4.0.0:运行时挂件(武器/配饰) ---- */
    /** v4.2.0:挂件目录(相对 dataDir;新结构 models/parts;旧 parts/ 仍兼容)。 */
    public static volatile String confPartsDir = "models/parts";
    /** 需要剥离静态件的 combo 前缀(如 "foot_"),剥离后由运行时挂件补上(null/空 = 不剥离)。 */
    public static volatile String[] confStripCombos = null;
    /** 静态件识别关键词(小写包含匹配 meshPartId/materialId,如 weapon/shield)。 */
    public static volatile String[] confStripTokens = null;
    /** 挂件索引 parts/index.json 解析结果(name → 条目)。 */
    private static final HashMap<String, JsonValue> partsIndex = new HashMap<String, JsonValue>();
    private static boolean partsIndexLoaded = false;
    /** v4.9.7:索引读取失败时的重试计数/重试帧(旧版一进函数就置 loaded=true,
     *  首次读取失败 → 整个进程所有兵模都“只有持枪动作没有枪”)。 */
    private static int partsIndexTries = 0;
    private static long partsIndexRetryFrame = -1L;
    private static String partsIndexNote = "";

    private static int depthBits = -1;
    private static String shaderSource = "(未初始化)";
    private static boolean usingEmbedded = false;
    private static boolean pinnedEmbedded = false;

    /* ================= v4.3.0:动画事件 / 最小状态机(anim_events.json) ================= */
    /** 单个攻击事件(EU5 particle_action 的绝对秒)。 */
    static final class AttackEvent {
        final float t;
        final String preset;
        final String place;
        final boolean atGrip;

        AttackEvent(float t, String preset, String place, boolean atGrip) {
            this.t = t;
            this.preset = preset;
            this.place = place;
            this.atGrip = atGrip;
        }
    }

    /** 一个武器的攻击事件表(按 animSet 归类)。 */
    static final class WeaponProfile {
        String id = "";
        String[] animSets = new String[0];
        AttackEvent[] events = new AttackEvent[0];
        float animDuration = 0f;
        float stateDuration = 0f;
        boolean loop = true;
    }

    /** animSet → 武器事件表(多武器共用同一 animSet 时先到先得)。 */
    private static final HashMap<String, WeaponProfile> profilesByAnimSet = new HashMap<String, WeaponProfile>();
    /** 预设名 → presets 节点(粒子接入时按需读)。 */
    private static final HashMap<String, JsonValue> presetNodes = new HashMap<String, JsonValue>();
    /** 最小状态机的配置节点(预留;transitions/when 见 anim_events.json)。 */
    private static JsonValue stateMachineNode;
    /** 粒子/挂点开关与尺度(粒子接入时使用)。 */
    public static volatile boolean confParticles = true;
    public static volatile float confParticleScale = 1f;
    public static volatile float confParticleRate = 1f;
    public static volatile int confParticleMax = 256;
    public static volatile String confLocatorFallback = "weapon_tip";
    /** 诊断:动画事件表装载结果。 */
    public static volatile String animEventsNote = "";
    /** combo → animSet 列表(逗号分隔;assembly.combos)。 */
    private static final HashMap<String, String> comboAnimSets = new HashMap<String, String>();
    /** combo → 类别(foot/mounted/artillery/ship;assembly.combos)。 */
    private static final HashMap<String, String> comboGroups = new HashMap<String, String>();
    /** v4.4.0:combo → rig(foot/mounted/artillery…;assembly.combos.rig / rigs[0])。 */
    private static final HashMap<String, String> comboRigs = new HashMap<String, String>();
    /** v4.4.0:"<rig>.<武器族>" → donor combo(assembly.animDonors)。 */
    private static final HashMap<String, String> animDonors = new HashMap<String, String>();
    /** donor combo → 原始动画数据缓存(不建网格/贴图)。 */
    private static final HashMap<String, Array<ModelAnimation>> donorAnimData =
            new HashMap<String, Array<ModelAnimation>>();

    /* ================= v4.4.0:攻击粒子(CPU 池 + 屏幕空间 billboard) ================= */
    /** 攻击相位桶数(仅 battle 状态;同兵种分成 6 组错峰开火)。 */
    static final int PHASE = 6;

    /** 单个粒子(池化,禁止每帧 new)。 */
    private static final class Fx {
        float x, y, z, vx, vy, vz;
        float age, life;
        float s0, s1;
        float r0, g0, b0, a0, r1, g1, b1, a1;
        float gravity, drag;
        int blend;      // 0=alpha 1=add
        int owner;      // 部队下标(只在自己视口里绘制)
        boolean alive;
    }

    private static Fx[] fxPool;
    private static int fxCount = 0;
    private static Texture fxTex;
    private static SpriteBatch fxBatch;
    private static final Matrix4 fxProj = new Matrix4();
    private static final Vector3 fxTmp = new Vector3();
    private static final Vector3 fxA = new Vector3();
    private static final Vector3 fxB = new Vector3();
    private static final Matrix4 fxM = new Matrix4();
    /** v4.4.0:battle 短锁存(毫秒;anim_events.battleHoldMs,0=关)。 */
    public static volatile float confBattleHoldMs = 1200f;
    /** 部队 key → 最后一次看到战斗状态的时间。 */
    private static final HashMap<String, Long> battleHold = new HashMap<String, Long>();

    /* ---- v4.4.0:battle 相位实例(同兵种不再齐步) ---- */
    private static final ModelInstance[][] uBattleInst = new ModelInstance[4][PHASE];
    private static final AnimationController[][] uBattleAnim = new AnimationController[4][PHASE];
    private static final float[] uBattlePrev = new float[4 * PHASE];
    private static final long[] uBattleUpd = new long[4 * PHASE];
    private static final int[] uBattleFired = new int[4 * PHASE];

    private static final ModelInstance[][] sBattleInst = new ModelInstance[4][PHASE];
    private static final AnimationController[][] sBattleAnim = new AnimationController[4][PHASE];
    private static final float[] sBattlePrev = new float[4 * PHASE];
    private static final long[] sBattleUpd = new long[4 * PHASE];
    private static final int[] sBattleFired = new int[4 * PHASE];
    /** 船模各状态动画 id(建相位实例用)。 */
    private static final String[] shipAnimIds = new String[4];
    /** 默认用户模型 / 船模的武器事件表(init 时解析)。 */
    private static WeaponProfile userProfile;
    private static WeaponProfile shipProfile;

    private static final IntBuffer VP = BufferUtils.newIntBuffer(4);
    private static final IntBuffer TMP1 = BufferUtils.newIntBuffer(1);

    /* v4.9.4:本帧已绘制模型的屏幕矩形(用于判断是否需要逐模型清深度) */
    private static final int[] drawnRects = new int[128 * 4];
    private static int drawnCount = 0;

    /** 本帧是否已有绘制矩形与 (x,y,w,h) 相交(1px 容差)。 */
    private static boolean overlapsDrawn(int x, int y, int w, int h) {
        for (int i = 0; i < drawnCount; i++) {
            int bx = drawnRects[i * 4];
            int by = drawnRects[i * 4 + 1];
            int bw = drawnRects[i * 4 + 2];
            int bh = drawnRects[i * 4 + 3];
            if (x < bx + bw + 1 && bx < x + w + 1 && y < by + bh + 1 && by < y + h + 1) {
                return true;
            }
        }
        return false;
    }

    private G3DArmyMin() {
    }

    public static boolean isReady() {
        return ready && batch != null && (usingUserModel ? uInstances[0][0] != null : instances[0] != null);
    }

    /** 诊断:已缓存的 combo 模型集数量(LRU 上限 maxComboSets)。 */
    public static int comboCacheCount() {
        return comboCache.size();
    }

    /** 诊断:已缓存的「船模+挂件」模型集数量(LRU 上限 maxShipSets)。 */
    public static int shipCacheCount() {
        return shipSetCache.size();
    }

    /* ================= v4.3.0:装配 / 动画事件的对外入口 ================= */

    /** 装配配置:combo → animSet(逗号分隔)与 group(artillery/ship 当伪 animSet)。 */
    public static void setComboAnimSets(HashMap<String, String> sets, HashMap<String, String> groups) {
        comboAnimSets.clear();
        if (sets != null) {
            comboAnimSets.putAll(sets);
        }
        comboGroups.clear();
        if (groups != null) {
            comboGroups.putAll(groups);
        }
    }

    /** v4.4.0:装配 rig 表(combo → foot/mounted/…)。 */
    public static void setComboRigs(HashMap<String, String> rigs) {
        comboRigs.clear();
        if (rigs != null) {
            comboRigs.putAll(rigs);
        }
    }

    /** v4.4.0:武器 → 动画 donor 表("<rig>.<武器族>" → donor combo)。 */
    public static void setAnimDonors(HashMap<String, String> donors) {
        animDonors.clear();
        if (donors != null) {
            animDonors.putAll(donors);
        }
    }

    /** 该 animSet 是否已有攻击事件表(装配骑乘模型时优先选带事件的主集)。 */
    public static boolean hasWeaponProfile(String animSet) {
        return animSet != null && profilesByAnimSet.containsKey(animSet);
    }

    /**
     * v4.3.0:解析 anim_events.json → Java 结构。
     * weapons[*].animSets 反查成 animSet → profile;presets 原样缓存;stateMachine 留节点。
     */
    public static void setAnimEvents(JsonValue root, boolean enabled, float scale, float rate,
                                     int max, String fallback) {
        profilesByAnimSet.clear();
        presetNodes.clear();
        stateMachineNode = null;
        confParticles = enabled;
        confParticleScale = scale > 0f ? scale : 1f;
        confParticleRate = rate > 0f ? rate : 1f;
        confParticleMax = Math.max(16, Math.min(1024, max));
        confLocatorFallback = fallback == null ? "weapon_tip" : fallback;
        confBattleHoldMs = Math.max(0f, Math.min(5000f, root == null ? 1200f : root.getFloat("battleHoldMs", 1200f)));
        battleHold.clear();
        holdBattleCache.clear();   // v4.9.2:事件表变了 → 判定缓存作废
        animEventsNote = "动画事件未加载";
        if (root == null) {
            return;
        }
        try {
            int wcount = 0;
            JsonValue weapons = root.get("weapons");
            if (weapons != null) {
                for (JsonValue w = weapons.child; w != null; w = w.next) {
                    if (w.name == null || w.name.startsWith("_")) {
                        continue;
                    }
                    wcount++;
                    WeaponProfile p = new WeaponProfile();
                    p.id = w.name;
                    ArrayList<String> sets = new ArrayList<String>();
                    JsonValue as = w.get("animSets");
                    if (as != null) {
                        if (as.isArray()) {
                            for (JsonValue v = as.child; v != null; v = v.next) {
                                if (v.isString() && v.asString() != null && v.asString().length() > 0) {
                                    sets.add(v.asString());
                                }
                            }
                        } else if (as.isString() && as.asString() != null) {
                            sets.add(as.asString());
                        }
                    }
                    p.animSets = sets.toArray(new String[sets.size()]);
                    p.animDuration = w.getFloat("animDuration", 0f);
                    p.stateDuration = w.getFloat("stateMachineDuration", 0f);
                    p.loop = w.getBoolean("loop", true);
                    ArrayList<AttackEvent> evs = new ArrayList<AttackEvent>();
                    JsonValue ev = w.get("events");
                    if (ev != null) {
                        for (JsonValue e = ev.child; e != null; e = e.next) {
                            float t = e.getFloat("t", -1f);
                            String preset = e.getString("preset", null);
                            if (t < 0f || preset == null || preset.length() == 0) {
                                continue;
                            }
                            evs.add(new AttackEvent(t, preset, e.getString("place", null),
                                    e.getBoolean("atGrip", false)));
                        }
                    }
                    p.events = evs.toArray(new AttackEvent[evs.size()]);
                    for (int i = 0; i < p.animSets.length; i++) {
                        if (!profilesByAnimSet.containsKey(p.animSets[i])) {
                            profilesByAnimSet.put(p.animSets[i], p);
                        }
                    }
                }
            }
            JsonValue presets = root.get("presets");
            if (presets != null) {
                for (JsonValue v = presets.child; v != null; v = v.next) {
                    if (v.name != null && !v.name.startsWith("_")) {
                        presetNodes.put(v.name, v);
                    }
                }
            }
            stateMachineNode = root.get("stateMachine");
            animEventsNote = "武器=" + wcount + " animSet=" + profilesByAnimSet.size()
                    + " 预设=" + presetNodes.size()
                    + (stateMachineNode != null ? " 状态机=有" : "");
        } catch (Throwable t) {
            animEventsNote = "动画事件解析失败: " + t;
        }
    }

    /** 该组合使用的武器事件表:assembly.animSet 逐个找,再退回 group(artillery/ship)。 */
    static WeaponProfile weaponProfileFor(String comboId) {
        if (comboId == null) {
            return null;
        }
        String sets = comboAnimSets.get(comboId);
        if (sets != null && sets.length() > 0) {
            String[] arr = sets.split(",");
            for (int i = 0; i < arr.length; i++) {
                String k = arr[i] == null ? null : arr[i].trim();
                if (k != null && k.length() > 0) {
                    WeaponProfile p = profilesByAnimSet.get(k);
                    if (p != null) {
                        return p;
                    }
                }
            }
        }
        String g = comboGroups.get(comboId);
        if (g != null && g.length() > 0) {
            return profilesByAnimSet.get(g);
        }
        return null;
    }

    /* ================= v4.4.0:武器 → 动画(同 rig 借用) ================= */

    /** 挂件签名 → 武器族(spear/sword/bow/musket/crossbow;null=没挂件/认不出)。 */
    static String weaponFamilyFromSig(String sig) {
        if (sig == null || sig.length() == 0) {
            return null;
        }
        String s = sig.toLowerCase();
        if (s.contains("crossbow")) {
            return "crossbow";
        }
        if (s.contains("musket") || s.contains("flintlock") || s.contains("matchlock")
                || s.contains("arquebus") || s.contains("handgonne") || s.contains("handgonna")
                || s.contains("pistol")) {
            return "musket";
        }
        if (s.contains("bow")) {
            return "bow";
        }
        if (s.contains("spear") || s.contains("pike") || s.contains("lance") || s.contains("halberd")) {
            return "spear";
        }
        if (s.contains("sword") || s.contains("blade") || s.contains("saber") || s.contains("rapier")
                || s.contains("axe") || s.contains("mace") || s.contains("knife") || s.contains("katar")) {
            return "sword";
        }
        return null;
    }

    /** 该 combo 在给定挂件下要借用的动画 donor(null=不用借)。 */
    private static String animDonorFor(String comboId, String attachSig) {
        String fam = weaponFamilyFromSig(attachSig);
        if (fam == null) {
            return null;
        }
        // combo 自带同族动画(如 arab_medieval 本身就是 sword)→ 不用借
        String own = comboAnimSets.get(comboId);
        if (own != null) {
            String[] parts = own.split(",");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null && fam.equals(parts[i].trim())) {
                    return null;
                }
            }
        }
        String rig = comboRigs.get(comboId);
        if (rig == null || rig.length() == 0) {
            return null;
        }
        String d = animDonors.get(rig + "." + fam);
        if (d == null || d.length() == 0 || d.equals(comboId)) {
            return null;
        }
        return d;
    }

    /** 武器族对应的事件表(与借来的动画一致);认不出 → 退回 combo 自身的 animSet 表。 */
    static WeaponProfile weaponProfileFor(String comboId, String attachSig) {
        String fam = weaponFamilyFromSig(attachSig);
        if (fam != null) {
            WeaponProfile p = profilesByAnimSet.get(fam);
            if (p != null) {
                return p;
            }
        }
        return weaponProfileFor(comboId);
    }

    private static <T> float lastKeyTime(Array<ModelNodeKeyframe<T>> a) {
        return (a == null || a.size == 0) ? 0f : a.get(a.size - 1).keytime;
    }

    private static float animMaxTime(ModelNodeAnimation m) {
        float d = lastKeyTime(m.translation);
        d = Math.max(d, lastKeyTime(m.rotation));
        d = Math.max(d, lastKeyTime(m.scaling));
        return d;
    }

    private static Array<NodeKeyframe<Vector3>> convVec(Array<ModelNodeKeyframe<Vector3>> src) {
        if (src == null || src.size == 0) {
            return null;
        }
        Array<NodeKeyframe<Vector3>> out = new Array<NodeKeyframe<Vector3>>(src.size);
        for (int i = 0; i < src.size; i++) {
            ModelNodeKeyframe<Vector3> k = src.get(i);
            if (k == null || k.value == null) {
                continue;
            }
            out.add(new NodeKeyframe<Vector3>(k.keytime, new Vector3(k.value)));
        }
        return out.size == 0 ? null : out;
    }

    private static Array<NodeKeyframe<Quaternion>> convQuat(Array<ModelNodeKeyframe<Quaternion>> src) {
        if (src == null || src.size == 0) {
            return null;
        }
        Array<NodeKeyframe<Quaternion>> out = new Array<NodeKeyframe<Quaternion>>(src.size);
        for (int i = 0; i < src.size; i++) {
            ModelNodeKeyframe<Quaternion> k = src.get(i);
            if (k == null || k.value == null) {
                continue;
            }
            out.add(new NodeKeyframe<Quaternion>(k.keytime, new Quaternion(k.value)));
        }
        return out.size == 0 ? null : out;
    }

    /**
     * v4.4.0:把 donor combo 的动画绑到 target 模型的骨节点上(只读 ModelData,不建网格/贴图)。
     * 骨骼集合一致才生效;节点缺失的通道自动跳过,全空则该动画丢弃。
     */
    private static Animation[] buildDonorAnims(String donorId, Model target) {
        Array<ModelAnimation> list = donorAnimData.get(donorId);
        if (list == null) {
            File f = findModelFile(donorId);
            if (f == null) {
                return null;
            }
            try {
                boolean ub = f.getName().toLowerCase().endsWith(".g3db");
                G3dModelLoader loader = ub
                        ? new G3dModelLoader(new UBJsonReader())
                        : new G3dModelLoader(new JsonReader());
                ModelData md = loader.loadModelData(new FileHandle(f), null);
                if (md == null || md.animations == null) {
                    return null;
                }
                list = md.animations;
                donorAnimData.put(donorId, list);
            } catch (Throwable t) {
                return null;
            }
        }
        ArrayList<Animation> out = new ArrayList<Animation>();
        for (int i = 0; i < list.size; i++) {
            ModelAnimation ma = list.get(i);
            if (ma == null || ma.id == null || ma.nodeAnimations == null) {
                continue;
            }
            Animation an = new Animation();
            an.id = ma.id;
            an.nodeAnimations = new Array<NodeAnimation>();
            float dur = 0f;
            for (int k = 0; k < ma.nodeAnimations.size; k++) {
                ModelNodeAnimation mna = ma.nodeAnimations.get(k);
                if (mna == null || mna.nodeId == null) {
                    continue;
                }
                Node node = target.getNode(mna.nodeId);
                if (node == null) {
                    continue;   // 该模型没有这根骨 → 跳过这条通道
                }
                NodeAnimation na = new NodeAnimation();
                na.node = node;
                na.translation = convVec(mna.translation);
                na.rotation = convQuat(mna.rotation);
                na.scaling = convVec(mna.scaling);
                an.nodeAnimations.add(na);
                dur = Math.max(dur, animMaxTime(mna));
            }
            if (an.nodeAnimations.size == 0) {
                continue;
            }
            an.duration = dur;
            out.add(an);
        }
        return out.isEmpty() ? null : out.toArray(new Animation[out.size()]);
    }

    /** 挂了武器 → 用该武器的动画替换模型自带动画(必须在建 ModelInstance 之前调用)。 */
    private static String applyWeaponAnimOverride(Model m, String comboId, String attachSig) {
        String donor = animDonorFor(comboId, attachSig);
        if (donor == null) {
            return null;
        }
        Animation[] anims = buildDonorAnims(donor, m);
        if (anims == null || anims.length == 0) {
            return null;
        }
        m.animations.clear();
        for (int i = 0; i < anims.length; i++) {
            m.animations.add(anims[i]);
        }
        return donor + "(" + weaponFamilyFromSig(attachSig) + ")";
    }

    /* ================= v4.4.0:battle 相位 / 事件跨越 ================= */

    /** 取模型动画时长(找不到返回 0)。 */
    private static float animDuration(Model m, String id) {
        try {
            Animation a = m.getAnimation(id);
            return a == null ? 0f : a.duration;
        } catch (Throwable t) {
            return 0f;
        }
    }

    /** 稳定相位:同一支部队每帧得到同一个桶(hash,不用渲染下标)。 */
    private static int phaseOf(String[] keys, int i) {
        String k = (keys != null && i < keys.length) ? keys[i] : null;
        int h = k != null ? k.hashCode() : (i * 31 + 7);
        return Math.floorMod(h, PHASE);
    }

    /** v4.7.2:武器是否有 t>0.05s 的延迟事件 —— 决定战斗结束后要不要短暂锁存攻击动画。 */
    private static boolean hasDelayedAttackEvents(WeaponProfile p) {
        if (p == null || p.events == null) {
            return false;
        }
        for (int i = 0; i < p.events.length; i++) {
            AttackEvent e = p.events[i];
            if (e != null && e.t > 0.05f) {
                return true;
            }
        }
        return false;
    }

    /** 稳定 owner:与渲染下标无关(候选排序变了粒子也不会串到别的视口)。 */
    private static int fxOwner(String[] keys, int i) {
        String k = (keys != null && i < keys.length) ? keys[i] : null;
        return k != null ? k.hashCode() : (i * 31 + 7);
    }

    /** 时间点是否被本帧跨过(处理循环回绕)。 */
    private static boolean crossed(float prev, float now, float t, float dur) {
        if (dur <= 0f) {
            return false;
        }
        if (now >= prev) {
            return prev <= t && now > t;
        }
        return (prev <= t && t <= dur) || (0f <= t && t < now);
    }

    /**
     * 推进一个 battle 相位控制器(每桶每帧只 update 一次),返回本帧跨过的事件位掩码。
     * 同一桶的其它部队直接复用该掩码 → 不会只有第一支部队触发。
     */
    private static int tickBattle(AnimationController c, float[] prevArr, long[] updArr, int[] firedArr,
                                  int idx, WeaponProfile prof, float dt) {
        if (c == null) {
            return 0;
        }
        if (updArr[idx] == comboFrame) {
            return firedArr[idx];
        }
        updArr[idx] = comboFrame;
        try {
            c.update(dt);
        } catch (Throwable ignored) {
        }
        float cur = 0f;
        float dur = 0f;
        try {
            if (c.current != null) {
                cur = c.current.time;
                dur = c.current.duration;
            }
        } catch (Throwable ignored) {
        }
        float prev = prevArr[idx];
        // 首次 tick（prev=-1）按 0 处理，只补 t=0 的事件：这样进入战斗第一帧就能看到
        // 枪口粒子，又不会把随机起始相位之前的历史事件一次性补发。
        boolean firstTick = prev < 0f;
        if (firstTick) {
            prev = 0f;
        }
        int mask = 0;
        if (prof != null && dur > 0f && prof.events.length > 0) {
            for (int e = 0; e < prof.events.length && e < 32; e++) {
                float t = prof.events[e].t;
                if (t >= dur || (firstTick && t > 1e-4f)) {
                    continue;
                }
                if (crossed(prev, cur, t, dur)) {
                    mask |= (1 << e);
                }
            }
        }
        firedArr[idx] = mask;
        prevArr[idx] = cur;
        return mask;
    }

    /* ================= v4.4.0:粒子池 ================= */

    /** 创建粒子贴图与 SpriteBatch(首次使用;需 GL 线程)。 */
    private static void ensureParticles() {
        if (fxBatch != null && fxTex != null) {
            return;
        }
        try {
            final int S = 32;
            Pixmap pm = new Pixmap(S, S, Pixmap.Format.RGBA8888);
            pm.setColor(1f, 1f, 1f, 0f);
            pm.fill();
            for (int y = 0; y < S; y++) {
                for (int x = 0; x < S; x++) {
                    float dx = (x - (S - 1) * 0.5f) / (S * 0.5f);
                    float dy = (y - (S - 1) * 0.5f) / (S * 0.5f);
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float a = 1f - d;
                    if (a <= 0f) {
                        continue;
                    }
                    pm.setColor(1f, 1f, 1f, a * a);
                    pm.drawPixel(x, y);
                }
            }
            fxTex = new Texture(pm);
            pm.dispose();
            fxTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            fxTex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            fxBatch = new SpriteBatch();
        } catch (Throwable ignored) {
        }
    }

    private static void freeParticles() {
        try {
            if (fxBatch != null) {
                fxBatch.dispose();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (fxTex != null) {
                fxTex.dispose();
            }
        } catch (Throwable ignored) {
        }
        fxBatch = null;
        fxTex = null;
        fxPool = null;
        fxCount = 0;
    }

    /** 取一个粒子槽(池满时复用最老的)。 */
    private static Fx fxGet(int owner) {
        int max = Math.max(16, confParticleMax);
        if (fxPool == null || fxPool.length != max) {
            fxPool = new Fx[max];
            for (int i = 0; i < max; i++) {
                fxPool[i] = new Fx();
            }
            fxCount = 0;
        }
        Fx p;
        if (fxCount < fxPool.length) {
            p = fxPool[fxCount++];
        } else {
            p = fxPool[0];
            fxPool[0] = fxPool[fxCount - 1];
            fxPool[fxCount - 1] = p;
        }
        p.alive = true;
        p.owner = owner;
        return p;
    }

    /** 每帧推进一次全部粒子。 */
    private static void updateParticles(float dt) {
        if (fxCount <= 0) {
            return;
        }
        int i = 0;
        while (i < fxCount) {
            Fx p = fxPool[i];
            p.age += dt;
            if (!p.alive || p.age >= p.life) {
                fxCount--;
                fxPool[i] = fxPool[fxCount];
                fxPool[fxCount] = p;
                continue;
            }
            if (p.drag > 0f) {
                float k = 1f - p.drag * dt;
                if (k < 0f) {
                    k = 0f;
                }
                p.vx *= k;
                p.vy *= k;
                p.vz *= k;
            }
            p.vy -= p.gravity * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            i++;
        }
    }

    /** 十六进制颜色(#RRGGBB / RRGGBB)→ 0xRRGGBB(解析失败返回 def)。 */
    private static int hexRgb(String s, int def) {
        if (s == null) {
            return def;
        }
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6) {
            return def;
        }
        try {
            return Integer.parseInt(t, 16);
        } catch (Throwable e) {
            return def;
        }
    }

    /** 按 anim_events.presets 发射一簇粒子(位置世界坐标、方向单位向量)。 */
    private static void spawnPreset(String name, float ox, float oy, float oz,
                                    float dx, float dy, float dz, int owner) {
        if (!confParticles || name == null) {
            return;
        }
        JsonValue p = presetNodes.get(name);
        if (p == null) {
            return;
        }
        JsonValue burst = p.get("burst");
        if (burst == null || burst.child == null) {
            return;
        }
        ensureParticles();
        float rate = confParticleRate > 0f ? confParticleRate : 1f;
        float scale = confParticleScale > 0f ? confParticleScale : 1f;
        for (JsonValue e = burst.child; e != null; e = e.next) {
            String kind = e.getString("kind", "smoke");
            int count = Math.max(1, Math.round(e.getInt("count", 1) * rate));
            float life = e.getFloat("life", 0.3f);
            if (life <= 0f) {
                life = 0.01f;
            }
            float s0 = e.getFloat("size0", 0.02f) * scale;
            float s1 = e.getFloat("size1", 0.02f) * scale;
            int c0 = hexRgb(e.getString("color0", null), 0xFFFFFF);
            int c1 = hexRgb(e.getString("color1", null), c0);
            int blend = "add".equalsIgnoreCase(e.getString("blend", "alpha")) ? 1 : 0;
            float gravity = e.getFloat("gravity", "spark".equals(kind) ? 7f : 0f);
            float drag = e.getFloat("drag", "smoke".equals(kind) ? 1.2f : 0f);
            float fwd = e.getFloat("forward", 0f);
            float up = e.getFloat("up", 0f);
            float spread = e.getFloat("spread", 0.04f);
            float posSpread = e.getFloat("posSpread", 0.02f);
            boolean spark = "spark".equals(kind);
            boolean smoke = "smoke".equals(kind) || "ship_wake".equals(kind);
            for (int k = 0; k < count; k++) {
                Fx f = fxGet(owner);
                float r1 = (float) Math.random() - 0.5f;
                float r2 = (float) Math.random() - 0.5f;
                float r3 = (float) Math.random() - 0.5f;
                // v4.7.6:与调试器 ParticleSystem.emit 语义一致 —— forward/up 是「初速度」,
                // 不是出生点偏移。旧版把 forward/up 加到位置上,导致粒子整体前移约半个枪模长度。
                f.x = ox + r1 * posSpread * 2f;
                f.y = oy + r2 * posSpread * 2f;
                f.z = oz + r3 * posSpread * 2f;
                f.vx = dx * fwd + r1 * spread * 2f;
                f.vy = dy * fwd + up + r2 * spread * 2f;
                f.vz = dz * fwd + r3 * spread * 2f;
                f.age = 0f;
                f.life = life * (0.85f + (float) Math.random() * 0.3f);
                f.s0 = s0;
                f.s1 = s1;
                f.r0 = ((c0 >> 16) & 0xFF) / 255f;
                f.g0 = ((c0 >> 8) & 0xFF) / 255f;
                f.b0 = (c0 & 0xFF) / 255f;
                f.a0 = 1f;
                f.r1 = ((c1 >> 16) & 0xFF) / 255f;
                f.g1 = ((c1 >> 8) & 0xFF) / 255f;
                f.b1 = (c1 & 0xFF) / 255f;
                f.a1 = 0f;
                f.gravity = gravity;
                f.drag = drag;
                f.blend = blend;
            }
        }
    }

    /** 在该部队的小视口里画它自己的粒子(alpha 一遍 + add 一遍)。 */
    private static void drawParticles(int owner, int pw, int ph) {
        if (fxCount <= 0 || fxBatch == null || fxTex == null || camera == null) {
            return;
        }
        boolean any = false;
        for (int i = 0; i < fxCount; i++) {
            Fx p = fxPool[i];
            if (p.alive && p.owner == owner) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        boolean depthOn = true;
        try {
            depthOn = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
        } catch (Throwable ignored) {
        }
        try {
            Gdx.gl.glDepthMask(false);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            fxProj.setToOrtho2D(0, 0, pw, ph);
            fxBatch.setProjectionMatrix(fxProj);
            drawFxPass(owner, pw, ph, 0);
            drawFxPass(owner, pw, ph, 1);
        } catch (Throwable ignored) {
        } finally {
            try {
                Gdx.gl.glDepthMask(true);
                if (depthOn) {
                    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                }
                Gdx.gl.glDisable(GL20.GL_BLEND);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void drawFxPass(int owner, int pw, int ph, int blendMode) {
        boolean started = false;
        float vh = camera.viewportHeight > 0f ? camera.viewportHeight : 2.2f;
        for (int i = 0; i < fxCount; i++) {
            Fx p = fxPool[i];
            if (!p.alive || p.owner != owner || p.blend != blendMode) {
                continue;
            }
            if (!started) {
                fxBatch.setBlendFunction(GL20.GL_SRC_ALPHA,
                        blendMode == 1 ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA);
                fxBatch.begin();
                started = true;
            }
            float t = p.age / p.life;
            if (t < 0f) {
                t = 0f;
            } else if (t > 1f) {
                t = 1f;
            }
            fxTmp.set(p.x, p.y, p.z);
            camera.project(fxTmp, 0, 0, pw, ph);
            // v4.9.9:相机可能被拉远(zoom>1)→ 每世界单位对应像素数要除 zoom
            float pzoom = camera.zoom > 0.01f ? camera.zoom : 1f;
            float size = p.s0 + (p.s1 - p.s0) * t;
            float pxSize = size * ph / (vh * pzoom);
            if (pxSize < 0.6f) {
                pxSize = 0.6f;
            }
            float a = (p.a0 + (p.a1 - p.a0) * t) * (1f - t);
            if (a <= 0.004f) {
                continue;
            }
            float r = p.r0 + (p.r1 - p.r0) * t;
            float g = p.g0 + (p.g1 - p.g0) * t;
            float b = p.b0 + (p.b1 - p.b0) * t;
            fxBatch.setColor(r, g, b, a);
            fxBatch.draw(fxTex, fxTmp.x - pxSize * 0.5f, fxTmp.y - pxSize * 0.5f, pxSize, pxSize);
        }
        if (started) {
            fxBatch.end();
        }
    }

    /* ---- 粒子锚点:骨骼优先,模型包围盒兜底 ---- */

    /** 骨骼节点世界坐标(找不到返回 false)。 */
    private static boolean boneWorld(ModelInstance inst, String bone, Vector3 out) {
        try {
            Node n = inst.getNode(bone, true, true);
            if (n == null) {
                return false;
            }
            fxM.set(inst.transform).mul(n.globalTransform);
            fxM.getTranslation(out);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 模型空间点 → 世界(含适配缩放/朝向)。 */
    private static void modelPoint(ModelInstance inst, float x, float y, float z, Vector3 out) {
        out.set(x, y, z).mul(inst.transform);
    }

    /** v4.7.3:骨骼局部点 → 世界(含适配缩放/朝向)。 */
    private static boolean bonePoint(ModelInstance inst, String bone, float lx, float ly, float lz, Vector3 out) {
        try {
            Node n = inst.getNode(bone, true, true);
            if (n == null) {
                return false;
            }
            fxM.set(inst.transform).mul(n.globalTransform);
            out.set(lx, ly, lz).mul(fxM);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * v4.4.0:一支部队本帧触发的攻击事件 → 粒子。
     * 锚点:武器骨(Unit_Wea_Right_Hand02/01) > 炮骨(barrel_end / cannon_back_j) > 船体侧面 > 包围盒。
     */
    private static void spawnUnitFx(int owner, ModelInstance inst, WeaponProfile prof, int mask,
                                    boolean ship, ModelSet set, float deg) {
        if (!confParticles || prof == null || mask == 0 || inst == null) {
            return;
        }
        try {
            inst.calculateTransforms();
        } catch (Throwable ignored) {
        }
        float rad = deg * 0.017453292f;
        float fdx = (float) Math.sin(rad);
        float fdz = (float) Math.cos(rad);
        boolean cannon = "cannon".equals(prof.id);
        boolean shipGun = "ship_cannon".equals(prof.id);
        float bbMinX = set != null ? set.bbMinX : (ship ? shipBbMinX : userBbMinX);
        float bbMinY = set != null ? set.bbMinY : (ship ? shipBbMinY : userBbMinY);
        float bbMinZ = set != null ? set.bbMinZ : (ship ? shipBbMinZ : userBbMinZ);
        float bbMaxX = set != null ? set.bbMaxX : (ship ? shipBbMaxX : userBbMaxX);
        float bbMaxY = set != null ? set.bbMaxY : (ship ? shipBbMaxY : userBbMaxY);
        float bbMaxZ = set != null ? set.bbMaxZ : (ship ? shipBbMaxZ : userBbMaxZ);
        try {
        for (int e = 0; e < prof.events.length && e < 32; e++) {
            if ((mask & (1 << e)) == 0) {
                continue;
            }
            AttackEvent ev = prof.events[e];
            if ("stop".equals(ev.preset)) {
                continue;   // EU5 的 stop = 清理上一轮持续粒子;一次性 burst 无需处理
            }
            String place = ev.place == null ? "" : ev.place;
            float ox;
            float oy;
            float oz;
            float dx = fdx;
            float dy = 0f;
            float dz = fdz;
            if (shipGun) {
                if ("water".equals(place)) {
                    modelPoint(inst, (bbMinX + bbMaxX) * 0.5f, bbMinY, bbMinZ, fxA);
                    dx = -fdx;
                    dz = -fdz;
                } else {
                    // 船炮:右舷侧、甲板高度(没有炮位节点)
                    modelPoint(inst, bbMaxX * 0.92f,
                            bbMinY + (bbMaxY - bbMinY) * 0.55f, (bbMinZ + bbMaxZ) * 0.5f, fxA);
                    dx = (float) Math.cos(rad);
                    dz = -(float) Math.sin(rad);
                }
                ox = fxA.x;
                oy = fxA.y;
                oz = fxA.z;
            } else if (cannon) {
                boolean isFuse = "fuse".equals(place);
                if (!(isFuse && boneWorld(inst, "cannon_back_j", fxA)) && !boneWorld(inst, "barrel_end", fxA)) {
                    modelPoint(inst, 0f, bbMinY + (bbMaxY - bbMinY) * 0.55f, bbMaxZ * 0.92f, fxA);
                }
                ox = fxA.x;
                oy = fxA.y;
                oz = fxA.z;
                if (boneWorld(inst, "barrel_base", fxB) || boneWorld(inst, "barrel", fxB)) {
                    dx = fxA.x - fxB.x;
                    dy = fxA.y - fxB.y;
                    dz = fxA.z - fxB.z;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len > 1e-4f) {
                        dx /= len;
                        dy /= len;
                        dz /= len;
                    } else {
                        dx = fdx;
                        dy = 0f;
                        dz = fdz;
                    }
                }
            } else {
                // v4.7.3:WW1/HOI4 骨架没有 Unit_Wea_* 骨 → 挂点骨 Weapon_Grip + 挂件网格远端顶点(枪口)
                boolean hasGrip = boneWorld(inst, "polySurface1Shape.Weapon_Grip", fxA)
                        || boneWorld(inst, "Unit_Wea_Right_Hand01", fxA)
                        || boneWorld(inst, "Unit_Base_Rig_RightHand", fxA);
                boolean hasTip = false;
                if (hasGrip && set != null && set.hasMuzzle && set.muzzleBone != null
                        && bonePoint(inst, set.muzzleBone, set.muzzleX, set.muzzleY, set.muzzleZ, fxB)) {
                    hasTip = true;
                }
                if (!hasTip) {
                    hasTip = boneWorld(inst, "Unit_Wea_Right_Hand02", fxB)
                            || boneWorld(inst, "Unit_Base_Rig_RightHand", fxB);
                }
                // 配置项 locatorFallback 可写自定义骨名(默认 weapon_tip = 上面的骨启发式)
                if (!hasTip && confLocatorFallback != null && confLocatorFallback.length() > 0
                        && !"weapon_tip".equalsIgnoreCase(confLocatorFallback)) {
                    hasTip = boneWorld(inst, confLocatorFallback, fxB);
                }
                boolean atGrip = ev.atGrip || "vfx_loc_02".equals(place);
                if (atGrip && hasGrip) {
                    ox = fxA.x;
                    oy = fxA.y;
                    oz = fxA.z;
                } else if (!atGrip && hasTip) {
                    ox = fxB.x;
                    oy = fxB.y;
                    oz = fxB.z;
                } else {
                    // v4.7.5:没有枪口顶点(旧模型/挂件还没加载)时,枪口事件用身体前缘,
                    // 不能退回握把 —— 骑兵等抬手的姿态握把就在头附近,粒子会和头部重合。
                    modelPoint(inst, 0f, bbMinY + (bbMaxY - bbMinY) * 0.6f, bbMinZ * 0.9f, fxA);
                    ox = fxA.x;
                    oy = fxA.y;
                    oz = fxA.z;
                }
                if (hasTip && hasGrip) {
                    dx = fxB.x - fxA.x;
                    dy = fxB.y - fxA.y;
                    dz = fxB.z - fxA.z;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len > 1e-4f) {
                        dx /= len;
                        dy /= len;
                        dz /= len;
                    } else {
                        dx = fdx;
                        dy = 0f;
                        dz = fdz;
                    }
                }
            }
            spawnPreset(ev.preset, ox, oy, oz, dx, dy, dz, owner);
        }
        } catch (Throwable ignored) {
        }
    }


    /** 创建托管资源(需 GL 线程;幂等;失败可重试)。 */
    public static synchronized boolean init() {
        if (isReady()) {
            return true;
        }
        try {
            if (depthBits < 0) {
                try {
                    Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_BITS, TMP1);
                    depthBits = TMP1.get(0);
                } catch (Throwable ignored) {
                    depthBits = 0;
                }
            }

            // 正交相机:与 2D 地图同为“上帝视角”;俯角见 applyCameraRig(v4.9.9 可随地图缩放环绕)
            // v1.4:视线中心下移,把脚下空区从 ~23% 收到 ~10% → 兵模更贴近兵牌
            worldH = clampWorldH(confWorldH);
            camera = new OrthographicCamera();
            camera.near = 0.1f;
            camera.far = 100f;
            camera.viewportHeight = worldH;
            camera.viewportWidth = worldH;
            rigAngleDeg = Float.NaN;
            applyCameraRig(0.4f * worldH, 0.4f * worldH, 0.3f * worldH, 1f);
            camera.update();

            env = new Environment();
            // 暖色柔光(与地图色调一致,降低违和感)
            env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.52f, 0.49f, 0.44f, 1f));
            env.add(new DirectionalLight().set(0.85f, 0.79f, 0.66f, -0.9f, -1.3f, -0.7f));

            for (int i = 0; i < 4; i++) {
                models[i] = null;
                instances[i] = null;
                for (int j = 0; j < 4; j++) {
                    uInstances[i][j] = null;
                    uAnims[i][j] = null;
                    sInstances[i][j] = null;
                    sAnims[i][j] = null;
                }
            }
            // v4.9.1(B2):重建后帧全量推进一次
            uPrevMask = -1L;
            uCurMask = -1L;
            sPrevMask = -1L;
            sCurMask = -1L;
            for (int j = 0; j < 4; j++) {
                stateAnimIds[j] = null;
            }
            usingUserModel = false;
            usingShipModel = false;
            userFit.idt();
            userFitScale = 1f;
            userInvFitScale = 1f;
            userFitDx = 0f;
            userFitDz = 0f;
            userAnchorX = 0f;
            userAnchorZ = 0f;
            userShadowShift = 0f;
            userCenterY = 0.4f * worldH;
            userHalfV = 0.4f * worldH;
            userHalfD = 0.3f * worldH;
            shipAnchorX = 0f;
            shipAnchorZ = 0f;
            shipShadowShift = 0f;
            shipCenterY = 0.2f * worldH;
            shipHalfV = 0.2f * worldH;
            shipHalfD = 0.3f * worldH;
            shipFit.idt();
            shipFitScale = 1f;
            shipInvFitScale = 1f;
            shipFitDx = 0f;
            shipFitDz = 0f;
            morphMap.clear();
            // ① 用户模型优先(dataDir 下的 <model>.g3dj / <model>.g3db)
            Model userModel = loadUserModel();
            if (userModel != null) {
                buildUserInstances(userModel);
            } else {
                // ② 内置几何兵模(四套配色:0=玩家绿 1=敌红 2=友蓝 3=中立)
                models[0] = buildSoldier(0.33f, 0.58f, 0.30f);
                models[1] = buildSoldier(0.62f, 0.26f, 0.22f);
                models[2] = buildSoldier(0.32f, 0.46f, 0.66f);
                models[3] = buildSoldier(0.62f, 0.58f, 0.50f);
                try {
                    retreatFlagModel = buildRetreatFlag();
                } catch (Throwable ignored) {
                }
                for (int i = 0; i < 4; i++) {
                    instances[i] = new ModelInstance(models[i]);
                }
                try {
                    retreatFlagInst = retreatFlagModel == null ? null : new ModelInstance(retreatFlagModel);
                } catch (Throwable ignored) {
                }
                tryWriteTemplate();
            }
            // v4.9.4:程序化接触阴影与“默认用户模型”无关 —— 独立构建。
            // 旧逻辑把 buildSoftShadow() 放在 userModel != null 分支里:
            // 默认模型(设置里的 model)一旦加载失败(userShadowInst 为 null),
            // 所有 combo 兵模都会“没阴影”,而你实际看到的模型依然正常( combo 是单独加载的)。
            try {
                userShadowModel = buildSoftShadow();
                userShadowInst = new ModelInstance(userShadowModel);
                shadowReady = true;
            } catch (Throwable se) {
                userShadowModel = null;
                userShadowInst = null;
                shadowReady = false;
            }
            // ①b v1.8.0:船模(海面部队显示船;<confShipModel>.g3dj/.g3db;缺文件 → 海面沿用陆军模型)
            Model shipModel = loadShipModel();
            if (shipModel != null) {
                shipModelRef = shipModel;
                buildShipInstances(shipModel);
            }
            // v4.4.0:默认用户模型/船模的武器事件表(按 assembly 的 animSet/group 选)
            userProfile = weaponProfileFor(confModel);
            shipProfile = weaponProfileFor(confShipModel);

            // v2.0.0:统计已加载模型的「单部件最大骨骼数」——DefaultShader 的 numBones 默认 12,
            // 拼装兵模(torso 44 骨等)超过会抛 "too many bones",必须在建批次前按实际值配置
            detectedBones = Math.max(12, Math.max(maxBonesOf(userModel), maxBonesOf(shipModel)));

            batch = createBatch();

            ready = true;
            lastError = "";
            collectDiag();
            return true;
        } catch (Throwable t) {
            lastError = "init: " + chain(t);
            return false;
        }
    }

    /** 用几何拼一个“士兵”:腿 + 躯干 + 头 + 脚下接触阴影圆盘。 */
    private static Model buildSoldier(float r, float g, float b) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Material body = new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f)));
        Matrix4 t = new Matrix4();
        MeshPartBuilder mp;

        mp = mb.part("legs", GL20.GL_TRIANGLES, attrs, body);
        t.idt().setToTranslation(0f, 0.22f, 0f);
        mp.setVertexTransform(t);
        mp.box(0.40f, 0.44f, 0.24f);

        mp = mb.part("torso", GL20.GL_TRIANGLES, attrs, body);
        t.idt().setToTranslation(0f, 0.70f, 0f);
        mp.setVertexTransform(t);
        mp.box(0.50f, 0.52f, 0.30f);

        mp = mb.part("head", GL20.GL_TRIANGLES, attrs, body);
        t.idt().setToTranslation(0f, 1.08f, 0f);
        mp.setVertexTransform(t);
        mp.sphere(0.17f, 0.34f, 0.34f, 12, 8);

        // 接触阴影(与模型同批:混合部件排在不透明之后,贴地小圆盘刚好压在脚部)
        Material disc = new Material(ColorAttribute.createDiffuse(0f, 0f, 0f, 1f),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.30f));
        mp = mb.part("shadow", GL20.GL_TRIANGLES, attrs, disc);
        t.idt().setToTranslation(0f, 0.012f, 0f);
        mp.setVertexTransform(t);
        mp.cylinder(0.95f, 0.02f, 0.95f, 16);

        return mb.end();
    }

    /** v1.4:撤退白旗(细旗杆+白布;仅内置模型的撤退状态渲染,跟随本体变换)。 */
    private static Model buildRetreatFlag() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Material pole = new Material(ColorAttribute.createDiffuse(new Color(0.28f, 0.24f, 0.20f, 1f)));
        Material cloth = new Material(ColorAttribute.createDiffuse(new Color(0.93f, 0.93f, 0.90f, 1f)));
        Matrix4 t = new Matrix4();
        MeshPartBuilder mp = mb.part("pole", GL20.GL_TRIANGLES, attrs, pole);
        t.idt().setToTranslation(0.34f, 0.62f, 0.06f);
        mp.setVertexTransform(t);
        mp.cylinder(0.02f, 1.1f, 0.02f, 6);
        mp = mb.part("cloth", GL20.GL_TRIANGLES, attrs, cloth);
        t.idt().setToTranslation(0.58f, 1.02f, 0.06f);
        mp.setVertexTransform(t);
        mp.box(0.46f, 0.30f, 0.02f);
        return mb.end();
    }

    /**
     * v1.4.8:用户模型脚下的**柔和接触阴影** —— 径向渐变半透明圆盘:
     * ①程序生成 64×64 贴图(中心黑、alpha 沿半径二次衰减,Linear 过滤+Clamp);
     * ②单个水平 quad(无 Normal → 不参与光照,纯 alpha 混合);
     * ③渲染时用与本体同一平移(脚底原点 y=0 上方 0.012),并撤销适配缩放 → 阴影保持世界尺寸;
     * ④阴随体走(位置随部队移动),不随程序化迈步/突刺摆动(脚不动,更自然)。
     */
    private static Model buildSoftShadow() {
        final int S = 64;
        // v4.9.2:把「接触核心」直接烘焙进阴影贴图 → 每个模型少一次 draw call。
        // 两层分别画时的衰减 = (1-0.75·A1)·(1-A2),烘焙时令 A = 1-(1-0.75·A1)·(1-A2),
        // 单层(opacity=1)的最终视觉与双层几乎一致(A2 按 d/core 采样,即旧“缩小版核心”)。
        final float core = confShadowCore;
        final boolean bakeCore = confShadowBake && core > 0.05f && core < 0.99f;
        shadowCoreBaked = bakeCore;
        Pixmap pm = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float dx = (x - (S - 1) * 0.5f) / (S * 0.5f);
                float dy = (y - (S - 1) * 0.5f) / (S * 0.5f);
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a1 = 1f - d;
                if (a1 <= 0f) {
                    continue;
                }
                float a1a = a1 * a1 * 0.42f;
                float alpha = a1a;
                if (bakeCore) {
                    float uc = d / core;
                    float a2a = 0f;
                    if (uc < 1f) {
                        float a2 = 1f - uc;
                        a2a = a2 * a2 * 0.42f;
                    }
                    alpha = 1f - (1f - 0.75f * a1a) * (1f - a2a);
                }
                pm.setColor(0f, 0f, 0f, alpha);
                pm.drawPixel(x, y);
            }
        }
        Texture tex = new Texture(pm);
        pm.dispose();
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        tex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        long attrs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates;
        Material mat = new Material(TextureAttribute.createDiffuse(tex),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f));
        MeshPartBuilder mpb = mb.part("softshadow", GL20.GL_TRIANGLES, attrs, mat);
        float r = 0.62f; // 世界单位:直径≈1.24(自动适配后的骑士体宽)
        float sy = 0.012f;
        MeshPartBuilder.VertexInfo a00 = new MeshPartBuilder.VertexInfo().setPos(-r, sy, -r).setUV(0f, 0f);
        MeshPartBuilder.VertexInfo a10 = new MeshPartBuilder.VertexInfo().setPos(r, sy, -r).setUV(1f, 0f);
        MeshPartBuilder.VertexInfo a11 = new MeshPartBuilder.VertexInfo().setPos(r, sy, r).setUV(1f, 1f);
        MeshPartBuilder.VertexInfo a01 = new MeshPartBuilder.VertexInfo().setPos(-r, sy, r).setUV(0f, 1f);
        mpb.rect(a00, a10, a11, a01);
        // v1.5.0:再加一份反绕序四边形 —— 双面化,游戏当前无论是否开启背面剔除都有一面朝上可见
        mpb.rect(a00, a01, a11, a10);
        return mb.end();
    }

    /* ================= 用户资源:模型 / 动画 / 格式示例 ================= */

    /* ---- v4.2.0:资源目录结构(models/<类>/{g3d,png};旧版 g3d/png/parts 平铺仍兼容) ---- */
    /** 资源是否已扫描。 */
    private static boolean resScanned = false;
    /** 模型名(不含扩展名)→ .g3dj/.g3db。 */
    private static final HashMap<String, File> resModels = new HashMap<String, File>();
    /** 贴图目录(按优先级;先到先得)。 */
    private static final ArrayList<File> resTexDirs = new ArrayList<File>();
    /** 挂件模型目录。 */
    private static final ArrayList<File> resPartG3d = new ArrayList<File>();
    /** 挂件贴图目录。 */
    private static final ArrayList<File> resPartPng = new ArrayList<File>();
    /** 扫描结果诊断。 */
    public static volatile String resNote = "";

    /** v4.2.0:扫描资源目录(dataDir 变化/热重载后调用;幂等)。 */
    public static synchronized void scanResources() {
        resScanned = false;
        resModels.clear();
        resTexDirs.clear();
        resPartG3d.clear();
        resPartPng.clear();
        resNote = "";
        if (dataDirPath == null) {
            return;
        }
        resScanned = true;
        int models = 0;
        int tex = 0;
        // ① 新结构:models/<类>/{g3d,png} + models/parts/{g3d,png}
        File modelsRoot = new File(dataDirPath, "models");
        if (modelsRoot.isDirectory()) {
            File[] cats = modelsRoot.listFiles();
            if (cats != null) {
                // main 优先(完整模型),parts 其次(挂件),其余按名字
                java.util.Arrays.sort(cats, new java.util.Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        int pa = rank(a.getName());
                        int pb = rank(b.getName());
                        return pa != pb ? pa - pb : a.getName().compareToIgnoreCase(b.getName());
                    }

                    private int rank(String n) {
                        if ("main".equalsIgnoreCase(n)) {
                            return 0;
                        }
                        return "parts".equalsIgnoreCase(n) ? 1 : 2;
                    }
                });
                for (int i = 0; i < cats.length; i++) {
                    File cat = cats[i];
                    if (!cat.isDirectory()) {
                        continue;
                    }
                    boolean isParts = "parts".equalsIgnoreCase(cat.getName());
                    File g3d = new File(cat, "g3d");
                    File png = new File(cat, "png");
                    if (png.isDirectory()) {
                        addTexDir(png);
                        tex++;
                        if (isParts) {
                            resPartPng.add(png);
                        }
                    }
                    if (g3d.isDirectory()) {
                        addTexDir(g3d);   // 贴图也可能直接放 g3d/ 旁边
                        models += addModels(g3d, isParts);
                    } else {
                        models += addModels(cat, isParts);   // 兼容 models/<类>/*.g3dj
                    }
                }
            }
        }
        // ② 旧结构:g3d/(模型 + 贴图)、png/(贴图)、根目录(模型/贴图)、parts/(挂件)
        File legacyG3d = new File(dataDirPath, "g3d");
        if (legacyG3d.isDirectory()) {
            addTexDir(legacyG3d);
            models += addModels(legacyG3d, false);
        }
        File legacyPng = new File(dataDirPath, "png");
        if (legacyPng.isDirectory()) {
            addTexDir(legacyPng);
            tex++;
        }
        addTexDir(new File(dataDirPath));
        File legacyParts = new File(dataDirPath, "parts");
        if (legacyParts.isDirectory()) {
            resPartG3d.add(legacyParts);
            resPartPng.add(legacyParts);
            models += addModels(legacyParts, true);
        }
        resNote = "资源: 模型=" + resModels.size() + " 贴图目录=" + resTexDirs.size()
                + " 挂件目录=" + resPartG3d.size();
        try {
            Gdx.app.log("G3DArmy", resNote);
        } catch (Throwable ignored) {
        }
    }

    /** 把一个目录下的模型登记进索引(同名先到先得)。 */
    private static int addModels(File dir, boolean parts) {
        File[] fs = dir.listFiles();
        if (fs == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < fs.length; i++) {
            File f = fs[i];
            if (!f.isFile()) {
                continue;
            }
            String low = f.getName().toLowerCase();
            if (!low.endsWith(".g3dj") && !low.endsWith(".g3db")) {
                continue;
            }
            String id = f.getName().substring(0, f.getName().lastIndexOf('.'));
            if (parts) {
                if (!resPartG3d.contains(dir)) {
                    resPartG3d.add(dir);
                }
                if (resModels.containsKey(id)) {
                    continue;
                }
            } else if (resModels.containsKey(id)) {
                continue;
            }
            resModels.put(id, f);
            n++;
        }
        return n;
    }

    private static void addTexDir(File dir) {
        if (dir != null && dir.isDirectory() && !resTexDirs.contains(dir)) {
            resTexDirs.add(dir);
        }
    }

    /** 按名字找挂件文件(先 index.json 给的 file,再 <名字>.g3dj/.g3db)。 */
    private static File findPartFile(String file, String name) {
        ensureRes();
        if (file != null) {
            for (int i = 0; i < resPartG3d.size(); i++) {
                File f = new File(resPartG3d.get(i), file);
                if (f.isFile()) {
                    return f;
                }
            }
        }
        if (name != null) {
            for (int i = 0; i < resPartG3d.size(); i++) {
                File a = new File(resPartG3d.get(i), name + ".g3dj");
                if (a.isFile()) {
                    return a;
                }
                File b = new File(resPartG3d.get(i), name + ".g3db");
                if (b.isFile()) {
                    return b;
                }
            }
        }
        return null;
    }

    /** 找挂件索引 index.json(models/parts/index.json → parts/index.json)。 */
    private static File findPartsIndexFile() {
        ensureRes();
        for (int i = 0; i < resPartG3d.size(); i++) {
            File f = new File(resPartG3d.get(i), "index.json");
            if (f.isFile()) {
                return f;
            }
            File up = new File(resPartG3d.get(i).getParentFile(), "index.json");
            if (up.isFile()) {
                return up;
            }
        }
        if (dataDirPath != null) {
            File f = new File(new File(dataDirPath, confPartsDir), "index.json");
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /** 资源索引未建立时补扫。 */
    private static void ensureRes() {
        if (!resScanned) {
            scanResources();
        }
    }

    /** 在 models/<类>/g3d 与旧 g3d/、根目录里找模型(.g3dj 优先);找不到返回 null。 */
    private static File findModelFile(String name) {
        if (name == null || name.length() == 0) {
            return null;
        }
        ensureRes();
        File f = resModels.get(name);
        if (f != null && f.isFile()) {
            return f;
        }
        // 兼容:调用方带扩展名
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            File g = resModels.get(name.substring(0, dot));
            if (g != null && g.isFile()) {
                return g;
            }
        }
        // 兜底:旧路径直接命中
        String[] exts = {".g3dj", ".g3db"};
        for (int i = 0; i < exts.length; i++) {
            File a = new File(dataDirPath, name + exts[i]);
            if (a.isFile()) {
                return a;
            }
            File b = new File(new File(dataDirPath, "g3d"), name + exts[i]);
            if (b.isFile()) {
                return b;
            }
        }
        return null;
    }

    /** 按扩展名选加载器 + 日志标签,统一处理 g3dj/g3db。 */
    private static Model loadModelFile(File f) {
        return loadModelFile(f, null);
    }

    /** v4.0.0:带静态件剥离的加载。 */
    private static Model loadModelFile(File f, String[] stripTokens) {
        if (f == null) {
            return null;
        }
        boolean ub = f.getName().toLowerCase().endsWith(".g3db");
        return ub
                ? loadUserModelData(new G3dModelLoader(new UBJsonReader()), f, stripTokens)
                : loadUserModelData(new G3dModelLoader(new JsonReader()), f, stripTokens);
    }

    /** 从 dataDir/g3d 读用户模型(<confModel>.g3dj / .g3db);无文件或失败 → null(用内置)。 */
    private static Model loadUserModel() {
        modelSource = "内置几何兵模";
        modelNote = "";
        if (dataDirPath == null || confModel == null) {
            return null;
        }
        String name = confModel.trim();
        if (name.length() == 0 || "builtin".equalsIgnoreCase(name)) {
            return null;
        }
        try {
            File f = findModelFile(name);
            if (f != null) {
                Model m = loadModelFile(f);
                modelSource = "用户模型 " + f.getName();
                return m;
            }
        } catch (Throwable t) {
            modelNote = "用户模型加载失败(已用内置): " + chain(t);
        }
        return null;
    }

    /** v1.8.0:从 dataDir/g3d 读船模(<confShipModel>.g3dj/.g3db);无文件/失败 → null(海面沿用陆军模型)。 */
    private static Model loadShipModel() {
        if (dataDirPath == null || confShipModel == null) {
            return null;
        }
        String name = confShipModel.trim();
        if (name.length() == 0 || "builtin".equalsIgnoreCase(name) || "-".equals(name)) {
            return null;
        }
        try {
            File f = findModelFile(name);
            if (f != null) {
                Model m = loadModelFile(f);
                modelSource = modelSource + " + 船模 " + f.getName();
                return m;
            }
        } catch (Throwable t) {
            String prefix = modelNote == null || modelNote.length() == 0 ? "" : modelNote + "; ";
            modelNote = prefix + "船模加载失败: " + chain(t);
        }
        return null;
    }

    /**
     * v4.0.0:带「静态件剥离」的版本 —— stripTokens 非空时删除 meshPartId/materialId
     * 命中任一关键词的部件(如 weapon/shield),由运行时挂件代替。
     */
    private static Model loadUserModelData(G3dModelLoader loader, File file, String[] stripTokens) {
        FileHandle handle = Gdx.files.absolute(file.getAbsolutePath());
        ModelData data = loader.loadModelData(handle, null);
        if (data == null) {
            throw new IllegalStateException("loadModelData 返回 null: " + file);
        }
        stripGearParts(data, stripTokens);
        tAttachedParts = 0;
        return buildFromData(loader, data, file);
    }

    /** v4.0.1:ModelData → (顶点色剥离 / 脚底 AO) → Model(自定义贴图 Provider) → 材质修正。 */
    private static Model buildFromData(G3dModelLoader loader, ModelData data, File file) {
        stripUniformVertexColors(data);
        applyVerticalShade(data);
        Model m = new Model(data, absTextureProvider());
        // v1.4.4:把「灰白自发光」归零
        // (旧版 fbx-conv 导出的垃圾值,如 cccccc——片元最后一步 +emissive 会把颜色加爆成纯白)
        for (int i = 0; i < m.materials.size; i++) {
            Material mat = m.materials.get(i);
            ColorAttribute em = mat.get(ColorAttribute.class, ColorAttribute.Emissive);
            if (em == null) {
                continue;
            }
            Color c = em.color;
            float mx = Math.max(c.r, Math.max(c.g, c.b));
            float mn = Math.min(c.r, Math.min(c.g, c.b));
            if (mx - mn < 0.02f && mx > 0.05f) {
                c.set(0f, 0f, 0f, 1f);
            }
        }
        return m;
    }

    /**
     * v4.1.1:脚底 AO —— 给每个网格加一层顶点色:按模型局部高度从「底部暗」渐变到「上部正常」。
     * 顶点色会乘进 diffuse(shader: diffuse = tex * u_diffuseColor * v_color),与染色互不冲突。
     *
     * @return 处理的网格数
     */
    private static int applyVerticalShade(ModelData data) {
        float bottom = confFeetDark;
        if (bottom >= 0.999f || bottom < 0.05f || data.meshes == null) {
            return 0;
        }
        // ① 全局 Y 范围(所有网格同一坐标空间)
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < data.meshes.size; i++) {
            ModelMesh m = data.meshes.get(i);
            if (m == null || m.attributes == null || m.vertices == null) {
                continue;
            }
            int off = 0;
            int posOff = -1;
            for (VertexAttribute a : m.attributes) {
                if (a.usage == VertexAttributes.Usage.Position) {
                    posOff = off;
                }
                off += a.numComponents;
            }
            if (posOff < 0 || off <= 0) {
                continue;
            }
            int verts = m.vertices.length / off;
            for (int v = 0; v < verts; v++) {
                float y = m.vertices[v * off + posOff + 1];
                if (y < minY) {
                    minY = y;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }
        float h = maxY - minY;
        if (h < 1e-3f) {
            return 0;
        }
        // ② 逐网格重建顶点,末尾追加 ColorUnpacked(灰度=高度因子)
        int done = 0;
        for (int i = 0; i < data.meshes.size; i++) {
            ModelMesh m = data.meshes.get(i);
            if (m == null || m.attributes == null || m.vertices == null) {
                continue;
            }
            int off = 0;
            int posOff = -1;
            boolean hasColor = false;
            for (VertexAttribute a : m.attributes) {
                if (a.usage == VertexAttributes.Usage.Position) {
                    posOff = off;
                }
                if (a.usage == VertexAttributes.Usage.ColorPacked || a.usage == VertexAttributes.Usage.ColorUnpacked) {
                    hasColor = true;
                }
                off += a.numComponents;
            }
            int oldStride = off;
            if (posOff < 0 || oldStride <= 0 || hasColor) {
                continue;   // 已有顶点色的网格不破坏
            }
            int verts = m.vertices.length / oldStride;
            int newStride = oldStride + 4;
            float[] nv = new float[verts * newStride];
            for (int v = 0; v < verts; v++) {
                int s0 = v * oldStride;
                int d0 = v * newStride;
                System.arraycopy(m.vertices, s0, nv, d0, oldStride);
                float t = (m.vertices[s0 + posOff + 1] - minY) / h;
                if (t < 0f) {
                    t = 0f;
                } else if (t > 1f) {
                    t = 1f;
                }
                float k = bottom + (1f - bottom) * (float) Math.pow(t, 1.3);
                nv[d0 + oldStride] = k;
                nv[d0 + oldStride + 1] = k;
                nv[d0 + oldStride + 2] = k;
                nv[d0 + oldStride + 3] = 1f;
            }
            Array<VertexAttribute> attrs = new Array<VertexAttribute>();
            for (VertexAttribute a : m.attributes) {
                attrs.add(a);
            }
            attrs.add(VertexAttribute.ColorUnpacked());
            m.attributes = attrs.toArray(VertexAttribute.class);
            m.vertices = nv;
            done++;
        }
        return done;
    }

    /**
     * v4.0.0:从 ModelData 里删除命中关键词的部件(静态武器/盾/配饰),返回删除数。
     * 只看部件名(meshPartId)与材质名(materialId),不涉及顶点,开销极小。
     */
    private static int stripGearParts(ModelData data, String[] tokens) {
        if (tokens == null || tokens.length == 0 || data.nodes == null) {
            return 0;
        }
        int removed = 0;
        for (int i = 0; i < data.nodes.size; i++) {
            ModelNode node = data.nodes.get(i);
            if (node == null || node.parts == null || node.parts.length == 0) {
                continue;
            }
            Array<ModelNodePart> keep = new Array<ModelNodePart>();
            for (int k = 0; k < node.parts.length; k++) {
                ModelNodePart p = node.parts[k];
                String key = ((p.meshPartId == null ? "" : p.meshPartId) + " "
                        + (p.materialId == null ? "" : p.materialId)).toLowerCase();
                boolean hit = false;
                for (int t = 0; t < tokens.length; t++) {
                    String tk = tokens[t];
                    if (tk != null && tk.length() > 0 && key.contains(tk.toLowerCase())) {
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    removed++;
                } else {
                    keep.add(p);
                }
            }
            if (keep.size != node.parts.length) {
                node.parts = keep.toArray(ModelNodePart.class);
            }
        }
        return removed;
    }

    /** 网格顶点色若为「全网格同一个值」(无美术信息,常见于转换器哨兵值)→ 剥离该列;返回是否剥离过。 */
    private static boolean stripUniformVertexColors(ModelData data) {
        boolean stripped = false;
        for (int i = 0; i < data.meshes.size; i++) {
            ModelMesh mesh = data.meshes.get(i);
            if (mesh == null || mesh.attributes == null || mesh.vertices == null) {
                continue;
            }
            int oldStrideB = 0;
            int dropB = 0;
            for (VertexAttribute a : mesh.attributes) {
                int szB = (a.usage == VertexAttributes.Usage.ColorPacked) ? 4 : a.numComponents * 4;
                oldStrideB += szB;
                if (a.usage == VertexAttributes.Usage.ColorUnpacked || a.usage == VertexAttributes.Usage.ColorPacked) {
                    dropB += szB;
                }
            }
            if (dropB == 0 || oldStrideB == 0) {
                continue;
            }
            int oldStride = oldStrideB / 4;
            int verts = mesh.vertices.length / oldStride;
            // 找第一个颜色属性(浮点下标)与其数据长度
            int colorOffF = -1;
            int colorFloats = 1;
            int offB = 0;
            for (VertexAttribute a : mesh.attributes) {
                int szB = (a.usage == VertexAttributes.Usage.ColorPacked) ? 4 : a.numComponents * 4;
                if (a.usage == VertexAttributes.Usage.ColorUnpacked || a.usage == VertexAttributes.Usage.ColorPacked) {
                    colorOffF = offB / 4;
                    colorFloats = szB / 4;
                    break;
                }
                offB += szB;
            }
            if (colorOffF < 0) {
                continue;
            }
            boolean uniform = true;
            for (int v = 1; v < verts && uniform; v++) {
                for (int c = 0; c < colorFloats; c++) {
                    if (Float.floatToIntBits(mesh.vertices[v * oldStride + colorOffF + c])
                            != Float.floatToIntBits(mesh.vertices[colorOffF + c])) {
                        uniform = false;
                        break;
                    }
                }
            }
            if (!uniform) {
                continue; // 顶点色有变化 = 可能是有意为之,保留原样
            }
            int newStride = oldStride - dropB / 4;
            float[] nv = new float[verts * newStride];
            for (int v = 0; v < verts; v++) {
                int s = v * oldStride;
                int d = v * newStride;
                int ob = 0;
                for (VertexAttribute a : mesh.attributes) {
                    int szB = (a.usage == VertexAttributes.Usage.ColorPacked) ? 4 : a.numComponents * 4;
                    if (a.usage != VertexAttributes.Usage.ColorUnpacked && a.usage != VertexAttributes.Usage.ColorPacked) {
                        int cnt = szB / 4;
                        System.arraycopy(mesh.vertices, s + ob / 4, nv, d, cnt);
                        d += cnt;
                    }
                    ob += szB;
                }
            }
            Array<VertexAttribute> kept = new Array<VertexAttribute>();
            for (VertexAttribute a : mesh.attributes) {
                if (a.usage != VertexAttributes.Usage.ColorUnpacked && a.usage != VertexAttributes.Usage.ColorPacked) {
                    kept.add(a);
                }
            }
            mesh.vertices = nv;
            mesh.attributes = kept.toArray(VertexAttribute.class);
            stripped = true;
        }
        return stripped;
    }

    /**
     * v1.4.1:自定义贴图加载 —— G3dModelLoader 解析材质时把「模型所在目录(绝对路径)+贴图名」作为贴图路径,
     * 自带的 FileTextureProvider 却用 Gdx.files.internal() 打开它(安卓 = APK assets,打不开磁盘文件);
     * 这里统一改用 Gdx.files.absolute(),桌面/安卓一致可用。
     */
    private static TextureProvider absTextureProvider() {
        return new TextureProvider() {
            @Override
            public Texture load(String fileName) {
                FileHandle fh = resolveTextureFile(fileName);
                if (fh == null || !fh.exists()) {
                    // 缺贴图:1×1 白色占位,避免 Model 构造阶段 NPE(模型按纯色渲染)
                    Gdx.app.log("G3DArmy", "贴图缺失,用占位白贴图: " + fileName);
                    Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                    pm.setColor(Color.WHITE);
                    pm.fill();
                    Texture t = new Texture(pm);
                    pm.dispose();
                    return t;
                }
                // v4.9.6:同一张 png 被多个 combo/挂件引用时只解码一次(引用计数)
                String path = fh.file().getAbsolutePath();
                SharedTexture shared = texCache.get(path);
                if (shared != null) {
                    shared.refs++;
                    return shared;
                }
                SharedTexture t = new SharedTexture(fh, path);
                t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                texCache.put(path, t);
                return t;
            }
        };
    }

    /** 贴图定位:优先按绝对路径;失败时依次尝试 models/&lt;类&gt;/png、models/&lt;类&gt;/g3d、png、g3d、根目录。 */
    private static FileHandle resolveTextureFile(String fileName) {
        if (fileName == null || fileName.trim().length() == 0) {
            return null;
        }
        FileHandle fh = Gdx.files.absolute(fileName);
        if (fh.exists()) {
            return fh;
        }
        ensureRes();
        String base = new File(fileName).getName();
        for (int i = 0; i < resTexDirs.size(); i++) {
            FileHandle alt = Gdx.files.absolute(new File(resTexDirs.get(i), base).getAbsolutePath());
            if (alt.exists()) {
                return alt;
            }
        }
        // 兜底:旧平铺目录
        if (dataDirPath != null) {
            String[] subs = {"png", "", "g3d"};
            for (int i = 0; i < subs.length; i++) {
                File dir = subs[i].length() == 0 ? new File(dataDirPath) : new File(dataDirPath, subs[i]);
                FileHandle alt = Gdx.files.absolute(new File(dir, base).getAbsolutePath());
                if (alt.exists()) {
                    return alt;
                }
            }
        }
        return fh;
    }

    /** 动画状态名称的自动匹配关键词(小写包含匹配)。 */
    private static final String[] KW_IDLE = {"idle", "stand", "stay", "rest", "wait", "breath"};
    private static final String[] KW_MOVE = {"walk", "run", "move", "march", "jog", "advance"};
    private static final String[] KW_BATTLE = {"attack", "battle", "fight", "hit", "sword", "combat", "strike", "shoot"};
    private static final String[] KW_RETREAT = {"retreat", "flee", "run", "escape", "panic", "away"};

    /** 用户模型 → 4 关系色 × 4 状态实例;每个有动画的状态一个独立控制器(切换状态=切换实例,相位连续)。 */
    private static void buildUserInstances(Model userModel) {
        models[0] = userModel; // 仅为统一 dispose;models[1..3] 保持 null
        usingUserModel = true;
        boolean hasAny = userModel.animations != null && userModel.animations.size > 0;
        String firstId = hasAny ? userModel.animations.get(0).id : null;
        String baseId = resolveAnim(userModel, confAnimation, null, firstId);
        stateAnimIds[0] = resolveAnim(userModel, confAnimIdle, KW_IDLE, baseId);
        stateAnimIds[1] = resolveAnim(userModel, confAnimMove, KW_MOVE, baseId);
        stateAnimIds[2] = resolveAnim(userModel, confAnimBattle, KW_BATTLE, baseId);
        stateAnimIds[3] = resolveAnim(userModel, confAnimRetreat, KW_RETREAT, stateAnimIds[1]);
        float speed = confAnimSpeed > 0f ? confAnimSpeed : 1f;
        for (int rel = 0; rel < 4; rel++) {
            for (int st = 0; st < 4; st++) {
                ModelInstance inst = new ModelInstance(userModel);
                uInstances[rel][st] = inst;
                if (stateAnimIds[st] != null) {
                    AnimationController c = new AnimationController(inst);
                    c.setAnimation(stateAnimIds[st], 0f, -1f, -1, speed, null);
                    uAnims[rel][st] = c;
                }
            }
        }
        // v4.4.0:battle 相位实例(仅会开火的模型)
        if (stateAnimIds[2] != null && weaponProfileFor(confModel) != null) {
            float dur = animDuration(userModel, stateAnimIds[2]);
            for (int rel = 0; rel < 4; rel++) {
                for (int ph = 0; ph < PHASE; ph++) {
                    ModelInstance bi = new ModelInstance(userModel);
                    uBattleInst[rel][ph] = bi;
                    AnimationController c = new AnimationController(bi);
                    AnimationController.AnimationDesc ad = c.setAnimation(stateAnimIds[2], 0f, -1f, -1, speed, null);
                    if (ad != null) {
                        ad.duration = dur;
                        ad.time = dur > 0f ? dur * ph / PHASE : 0f;
                        try {
                            c.update(0f);
                        } catch (Throwable ignored) {
                        }
                    }
                    uBattleAnim[rel][ph] = c;
                    uBattlePrev[rel * PHASE + ph] = -1f;
                }
            }
        }
        // v1.4.1:自动适配 —— 用户模型尺寸/原点千差万别(骑士:身高 12.2、脚底 y=-8.85),
        // 统一按包围盒:脚底贴地(bbox.min.y→0)、水平居中、高度缩到视口约 85%。
        String fitNote = "";
        {
            float fitS = confModelScale > 0f ? confModelScale : 1f;
            float fitDx = 0f;
            float fitDy = confModelOffsetY;
            float fitDz = 0f;
            if (confModelAutoFit && uInstances[0][0] != null) {
                try {
                    BoundingBox bb = new BoundingBox();
                    uInstances[0][0].calculateBoundingBox(bb);
                    float h = bb.max.y - bb.min.y;
                    float s0 = autoFitScale(bb);
                    if (s0 > 0f) {
                        float s = confModelScale > 0f ? confModelScale : s0;
                        fitS = s;
                        fitDx = -(bb.min.x + bb.max.x) * 0.5f * s;
                        fitDy = -bb.min.y * s + confModelOffsetY;
                        fitDz = -(bb.min.z + bb.max.z) * 0.5f * s;
                        fitNote = "自动适配 缩放=" + r3(s) + " 贴地↑" + r3(-bb.min.y * s)
                                + (confFitByMax ? "(按最大边)" : "");
                    } else {
                        fitNote = "自动适配跳过(包围盒高≈0, h=" + r3(h) + ")";
                    }
                } catch (Throwable t) {
                    fitNote = "自动适配失败(按原始尺寸): " + chain(t);
                }
            } else if (confModelScale > 0f) {
                fitNote = "手动缩放=" + r3(fitS);
            }
            userFitScale = fitS;
            userInvFitScale = 1f / fitS;
            userFitDx = fitDx;
            userFitDz = fitDz;
            userFit.setToTranslation(fitDx, fitDy, fitDz);
            userFit.scale(fitS, fitS, fitS);
        }
        String animInfo;
        if (!hasAny) {
            animInfo = "无动画(程序化多态动作)";
        } else {
            animInfo = "动画: 停留=" + animLabel(stateAnimIds[0]) + " 移动=" + animLabel(stateAnimIds[1])
                    + " 战斗=" + animLabel(stateAnimIds[2]) + " 撤退=" + animLabel(stateAnimIds[3]);
        }
        modelNote = (confTint ? "" : "未染色;") + animInfo + (fitNote.length() == 0 ? "" : "; " + fitNote);
        baseUser = captureBase(uInstances[0][0]);
        try {
            BoundingBox bb = new BoundingBox();
            uInstances[0][0].calculateBoundingBox(bb);
            // v4.1.4:包围盒/锚点只取本体几何(运行时挂件在骨局部空间,会把模型抬高且把锚点拉飞)
            float[] body = computeBodyBounds(userModel);
            if (body != null) {
                bb.min.set(body[0], body[1], body[2]);
                bb.max.set(body[3], body[4], body[5]);
                userBbMinX = body[0];
                userBbMinY = body[1];
                userBbMinZ = body[2];
                userBbMaxX = body[3];
                userBbMaxY = body[4];
                userBbMaxZ = body[5];
                if (confShadowAnchor) {
                    userAnchorX = body[6];
                    userAnchorZ = body[7];
                }
            }
            userShadowScale = shadowScaleFor(Math.max(bb.max.x - bb.min.x, bb.max.z - bb.min.z) * userFitScale);
            userShadowShift = shadowShiftFor((bb.max.y - bb.min.y) * userFitScale);
            // v4.9.9:环绕几何(世界坐标;含阴影盘,保证环绕时脚底阴影也不越窗)
            float bh = (bb.max.y - bb.min.y) * userFitScale;
            float bx = (bb.max.x - bb.min.x) * userFitScale;
            float bz = (bb.max.z - bb.min.z) * userFitScale;
            userHalfV = 0.5f * bh;
            userCenterY = userHalfV + confModelOffsetY;
            float bodyR = 0.5f * (float) Math.sqrt(bx * bx + bz * bz);
            float shR = 0.62f * userShadowScale + Math.abs(userShadowShift);
            userHalfD = bodyR > shR ? bodyR : shR;
        } catch (Throwable ignored) {
        }
    }
    /**
     * v1.8.0:船模 → 4 关系色 × 4 状态实例。适配按“最大维度”缩放(船长占视口约 85% 宽),
     * 其余与陆军模型一致:贴水线(y=0)、水平居中、可染色、可选用模型同套动画名匹配。
     */
    private static void buildShipInstances(Model shipModel) {
        usingShipModel = true;
        boolean hasAny = shipModel.animations != null && shipModel.animations.size > 0;
        String firstId = hasAny ? shipModel.animations.get(0).id : null;
        shipAnimIds[0] = resolveAnim(shipModel, confAnimIdle, KW_IDLE, firstId);
        shipAnimIds[1] = resolveAnim(shipModel, confAnimMove, KW_MOVE, firstId);
        shipAnimIds[2] = resolveAnim(shipModel, confAnimBattle, KW_BATTLE, firstId);
        shipAnimIds[3] = resolveAnim(shipModel, confAnimRetreat, KW_RETREAT, shipAnimIds[1]);
        float speed = confAnimSpeed > 0f ? confAnimSpeed : 1f;
        for (int rel = 0; rel < 4; rel++) {
            for (int st = 0; st < 4; st++) {
                ModelInstance inst = new ModelInstance(shipModel);
                sInstances[rel][st] = inst;
                if (shipAnimIds[st] != null) {
                    AnimationController c = new AnimationController(inst);
                    c.setAnimation(shipAnimIds[st], 0f, -1f, -1, speed, null);
                    sAnims[rel][st] = c;
                }
            }
        }
        // v4.4.0:船炮相位实例(仅会开火的船型)
        if (shipAnimIds[2] != null && weaponProfileFor(confShipModel) != null) {
            float dur = animDuration(shipModel, shipAnimIds[2]);
            for (int rel = 0; rel < 4; rel++) {
                for (int ph = 0; ph < PHASE; ph++) {
                    ModelInstance bi = new ModelInstance(shipModel);
                    sBattleInst[rel][ph] = bi;
                    AnimationController c = new AnimationController(bi);
                    AnimationController.AnimationDesc ad = c.setAnimation(shipAnimIds[2], 0f, -1f, -1, speed, null);
                    if (ad != null) {
                        ad.duration = dur;
                        ad.time = dur > 0f ? dur * ph / PHASE : 0f;
                        try {
                            c.update(0f);
                        } catch (Throwable ignored) {
                        }
                    }
                    sBattleAnim[rel][ph] = c;
                    sBattlePrev[rel * PHASE + ph] = -1f;
                }
            }
        }
        // 适配:尺寸取 max(高, 水平长边) —— 长船不超出视口,高矮船照常贴线
        baseShip = captureBase(sInstances[0][0]);
        try {
            BoundingBox bb = new BoundingBox();
            sInstances[0][0].calculateBoundingBox(bb);
            float[] body = computeBodyBounds(shipModel);
            if (body != null) {
                bb.min.set(body[0], body[1], body[2]);
                bb.max.set(body[3], body[4], body[5]);
                shipBbMinX = body[0];
                shipBbMinY = body[1];
                shipBbMinZ = body[2];
                shipBbMaxX = body[3];
                shipBbMaxY = body[4];
                shipBbMaxZ = body[5];
            }
            float h = bb.max.y - bb.min.y;
            float w = Math.max(bb.max.x - bb.min.x, bb.max.z - bb.min.z);
            float big = Math.max(h, w);
            if (big > 1e-3f) {
                float pct = confFitPct > 0.1f ? Math.min(confFitPct, 1.2f) : 0.85f;
                float s = (worldH > 0f ? worldH : 2.2f) * pct / big;
                if (confModelScale > 0f) {
                    s = confModelScale;
                }
                shipFitScale = s;
                shipInvFitScale = 1f / s;
                shipFitDx = -(bb.min.x + bb.max.x) * 0.5f * s;
                shipFitDz = -(bb.min.z + bb.max.z) * 0.5f * s;
                shipFit.setToTranslation(shipFitDx, -bb.min.y * s + confModelOffsetY, shipFitDz);
                shipFit.scale(s, s, s);
                shipShadowScale = shadowScaleFor(Math.max(bb.max.x - bb.min.x, bb.max.z - bb.min.z) * s);
                if (confShadowAnchor && body != null) {
                    shipAnchorX = body[6];
                    shipAnchorZ = body[7];
                }
                shipShadowShift = shadowShiftFor((bb.max.y - bb.min.y) * s);
                // v4.9.9:环绕几何(世界坐标;含阴影盘)
                float sh = (bb.max.y - bb.min.y) * s;
                float sx = (bb.max.x - bb.min.x) * s;
                float sz = (bb.max.z - bb.min.z) * s;
                shipHalfV = 0.5f * sh;
                shipCenterY = shipHalfV + confModelOffsetY;
                float sBodyR = 0.5f * (float) Math.sqrt(sx * sx + sz * sz);
                float sShR = 0.62f * shipShadowScale + Math.abs(shipShadowShift);
                shipHalfD = sBodyR > sShR ? sBodyR : sShR;
            }
        } catch (Throwable ignored) {
        }
    }

    private static String animLabel(String id) {
        return id == null ? "-" : id;
    }

    /* ================= v2.1.0:combo 按需加载 ================= */

    /** v4.0.0:combo + 运行时挂件签名(缓存键 = combo|挂件)。 */
    private static ModelSet comboSet(String id, String attachSig) {
        if (id == null || id.length() == 0) {
            return null;
        }
        String key = (attachSig == null || attachSig.length() == 0) ? id : (id + "|" + attachSig);
        ModelSet s = comboCache.get(key);
        if (s != null) {
            s.lastUse = comboFrame;
            return s;
        }
        // v4.7.1:默认模型 id 带挂件时也允许加载 combo(否则 foot_ww1_west 等默认模型永远没武器);
        // 仅「默认模型 + 无挂件」才回退共享的 uInstances。
        boolean defaultNoAttach = (id.equals(confModel) || id.equals(confShipModel))
                && (attachSig == null || attachSig.length() == 0);
        if (comboFailedNow(key) || defaultNoAttach) {
            return null;   // 失败/无挂件的默认模型 → 走原有全局资源
        }
        if (comboQueue.size() < 64 && !comboQueue.contains(key)) {
            comboQueue.add(key);
        }
        return null;
    }

    /**
     * v4.5.0:船模 + 挂件 → 模型集(按需加载;键 = 船模id|挂件签名)。
     * null = 用全局基础船模(未挂件/正在加载/加载失败)。
     */
    private static ModelSet shipSet(String modelId, String attachSig) {
        if (!usingShipModel) {
            return null;
        }
        String id = (modelId == null || modelId.length() == 0) ? confShipModel : modelId;
        if (id == null || id.length() == 0 || "builtin".equalsIgnoreCase(id) || "-".equals(id)) {
            return null;
        }
        String sig = attachSig == null ? "" : attachSig;
        if (id.equals(confShipModel) && sig.length() == 0) {
            return null;    // 基础船模走共享的 sInstances
        }
        String key = sig.length() == 0 ? id : (id + "|" + sig);
        ModelSet s = shipSetCache.get(key);
        if (s != null) {
            s.lastUse = comboFrame;
            return s;
        }
        if (shipSetFailed.contains(key)) {
            return null;
        }
        if (shipSetQueue.size() < 32 && !shipSetQueue.contains(key)) {
            shipSetQueue.add(key);
        }
        return null;
    }

    /** v4.8.0:数组取下标(空数组/越界 = null)。 */
    private static String arrAt(String[] a, int i) {
        return (a != null && i >= 0 && i < a.length) ? a[i] : null;
    }

    /**
     * v4.8.0:过渡预热 —— 过渡一开始就把「陆军形态 + 船模形态」都排进按需加载队列
     * (缩小段有半个行程的时间,足够把目标模型加载完;避免放大段闪成默认模型)。
     */
    private static void prefetchMorph(String landCombo, String landAttach,
                                      String shipCombo, String shipAttach) {
        try {
            comboSet(landCombo, landAttach);   // 未加载 → 入队;已加载只刷新 lastUse
            shipSet(shipCombo, shipAttach);
        } catch (Throwable ignored) {
        }
    }

    /**
     * v4.9.7:预加载视内部队的 combo(模型暂时不画时提前排队)。
     * 缩小到兵模阈值以下、或刚缩放完的那几帧里,插件依然会采集部队并调这里 →
     * 加载队列被消费,放大回来立刻就是正确模型,不再是兜底模型。
     */
    public static void prefetch(String landCombo, String landAttach, String shipCombo, String shipAttach) {
        if (!isReady()) {
            return;
        }
        prefetchMorph(landCombo, landAttach, shipCombo, shipAttach);
    }

    /** v4.8.0:过渡目标形态的模型是否已就绪(已加载 / 已失败 / 无需加载)。 */
    private static boolean morphTargetReady(int targetKind, String shipCombo, String shipAttach,
                                            String landCombo, String landAttach) {
        if (targetKind == 1) {
            return shipModelReady(shipCombo, shipAttach);
        }
        return landModelReady(landCombo, landAttach);
    }

    /** v4.8.0:陆军 combo(combo|挂件)是否已可渲染。 */
    private static boolean landModelReady(String id, String attachSig) {
        if (id == null || id.length() == 0) {
            return true;   // 无 combo → 用默认模型,无需等待
        }
        String key = (attachSig == null || attachSig.length() == 0) ? id : (id + "|" + attachSig);
        if (comboCache.containsKey(key) || comboFailed.containsKey(key)) {
            return true;
        }
        return (id.equals(confModel) || id.equals(confShipModel))
                && (attachSig == null || attachSig.length() == 0);   // 默认模型无挂件 → 走共享实例
    }

    /** v4.8.0:船模 combo(船模|挂件)是否已可渲染。 */
    private static boolean shipModelReady(String modelId, String attachSig) {
        if (!usingShipModel) {
            return true;
        }
        String id = (modelId == null || modelId.length() == 0) ? confShipModel : modelId;
        if (id == null || id.length() == 0 || "builtin".equalsIgnoreCase(id) || "-".equals(id)) {
            return true;
        }
        String sig = attachSig == null ? "" : attachSig;
        if (id.equals(confShipModel) && sig.length() == 0) {
            return true;   // 基础船模走共享实例
        }
        String key = sig.length() == 0 ? id : (id + "|" + sig);
        return shipSetCache.containsKey(key) || shipSetFailed.contains(key);
    }

    /** v4.9.2:「战斗结束后是否继续播完攻击」判定缓存(键 = combo|挂件|默认模型)。 */
    private static final HashMap<String, Boolean> holdBattleCache = new HashMap<String, Boolean>();

    private static boolean holdBattleFor(String comboId, String attachSig) {
        if (confBattleHoldMs <= 0f) {
            return false;
        }
        String key = (comboId == null ? "-" : comboId) + "|"
                + (attachSig == null ? "" : attachSig) + "|" + (confModel == null ? "" : confModel);
        Boolean v = holdBattleCache.get(key);
        if (v != null) {
            return v.booleanValue();
        }
        WeaponProfile hp = weaponProfileFor(comboId, attachSig);
        if (hp == null) {
            hp = userProfile;
        }
        if (hp == null) {
            return false;   // 事件表还没就绪 → 本帧不缓存
        }
        boolean r = hasDelayedAttackEvents(hp);
        if (holdBattleCache.size() > 256) {
            holdBattleCache.clear();
        }
        holdBattleCache.put(key, Boolean.valueOf(r));
        return r;
    }

    /**
     * v4.9.1(B2):按位掩码推进动画控制器 —— 只更新上一帧真正画到的 (rel×state)。
     * 位定义:bit = rel*4 + st;掩码为 -1(全 1)时退化为旧行为(刚创建/刚重建时)。
     */
    private static void updateAnimsMasked(AnimationController[][] anims, long mask, boolean phaseBattle, float dt) {
        if (anims == null || mask == 0L) {
            return;
        }
        for (int r = 0; r < 4; r++) {
            for (int s = 0; s < 4; s++) {
                if ((mask & (1L << (r * 4 + s))) == 0L) {
                    continue;
                }
                if (s == 2 && phaseBattle) {
                    continue;   // v4.4.0:battle 由相位控制器负责
                }
                AnimationController c = anims[r][s];
                if (c == null) {
                    continue;
                }
                try {
                    c.update(dt);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 每帧最多加载 1 个 combo(在渲染前调用,便于必要时先重建批次)。 */
    private static void pumpComboQueue() {
        if (comboQueue.isEmpty()) {
            return;
        }
        // v4.9.6:上一次加载超预算 → 本帧先不加载,避免连续掉帧
        if (comboSleepFrames > 0) {
            comboSleepFrames--;
            return;
        }
        String key = comboQueue.remove(0);
        if (key == null || comboCache.containsKey(key) || comboFailedNow(key)) {
            return;
        }
        int bar = key.indexOf('|');
        String id = bar < 0 ? key : key.substring(0, bar);
        String attachSig = bar < 0 ? null : key.substring(bar + 1);
        boolean defaultNoAttach = (id.equals(confModel) || id.equals(confShipModel))
                && (attachSig == null || attachSig.length() == 0);
        if (defaultNoAttach) {
            return;     // v4.7.1:默认模型 + 无挂件才跳过;带挂件要正常加载
        }
        loadIntoCache(key, id, attachSig, false);
    }

    /**
     * 加载一个 ModelSet 并放入 combo / 船模挂件缓存。
     *
     * <p>两个队列的失败策略不同:combo 用退避重试(瞬时失败可自愈),船模组合用永久失败表;
     * 其余(耗时统计、骨骼上限、LRU 淘汰)完全一致 —— 旧版是两份逐行复制的实现。</p>
     *
     * @param ship true = 船模挂件组合(长边适配缓存)
     */
    private static void loadIntoCache(String key, String id, String attachSig, boolean ship) {
        HashMap<String, ModelSet> cache = ship ? shipSetCache : comboCache;
        long t0 = System.currentTimeMillis();
        Model m = loadComboModel(id, attachSig);
        if (m == null) {
            if (ship) {
                shipSetFailed.add(key);
            } else {
                noteComboFailure(key);
            }
            return;
        }
        int attached = tAttachedParts;
        try {
            ModelSet s = buildModelSet(m, key, id, attachSig, ship);
            // v4.3.0/v4.4.0:武器事件表按「挂了什么武器」优先,再退回 assembly 的 animSet/group
            s.animSet = comboAnimSets.get(id);
            s.weapon = weaponProfileFor(id, attachSig);
            cache.put(key, s);
            if (ship) {
                shipSetFailed.remove(key);
            } else {
                comboFailed.remove(key);   // v4.9.7:成功即清除失败记录(重试自愈后不再受影响)
            }
            long el = System.currentTimeMillis() - t0;
            comboLoads++;
            comboLoadMsSum += el;
            if (el > comboLoadMsMax) {
                comboLoadMsMax = el;
            }
            if (!ship) {
                applyLoadBudget(el);      // v4.9.6:超预算 → 后续几帧暂停加载
            }
            int nb = maxBonesOf(m);
            StringBuilder note = new StringBuilder();
            note.append(ship ? "船模[" : "combo[").append(id).append("] 已加载 ").append(el)
                    .append("ms 骨骼=").append(nb);
            if (attached > 0) {
                note.append(" 挂件=").append(attached);
            }
            if (!ship && s.animDonor != null) {
                note.append(" 借用动画=").append(s.animDonor);
            }
            if (s.animSet != null) {
                note.append(" animSet=").append(s.animSet);
            }
            if (s.weapon != null) {
                note.append(" 武器事件=").append(s.weapon.id);
                if (!ship) {
                    note.append('(').append(s.weapon.events.length).append("个)");
                }
            }
            if (!ship && confShadowAnchor) {
                note.append(" 阴影锚点=").append(r3(s.anchorX)).append(',').append(r3(s.anchorZ))
                        .append(" 偏移=").append(r3(s.shadowShift));
            }
            if (nb > detectedBones) {
                note.append(" → 着色器骨骼上限升到 ").append(nb);
                raiseBoneLimit(nb);
            }
            try {
                Gdx.app.log("G3DArmy", note.toString());
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            if (ship) {
                shipSetFailed.add(key);
            } else {
                noteComboFailure(key);
            }
            try {
                m.dispose();
            } catch (Throwable ignored) {
            }
        }
        // LRU 淘汰(至少 1800 帧没用过才释放)
        int cap = ship ? maxShipSets : maxComboSets;
        if (cache.size() > cap) {
            ModelSet victim = null;
            for (ModelSet v : cache.values()) {
                if (victim == null || v.lastUse < victim.lastUse) {
                    victim = v;
                }
            }
            if (victim != null && comboFrame - victim.lastUse > 1800L) {
                cache.remove(victim.id);
                try {
                    if (victim.model != null) {
                        victim.model.dispose();
                    }
                    for (int i = 0; i < victim.extras.size(); i++) {
                        victim.extras.get(i).dispose();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** v4.5.0:每帧最多加载 1 个船模挂件组合(船模适配固定按长边)。 */
    private static void pumpShipQueue() {
        if (shipSetQueue.isEmpty()) {
            return;
        }
        String key = shipSetQueue.remove(0);
        if (key == null || shipSetCache.containsKey(key) || shipSetFailed.contains(key)) {
            return;
        }
        int bar = key.indexOf('|');
        String id = bar < 0 ? key : key.substring(0, bar);
        String attachSig = bar < 0 ? null : key.substring(bar + 1);
        loadIntoCache(key, id, attachSig, true);
    }

    /* ================= v4.0.0:运行时挂件(武器/配饰) ================= */

    /** 懒加载 <partsDir>/index.json(368 件的挂点/贴图/包围盒)。 */
    private static void ensurePartsIndex() {
        if (partsIndexLoaded || dataDirPath == null) {
            return;
        }
        // v4.9.7:失败后每 120 帧重试一次(最多 20 次),成功后置 loaded
        if (partsIndexRetryFrame >= 0L && comboFrame - partsIndexRetryFrame < 120L) {
            return;
        }
        try {
            File f = findPartsIndexFile();
            if (f == null) {
                partsIndexNote = "挂件索引缺失(models/parts/index.json)";
                partsIndexRetry(0);
                return;
            }
            JsonValue root = new JsonReader().parse(readText(f));
            JsonValue parts = root.get("parts");
            if (parts == null) {
                partsIndexNote = "挂件索引无 parts 节点";
                partsIndexRetry(0);
                return;
            }
            for (JsonValue c = parts.child; c != null; c = c.next) {
                if (c.name != null) {
                    partsIndex.put(c.name, c);
                }
            }
            partsIndexLoaded = true;
            partsIndexNote = "挂件索引=" + partsIndex.size() + " 件";
            try {
                Gdx.app.log("G3DArmy", partsIndexNote);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            partsIndexNote = "挂件索引读取失败: " + chain(t);
            partsIndexRetry(1);
        }
    }

    /** v4.9.7:挂件索引重试计数(文件缺失不计次,读取异常计次;20 次后放弃)。 */
    private static void partsIndexRetry(int add) {
        partsIndexTries += add;
        partsIndexRetryFrame = comboFrame;
        if (partsIndexTries >= 20) {
            partsIndexLoaded = true;   // 放弃,避免无限重试
        }
    }

    /** 在若干目录里找同名文件(找不到返回 null)。 */
    private static File findInDirs(ArrayList<File> dirs, String base) {
        for (int i = 0; i < dirs.size(); i++) {
            File f = new File(dirs.get(i), base);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /** 读文本文件(挂件索引用)。 */
    private static String readText(File f) throws Exception {
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

    /** v4.0.1:combo 加载(可带运行时挂件;挂件在 ModelData 层合并 → 绑定身体骨树)。 */
    private static Model loadComboModel(String id, String attachSig) {
        if (dataDirPath == null) {
            return null;
        }
        try {
            File f = findModelFile(id);
            if (f == null) {
                return null;
            }
            String lower = f.getName().toLowerCase();
            boolean ub = lower.endsWith(".g3db");
            G3dModelLoader loader = ub ? new G3dModelLoader(new UBJsonReader()) : new G3dModelLoader(new JsonReader());
            FileHandle handle = new FileHandle(f);
            ModelData data = loader.loadModelData(handle, null);
            if (data == null) {
                return null;
            }
            stripGearParts(data, stripTokensFor(id));
            int attached = 0;
            if (attachSig != null && attachSig.length() > 0) {
                String[] toks = attachSig.split(";");
                for (int i = 0; i < toks.length; i++) {
                    if (toks[i] != null && toks[i].length() > 0 && mergePartData(data, toks[i])) {
                        attached++;
                    }
                }
            }
            tAttachedParts = attached;
            return buildFromData(loader, data, f);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** v4.0.0:本次加载附加信息(诊断)。 */
    private static int tAttachedParts = 0;

    /**
     * v4.0.1:把挂件合并进 body 的 ModelData(真正的骨架绑定):
     * ①追加 mesh/material(改名去重);②新建 ModelNodePart 挂到 body 的部件节点上,
     * `bones` 里写「目标骨名 → 部件自身绑定矩阵」→ libGDX `Model.loadNodes` 会自己
     * `invBoneBindTransforms.put(body.getNode(骨名), inv(矩阵))` → 随动画/手部运动。
     * 贴图改写成绝对路径(挂件 png 在 parts/ 下)。
     */
    private static boolean mergePartData(ModelData body, String token) {
        try {
            ensurePartsIndex();
            String name = token;
            String boneOverride = null;
            int at = token.indexOf('@');
            if (at > 0) {
                name = token.substring(0, at);
                boneOverride = token.substring(at + 1);
            }
            JsonValue idx = partsIndex.get(name);
            String file = idx != null ? idx.getString("file", name + ".g3dj") : name + ".g3dj";
            File partFile = findPartFile(file, name);
            if (partFile == null || !partFile.isFile()) {
                return false;
            }
            FileHandle ph = new FileHandle(partFile);
            boolean ub = file.toLowerCase().endsWith(".g3db");
            G3dModelLoader pl = ub ? new G3dModelLoader(new UBJsonReader()) : new G3dModelLoader(new JsonReader());
            ModelData pd = pl.loadModelData(ph, null);
            if (pd == null || pd.nodes == null) {
                return false;
            }
            // ① 材质(改名 + 贴图绝对路径)
            if (pd.materials != null) {
                for (int i = 0; i < pd.materials.size; i++) {
                    ModelMaterial mm = pd.materials.get(i);
                    if (mm == null) {
                        continue;
                    }
                    String oldId = mm.id;
                    mm.id = (oldId == null ? ("mat" + i) : oldId) + "#" + name;
                    if (mm.textures != null) {
                        for (int t = 0; t < mm.textures.size; t++) {
                            ModelTexture tex = mm.textures.get(t);
                            if (tex != null && tex.fileName != null) {
                                String base = new File(tex.fileName).getName();
                                // 挂件贴图:优先 models/parts/png,其次贴图索引目录
                                File hit = findInDirs(resPartPng, base);
                                if (hit == null) {
                                    hit = findInDirs(resTexDirs, base);
                                }
                                tex.fileName = (hit != null ? hit : new File(partFile.getParentFile(), base)).getAbsolutePath();
                            }
                        }
                    }
                    body.materials.add(mm);
                }
            }
            // ② 网格(改名)
            ObjectMap<String, String> meshMap = new ObjectMap<String, String>();
            if (pd.meshes != null) {
                for (int i = 0; i < pd.meshes.size; i++) {
                    ModelMesh mesh = pd.meshes.get(i);
                    if (mesh == null) {
                        continue;
                    }
                    String oldId = mesh.id;
                    String newId = (oldId == null ? ("mesh" + i) : oldId) + "#" + name;
                    mesh.id = newId;
                    if (oldId != null) {
                        meshMap.put(oldId, newId);
                    }
                    if (mesh.parts != null) {
                        for (int k = 0; k < mesh.parts.length; k++) {
                            if (mesh.parts[k] != null && mesh.parts[k].id != null) {
                                mesh.parts[k].id = mesh.parts[k].id + "#" + name;
                            }
                        }
                    }
                    body.meshes.add(mesh);
                }
            }
            // ③ 目标骨名
            String boneName = boneOverride;
            if ("wea".equals(boneName)) {
                boneName = "Unit_Wea_Right_Hand01";
            } else if ("left".equals(boneName)) {
                boneName = "Unit_Base_Rig_LeftHand";
            } else if ("spine1".equals(boneName)) {
                boneName = "Unit_Base_Rig_Spine1";
            } else if ("spine2".equals(boneName)) {
                boneName = "Unit_Base_Rig_Spine2";
            }
            String mode = "official";
            if (boneName == null && idx != null && idx.get("mount") != null) {
                JsonValue mount = idx.get("mount");
                boneName = mount.getString("bone", null);
                mode = mount.getString("mode", "official");
            }
            if (boneName == null || boneName.length() == 0) {
                boneName = "Unit_Wea_Right_Hand01";
            }
            // ④ 宿主节点(带部件的节点)
            ModelNode host = findFirstPartsNode(body.nodes);
            if (host == null) {
                return false;
            }
            // ⑤ 收集挂件的 nodeParts,重映射骨名
            java.util.ArrayList<ModelNodePart> add = new java.util.ArrayList<ModelNodePart>();
            if (pd.nodes != null) {
                for (int i = 0; i < pd.nodes.size; i++) {
                    collectPartsRemapOne(pd.nodes.get(i), name, boneName, add);
                }
            }
            if (add.isEmpty()) {
                return false;
            }
            int oldLen = host.parts == null ? 0 : host.parts.length;
            ModelNodePart[] np = new ModelNodePart[oldLen + add.size()];
            if (oldLen > 0) {
                System.arraycopy(host.parts, 0, np, 0, oldLen);
            }
            for (int i = 0; i < add.size(); i++) {
                np[oldLen + i] = add.get(i);
            }
            host.parts = np;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 递归收集挂件部件的引用并把骨骼重映射到 body 的目标骨。 */
    private static void collectPartsRemapOne(ModelNode n, String name, String boneName,
                                             java.util.ArrayList<ModelNodePart> out) {
        if (n == null) {
            return;
        }
        if (n.parts != null) {
            for (int k = 0; k < n.parts.length; k++) {
                ModelNodePart p = n.parts[k];
                if (p == null || p.meshPartId == null) {
                    continue;
                }
                ModelNodePart c = new ModelNodePart();
                c.meshPartId = p.meshPartId + "#" + name;
                c.materialId = p.materialId == null ? null : (p.materialId + "#" + name);
                c.uvMapping = p.uvMapping;
                c.bones = new ArrayMap<String, Matrix4>();
                if (p.bones != null && p.bones.size > 0) {
                    // 部件自己的绑定矩阵 C 沿用(官方件=单位阵;烘焙件=C),骨名换成 body 目标骨
                    Matrix4 bind = p.bones.getValueAt(0);
                    c.bones.put(boneName, bind == null ? new Matrix4().idt() : new Matrix4(bind));
                } else {
                    c.bones.put(boneName, new Matrix4().idt());
                }
                out.add(c);
            }
        }
        if (n.children != null) {
            for (int i = 0; i < n.children.length; i++) {
                collectPartsRemapOne(n.children[i], name, boneName, out);
            }
        }
    }

    /** 递归找第一个带部件的 ModelNode(挂件宿主)。 */
    private static ModelNode findFirstPartsNode(Array<ModelNode> nodes) {
        if (nodes == null) {
            return null;
        }
        for (int i = 0; i < nodes.size; i++) {
            ModelNode found = findFirstPartsNodeOne(nodes.get(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static ModelNode findFirstPartsNodeOne(ModelNode n) {
        if (n == null) {
            return null;
        }
        if (n.parts != null && n.parts.length > 0) {
            return n;
        }
        if (n.children != null) {
            for (int i = 0; i < n.children.length; i++) {
                ModelNode c = findFirstPartsNodeOne(n.children[i]);
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
    }

    /** v4.0.0:命中 confStripCombos 前缀的 combo 返回剥离关键词表,否则 null。 */
    private static String[] stripTokensFor(String comboId) {
        if (confStripCombos == null || confStripCombos.length == 0
                || confStripTokens == null || confStripTokens.length == 0 || comboId == null) {
            return null;
        }
        for (int i = 0; i < confStripCombos.length; i++) {
            String p = confStripCombos[i];
            if (p != null && p.length() > 0 && comboId.startsWith(p)) {
                return confStripTokens;
            }
        }
        return null;
    }

    /** 骨骼上限提高 → 必须换一个着色器提供者(Config.numBones 只在建批次时生效)。 */
    private static void raiseBoneLimit(int nb) {
        if (nb <= detectedBones) {
            return;
        }
        // v4.9.4:配置里钉死了 numBones 且足够用时，不重建 ModelBatch —— 重建 = 重新编译
        // DefaultShader(Adreno 上 100~600ms)，正是镜头扫到新兵种时的卡顿来源。
        if (confNumBones > 0 && nb <= confNumBones) {
            detectedBones = nb;
            return;
        }
        detectedBones = nb;
        try {
            if (batch != null) {
                batch.dispose();
            }
        } catch (Throwable ignored) {
        }
        batch = createBatch();
    }

    /** 为一个 combo 建 4×4 实例 + 自动适配(与默认用户模型同一套规则)。 */
    private static ModelSet buildModelSet(Model m, String id, String comboId, String attachSig) {
        return buildModelSet(m, id, comboId, attachSig, false);
    }

    /**
     * v4.5.0:可指定长边适配的版本(船模=true:按 max(高,水平长边) 缩放,不裁切)。
     */
    private static ModelSet buildModelSet(Model m, String id, String comboId, String attachSig, boolean longEdgeFit) {
        // v4.4.0:武器决定动画 —— 挂了什么武器就借对应 animSet 的动画(同 rig 骨骼一致)
        // 必须在建 ModelInstance 之前换(ModelInstance 构造时会拷贝 model.animations)
        String donorNote = applyWeaponAnimOverride(m, comboId, attachSig);
        ModelSet s = new ModelSet(id);
        s.animDonor = donorNote;
        s.model = m;
        boolean hasAny = m.animations != null && m.animations.size > 0;
        String firstId = hasAny ? m.animations.get(0).id : null;
        String baseId = resolveAnim(m, confAnimation, null, firstId);
        s.animIds[0] = resolveAnim(m, confAnimIdle, KW_IDLE, baseId);
        s.animIds[1] = resolveAnim(m, confAnimMove, KW_MOVE, baseId);
        s.animIds[2] = resolveAnim(m, confAnimBattle, KW_BATTLE, baseId);
        s.animIds[3] = resolveAnim(m, confAnimRetreat, KW_RETREAT, s.animIds[1]);
        float speed = confAnimSpeed > 0f ? confAnimSpeed : 1f;
        for (int rel = 0; rel < 4; rel++) {
            for (int st = 0; st < 4; st++) {
                ModelInstance inst = new ModelInstance(m);
                s.inst[rel][st] = inst;
                if (s.animIds[st] != null) {
                    AnimationController c = new AnimationController(inst);
                    c.setAnimation(s.animIds[st], 0f, -1f, -1, speed, null);
                    s.anim[rel][st] = c;
                }
            }
        }
        // v4.4.0:battle 相位实例(6 桶错峰开火)
        // 注意:setAnimation 的 offset 参数会把 duration 改成「动画时长-offset」→ 循环会丢尾巴;
        // 所以先按 offset=0 建,再直接改 current.time 到目标相位(循环时仍按完整时长回绕)。
        // 只有「会开火」的 combo(有 animSet/group → 有武器事件表)才建,免得骆驼/大象白建 24 套。
        if (s.animIds[2] != null && weaponProfileFor(comboId, attachSig) != null) {
            float dur = animDuration(m, s.animIds[2]);
            for (int rel = 0; rel < 4; rel++) {
                for (int ph = 0; ph < PHASE; ph++) {
                    ModelInstance bi = new ModelInstance(m);
                    s.battleInst[rel][ph] = bi;
                    AnimationController c = new AnimationController(bi);
                    AnimationController.AnimationDesc ad = c.setAnimation(s.animIds[2], 0f, -1f, -1, speed, null);
                    if (ad != null) {
                        ad.duration = dur;
                        ad.time = dur > 0f ? dur * ph / PHASE : 0f;
                        try {
                            c.update(0f);
                        } catch (Throwable ignored) {
                        }
                    }
                    s.battleAnim[rel][ph] = c;
                    s.battlePrev[rel * PHASE + ph] = -1f;
                }
            }
        }
        try {
            BoundingBox bb = new BoundingBox();
            s.inst[0][0].calculateBoundingBox(bb);
            // v4.1.4:包围盒/锚点只取本体几何 —— 运行时挂件顶点在骨局部空间,
            // 若混入会让包围盒把手骨坐标当模型坐标(模型被抬离地面)、锚点被拉到手/兵器位置。
            float[] body = computeBodyBounds(m);
            if (body != null) {
                bb.min.set(body[0], body[1], body[2]);
                bb.max.set(body[3], body[4], body[5]);
                if (confShadowAnchor) {
                    s.anchorX = body[6];
                    s.anchorZ = body[7];
                }
                s.bbMinX = body[0];
                s.bbMinY = body[1];
                s.bbMinZ = body[2];
                s.bbMaxX = body[3];
                s.bbMaxY = body[4];
                s.bbMaxZ = body[5];
            }
            float sc0 = autoFitScale(bb, longEdgeFit || confFitByMax);
            if (sc0 > 0f) {
                float sc = confModelScale > 0f ? confModelScale : sc0;
                s.fitScale = sc;
                s.invFitScale = 1f / sc;
                s.dx = -(bb.min.x + bb.max.x) * 0.5f * sc;
                s.dz = -(bb.min.z + bb.max.z) * 0.5f * sc;
                s.fit.setToTranslation(s.dx, -bb.min.y * sc + confModelOffsetY, s.dz);
                s.fit.scale(sc, sc, sc);
                s.shadowScale = shadowScaleFor(Math.max(bb.max.x - bb.min.x, bb.max.z - bb.min.z) * sc);
                s.shadowShift = shadowShiftFor((bb.max.y - bb.min.y) * sc);
                // v4.9.9:环绕几何(世界坐标):竖直中心/半高 + 水平半宽(含阴影盘与任意朝向)
                float bh = (bb.max.y - bb.min.y) * sc;
                float bx = (bb.max.x - bb.min.x) * sc;
                float bz = (bb.max.z - bb.min.z) * sc;
                s.halfV = 0.5f * bh;
                s.centerY = s.halfV + confModelOffsetY;
                float bodyR = 0.5f * (float) Math.sqrt(bx * bx + bz * bz);
                float shR = 0.62f * s.shadowScale + Math.abs(s.shadowShift);
                s.halfD = bodyR > shR ? bodyR : shR;
            }
        } catch (Throwable ignored) {
        }
        computeMuzzle(m, s);
        s.baseDiff = captureBase(s.inst[0][0]);
        return s;
    }

    /**
     * v4.7.3:找运行时挂件(武器)网格,记录其在挂点骨局部空间的远端顶点(枪口/刀尖)。
     * 只处理「单骨绑定 + 骨名包含 Weapon_Grip」的刚性挂件;找不到则用骨骼/包围盒兜底。
     */
    private static void computeMuzzle(Model m, ModelSet s) {
        try {
            if (m == null || m.nodes == null || s == null) {
                return;
            }
            for (int ni = 0; ni < m.nodes.size; ni++) {
                Node n = m.nodes.get(ni);
                if (n == null || n.parts == null) {
                    continue;
                }
                for (int pi = 0; pi < n.parts.size; pi++) {
                    NodePart p = n.parts.get(pi);
                    if (p == null || p.meshPart == null || p.meshPart.mesh == null
                            || p.invBoneBindTransforms == null || p.invBoneBindTransforms.size != 1) {
                        continue;
                    }
                    Node bone = p.invBoneBindTransforms.getKeyAt(0);
                    if (bone == null || bone.id == null
                            || bone.id.toLowerCase().indexOf("weapon_grip") < 0) {
                        continue;
                    }
                    Mesh mesh = p.meshPart.mesh;
                    VertexAttribute pa = mesh.getVertexAttribute(VertexAttributes.Usage.Position);
                    int stride = mesh.getVertexSize() / 4;
                    if (pa == null || stride <= 0) {
                        continue;
                    }
                    float[] verts = readVerts(mesh, stride);
                    if (verts == null) {
                        continue;
                    }
                    int off = pa.offset / 4;
                    float bx = 0f, by = 0f, bz = 0f;
                    float best = -1f;
                    for (int k = off; k + 2 < verts.length; k += stride) {
                        float x = verts[k];
                        float y = verts[k + 1];
                        float z = verts[k + 2];
                        float d = x * x + y * y + z * z;
                        if (d > best) {
                            best = d;
                            bx = x;
                            by = y;
                            bz = z;
                        }
                    }
                    if (best < 0f) {
                        continue;
                    }
                    s.hasMuzzle = true;
                    s.muzzleBone = bone.id;
                    s.muzzleX = bx;
                    s.muzzleY = by;
                    s.muzzleZ = bz;
                    if (G3DArmyPlugin.logEnabled()) {
                        try {
                            Gdx.app.log("G3DArmy", "枪口挂件 " + s.id + " bone=" + bone.id
                                    + " local=(" + r3(bx) + "," + r3(by) + "," + r3(bz) + ")"
                                    + " dist=" + r3((float) Math.sqrt(best)));
                        } catch (Throwable ignored2) {
                        }
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** v4.1.1:根据模型水平尺寸算接触阴影缩放(阴影 quad 直径 = 1.24 世界单位)。 */
    private static float shadowScaleFor(float worldWidth) {
        float s = worldWidth / 1.24f * 1.0f;
        if (s < 0.75f) {
            s = 0.75f;
        } else if (s > 2.6f) {
            s = 2.6f;
        }
        s *= (confShadowScale > 0.05f ? confShadowScale : 1f);
        return s;
    }

    /**
     * v4.1.4:阴影前后偏移(世界单位)= 固定偏移 + 按模型高度的比例偏移。
     * 比例项让“大模型/小模型”在屏幕上保持同样的偏移观感(固定世界单位会随尺寸显得不一样)。
     */
    private static float shadowShiftFor(float worldHgt) {
        return confShadowShiftZ + confShadowShiftPct * (worldHgt > 0f ? worldHgt : 0f);
    }

    /** v4.1.4:阴影锚点探测带(模型高度最低这一比例内的顶点 = 「脚底接触带」)。 */
    private static final float ANCHOR_BAND = 0.12f;

    /** v4.1.4:自根向叶算节点局部/世界变换(父节点必须先算,否则子节点拿到旧矩阵)。 */
    private static void ensureNodeWorld(Node n, int depth) {
        if (n == null || depth > 12) {
            return;
        }
        ensureNodeWorld(n.getParent(), depth + 1);
        n.calculateLocalTransform();
        n.calculateWorldTransform();
    }

    /**
     * v4.1.4:该部件是否属于「本体几何」。
     * 运行时挂件的绑定矩阵是单位阵(顶点在手骨局部空间)→ 排除。不排除的话:
     * ① libGDX 的包围盒会把手骨局部坐标当模型坐标 → 模型被"挂件最低点"抬离地面(悬空);
     * ② 锚点被拉到手/兵器位置 → 阴影随朝向甩动。
     */
    private static boolean isBodyPart(NodePart p) {
        if (p == null || p.invBoneBindTransforms == null || p.invBoneBindTransforms.size <= 0) {
            return true;    // 静态部件(顶点在节点空间)照常参与
        }
        Matrix4 inv = null;
        for (ObjectMap.Entry<Node, Matrix4> e : p.invBoneBindTransforms) {
            inv = e.value;
            break;
        }
        if (inv == null) {
            return true;
        }
        float[] v = inv.val;
        return !(Math.abs(v[Matrix4.M00] - 1f) < 1e-3f && Math.abs(v[Matrix4.M11] - 1f) < 1e-3f
                && Math.abs(v[Matrix4.M22] - 1f) < 1e-3f
                && Math.abs(v[Matrix4.M01]) < 1e-3f && Math.abs(v[Matrix4.M02]) < 1e-3f
                && Math.abs(v[Matrix4.M10]) < 1e-3f && Math.abs(v[Matrix4.M12]) < 1e-3f
                && Math.abs(v[Matrix4.M20]) < 1e-3f && Math.abs(v[Matrix4.M21]) < 1e-3f
                && Math.abs(v[Matrix4.M03]) < 1e-3f && Math.abs(v[Matrix4.M13]) < 1e-3f
                && Math.abs(v[Matrix4.M23]) < 1e-3f);
    }

    /** v4.1.4:部件顶点 → 模型空间的变换(蒙皮:bone.globalTransform × invBind;静态:节点世界变换)。 */
    private static Matrix4 partTransform(Node n, NodePart p) {
        if (p.invBoneBindTransforms != null && p.invBoneBindTransforms.size > 0) {
            Node boneNode = null;
            Matrix4 invBind = null;
            for (ObjectMap.Entry<Node, Matrix4> e : p.invBoneBindTransforms) {
                boneNode = e.key;
                invBind = e.value;
                break;
            }
            if (boneNode != null && invBind != null) {
                return new Matrix4(boneNode.globalTransform).mul(invBind);
            }
        }
        return n.globalTransform;
    }

    private static float[] readVerts(Mesh mesh, int stride) {
        try {
            float[] v = new float[mesh.getNumVertices() * stride];
            mesh.getVertices(v);
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    private static float tx(Matrix4 m, float x, float y, float z) {
        return m.val[Matrix4.M00] * x + m.val[Matrix4.M10] * y + m.val[Matrix4.M20] * z + m.val[Matrix4.M30];
    }

    private static float ty(Matrix4 m, float x, float y, float z) {
        return m.val[Matrix4.M01] * x + m.val[Matrix4.M11] * y + m.val[Matrix4.M21] * z + m.val[Matrix4.M31];
    }

    private static float tz(Matrix4 m, float x, float y, float z) {
        return m.val[Matrix4.M02] * x + m.val[Matrix4.M12] * y + m.val[Matrix4.M22] * z + m.val[Matrix4.M32];
    }

    /**
     * v4.1.4:统计「本体几何」包围盒 + 脚底接触带锚点(都在模型空间)。
     * 只看本体(排除运行时挂件):包围盒用于贴地/缩放/阴影尺寸,锚点用于阴影水平位置。
     * 锚点 = 最低 ANCHOR_BAND 高度带内顶点的均值(按顶点密度加权 → 靴/蹄密、兵器柄稀),
     * 再限幅在包围盒中心 ±35% 长度内,防止低垂马尾/长兵器把阴影拉飞。
     *
     * @return float[]{minX,minY,minZ,maxX,maxY,maxZ,anchorX,anchorZ};无本体几何返回 null
     */
    private static float[] computeBodyBounds(Model m) {
        if (m == null || m.nodes == null || m.nodes.size == 0) {
            return null;
        }
        for (int i = 0; i < m.nodes.size; i++) {
            try {
                ensureNodeWorld(m.nodes.get(i), 0);
            } catch (Throwable ignored) {
            }
        }
        // ① 本体 y 范围(确定接触带高度)
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int ni = 0; ni < m.nodes.size; ni++) {
            Node n = m.nodes.get(ni);
            if (n == null || n.parts == null) {
                continue;
            }
            for (int pi = 0; pi < n.parts.size; pi++) {
                NodePart p = n.parts.get(pi);
                if (!isBodyPart(p) || p.meshPart == null || p.meshPart.mesh == null) {
                    continue;
                }
                Mesh mesh = p.meshPart.mesh;
                VertexAttribute pa = mesh.getVertexAttribute(VertexAttributes.Usage.Position);
                int stride = mesh.getVertexSize() / 4;
                if (pa == null || stride <= 0 || mesh.getNumVertices() <= 0) {
                    continue;
                }
                float[] verts = readVerts(mesh, stride);
                if (verts == null) {
                    continue;
                }
                Matrix4 xf = partTransform(n, p);
                int off = pa.offset / 4;
                for (int k = off; k + 2 < verts.length; k += stride) {
                    float y = ty(xf, verts[k], verts[k + 1], verts[k + 2]);
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (minY > maxY || maxY - minY <= 1e-4f) {
            return null;
        }
        float yLimit = minY + (maxY - minY) * ANCHOR_BAND;
        // ② 全包围盒 + 接触带均值
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        double sumX = 0.0, sumZ = 0.0;
        int count = 0;
        for (int ni = 0; ni < m.nodes.size; ni++) {
            Node n = m.nodes.get(ni);
            if (n == null || n.parts == null) {
                continue;
            }
            for (int pi = 0; pi < n.parts.size; pi++) {
                NodePart p = n.parts.get(pi);
                if (!isBodyPart(p) || p.meshPart == null || p.meshPart.mesh == null) {
                    continue;
                }
                Mesh mesh = p.meshPart.mesh;
                VertexAttribute pa = mesh.getVertexAttribute(VertexAttributes.Usage.Position);
                int stride = mesh.getVertexSize() / 4;
                if (pa == null || stride <= 0 || mesh.getNumVertices() <= 0) {
                    continue;
                }
                float[] verts = readVerts(mesh, stride);
                if (verts == null) {
                    continue;
                }
                Matrix4 xf = partTransform(n, p);
                int off = pa.offset / 4;
                for (int k = off; k + 2 < verts.length; k += stride) {
                    float x = tx(xf, verts[k], verts[k + 1], verts[k + 2]);
                    float y = ty(xf, verts[k], verts[k + 1], verts[k + 2]);
                    float z = tz(xf, verts[k], verts[k + 1], verts[k + 2]);
                    if (x < x0) {
                        x0 = x;
                    }
                    if (x > x1) {
                        x1 = x;
                    }
                    if (y < y0) {
                        y0 = y;
                    }
                    if (y > y1) {
                        y1 = y;
                    }
                    if (z < z0) {
                        z0 = z;
                    }
                    if (z > z1) {
                        z1 = z;
                    }
                    if (y <= yLimit) {
                        sumX += x;
                        sumZ += z;
                        count++;
                    }
                }
            }
        }
        if (x0 > x1 || z0 > z1) {
            return null;
        }
        float ax = count >= 6 ? (float) (sumX / count) : (x0 + x1) * 0.5f;
        float az = count >= 6 ? (float) (sumZ / count) : (z0 + z1) * 0.5f;
        float cx = (x0 + x1) * 0.5f;
        float cz = (z0 + z1) * 0.5f;
        float limX = (x1 - x0) * 0.35f;
        float limZ = (z1 - z0) * 0.35f;
        ax = cx + Math.max(-limX, Math.min(limX, ax - cx));
        az = cz + Math.max(-limZ, Math.min(limZ, az - cz));
        return new float[]{x0, y0, z0, x1, y1, z1, ax, az};
    }

    /** v4.1.0:记录实例材质基准 diffuse 色(染色插值的底)。 */
    private static Array<Color> captureBase(ModelInstance inst) {
        if (inst == null || inst.materials == null) {
            return null;
        }
        Array<Color> out = new Array<Color>(inst.materials.size);
        for (int i = 0; i < inst.materials.size; i++) {
            Material m = inst.materials.get(i);
            ColorAttribute ca = m == null ? null : m.get(ColorAttribute.class, ColorAttribute.Diffuse);
            out.add(ca == null ? new Color(1f, 1f, 1f, 1f) : new Color(ca.color));
        }
        return out;
    }

    /** v4.1.0:该材质是否跳过染色(名称命中 confTintSkip)。 */
    private static boolean tintSkipMaterial(String id) {
        if (id == null || confTintSkip == null || confTintSkip.length == 0) {
            return false;
        }
        String low = id.toLowerCase();
        for (int i = 0; i < confTintSkip.length; i++) {
            String k = confTintSkip[i];
            if (k != null && k.length() > 0 && low.contains(k.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * v4.1.0:按部队颜色动态染色(EU5 式):材质 diffuse = 基准色 ↔ 部队色按强度插值。
     * 每帧开画前调用 —— 因为实例是 4 关系色 × 4 状态共享的,不同文明要各染各的。
     */
    private static void applyTint(ModelInstance inst, Array<Color> base, int rgb) {
        if (inst == null || base == null) {
            return;
        }
        float tr = ((rgb >> 16) & 0xFF) / 255f;
        float tg = ((rgb >> 8) & 0xFF) / 255f;
        float tb = (rgb & 0xFF) / 255f;
        float s = confTintStrength;
        if (s <= 0f) {
            resetTint(inst, base);
            return;
        }
        if (s > 1f) {
            s = 1f;
        }
        for (int i = 0; i < inst.materials.size && i < base.size; i++) {
            Material m = inst.materials.get(i);
            if (m == null || tintSkipMaterial(m.id)) {
                continue;
            }
            Color bc = base.get(i);
            float r = bc.r + (tr - bc.r) * s;
            float g = bc.g + (tg - bc.g) * s;
            float b = bc.b + (tb - bc.b) * s;
            ColorAttribute ca = m.get(ColorAttribute.class, ColorAttribute.Diffuse);
            if (ca == null) {
                m.set(ColorAttribute.createDiffuse(r, g, b, 1f));
            } else {
                ca.color.set(r, g, b, 1f);
            }
        }
    }

    /** v4.1.0:恢复材质基准色(实例被另一支无染色部队复用时必须执行)。 */
    private static void resetTint(ModelInstance inst, Array<Color> base) {
        if (inst == null || base == null) {
            return;
        }
        for (int i = 0; i < inst.materials.size && i < base.size; i++) {
            Material m = inst.materials.get(i);
            if (m == null || tintSkipMaterial(m.id)) {
                continue;
            }
            Color bc = base.get(i);
            ColorAttribute ca = m.get(ColorAttribute.class, ColorAttribute.Diffuse);
            if (ca == null) {
                m.set(ColorAttribute.createDiffuse(bc.r, bc.g, bc.b, 1f));
            } else {
                ca.color.set(bc);
            }
        }
    }

    /**
     * v3.0.1:自动适配缩放(适配占比 + 适配维度可配)。
     * 按高度(旧行为)时只看 bbox 高;按最大边时取 max(高, 水平长边) —— 马/船等长模型不再被裁切。
     * @return 缩放倍率;包围盒退化时返回 0
     */
    private static float autoFitScale(BoundingBox bb) {
        return autoFitScale(bb, confFitByMax);
    }

    /**
     * v4.5.0:可指定适配维度的版本(船模固定按最大边,马/船等长模型不裁切)。
     */
    private static float autoFitScale(BoundingBox bb, boolean byMax) {
        float h = bb.max.y - bb.min.y;
        float w = Math.max(bb.max.x - bb.min.x, bb.max.z - bb.min.z);
        float ref = byMax ? Math.max(h, w) : h;
        if (ref <= 1e-3f) {
            return 0f;
        }
        float pct = confFitPct > 0.1f ? Math.min(confFitPct, 1.2f) : 0.85f;
        return (worldH > 0f ? worldH : 2.2f) * pct / ref;
    }

    /** 保留三位小数的日志格式化。 */
    private static float r3(float v) {
        return Math.round(v * 1000f) / 1000f;
    }

    /** 解析某状态动画:"-"=禁用;指定名=精确匹配(缺失回退 fallback);空=按关键词自动匹配(缺省回退 fallback)。 */
    private static String resolveAnim(Model m, String spec, String[] keywords, String fallback) {
        try {
            if ("-".equals(spec)) {
                return null;
            }
            if (spec != null && spec.length() > 0) {
                for (int i = 0; i < m.animations.size; i++) {
                    if (spec.equals(m.animations.get(i).id)) {
                        return spec;
                    }
                }
                return fallback;
            }
            if (keywords != null) {
                for (int i = 0; i < m.animations.size; i++) {
                    String id = m.animations.get(i).id;
                    if (id == null) {
                        continue;
                    }
                    String low = id.toLowerCase();
                    for (int k = 0; k < keywords.length; k++) {
                        if (low.contains(keywords[k])) {
                            return id;
                        }
                    }
                }
            }
            return fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 内置模型时:把内置兵模导出为 dataDir 的格式示例(仅首次),并做可加载自检。 */
    private static void tryWriteTemplate() {
        try {
            if (dataDirPath == null) {
                return;
            }
            // v4.2.0:示例模型放 models/main/g3d/(新结构),老目录仅在无 models/ 时兜底
            File modelsRoot = new File(dataDirPath, "models");
            File g3dDir = modelsRoot.isDirectory() || !new File(dataDirPath, "g3d").isDirectory()
                    ? new File(new File(modelsRoot, "main"), "g3d")
                    : new File(dataDirPath, "g3d");
            if (!g3dDir.isDirectory()) {
                g3dDir.mkdirs();
            }
            File out = new File(g3dDir, "soldier-template.g3dj");
            if (out.exists()) {
                return;
            }
            Model m = models[0];
            if (m == null) {
                return;
            }
            String json = buildTemplateG3dj(m);
            if (json == null) {
                return;
            }
            FileWriter w = new FileWriter(out);
            w.write(json);
            w.close();
            String check;
            try {
                Model t = new G3dModelLoader(new JsonReader()).loadModel(Gdx.files.absolute(out.getAbsolutePath()));
                t.dispose();
                check = "自检通过";
            } catch (Throwable te) {
                check = "自检失败(" + chain(te) + ")";
            }
            modelNote = "已生成格式示例 g3d/soldier-template.g3dj:" + check;
        } catch (Throwable ignored) {
        }
    }

    /** 把运行时 Model 导出成 g3dj 文本(几何+材质;可作格式示例/可编辑起点)。 */
    private static String buildTemplateG3dj(Model m) {
        Array<Mesh> meshes = new Array<Mesh>();
        Array<Array<PartRec>> recs = new Array<Array<PartRec>>();
        Array<Material> mats = new Array<Material>();
        for (int i = 0; i < m.nodes.size; i++) {
            collectParts(m.nodes.get(i), meshes, recs, mats);
        }
        if (meshes.size == 0 || mats.size == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder(1 << 16);
        sb.append("{\n");
        sb.append("\"version\":[0,1],\n");
        sb.append("\"id\":\"soldierTemplate\",\n");
        // 材质
        sb.append("\"materials\":[");
        for (int i = 0; i < mats.size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Color c = new Color(1f, 1f, 1f, 1f);
            try {
                ColorAttribute ca = mats.get(i).get(ColorAttribute.class, ColorAttribute.Diffuse);
                if (ca != null) {
                    c = ca.color;
                }
            } catch (Throwable ignored) {
            }
            sb.append("{\"id\":\"m").append(i).append("\",\"diffuse\":[")
                    .append(f(c.r)).append(',').append(f(c.g)).append(',')
                    .append(f(c.b)).append(',').append(f(c.a)).append("]}");
        }
        sb.append("],\n");
        // 网格 + 部件
        sb.append("\"meshes\":[");
        for (int i = 0; i < meshes.size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Mesh mesh = meshes.get(i);
            float[] v = new float[mesh.getNumVertices() * (mesh.getVertexSize() / 4)];
            mesh.getVertices(v);
            short[] idx = new short[mesh.getNumIndices()];
            mesh.getIndices(idx);
            sb.append("{\"id\":\"mesh").append(i)
                    .append("\",\"attributes\":[\"POSITION\",\"NORMAL\"],\"vertices\":[");
            for (int k = 0; k < v.length; k++) {
                if (k > 0) {
                    sb.append(',');
                }
                sb.append(f(v[k]));
            }
            sb.append("],\"parts\":[");
            Array<PartRec> list = recs.get(i);
            for (int k = 0; k < list.size; k++) {
                if (k > 0) {
                    sb.append(',');
                }
                PartRec r = list.get(k);
                sb.append("{\"id\":\"").append(r.id).append("\",\"type\":\"TRIANGLES\",\"indices\":[");
                for (int x = r.offset; x < r.offset + r.size && x < idx.length; x++) {
                    if (x > r.offset) {
                        sb.append(',');
                    }
                    sb.append(idx[x] & 0xFFFF);
                }
                sb.append("]}");
            }
            sb.append("]}");
        }
        sb.append("],\n");
        // 节点(几何已烘焙到世界坐标,每个网格一个节点)
        sb.append("\"nodes\":[");
        for (int i = 0; i < meshes.size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"n").append(i).append("\",\"mesh\":\"mesh").append(i).append("\",\"parts\":[");
            Array<PartRec> list = recs.get(i);
            for (int k = 0; k < list.size; k++) {
                if (k > 0) {
                    sb.append(',');
                }
                sb.append("{\"meshpartid\":\"").append(list.get(k).id)
                        .append("\",\"materialid\":\"m").append(list.get(k).mat).append("\"}");
            }
            sb.append("]}");
        }
        sb.append("],\n");
        // 场景
        sb.append("\"scenes\":[{\"id\":\"scene\",\"nodes\":[");
        for (int i = 0; i < meshes.size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("\"n").append(i).append("\"");
        }
        sb.append("]}]\n}\n");
        return sb.toString();
    }

    private static void collectParts(Node n, Array<Mesh> meshes, Array<Array<PartRec>> recs, Array<Material> mats) {
        for (int i = 0; i < n.parts.size; i++) {
            NodePart p = n.parts.get(i);
            if (p == null || p.material == null || p.meshPart == null || p.meshPart.mesh == null) {
                continue;
            }
            Mesh mesh = p.meshPart.mesh;
            int mi = meshes.indexOf(mesh, true);
            if (mi < 0) {
                meshes.add(mesh);
                recs.add(new Array<PartRec>());
                mi = meshes.size - 1;
            }
            PartRec r = new PartRec();
            r.id = p.meshPart.id != null ? p.meshPart.id : ("part" + recs.get(mi).size);
            r.offset = p.meshPart.offset;
            r.size = p.meshPart.size;
            int matIdx = mats.indexOf(p.material, true);
            if (matIdx < 0) {
                mats.add(p.material);
                matIdx = mats.size - 1;
            }
            r.mat = matIdx;
            recs.get(mi).add(r);
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            collectParts(n.getChild(i), meshes, recs, mats);
        }
    }

    private static final class PartRec {
        String id;
        int offset;
        int size;
        int mat;
    }

    /** 紧凑浮点输出(整数不带小数点,其余最多 4 位小数)。 */
    private static String f(float v) {
        if (v == (int) v) {
            return String.valueOf((int) v);
        }
        return String.valueOf(Math.round(v * 10000f) / 10000f);
    }

    private static float clampWorldH(float v) {
        if (v < 0.5f) {
            return 0.5f;
        }
        if (v > 50f) {
            return 50f;
        }
        return v;
    }

    /**
     * v4.9.9:把相机放到「以模型中心为圆心的圆弧」上(= 纯 3D 环绕:圆心 = 模型竖直中心,
     * 半径 dist,极角 = 俯角),并按当前俯角下的投影包围自动拉远(缩小),
     * 保证环绕全程模型(含脚下阴影盘)不超出窗口。
     *
     * <p>正交相机下半径只决定朝向(沿视线方向平移不改成像),所以模型中心恒落在窗口中心;
     * 俯角变小 → 更接近平视(立体感强),变大 → 更俯视。</p>
     *
     * @param centerY 环绕圆心(世界 Y;= 模型竖直中心)
     * @param halfV   模型竖直半高(世界单位)
     * @param halfD   模型水平半宽(世界单位;取 XZ 对角线之半 → 任意朝向都不溢出)
     * @param aspect  窗口宽/高比(pw/ph)
     */
    private static void applyCameraRig(float centerY, float halfV, float halfD, float aspect) {
        if (camera == null) {
            return;
        }
        ensureRigAngle();
        float sn = rigSn;
        float cs = rigCs;
        float h = worldH > 0f ? worldH : 2.2f;
        float dist = 1.2f * h;                      // 圆弧半径(正交相机下只影响朝向)
        camera.position.set(0f, centerY + dist * sn, dist * cs);
        camera.direction.set(0f, -sn, -cs);
        camera.up.set(0f, cs, -sn);
        // 该俯角下模型在窗口里的投影半高/半宽 → 需要的最小拉远比例
        float projY = (halfV * cs + halfD * sn) * 1.04f;   // 4% 余量(挥砍/动画微超包围盒)
        float projX = halfD * 1.04f;
        float zy = projY / (0.5f * h);
        float zx = projX / (0.5f * h * (aspect > 0.05f ? aspect : 1f));
        float zoom = zy > zx ? zy : zx;
        if (!(zoom > 1f)) {          // 包含 NaN 保护
            zoom = 1f;
        } else if (zoom > 4f) {
            zoom = 4f;
        }
        camera.zoom = zoom;
    }

    /** v4.9.9:按当前 viewPitchDeg 重算圆弧极角的 sin/cos(同帧多模型只算一次)。 */
    private static void ensureRigAngle() {
        float deg = viewPitchDeg > 0.5f ? viewPitchDeg : BASE_PITCH_DEG;
        if (deg < 1f) {
            deg = 1f;
        } else if (deg > 85f) {
            deg = 85f;
        }
        if (deg == rigAngleDeg) {
            return;
        }
        rigAngleDeg = deg;
        float rad = deg * 0.017453292f;
        rigSn = (float) Math.sin(rad);
        rigCs = (float) Math.cos(rad);
    }

    /** 按优先级创建 ModelBatch(APK glsl → 内置 glsl)。
     *  v2.0.0:按模型实际骨骼数配置 numBones(默认 12 会让 44 骨的拼装兵模抛 "too many bones")。 */
    private static ModelBatch createBatch() {
        int nb = effectiveNumBones();
        lastNumBones = nb;
        if (!pinnedEmbedded) {
            try {
                FileHandle vh = Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/shaders/default.vertex.glsl");
                FileHandle fh = Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/shaders/default.fragment.glsl");
                if (vh.exists() && fh.exists()) {
                    DefaultShader.Config cfg = new DefaultShader.Config(vh.readString("UTF-8"), fh.readString("UTF-8"));
                    cfg.numBones = nb;
                    // v4.9.0:环境只有 ambient + 1 个平行光 → 去掉默认的 5 点光/2 平行光
                    //(ubershader 光照循环与 uniform 都随之变少,移动端片元开销更低)
                    cfg.numDirectionalLights = 1;
                    cfg.numPointLights = 0;
                    DefaultShaderProvider sp = new DefaultShaderProvider(cfg);
                    ModelBatch b = new ModelBatch(sp);
                    shaderSource = "APK classpath glsl";
                    usingEmbedded = false;
                    return b;
                }
            } catch (Throwable ignored) {
                // 落到内置 glsl
            }
        }
        DefaultShader.Config cfg2 = new DefaultShader.Config(G3DShaders.VERTEX, G3DShaders.FRAGMENT);
        cfg2.numBones = nb;
        // v4.9.0:同 APK 分支 —— 只保留 1 个平行光,点光源置 0
        cfg2.numDirectionalLights = 1;
        cfg2.numPointLights = 0;
        DefaultShaderProvider sp = new DefaultShaderProvider(cfg2);
        ModelBatch b = new ModelBatch(sp);
        shaderSource = "内置 glsl(插件自带)";
        usingEmbedded = true;
        return b;
    }

    /** 生效的骨骼上限:配置 >0 时至少用配置值，取配置与实测的较大者(避免固定值太低导致模型异常)。 */
    private static int effectiveNumBones() {
        int nb = confNumBones > detectedBones ? confNumBones : detectedBones;
        if (nb < 12) {
            nb = 12;
        }
        return nb;
    }

    /** 模型里单个 NodePart 引用的最大骨骼数(DefaultShader 的 numBones 必须 ≥ 该值)。 */
    private static int maxBonesOf(Model m) {
        int max = 0;
        try {
            if (m == null) {
                return 0;
            }
            for (int i = 0; i < m.nodes.size; i++) {
                max = Math.max(max, maxBonesOfNode(m.nodes.get(i)));
            }
        } catch (Throwable ignored) {
        }
        return max;
    }

    private static int maxBonesOfNode(Node n) {
        int max = 0;
        try {
            if (n.parts != null) {
                for (int i = 0; i < n.parts.size; i++) {
                    NodePart p = n.parts.get(i);
                    if (p != null && p.invBoneBindTransforms != null) {
                        max = Math.max(max, p.invBoneBindTransforms.size);
                    }
                }
            }
            int cc = n.getChildCount();
            for (int i = 0; i < cc; i++) {
                max = Math.max(max, maxBonesOfNode(n.getChild(i)));
            }
        } catch (Throwable ignored) {
        }
        return max;
    }

    /** GL 上下文可能丢失(切后台回来):资源作废,下次 draw 自动重建。 */
    public static synchronized void invalidate() {
        ready = false;
        disposeQuietly();
        lastError = "";
    }

    /**
     * v4.8.0:只重建<b>船模</b>资源(时代换船型时用)。
     *
     * <p>旧实现直接调 {@link #invalidate()}:连陆军 combo 缓存一起清空 → 换时代后所有
     * 兵模都要逐帧重新加载(期间全部显示默认模型,多模型场景尤其明显)。船型变化只影响
     * 船模本身,所以这里只作废船模实例/船模挂件组合,并按新的 confShipModel 重建。</p>
     */
    public static synchronized void invalidateShip() {
        if (!ready) {
            return;   // 还没初始化(或已作废)→ 下次 init 直接按新船型建
        }
        try {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    sInstances[i][j] = null;
                    sAnims[i][j] = null;
                }
            }
            for (int r = 0; r < 4; r++) {
                for (int ph = 0; ph < PHASE; ph++) {
                    sBattleInst[r][ph] = null;
                    sBattleAnim[r][ph] = null;
                    sBattlePrev[r * PHASE + ph] = -1f;
                    sBattleUpd[r * PHASE + ph] = 0L;
                    sBattleFired[r * PHASE + ph] = 0;
                }
            }
            for (int j = 0; j < 4; j++) {
                shipAnimIds[j] = null;
            }
            // 船模挂件组合键里含船模 id → 旧组合不可能再被命中,直接释放
            disposeModelSets(shipSetCache);
            shipSetFailed.clear();
            shipSetQueue.clear();
            try {
                if (shipModelRef != null) {
                    shipModelRef.dispose();
                }
            } catch (Throwable ignored) {
            }
            shipModelRef = null;
            usingShipModel = false;
            shipProfile = null;
            baseShip = null;
            // v4.9.1(B2):船模实例重建 → 掩码复位为全量
            sPrevMask = -1L;
            sCurMask = -1L;
            shipAnchorX = 0f;
            shipAnchorZ = 0f;
            shipShadowShift = 0f;
            // v4.9.9:环绕几何复位(避免上一代船模的尺寸残留)
            shipCenterY = 0.2f * worldH;
            shipHalfV = 0.2f * worldH;
            shipHalfD = 0.3f * worldH;
            shipFit.idt();
            shipFitScale = 1f;
            shipInvFitScale = 1f;
            shipFitDx = 0f;
            shipFitDz = 0f;
            Model shipModel = loadShipModel();
            if (shipModel != null) {
                shipModelRef = shipModel;
                buildShipInstances(shipModel);
                shipProfile = weaponProfileFor(confShipModel);
                raiseBoneLimit(maxBonesOf(shipModel));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 释放一组按需加载的 ModelSet(本体 + 挂件模型)并清空缓存。 */
    private static void disposeModelSets(HashMap<String, ModelSet> cache) {
        for (ModelSet s : cache.values()) {
            if (s == null) {
                continue;
            }
            try {
                if (s.model != null) {
                    s.model.dispose();
                }
                for (int i = 0; i < s.extras.size(); i++) {
                    s.extras.get(i).dispose();
                }
            } catch (Throwable ignored) {
            }
        }
        cache.clear();
    }

    private static void disposeQuietly() {
        // v2.1.0:先释放按需加载的 combo 模型(每个 Model 自带贴图 disposables)
        disposeModelSets(comboCache);
        comboFailed.clear();
        comboQueue.clear();
        // v4.5.0:船模挂件模型集
        disposeModelSets(shipSetCache);
        shipSetFailed.clear();
        shipSetQueue.clear();
        partsIndex.clear();
        partsIndexLoaded = false;
        partsIndexTries = 0;
        partsIndexRetryFrame = -1L;
        try {
            if (batch != null) {
                batch.dispose();
            }
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < 4; i++) {
            try {
                if (models[i] != null) {
                    models[i].dispose();
                }
            } catch (Throwable ignored) {
            }
            models[i] = null;
            instances[i] = null;
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                uInstances[i][j] = null;
                uAnims[i][j] = null;
                sInstances[i][j] = null;
                sAnims[i][j] = null;
            }
        }
        // v4.9.1(B2):资源作废 → 掩码复位(下次 init 全量推进)
        uPrevMask = -1L;
        uCurMask = -1L;
        sPrevMask = -1L;
        sCurMask = -1L;
        for (int j = 0; j < 4; j++) {
            stateAnimIds[j] = null;
        }
        // v4.4.0:释放粒子资源与 battle 相位实例
        freeParticles();
        battleHold.clear();
        holdBattleCache.clear();   // v4.9.2
        donorAnimData.clear();
        userProfile = null;
        shipProfile = null;
        for (int r = 0; r < 4; r++) {
            for (int ph = 0; ph < PHASE; ph++) {
                uBattleInst[r][ph] = null;
                uBattleAnim[r][ph] = null;
                sBattleInst[r][ph] = null;
                sBattleAnim[r][ph] = null;
                uBattlePrev[r * PHASE + ph] = -1f;
                sBattlePrev[r * PHASE + ph] = -1f;
                uBattleUpd[r * PHASE + ph] = 0L;
                sBattleUpd[r * PHASE + ph] = 0L;
                uBattleFired[r * PHASE + ph] = 0;
                sBattleFired[r * PHASE + ph] = 0;
            }
        }
        for (int j = 0; j < 4; j++) {
            shipAnimIds[j] = null;
        }
        try {
            if (retreatFlagModel != null) {
                retreatFlagModel.dispose();
            }
        } catch (Throwable ignored) {
        }
        retreatFlagModel = null;
        retreatFlagInst = null;
        try {
            if (userShadowModel != null) {
                userShadowModel.dispose();
            }
        } catch (Throwable ignored) {
        }
        userShadowModel = null;
        userShadowInst = null;
        shadowReady = false;
        try {
            if (shipModelRef != null) {
                shipModelRef.dispose();
            }
        } catch (Throwable ignored) {
        }
        shipModelRef = null;
        usingShipModel = false;
        usingUserModel = false;
        // v4.9.6:共享贴图缓存兜底释放(正常情况引用已归零)
        for (SharedTexture st : texCache.values()) {
            try {
                st.refs = 0;
                st.dispose();
            } catch (Throwable ignored) {
            }
        }
        texCache.clear();
        batch = null;
    }

    private static void collectDiag() {
        StringBuilder sb = new StringBuilder();
        sb.append("gdx(设备)=").append(versionSafe()).append(' ');
        try {
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, VP);
            sb.append("viewport=(").append(VP.get(0)).append(',').append(VP.get(1)).append(',')
                    .append(VP.get(2)).append(',').append(VP.get(3)).append(") ");
        } catch (Throwable ignored) {
        }
        sb.append("depthBits=").append(depthBits).append(' ');
        try {
            sb.append("gl=").append(Gdx.gl.glGetString(GL20.GL_VERSION)).append(' ');
        } catch (Throwable ignored) {
        }
        sb.append("cam=ortho(").append(worldH).append(") ");
        sb.append("model=").append(modelSource).append(' ');
        sb.append("fx=").append(confParticles ? "on" : "off");
        if (animEventsNote != null && animEventsNote.length() > 0) {
            sb.append('[').append(animEventsNote).append(']');
        }
        sb.append(' ');
        sb.append("shader=").append(shaderSource).append(' ');
        sb.append("ModelBatch=").append(srcTag(ModelBatch.class)).append(' ');
        try {
            sb.append("ui=").append(CFG.GAME_WIDTH).append('x').append(CFG.GAME_HEIGHT)
                    .append(" px=").append(Gdx.graphics.getWidth()).append('x').append(Gdx.graphics.getHeight());
        } catch (Throwable ignored) {
        }
        diag = sb.toString();
    }

    private static String srcTag(Class<?> c) {
        try {
            return c.getClassLoader() == G3DArmyMin.class.getClassLoader() ? "插件自带" : "游戏侧";
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String versionSafe() {
        try {
            return String.valueOf(Class.forName("com.badlogic.gdx.Version").getField("VERSION").get(null));
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable c = t;
        for (int i = 0; i < 6 && c != null; i++) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
            c = c.getCause();
        }
        return sb.toString();
    }

    /**
     * v4.8.0:渲染一帧的全部兵模(在 oSB.end() 与 oSB.begin() 之间调用)。
     *
     * <p>状态捕获/还原每帧只做一次;循环内每个兵模一个独立小视口。矩形保持等比,
     * 允许超出屏幕(由窗口裁剪)。</p>
     *
     * <p>陆↔海过渡的两半段用不同形态的模型(前半段旧形态缩小、后半段新形态放大),
     * 所以每支部队同时传入陆/海两套 combo 与挂件签名,按 drawKind 取。</p>
     *
     * @param combosShip    每部队船模 combo id(null = 与 combos 同源)
     * @param attachesShip  每部队船模挂件签名
     */
    public static void renderAll(float angle, float[] rects, int[] rels, float[] facings, int[] states, int[] kinds,
                                 String[] keys, float[] morphs, String[] combos, String[] attaches, int[] tints,
                                 String[] combosShip, String[] attachesShip, int n) {
        lastRendered = 0;
        if (!isReady()) {
            return;
        }
        // v4.9.7:即使这一帧一个模型都不画(缩小到兵模阈值以下/刚缩放完),
        // 也要推进加载队列 —— 否则缩小期间队列不消费,放大回来只能 1 帧 1 个
        // 慢慢加载,期间全是无枪兜底模型(用户看到的“重新显示时模型不更新”)。
        comboFrame++;
        pumpComboQueue();
        if (comboQueue.isEmpty()) {
            pumpShipQueue();   // v4.5.0:每帧最多加载 1 个模型(combo 优先)
        }
        if (n <= 0) {
            return;
        }
        float dt = 0.016f;
        try {
            float g = Gdx.graphics.getDeltaTime();
            if (g > 0f && g < 0.1f) {
                dt = g;
            }
        } catch (Throwable ignored) {
        }
        animT += dt;
        if (animT > 1000f) {
            animT -= 1000f;
        }
        // v4.4.0:粒子池每帧推进一次
        updateParticles(dt);
        // v1.9.0:过渡基准(帧号=“上一帧是否还在渲染”;毫秒=进度)
        long morphFid = 0L;
        long morphNow = System.currentTimeMillis();
        try {
            morphFid = Gdx.graphics.getFrameId();
        } catch (Throwable ignored) {
        }
        if (morphMap.size() > 256) {
            morphMap.clear();
        }
        if (usingUserModel) {
            // v4.9.1(B2):只推进上一帧真正画到的 (rel×state) ——
            // 旧实现每帧固定推 16 个控制器,多模型同屏时是纯浪费(看不到的状态不必算)
            uPrevMask = uCurMask;
            uCurMask = 0L;
            updateAnimsMasked(uAnims, uPrevMask, uBattleInst[0][0] != null, dt);
        }
        if (usingShipModel) {
            // v1.8.0:船模动画同理(一般无动画 → 空转)
            sPrevMask = sCurMask;
            sCurMask = 0L;
            updateAnimsMasked(sAnims, sPrevMask, sBattleInst[0][0] != null, dt);
        }
        // v2.1.0:按需加载的 combo 动画同样每帧推进(只在被使用时才更新,省开销)
        for (ModelSet cs : comboCache.values()) {
            if (cs == null || comboFrame - cs.lastUse > 2L) {
                continue;
            }
            cs.prevMask = cs.curMask;
            cs.curMask = 0L;
            updateAnimsMasked(cs.anim, cs.prevMask, cs.battleInst[0][0] != null, dt);
        }
        // v4.5.0:船模挂件组合同样每帧推进
        for (ModelSet cs : shipSetCache.values()) {
            if (cs == null || comboFrame - cs.lastUse > 2L) {
                continue;
            }
            cs.prevMask = cs.curMask;
            cs.curMask = 0L;
            updateAnimsMasked(cs.anim, cs.prevMask, cs.battleInst[0][0] != null, dt);
        }

        // ---- 捕获状态(结束后原样还原;每帧一次) ----
        int vpx = 0;
        int vpy = 0;
        int vpw = Gdx.graphics.getWidth();
        int vph = Gdx.graphics.getHeight();
        boolean scissorOn = false;
        boolean depthOn = false;
        boolean cullOn = false;
        boolean blendOn = false;
        int cullMode = GL20.GL_BACK;
        int bSrcRGB = GL20.GL_SRC_ALPHA;
        int bDstRGB = GL20.GL_ONE_MINUS_SRC_ALPHA;
        int bSrcA = GL20.GL_SRC_ALPHA;
        int bDstA = GL20.GL_ONE_MINUS_SRC_ALPHA;
        try {
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, VP);
            if (VP.get(2) > 0 && VP.get(3) > 0) {
                vpx = VP.get(0);
                vpy = VP.get(1);
                vpw = VP.get(2);
                vph = VP.get(3);
            }
            scissorOn = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
            depthOn = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
            cullOn = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
            blendOn = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        } catch (Throwable ignored) {
        }
        try {
            Gdx.gl.glGetIntegerv(GL20.GL_CULL_FACE_MODE, TMP1);
            cullMode = TMP1.get(0);
        } catch (Throwable ignored) {
        }
        try {
            Gdx.gl.glGetIntegerv(GL20.GL_BLEND_SRC_RGB, TMP1);
            bSrcRGB = TMP1.get(0);
        } catch (Throwable ignored) {
        }
        try {
            Gdx.gl.glGetIntegerv(GL20.GL_BLEND_DST_RGB, TMP1);
            bDstRGB = TMP1.get(0);
        } catch (Throwable ignored) {
        }
        try {
            Gdx.gl.glGetIntegerv(GL20.GL_BLEND_SRC_ALPHA, TMP1);
            bSrcA = TMP1.get(0);
        } catch (Throwable ignored) {
        }
        try {
            Gdx.gl.glGetIntegerv(GL20.GL_BLEND_DST_ALPHA, TMP1);
            bDstA = TMP1.get(0);
        } catch (Throwable ignored) {
        }

        try {
            float kx = vpw / (float) CFG.GAME_WIDTH;
            float ky = vph / (float) CFG.GAME_HEIGHT;
            float degY = angle * 57.29578f;
            final long nowMs = morphNow;   // v4.9.2:每帧只取一次时间戳

            // v4.9.4:整帧只清一次深度。移动 GPU(tile-based)上中途 glClear 会打断 tile
            // buffer,多个模型时是主要开销;只有矩形相交的模型才需要单独再清。
            drawnCount = 0;
            if (depthBits > 0) {
                Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                Gdx.gl.glViewport(vpx, vpy, vpw, vph);
                Gdx.gl.glDepthMask(true);
                Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            }

            for (int i = 0; i < n; i++) {
                float uiX = rects[i * 4];
                float uiY = rects[i * 4 + 1];
                float uiW = rects[i * 4 + 2];
                float uiH = rects[i * 4 + 3];
                // v1.9.x 陆↔海过渡(两种驱动,共用“只改视口尺寸”的零开销缩换):
                //  ① 行程进度驱动(v1.9.1,优先)—— 跨陆/海路段 0→50% 旧模型缩小、50%→100% 新模型长大;
                //  ② 到站兑底(时间驱动,v1.9.0 保留)—— 类型翻转时缩-换-长(无路段数据/过快时用)。
                // 缩到 <4px 直接跳过绘制。
                int kind0 = (kinds != null && i < kinds.length) ? kinds[i] : 0;
                int drawKind = kind0;
                float mp = (morphs != null && i < morphs.length) ? morphs[i] : -1f;
                // v4.8.0:陆/海两套 combo+挂件分开取(过渡两半段各用各的;见 renderAll 说明)
                String landCombo = arrAt(combos, i);
                String landAttach = arrAt(attaches, i);
                String shipCombo = combosShip != null ? arrAt(combosShip, i) : landCombo;
                String shipAttach = attachesShip != null ? arrAt(attachesShip, i) : landAttach;
                if (!usingShipModel) {
                    // 无船模资源:海陆都用陆军模型,不做缩换(否则会拿船模 id 去加载陆军模型)
                    drawKind = 0;
                    mp = -1f;
                }
                if (mp >= 0f) {
                    if (mp > 1f) {
                        mp = 1f;
                    }
                    // v4.8.0:过渡一开始就把两形态都排进加载队列(缩小段争取把目标模型加载完)
                    prefetchMorph(landCombo, landAttach, shipCombo, shipAttach);
                    // v4.8.0:目标模型还没就绪 → 冻在最小尺寸等它(否则放大段会闪成默认模型)
                    boolean hold = mp >= 0.5f && !morphTargetReady(kind0 & 1, shipCombo, shipAttach, landCombo, landAttach);
                    if (hold) {
                        mp = 0.5f;
                    }
                    boolean oldHalf = mp < 0.5f || hold;
                    if (oldHalf) {
                        drawKind = 1 - (kind0 & 1);   // 前半段仍显示旧模型(缩小中)
                    }
                    float h = oldHalf ? (1f - mp * 2f) : ((mp - 0.5f) * 2f);
                    float m = MORPH_MIN + (1f - MORPH_MIN) * (h * h * (3f - 2f * h));
                    // v4.8.0:缩放锚点改为「脚底中心」(旧版取矩形中心 → 明明是原地换模,
                    // 视觉却像整体浮起/落下;底边锚定 = 缩进地面再长出)
                    float mc = uiX + uiW * 0.5f;
                    float mb = uiY + uiH;
                    uiW *= m;
                    uiH *= m;
                    uiX = mc - uiW * 0.5f;
                    uiY = mb - uiH;
                    if (keys != null && i < keys.length && keys[i] != null) {
                        // 同步时间过渡状态:到站瞬间不再二次播放“到站过渡”
                        // v4.8.0:-2 = 本帧由行程驱动(到站后若目标模型没加载好,时间分支接着补完放大段)
                        long[] me = morphMap.get(keys[i]);
                        long cur = (morphFid << 1) | (kind0 & 1);
                        if (me == null) {
                            morphMap.put(keys[i], new long[]{cur, -2L});
                        } else {
                            me[0] = cur;
                            me[1] = -2L;
                        }
                    }
                } else if (confMorphMs > 0f && keys != null && i < keys.length && keys[i] != null) {
                    long[] me = morphMap.get(keys[i]);
                    long cur = (morphFid << 1) | (kind0 & 1);
                    if (me == null) {
                        me = new long[]{cur, -1L};
                        morphMap.put(keys[i], me);
                    } else {
                        if (me[1] == -2L) {
                            // v4.8.0:上一帧还是「行程驱动过渡」(到站了)—— 目标模型若没加载好,
                            // 就从「已缩到最小」的状态接着用时间过渡把放大段补完;否则直接结束。
                            boolean recent = (me[0] >> 1) >= morphFid - 2L;
                            boolean readyNow = morphTargetReady(kind0 & 1, shipCombo, shipAttach, landCombo, landAttach);
                            me[1] = (recent && !readyNow) ? (morphNow - (long) (confMorphMs * 0.5f)) : -1L;
                        }
                        if ((me[0] >> 1) >= morphFid - 2L && (int) (me[0] & 1L) != (kind0 & 1) && me[1] < 0L) {
                            me[1] = morphNow;      // 最近两帧还在渲染、模型类型翻转 → 开始过渡
                        }
                        me[0] = cur;
                    }
                    if (me[1] >= 0L) {
                        // v4.8.0:过渡中把两形态都排队加载;目标未就绪 → 冻在最小尺寸等它
                        prefetchMorph(landCombo, landAttach, shipCombo, shipAttach);
                        boolean ready = morphTargetReady(kind0 & 1, shipCombo, shipAttach, landCombo, landAttach);
                        float p = (morphNow - me[1]) / confMorphMs;
                        if (p >= 1f) {
                            if (ready) {
                                me[1] = -1L;
                            } else {
                                p = 0.5f;          // 目标还没加载好 → 停在中点继续等
                            }
                        }
                        if (me[1] >= 0L) {
                            if (p < 0f) {
                                p = 0f;
                            }
                            boolean hold = p >= 0.5f && !ready;
                            if (hold) {
                                p = 0.5f;
                            }
                            boolean oldHalf = p < 0.5f || hold;
                            if (oldHalf) {
                                drawKind = 1 - (kind0 & 1);   // 前半段仍显示旧模型(缩小中)
                            }
                            float h = oldHalf ? (1f - p * 2f) : ((p - 0.5f) * 2f);
                            float m = MORPH_MIN + (1f - MORPH_MIN) * (h * h * (3f - 2f * h));
                            // v4.8.0:脚底锚定(同行程驱动分支)
                            float mc = uiX + uiW * 0.5f;
                            float mb = uiY + uiH;
                            uiW *= m;
                            uiH *= m;
                            uiX = mc - uiW * 0.5f;
                            uiY = mb - uiH;
                        }
                    }
                }
                if (uiW < 4f || uiH < 4f) {
                    continue;
                }
                int pw = Math.max(1, Math.round(uiW * kx));
                int ph = Math.max(1, Math.round(uiH * ky));
                int px = vpx + Math.round(uiX * kx);
                int py = vpy + vph - Math.round((uiY + uiH) * ky);

                // v4.9.4:glViewport 本身就会裁掉矩形外的片元 → 不再逐模型开关 scissor;
                // 只有与已绘制模型矩形相交时才单独清一次深度(否则沿用整帧的那次清)。
                Gdx.gl.glViewport(px, py, pw, ph);
                Gdx.gl.glDepthMask(true);
                if (depthBits > 0 && overlapsDrawn(px, py, pw, ph)) {
                    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                    Gdx.gl.glScissor(px, py, pw, ph);
                    Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
                    Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                }
                if (drawnCount < drawnRects.length / 4) {
                    drawnRects[drawnCount * 4] = px;
                    drawnRects[drawnCount * 4 + 1] = py;
                    drawnRects[drawnCount * 4 + 2] = pw;
                    drawnRects[drawnCount * 4 + 3] = ph;
                    drawnCount++;
                }

                camera.viewportHeight = worldH;
                camera.viewportWidth = worldH * (float) pw / (float) ph;

                int rel = rels[i];
                if (rel < 0) {
                    rel = 0;
                } else if (rel > 3) {
                    rel = 3;
                }
                int st = (states != null && i < states.length) ? states[i] : 0;
                if (st < 0) {
                    st = 0;
                } else if (st > 3) {
                    st = 3;
                }
                // v4.4.0:稳定 owner(粒子/相位不用渲染下标 —— 候选排序变化不会串视口)
                int owner = fxOwner(keys, i);
                // v4.8.0:当前要画的是哪种形态(drawKind 可能被过渡改过)→ 取对应那套模型签名
                boolean shipKind = (drawKind & 1) == 1;
                String comboId = shipKind ? shipCombo : landCombo;
                String attachSig = shipKind ? shipAttach : landAttach;
                // v4.7.2:battle 短锁存只对「有延迟开火事件」的武器生效;
                // WW1 步枪事件在 t=0(进入战斗第一帧已开火)→ 战斗结束立即切 idle,不再留攻击尾帧。
                // v4.9.2:结果按签名缓存(旧版每帧每部队都 split animSet + 扫事件表,产生垃圾)
                boolean holdBattle = drawKind != 1 && holdBattleFor(comboId, attachSig);
                String unitKey = (keys != null && i < keys.length) ? keys[i] : null;
                if (unitKey != null && holdBattle) {
                    if (st == 2) {
                        battleHold.put(unitKey, nowMs);
                        if (battleHold.size() > 512) {
                            battleHold.clear();
                            battleHold.put(unitKey, nowMs);
                        }
                    } else {
                        Long last = battleHold.get(unitKey);
                        if (last != null) {
                            if (nowMs - last <= (long) confBattleHoldMs) {
                                st = 2;   // 继续播完这段攻击(最多 battleHoldMs)
                            } else {
                                battleHold.remove(unitKey);
                            }
                        }
                    }
                }
                // v1.8.0:按 kind 选实例(1=船模;无船模/加载失败自动回落陆军模型)
                // v1.9.0:过渡前半段 drawKind 指旧模型(缩小中),中点后指新模型(放大中)
                boolean ship = drawKind == 1 && usingShipModel && sInstances[rel][st] != null;
                // v2.1.0:陆军方框按部队主兵种 combo 取模型(未加载/失败 → null = 用默认模型)
                // v4.5.0:海面 = 船模(文明覆盖/船挂件,按需加载;null = 基础船模)
                // v4.8.0:按 shipKind 取对应缓存(过渡中两种形态各用各的 combo)
                ModelSet set = shipKind ? shipSet(comboId, attachSig) : comboSet(comboId, attachSig);
                ModelInstance inst;
                boolean animOwned;                      // 该状态是否已有动画在播(有则不叠加程序化动作)
                AnimationController battleCtrl = null;  // v4.4.0:battle 相位控制器
                int animOwner = 0;                      // v4.9.1(B2):1=基础船模 2=ModelSet 3=默认模型
                int phase = (st == 2) ? phaseOf(keys, i) : -1;
                if (phase >= 0 && ship && set == null && sBattleInst[rel][phase] != null) {
                    inst = sBattleInst[rel][phase];
                    battleCtrl = sBattleAnim[rel][phase];
                    animOwned = battleCtrl != null;
                } else if (phase >= 0 && set != null && set.battleInst[rel][phase] != null) {
                    inst = set.battleInst[rel][phase];
                    battleCtrl = set.battleAnim[rel][phase];
                    animOwned = battleCtrl != null;
                } else if (phase >= 0 && !ship && set == null && usingUserModel && uBattleInst[rel][phase] != null) {
                    inst = uBattleInst[rel][phase];
                    battleCtrl = uBattleAnim[rel][phase];
                    animOwned = battleCtrl != null;
                } else if (ship && set == null) {
                    inst = sInstances[rel][st];
                    animOwned = sAnims[rel][st] != null;
                    animOwner = 1;
                } else if (set != null) {
                    inst = set.inst[rel][st];
                    animOwned = set.anim[rel][st] != null;
                    animOwner = 2;
                } else if (usingUserModel) {
                    inst = uInstances[rel][st];
                    animOwned = uAnims[rel][st] != null;
                    animOwner = 3;
                } else {
                    inst = instances[rel];
                    animOwned = false;
                }
                if (inst == null) {
                    continue;
                }
                // v4.9.1(B2):登记本帧真正用到的 (rel,state) —— 下一帧只推进这些控制器
                if (animOwner != 0) {
                    long usedBit = 1L << (rel * 4 + st);
                    AnimationController nac;
                    long prevBits;
                    if (animOwner == 1) {
                        sCurMask |= usedBit;
                        prevBits = sPrevMask;
                        nac = sAnims[rel][st];
                    } else if (animOwner == 2) {
                        set.curMask |= usedBit;
                        prevBits = set.prevMask;
                        nac = set.anim[rel][st];
                    } else {
                        uCurMask |= usedBit;
                        prevBits = uPrevMask;
                        nac = uAnims[rel][st];
                    }
                    // 该状态上一帧没画到(掩码里没有)→ 先按当前时间应用一次姿态,
                    // 否则这一帧会以绑定姿态(T-pose)出现
                    if (nac != null && (prevBits & usedBit) == 0L) {
                        try {
                            nac.update(0f);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                if (ship && set == null) {
                    // v1.8.0:船模用「长边适配」变换(贴水线+居中+缩放到不裁切)
                    inst.transform.set(shipFit);
                } else if (set != null) {
                    // v2.1.0:combo 模型共用同一套自动适配规则
                    inst.transform.set(set.fit);
                } else if (usingUserModel) {
                    // v1.4.1:用户模型在「适配变换」(缩放+贴地+居中)基础上叠加朝向/动作
                    inst.transform.set(userFit);
                } else {
                    inst.transform.idt();
                }
                float faceDeg = (facings != null && i < facings.length) ? facings[i] : 0f;
                float deg = faceDeg;
                if (ship) {
                    deg += confShipYaw;
                } else if ((usingUserModel || set != null) && confModelYaw != 0f) {
                    deg += confModelYaw;
                }
                if (spinSpeed != 0f) {
                    deg += degY;
                }
                // 程序化位移以「世界单位」为准:用户/船模型基底含缩放 → 按 1/scale 换算回局部量
                float ovs = set != null ? set.invFitScale
                        : (ship ? shipInvFitScale : (usingUserModel ? userInvFitScale : 1f));
                if (!animOwned) {
                    // 程序化多态动作(内置模型 / 用户模型缺该状态动画时的兜底):
                    // 停留=轻微呼吸 移动=步伐起伏 战斗=沿面向反复突刺 撤退=小碎步(前倾见下)
                    if (st == 2) {
                        float rad = (float) Math.toRadians(faceDeg);
                        float jabPh = animT * 4.2f + i * 1.3f;
                        float jab = Math.max(0f, (float) Math.sin(jabPh));
                        float k = 0.16f * jab * jab;
                        inst.transform.translate((float) Math.sin(rad) * k * ovs, 0f, (float) Math.cos(rad) * k * ovs);
                    } else if (st == 3) {
                        float bobPh = animT * 9.5f + i * 1.7f;
                        inst.transform.translate(0f, 0.07f * Math.abs((float) Math.sin(bobPh)) * ovs, 0f);
                    } else if (st == 1) {
                        float bobPh = animT * 7f + i * 1.7f;
                        inst.transform.translate(0f, 0.06f * Math.abs((float) Math.sin(bobPh)) * ovs, 0f);
                    } else {
                        float brPh = animT * 1.9f + i * 2.1f;
                        inst.transform.translate(0f, 0.012f * (float) Math.sin(brPh) * ovs, 0f);
                    }
                }
                if (deg != 0f) {
                    inst.transform.rotate(Vector3.Y, deg);
                }
                if (!animOwned && st == 3) {
                    // 撤退前倾(沿面向方向俯冲)
                    inst.transform.rotate(Vector3.X, 16f);
                }

                // v4.1.0:按部队颜色动态染色(EU5 式);实例是共享的 → 不染色的也要恢复基准色
                Array<Color> tintBase = set != null ? set.baseDiff
                        : (ship ? baseShip : (usingUserModel ? baseUser : null));
                if (tintBase != null) {
                    int rgb = (tints != null && i < tints.length) ? tints[i] : -1;
                    if (confTint && rgb >= 0) {
                        applyTint(inst, tintBase, rgb);
                    } else {
                        resetTint(inst, tintBase);
                    }
                }

                // v4.4.0:battle 相位控制器推进 + 本帧跨过的事件 → 发粒子
                if (battleCtrl != null && phase >= 0) {
                    int pidx = rel * PHASE + phase;
                    int mask;
                    WeaponProfile wp;
                    if (ship) {
                        wp = (set != null && set.weapon != null) ? set.weapon : shipProfile;
                        if (set != null) {
                            mask = tickBattle(battleCtrl, set.battlePrev, set.battleUpd, set.battleFired, pidx, wp, dt);
                        } else {
                            mask = tickBattle(battleCtrl, sBattlePrev, sBattleUpd, sBattleFired, pidx, wp, dt);
                        }
                    } else if (set != null) {
                        wp = set.weapon;
                        mask = tickBattle(battleCtrl, set.battlePrev, set.battleUpd, set.battleFired, pidx, wp, dt);
                    } else {
                        wp = userProfile;
                        mask = tickBattle(battleCtrl, uBattlePrev, uBattleUpd, uBattleFired, pidx, wp, dt);
                    }
                    if (mask != 0) {
                        spawnUnitFx(owner, inst, wp, mask, ship, set, deg);
                    }
                }

                // v4.9.9:相机沿「以模型中心为圆心的圆弧」环绕(俯角随地图缩放),
                // 并按当前俯角的投影包围自动拉远 → 环绕全程不超出窗口。
                // 几何参数已在资源构建时算好(每帧只做几次乘加,无分配/无查表)。
                float winAspect = (float) pw / (float) ph;
                if (set != null) {
                    applyCameraRig(set.centerY, set.halfV, set.halfD, winAspect);
                } else if (ship) {
                    applyCameraRig(shipCenterY, shipHalfV, shipHalfD, winAspect);
                } else if (usingUserModel) {
                    applyCameraRig(userCenterY, userHalfV, userHalfD, winAspect);
                } else {
                    applyCameraRig(0.30f * worldH, 0.30f * worldH, 0.22f * worldH, winAspect);
                }
                camera.update();
                batch.begin(camera);
                batch.render(inst, env);
                if ((ship || usingUserModel || set != null) && userShadowInst != null) {
                    // v4.1.4:阴影锚点 = 「脚底接触带」中心(按模型顶点几何算出,不是包围盒中心)。
                    // 变换顺序与实例一致(旋转 → 适配缩放 → 适配平移),再叠加阴影前后偏移(绝对值+高度比例)。
                    // 这样长矛/马尾/马身/船体把包围盒拉偏时,阴影依然稳定贴在脚下。
                    float anchX = set != null ? set.anchorX : (ship ? shipAnchorX : userAnchorX);
                    float anchZ = set != null ? set.anchorZ : (ship ? shipAnchorZ : userAnchorZ);
                    float fScale = set != null ? set.fitScale : (ship ? shipFitScale : userFitScale);
                    float fDx = set != null ? set.dx : (ship ? shipFitDx : userFitDx);
                    float fDz = set != null ? set.dz : (ship ? shipFitDz : userFitDz);
                    float fShift = set != null ? set.shadowShift : (ship ? shipShadowShift : userShadowShift);
                    float sRad = deg * 0.017453292f;
                    float sCos = (float) Math.cos(sRad);
                    float sSin = (float) Math.sin(sRad);
                    float axm = anchX * fScale;
                    float azm = anchZ * fScale;
                    float shx = axm * sCos + azm * sSin + fDx;
                    float shz = -axm * sSin + azm * sCos + fDz + fShift;
                    float ss = set != null ? set.shadowScale : (ship ? shipShadowScale : userShadowScale);
                    BlendingAttribute sba = userShadowInst.materials.size > 0
                            ? userShadowInst.materials.get(0).get(BlendingAttribute.class, BlendingAttribute.Type) : null;
                    // 主柔和阴影(大范围接地);v4.9.2:核心已烘焙进贴图 → opacity 用 1
                    if (sba != null) {
                        sba.opacity = shadowCoreBaked ? 1f : 0.75f;
                    }
                    userShadowInst.transform.setToTranslation(shx, 0f, shz);
                    if (ss > 0f && (ss < 0.99f || ss > 1.01f)) {
                        userShadowInst.transform.scale(ss, 1f, ss);
                    }
                    batch.render(userShadowInst, env);
                    // v4.1.2:再叠一层更小/更浓的「接触核心」—— 把阴影牢牢压在脚底
                    // v4.9.2:已烘焙进贴图时不再画第二层(省 1 次 draw call/模型)
                    float core = confShadowCore;
                    if (!shadowCoreBaked && core > 0.05f) {
                        if (sba != null) {
                            sba.opacity = 1f;
                        }
                        userShadowInst.transform.setToTranslation(shx, 0.006f, shz);
                        userShadowInst.transform.scale(ss * core, 1f, ss * core);
                        batch.render(userShadowInst, env);
                    }
                }
                if (st == 3 && !usingUserModel && set == null && retreatFlagInst != null) {
                    // v1.4:内置模型的撤退白旗(跟随本体变换)
                    retreatFlagInst.transform.set(inst.transform);
                    batch.render(retreatFlagInst, env);
                }
                batch.end();
                // v4.4.0:粒子只在本部队的小视口里画(在下一个 scissor 之前)
                drawParticles(owner, pw, ph);
                lastRendered++;
            }
            lastError = "";
        } catch (Throwable t) {
            String msg = chain(t);
            lastError = "render: " + msg;
            if (!usingEmbedded) {
                pinnedEmbedded = true;
                synchronized (G3DArmyMin.class) {
                    disposeQuietly();
                    ready = false;
                }
                lastError = "render(APK glsl 失败,已改用内置 glsl,将自动重建): " + msg;
            }
        } finally {
            try {
                Gdx.gl.glViewport(vpx, vpy, vpw, vph);
                if (scissorOn) {
                    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                } else {
                    Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                }
                if (depthOn) {
                    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                } else {
                    Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
                }
                if (cullOn) {
                    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                } else {
                    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
                }
                Gdx.gl.glCullFace(cullMode);
                if (blendOn) {
                    Gdx.gl.glEnable(GL20.GL_BLEND);
                } else {
                    Gdx.gl.glDisable(GL20.GL_BLEND);
                }
                Gdx.gl.glBlendFuncSeparate(bSrcRGB, bDstRGB, bSrcA, bDstA);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                Gdx.gl.glDepthMask(true);
            } catch (Throwable ignored) {
            }
        }
    }
}
