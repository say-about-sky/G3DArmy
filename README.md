# G3DArmy — 3D 兵模(拼装兵模 + 伪3D地图适配)

> **简介**:把兵牌换成**拼装 3D 兵模**(libGDX g3d) —— 按文化 / 宗教 / 时代 / 科技 / 兵种拼装,
> 支持陆海过渡、行军朝向、近大远小与伪3D地图倾斜适配;纯热插件,不改游戏类。
>
> **快速开始**:① `gradlew.bat shadowJar`(纯 Gradle:`extractG3d` 自动从 gdx 1.12.1 提取 `g3d` 类,宿主 jar 自动定位)→
> ② `out\G3DArmy-4.9.14.jar` → ③ 丢进 `hotplugins\`,在游戏的「附加包/插件」面板加载;改 `setting.json` 后点「全部重载」生效。
> （也可双击 `build.bat`；路径可用 `AOH_GAME_ROOT` / `-PgameRoot=` / `-PhostJar=` 指定）
> **依赖**(不进版本库):JDK 11+、`game.jar`;Gradle 依赖(`com.badlogicgames.gdx:gdx:1.12.1`、shadow 插件)首次构建自动下载(阿里云镜像)。
> **许可**:GPL-3.0,见 `LICENSE.TXT`。
> **隐私**:纯本地运行,不联网、不收集数据;配置与日志都在 `hotplugins\` 本地。
>
> 配置项与逐版本记录见下文。

### 构建加速（国内网络）

- 镜像顺序：阿里云 `gradle-plugin` → 阿里云 `public` → 阿里云 `google` → 华为云 → `mavenCentral()`（兜底）。
  实测：阿里云 gdx 9.3 MB/s、Maven Central 0.2 MB/s；Gradle Plugin Portal 单个 POM 1.9 s → 阿里云 0.14 s。
- `gradle.properties` 已开 `daemon / parallel / caching / vfs.watch` + 2 GB 堆，增量构建 1~3 s。
- Gradle 发行包走腾讯云镜像（3.7 MB/s）。

### IntelliJ IDEA 运行/调试配置

`.run\` 内的配置打开工程即生效：

| 配置 | 类型 | 作用 |
|---|---|---|
| 构建 jar（gradle shadowJar） | Gradle | 出 `out\G3DArmy-<版本>.jar` |
| 调试游戏（桌面 JDWP 5005） | Remote JVM Debug | 先 `StartFinality-jdwp.bat` 启动游戏再 Debug |
| 调试游戏（安卓 adb 8700） | Remote JVM Debug | `adb forward tcp:8700 jdwp:<pid>` 后 Debug |

> **v3.0.1 = HotG3DArmyTest(v2.1.0)+ HotG3DArmyPseudo3DMapVersion(v1.10.0)合并**
> —— 保留拼装兵模/主兵种选择/陆海过渡/骨骼自适应,并加入伪3D地图(Pseudo3DMap)适配。
> 包名/插件 id 已改为 `com.saysky.g3darmy`,产物 = `G3DArmy-<版本>.jar`。
> **v4.8.0**:陆↔海过渡两半段各用各自形态的模型与挂件(修“船→兵放大段回退默认兵模”)、
> 过渡前预热并等目标模型就绪、脚底锚定缩放、换时代只重建船模(不再清空全部兵模缓存)。
> **v4.9.14**:新增 `modelLift: [平视端, 俯视端]`(渲染窗口相对兵牌位置,px,留空=自动按俯角补偿,
> 解决“俯视时脚离兵牌越来越远”);所有上下限字段统一改为数组 `[min, max]`:`modelPxLimit`、
> `modelMulLimit`(旧标量键仍兼容)。
>
> **v4.9.14 代码清理**:删除历史探针与兼容残留 —— 一次性着色器探针(`probeUserShader`)、贴图/顶点色
> 诊断(`texTrace`)、`whiteModel` 全白排查模式、只写不读的 `comboNote`/粒子计数、每帧
> `System.nanoTime` 耗时采集(`耗时=list=… render=…` 项已从状态日志移除)、4 个旧 `renderAll` 重载、
> 死方法 `appendAttachment`/`findFirstPart(Node)`、死重载 `buildModelSet`/`comboSet`/`typeEntry`/
> `moveGroupKey`/`addCandidate`,并把 combo/船模加载队列合并为 `loadIntoCache`、ModelSet 释放合并为
> `disposeModelSets`、`getFloat`/`getBool` 共用 `rawValue`。行为与配置语义不变。

> **v4.9.13**:新增 `modelPxMin`/`modelPxMax`(渲染窗口像素上下限,长边等比夹取)与
> `modelMulMin`/`modelMulMax`(模型尺寸倍率上下限,即近大远小系数的夹取区间);两者都与
> 3D 环绕的自动拉远兼容(等比 → 宽高比不变)。

> **v4.9.12**:`nearFar` 近大远小改为与**地图局部缩放同源**的 `1/D²` 公式(以屏幕中线归一),
> 下大上小、平均尺寸不变,比旧的线性近似强得多;状态日志新增 `近大远小ks=…(缩放系数=a~b)`。

> **v4.9.11**:`viewPitch` 方向按实机确认改为与地图一致:**拉近(地图平视)→ 兵模更平视(俯角小)**,
> 缩到最小 → 兵模更俯视;默认 `[12, 55]`。

> **v4.9.10**:`viewPitch` 改为直接随**地图缩放**驱动(与地图倾斜开关无关,旧版要求伪3D补丁生效)。

> **v4.9.9**:`viewPitch` 改为**相机以模型中心为圆心的 3D 环绕**(原来绕脚底),并按每个模型的
> 包围盒自动拉远(zoom)保证环绕全程不超出渲染窗口。

> **v4.9.8**:伪3D 适配两项新配置 —— `setting.json` 的 `nearFar`(近大远小比例,小数,越大越明显)
> 与 `viewPitch: [最小, 最大]`(渲染窗口视角随地图缩放变化的俯仰角区间,度)。

> **v4.9.5**:`setting.json` 新增 `comboCache`(默认 20)/`shipCache`(默认 8)——之前硬编码 10/4,
> 后期战场同屏会有 12~20 种 combo(文化×宗教×科技×兵种),容量不够时不停「淘汰→重载」,
> 每次重载 80~100ms 且排在渲染帧里(日志 `加载=328次/共25976ms` = 约 87% 帧时在加载)→
> 兵牌移动时的“秒卡”就是它。状态日志现在打印 `combo=已用/上限 shipSet=已用/上限`。
> **v4.9.3**:`setting.json` 新增 `modelMinScale`(兵模出现的最小地图缩放绝对值,`0`=按
> `modelMinScaleMul` 自动算);行军状态改为“`inMovement` 或 路线进度推进”双源判定 ——
> 修复兵牌被高防御地块/模组阻断、占领后恢复移动时兵模仍停在 idle 的问题。
> **v4.9.0**:兵模晚于兵牌出现(`modelMinScaleMul` 默认 1.25)、5 档最高速隐藏移动中部队
> (`hideMovingAtMaxSpeed`)、着色器只保留 1 平行光 0 点光。
> **v4.9.1**:选型结果按「主兵种签名」缓存(不再每帧每部队走 JSON);动画控制器只推进
> 上一帧实际画到的 (rel×state)。

# 原版兵模扩展(3D 兵模放在部队方框处)

> 以地图锚定立方体为蓝本:把地图上的**部队方框**扩展成 **3D 兵模**。
> 所有坐标/可见性逻辑都对齐**原版游戏源码**(见下"原版事实")。

## 原版事实(源码核实)

**① 部队显示的三档缩放策略**(`ProvinceDrawArmy` / `BattleManager` / `SiegeManager`):

| 档 | 条件 | 表现 |
| --- | --- | --- |
| 完整显示 | `scale >= Game.DRAW_ARMY_MIN_SCALE`(地图配置:**EarthM 0.4 / Earth3 0.5**;代码默认 0.5;设置项 DrawArmyScale 可调 0.05~1.0) | 旗标 + 士气条 + 方框 + 数字 |
| 淡出过渡 | `ProvinceDrawArmy.DRAW_ARMY_MIN_SCALE_ANIMATION = 0.3 ≤ scale < 阈值`(且处于淡出动画中) | 渐隐/渐显 |
| 隐藏 | `scale < 0.3` | 不显示;**框选模式(selectMode)下强制全亮显示** |

> 注:另有 `scale ≥ 1.0` 的阈值是**船只**(ShipManager),不是部队。

**② 绿色方框**:玩家自己的部队走 `Civ.updateArmyImgID_Player()` →
`Images.armyPlayer0..14`(由 `Game.player.playerData.armyImgID` 选定,默认 0 = 绿);
敌=红(`armyEnemy`)、友=蓝(`armyAlly`)、其他=中性(`army`);方框左端是士气色条(高士气=绿)。

**③ 部队屏幕坐标**:`ProvinceDrawArmy.getArmyPosX/getArmyPosY(省ID, 部队下标)`
(内部 = 省中心 + 省份位移 + 部队偏移,再乘缩放;与方框绘制同源)→ 本插件直接调用,
天然与被遮住的方框**逐像素对齐**。

## 本插件显示逻辑(v1.4)

- **放置**:兵模横向以方框中心对齐,**底边位于方框顶部上方 `liftPx` 像素**
  (默认 8px;不再与方框重叠);v1.4 将视线中心下移,收回模型矩形底部约一半空区,
  **兵模整体更贴近兵牌**(实际间距约减半;`liftPx` 仍可微调);
- **四态动作(v1.4)**:停留 / 移动 / 战斗 / **撤退**四个状态独立驱动 ——
  **移动**按**行军路径**自动转向(v1.4.5:取**当前路段两端省中心连线方向** = 黄引导线方向;
  旧版“精灵位置→目标中心”会被站位堆叠偏移/到达弹跳/行军摆动污染 → 过地块时小幅乱转;
  **v1.4.6**:转向按 `turnTau` 时间常数平滑过渡,不再一帧硬切),**停留**轻微呼吸;**战斗**见下;
  **撤退** = `a.inRetreat`(与兵牌白旗同源)—— 内置模型:小碎步+前倾+**手中白旗**,
  用户模型:按 `animationRetreat`/关键词(retreat/flee/run/escape…)匹配(缺省回退移动动画);
- **跟随**:在**绘制帧内**取坐标(与地图同帧,零延迟);
  移动中的部队位置本身就是沿路径插值的(`MoveUnits.update()` 直接改部队 `iShiftX/Y_Scaled`),
  所以兵模天然贴着黄色路径线走;
- **显示**:对齐①②—— `scale ≥ 阈值` 或**框选模式**时显示;淡出档不画(原版是渐隐,后续可加);
- **尺寸**:固定屏幕像素(`modelPx`,与方框一致,不随缩放变化);
- **配色**:玩家绿 / 敌红 / 友蓝 / 中立中性色(与原版方框语义一致);
- **脚下阴影(v1.4.8)**:兵模脚下渲染**柔和接触阴影** —— 径向渐变半透明圆盘(程序生成贴图、无光照纯 alpha 混合),
  与脚底同原点、随部队移动/转身,阴影**撤销适配缩放**保持世界尺寸;
  内置几何兵模自带硬边阴影盘,用户模型(如骑士)用软阴影;
- **范围**:默认**全部部队**(`scope: "all"`,玩家绿/敌红/友蓝/中立),可改 `"player"` 只看玩家;
- **战斗表现(v1.3)**:交战部队不再隐藏,兵模**锚定游戏战斗横幅** —— 交战时游戏画的是
  合并横幅「进攻方|⚔|防守方」(`Battle.drawBattle_Just` 同源几何,双方同格同横幅),
  进攻方对齐横幅**左半区**、防守方对齐**右半区**,**双方同一基准线**(修复 v1.2 “左侧兵模偏下”),
  面对面,左右间距 `battleSplitPx`(默认 55px);战斗状态播放战斗动画(无动画则程序化突刺);
- **堆叠节流(v1.3)**:同省多部队层叠时(方框从上到下叠放,顺序=军队列表序,
  `Province.updateArmyPosY` 同源),默认**只显示首个(最上)静止兵牌**的兵模;
  该部队开始移动后,下一个静止兵牌的兵模自动接替(依次显示,防重叠/防卡顿);
  移动中的部队始终显示;`stackFirstOnly: false` 可关闭恢复全显;
- **选中优先(v1.3.1)**:玩家选中的军牌(单选/多选,`Game.activeArmy`)始终显示其兵模 ——
  即使它不是堆叠中的首个(为后期多兵种兵牌铺路)。

## 用户模型/动画(资源文件公开,v1.4.8)

数据目录:
> **v4.2.0 资源目录结构(新)**:
> ```
> hotplugins/resources/G3DArmy/          ← 目录名 = jar 文件主名(现在固定为 G3DArmy.jar)
>   setting.json        总体渲染设置(原 army.json;旧文件仍兼容)
>   loadSelect.json     装载配置:兵种/船型 → 模型(原 parts.json 的 unitSelect/shipSelect)
>   anim_events.json    动画事件/最小状态机(攻击粒子时间线;预留)
>   civilization/       按文明覆盖的变体拼装(ger.json / usa.json 示例)
>   models/
>     assembly.json       运行时装配配置(原 parts.json 其余部分:rigs/animSets/combos…)
>     attachments.json    挂件(武器/盾)规则:unitSelect / shipSelect(旧字段 rules = unitSelect)
>     main/{g3d,png}      完整模型 + 贴图(combo 成品)
>     parts/{g3d,png}     运行时挂件 + 贴图 + index.json
>     body|head|feet/{g3d,png,index.json}   预留:按部件拆分
> ```
> 查找顺序:模型 `models/<类>/g3d/` → 旧 `g3d/` → 根目录;
> 贴图 `models/<类>/png/` → `models/<类>/g3d/` → 旧 `png/` → 根目录。
> 旧版「g3d/ + png/ + parts/ + army.json + parts.json」仍然兼容(`deploy-combos.ps1` 会就地迁移一次)。

1. 放 `model.g3dj` 或 `model.g3db`(libGDX g3d 格式,可用 fbx-conv 从 Blender/FBX 导出)→ 
   重启游戏即替换兵模;模型要求:Y 轴朝上(尺寸/原点不限 —— **v1.4.1 自动适配**:
   按包围盒缩放至视口约 85%、水平居中、脚底贴地;骑士原始身高 12.2、脚底 y=-8.85 开箱即用);
   4 套关系色实例由插件自动生成(ModelInstance 自带材质副本,染色互不影响);
   **贴图(v1.4.1)**:模型引用的贴图文件(如 `Knight.png`)放同一目录即可 —— 按模型目录
   **绝对路径**加载(`Gdx.files.absolute`),安卓同样有效(旧版用 `internal()` 在安卓会丢贴图);
   缺贴图时以占位白贴图纯色显示;
   **顶点色剥离(v1.4.2)**:全网格同一值的顶点色(转换器哨兵值,如骑士的 `0x7FC00000`)自动剥离
   —— 修复“模型发白”;顶点色有变化的模型保留原样;
2. 模型自带顶点/骨骼动画时按**四态**自动匹配(可配置):
   - `animationIdle` 停留(自动匹配关键词:idle/stand/stay/rest…)
   - `animationMove` 移动(关键词:walk/run/move/march…)
   - `animationBattle` 战斗(关键词:attack/battle/fight/hit…)
   - `animationRetreat` 撤退(关键词:retreat/flee/run/escape…;缺省回退移动动画)
   - 基础键 `animation`:以上都空缺时的回退(""=第一个;"-"=禁用动画);`animSpeed` 调速;
   - 某状态找不到动画 → 该状态自动用**程序化动作**兜底(呼吸/步伐/突刺/撤退前倾);
3. `tint` 控制染色方式(`"civ"` 文明色（默认）/ `"relation"` 关系色 / `"none"`）；`worldH` 调视口世界高度(模型显得过大/被裁 时调大,如 4~6);
4. 想回内置几何兵模:删除 model.g3dj/g3db 或 `"model":"builtin"`;
5. 首次运行自动生成(仅内置模式):
   - `soldier-template.g3dj` —— 内置兵模导出的**格式示例**(写入后当场做可加载自检,
     日志会显示“自检通过/自检失败”;可改名为 model.g3dj 先用它跑通链路);
   - `README-用户资源说明.txt`(UTF-8)。

## 配置(免重装,改完「全部重载」)

`hotplugins/resources/<插件文件主名>/army.json`(删除后重启自动重建):

| 键 | 默认 | 含义 |
| --- | --- | --- |
| `enabled` | true | 总开关:`false` = 不显示 3D 兵模(取代原右上角「关闭 3D 兵模」按钮;改后重载生效) |
| `modelPx` | 170 | 兵模屏幕尺寸(px) |
| `modelPxW` | 0 | v3.0.1 窗口宽(px;0=用 `modelPx`) |
| `modelPxH` | 0 | v3.0.1 窗口高(px;0=用 `modelPx`) |
| `modelPxLimit` | `[0, 0]` | v4.9.14 **渲染窗口像素上下限** `[min, max]`(0=该侧不限)。按窗口长边夹取,**等比缩放**(宽高比不变→3D 环绕的 aspect/自动拉远不受影响)。旧标量键 `modelPxMin`/`modelPxMax` 仍兼容 |
| `modelMulLimit` | `[0.5, 2]` | v4.9.14 **模型尺寸倍率上下限** `[min, max]`(相对默认大小 1.0;即「近大远小」尺寸系数的夹取区间)。旧标量键 `modelMulMin`/`modelMulMax` 仍兼容 |
| `modelLift` | `[]` | v4.9.14 **渲染窗口相对兵牌的位置** `[平视端, 俯视端]`(px;负值 = 压到兵牌基准线下方)。**留空 `[]` = 自动**:随俯角把窗口下移,让「脚」与兵牌保持 `liftPx` 的固定间距(解决俯视时脚离兵牌越来越远) |
| `modelFitPct` | 0.85 | v3.0.1 自动适配占比(调小=更小,更不易被裁切) |
| `modelFitBy` | `height` | v3.0.1 适配维度:`height`=只按高 / `max`=按最大边(马等长模型不裁切) |
| `liftPx` | 8 | 兵模底边与方框顶部的间距(px;调大 = 更靠上) |
| `battleSplitPx` | 55 | 战斗时进攻/防守左右分离距离(px;0=不分离) |
| `stackFirstOnly` | true | 同格堆叠/同行军段只显示一个兵模(选中者优先;`false`=全显) |
| `model` | `"model"` | 用户模型文件名(不含扩展名;`"builtin"`/空 = 内置) |
| `shipModel` | `"ship"` | v1.8.0 船模文件名(不含扩展名;海面地块;`""`/`"-"`/`"builtin"` = 不用船模) |
| `morphMs` | 550 | v1.9.1 到站兜底过渡时长(毫秒;0=关闭该兜底) |
| `morphMove` | true | v1.9.1 过渡绑定行军进度(`false`=改回“到站后过渡”,用 `morphMs` 时长) |
| `numBones` | 0 | v2.0.0 着色器骨骼上限(0=自动按模型统计;拼装兵模 torso 44 骨需 ≥44) |
| `comboMode` | true | v2.1.0 按主兵种选拼装兵模(**关掉则一律用 `model`**) |
| `partsFile` | loadSelect.json | v4.2.0 装载配置文件名(旧默认 parts.json;写旧名也能自动兜底) |
| `animation` | `""` | 动画基础名(回退;`""`=第一个,`"-"`=禁用全部动画) |
| `animationIdle` | `""` | 停留动画名(`""`=自动按关键词匹配) |
| `animationMove` | `""` | 移动动画名(`""`=自动按关键词匹配) |
| `animationBattle` | `""` | 战斗动画名(`""`=自动按关键词匹配) |
| `animationRetreat` | `""` | 撤退动画名(`""`=自动按关键词匹配;缺省回退移动动画) |
| `animSpeed` | 1 | 动画速度倍率 |
| `worldH` | 2.2 | 3D 视口世界高度(模型过大/被裁时调大) |
| `tint` | `"civ"` | v4.1.0 染色方式:`"civ"`=文明色(EU5 式,默认)/ `"relation"`=关系色(旧行为)/ `"none"`=不染;兼容旧写法 `true`=relation、`false`=none |
| `tintStrength` | 0.45 | v4.1.0 染色强度(0~1;与原贴图色插值,越大国家色越明显) |
| `tintLift` | 0.22 | v4.1.0 保底亮度:国家色太黑时整体提亮到该亮度,避免兵模发黑 |
| `tintSkip` | `"head,skin"` | v4.1.0 跳过染色的材质关键词(逗号分隔)——脸/皮肤不被染成国家色 |
| `feetDark` | 0.5 | v4.1.1 脚底亮度(0.05~1;1=关闭);底部顶点变暗→接地阴影感 |
| `shadowScale` | 1 | v4.1.1 接触阴影尺寸倍数(基础尺寸按模型长边自动算) |
| `shadowAnchor` | true | v4.1.4 阴影自动锚在「脚底接触带」几何中心(按模型顶点算;长兵器/马身/船体把包围盒拉偏也不怕)。`false`=退回固定原点 |
| `shadowShiftZ` | 0 | v4.1.4 阴影前后**固定**偏移(世界单位;负=屏幕上方/远处,正=下方/近处)。固定值对不同大小模型观感不同,优先用 `shadowShiftPct` |
| `shadowShiftPct` | 0 | v4.1.4 阴影前后**比例**偏移(占模型世界高度;0.02=模型高的 2%)。模型越大偏移越大 → 大马/船与步兵观感一致 |
| `shadowCore` | 0.6 | v4.1.2 叠加的「接触核心」阴影缩放(相对主阴影;0=关;越小越贴脚、脚下更暗) |
| `modelAutoFit` | true | 用户模型自动适配(缩放至视口约 85% + 贴地 + 居中) |
| `modelScale` | 0 | 手动缩放倍率(`>0` 覆盖自动缩放) |
| `modelOffsetY` | 0 | 额外 Y 偏移(世界单位,叠加在自动贴地上) |
| `modelYaw` | 0 | 基础朝向修正(度;模型背对行军方向时设 180) |
| `shipYaw` | 0 | v1.8.0 船模朝向修正(度;船头背对航向时设 180) |
| `turnTau` | 0.2 | 转身平滑时间常数(秒;越小转得越快,`0`=瞬间转向) |
| `tiltStrength` | 1 | v3.0.0 伪3D地图适配强度(0=关闭;1=标准;上限 2;实时生效) |
| `nearFar` | 1 | v4.9.12 **近大远小比例**(小数;0=关闭;1=标准;越大越明显;上限 4)。尺寸系数 = 以屏幕中线归一的**地图局部纵向缩放比** `1/D²`(D = 1 + ks·nearFar·(1−Y/H))→ 屏幕下方更大、上方更小、平均尺寸不变;仅地图倾斜开启时生效 |
| `viewPitch` | `[12, 55]` | v4.9.11 **渲染窗口相机绕模型中心 3D 环绕的俯仰角区间** `[最小, 最大]`(度),直接随**地图缩放**变化:**拉近(地图越平视)→ 越接近最小值(兵模也更平视)**;缩到最小(地图接近俯视)→ 趋最大值(兵模也从上方看)。与地图倾斜开关无关;相机按投影包围自动拉远保证不越窗;两值相等 = 关闭(固定基准俯角 21.78°) |
| `scope` | `"all"` | `"all"`=全部部队(默认)/ `"player"`=仅玩家部队 |
| `maxModels` | 24 | 同屏兵模上限(上限 128) |
| `spinSpeed` | 0 | 自旋(弧度/秒;0=静立) |

> 旧版配置缺少新键时自动用默认值(无需手动改 army.json)。

### 尺寸体系(谁控制什么,v4.9.13)

```
模型默认大小 = modelFitPct(占窗口比例) / modelAutoFit / modelScale(手动覆盖) / worldH(3D 视口高)
         ↓ 再乘
窗口默认大小 = modelPx / modelPxW / modelPxH            ← 「默认窗口大小」
         ↓ × 尺寸倍率 f(近大远小;夹在 modelMulLimit[0] ~ modelMulLimit[1])
         ↓ 再按长边夹到 modelPxLimit[0] ~ modelPxLimit[1](等比,保持宽高比)
窗口底边 = 兵牌顶上方 liftPxEff 像素(自动/手动随俯角变;可为负 = 压到基准线下方)
         ↓ 3D 环绕内部再乘 camera.zoom(按模型包围盒自动拉远,保证不超出该窗口)
```

- `modelMulLimit` **只夹「近大远小」的尺寸系数**(默认 1;地图倾斜关闭时恒为 1,不会被夹);
- `modelPxLimit` 夹的是**窗口像素长边**,与近大远小是否生效无关,始终生效;
- `modelLift` 控制窗口**纵向位置**:留空则随俯角自动补偿(脚在窗口内的比例 = 0.5 − 0.4·cosθ,
  俯视时脚往上收 → 窗口自动下移,脚与兵牌间距恒定);写死则按 `[平视端, 俯视端]` 用与
  `viewPitch` 同一个缩放归一值插值,负值 = 窗口落到兵牌基准线下方;
- 以上都是**等比/纯平移** → 窗口宽高比不变,3D 环绕的 `pw/ph`(aspect)、包围盒半径、
  自动拉远全部维持不变;
- 调节建议:想让兵模整体更大就调 `modelPxW/H`,想让“远近差”更夸张就 `nearFar`/放宽 `modelMulLimit`,
  不想让边缘小模型糊成一片就设 `modelPxLimit`(如 `[90, 0]`),
  想让俯视时兵模更贴兵牌就写 `modelLift`(如 `[8, -20]`)。

## 显示规则(v1.7.0)
1. **地图模式**:只在默认「政治」模式显示兵模;外交/宗教/招募选择等其它模式不渲染;
2. **迷雾**:看不到兵牌的地块也看不到兵模(与兵牌同源:Province.getFogDrawArmy);
3. **视角内**:只渲染当前视角中能看到兵牌的部队(兵牌在屏 ±200px);
4. **节流**:同地块堆叠、同行军段(同文明同起终点)、同战斗同侧均只显示一个兵模(选中者优先;
   v1.7.1 起堆叠组锚定**整叠最上方兵牌**的头顶,不再遮挡堆叠兵牌;
   v1.7.2 起行军组**粘性跟随**同一兵牌 —— 同组兵种速度不同也不再来回切换/闪现);
5. **数量优先级**(超出 `maxModels` 时):选中 > 我方(绿)> 敌方(红)> 盟友(蓝)> 其他(灰),
   同级取离**视角中心**更近者;默认上限 24(兵多时移动动画不再被截断)。
6. **海面(v1.8.0)**:下海部队换为**船模**(上岸自动换回);海战与陆地同逻辑
   (锚定海战横幅/同侧节流/迷雾);`ship.g3dj`(+贴图)放入资源目录即生效,
   缺失时海面沿用陆军模型。
7. **陆↔海过渡(v1.9.1)**:过渡**与行军进度绑定** —— 部队在跨陆/海的路段上,
   行进 0→50% 当前模型缩到最小,**50%→100% 已换成另一种模型并放大**(到达时正好全尺寸);
   不再等到站后再播放。`morphMove:false` 可改回“到站后过渡”(`morphMs` 时长);
   `morphMs:0` 且 `morphMove:false` = 完全关闭。纯视口缩放实现:绘制次数不变、不影响流畅度。
   > **v4.8.0 修复**:过渡的两半段各用**各自形态**的模型 —— 缩小段用“船模 combo+船模挂件”、
   > 放大段用“陆军 combo+陆军挂件”。旧版每支部队只带一套签名(按当前地块算),船→兵时放大段
   > 会拿船模 id 去加载陆军模型(查不到 → 回退**默认兵模**),兵→船则拿步兵 id 当船模。
   > 另外过渡开始即**预热两种形态**的模型加载,目标模型未就绪时缩到最小后**原地等待**再放大,
   > 不再闪一下默认模型。无船模资源时海陆都用陆军模型,不再按船模 id 找模型。

## 兵模拼接选择(v4.7.0)

装载配置 `loadSelect.json` 放资源目录(旧 `parts.json` 仍可读),插件读取:

- `unitSelect`:宗教块数组 `[ { "religion": "Catholic", "0:Warior": { "level": {...} } }, ... ]`;按部队宗教取第一个命中块;
- `unitgroup`:宗教块没命中时的文化簇兜底;可写 `unitgroup.<文化组>.<兵种>.level`,也可直接在 `unitgroup` 下按兵种写(旧格式);
- `shipSelect`:按时代(`Game_Calendar.CURRENT_AGEID`)取 `galley` 船型;"全部重载"或时代变化后重建船模。

> v4.7.0 优先级(文件层):**civilization/<tag>.json > loadSelect.json 的 unitSelect(宗教块)> unitgroup(文化组)> setting.json 的 model**。
> `level` 只是等级段选项,可以写在任意层级的对象里(religion 块、文化组、兵种条目、byId 等都能再包一层 level)。
> 具体文明文件不需要 religion/group/default;没写的兵种自动回落全局。
> `religion` 可用宗教英文名(如 `Catholic`、`Sunni`)或 `Religions.json` 的数字 ID;`religionGroup` 用 `ReligionGroupID`(当前 0~5)。

**选型规则**:兵牌显示哪个模型 = 该兵牌里**数量(`num`)最多的兵团**为主兵种;
`主兵种 uID` → `ArmyManager.lUnitsTypes[uid].File`(Warior/Spearmen/Cavalry…)+
`ArmyManager.lArmy[uid][aID].UnitLevel` 作为等级;宗教
(`Civilization.getReligionID()` → `Religions.json`)用于匹配 `unitSelect` 宗教块,
没命中时用文化分组(`Civilization.iGroupID` → civsGroups)匹配 `unitgroup`。
最后解析不到 → 用 `setting.json` 的 `model`。

模型文件本身在 `models/main/g3d/`;其余模型库元数据(rigs/animSets/combos/mounts)移到 `models/assembly.json`。

### 文明变体覆盖(v4.7.0)

`civilization/<tag>.json` 按**具体文明**覆盖(文件层优先级最高)。
`unitSelect` 是**顶层数组**(不再有 `loadSelect` 包裹),每个元素是兵种表;
具体文明不需要 `religion`/`group`,也不需要 `default`:

```json
{ "civ": "ger",
  "unitSelect": [
    { "1:Spearmen": { "level": { "0-3": "foot_euro_ancient", "4+": "foot_euro_medieval" } },
      "3:Archer":   { "level": { "0+": "foot_euro_medieval" } } }
  ],
  "shipSelect": "ship_galley",
  "attachments": {
    "unitSelect": [
      { "1:Spearmen": { "level": { "0-3": ["unit_weapon_spear_long"], "4+": ["unit_weapon_spear_long"] } } }
    ],
    "shipSelect": { "default": [] }
  } }
```

查找:文明 tag(`Civilization.getCivTag()` 小写)→ 对应文件。
旧的 `loadSelect.unitSelect` / 扁平 `loadSelect` 写法仍兼容。
覆盖里没写的兵种自动回落全局 `loadSelect.json` 的 `unitSelect`(宗教块)/`unitgroup` 与 `models/attachments.json`。
`assembly.body/head/feet` 是分件预留,当前 `models/body|head|feet` 为空 → 忽略。

## 挂件规则(models/attachments.json,v4.7.0)

`attachments.json` 与 `loadSelect.json` 同构:

- `unitSelect`:宗教块数组(`[{ "religion": "Catholic", "<uid>:<Type>": { "level": {...} } }]`);
- `unitgroup`:文化组兜底(可写成 `<文化组> → 兵种表`,也可直接按兵种写);
- `shipSelect`:船模挂件。可直接写一条规则(带 `byId/religion/religionGroup/group/level/default`),也可按兵种类别分组;
- 旧字段 `rules` 仍按 `unitSelect` 读取(兼容旧文件)。

宗教块/兵种条目内仍支持 **byId(aID/兵团名) > religion > religionGroup > group > level > default**(旧格式兼容),
值必须是数组;`level` 可嵌在任意层级,键写法 `"0-3"`(区间)/ `"7+"`(及以上)/ `"3"`(精确),按书写顺序先命中先用:

```json
{
  "unitSelect": [
    { "religion": "Catholic",
      "1:Spearmen": { "level": { "0-3": ["unit_weapon_spear_long"], "4+": ["unit_weapon_spear_long"] } } }
  ],
  "unitgroup": {
    "European": {
      "1:Spearmen": { "level": { "0+": ["unit_weapon_spear_long"] } }
    }
  },
  "shipSelect": {
    "religionGroup": { "1": { "level": { "0-3": [], "4+": [] } } },
    "default": []
  }
}
```

船模挂件的等级/文化取自该部队**主兵团**(与 unitSelect 同源);船模本身仍按 `loadSelect.json`
的 `shipSelect`(时代)或文明覆盖选择。

## 武器决定动画(v4.4.0)

**动画跟着「手里拿的武器」走，不是跟着身体型号走。** 身体(combo)由等级/宗教/文化选，武器由
`models/attachments.json` 选；两者不一致时用 `models/assembly.json` 的 `animDonors` 借动画：

```json
"animDonors": {
  "foot":    { "spear": "foot_euro_ancient", "sword": "foot_euro_medieval",
               "bow": "foot_eastasia_medieval", "musket": "foot_euro_line" },
  "mounted": { "sword": "mounted_euro_knight", "bow": "mounted_eastasia_archer",
               "musket": "mounted_arab_musket" }
}
```

- 键 = `<assembly.combos[combo].rig>.<武器族>`；武器族由**挂件签名的件名**判定
  （crossbow / musket(flintlock/matchlock/arquebus/pistol) / bow / spear(pike/lance/halberd) / sword(blade/saber/axe/mace/knife)）。
- 命中且与 combo 自带动画不同族时：**只解析 donor 的 g3dj 动画数据**（不建网格/贴图），
  把动画通道按骨名**重新绑定到当前模型**的骨节点上，然后替换 `Model.animations`。
  骨骼集合一致（foot 系 53 根、mounted 系 76 根，离线校验 miss=0）。
- 自带同族动画的 combo（如 `foot_arab_medieval` 本来就叫 sword）不会借。
- 攻击粒子事件表同步改用武器族对应的表（`bow` 就播 3.967s 的弓箭动作 + `bow_release`）。

效果举例（重载后看 `借用动画=` 日志）：

| 兵种/等级 | 身体 combo | 挂件 | 结果 |
|---|---|---|---|
| Archer 7+ | `foot_euro_line`(musket) | `bow_wood` | 借 `foot_eastasia_medieval` → **弓箭动画** + 弓箭事件 |
| Warior 7+ | `foot_euro_line`(musket) | sword+shield | 借 `foot_euro_medieval` → 剑盾动画 |
| Spearmen 4-6 | `foot_euro_medieval`(sword) | `spear_long` | 借 `foot_euro_ancient` → 长矛动画 |
| Spearmen 7+ | `foot_euro_line`(musket) | `spear_long` | 借 `foot_euro_ancient` → 长矛动画 |
| Cavalry | mounted_* | 无挂件 | 不借，用自身 |

## 动画事件 / 武器事件表(v4.3.0)
`anim_events.json` 定义「动画绝对时间 → 粒子预设」与最小状态机(数据取自 EU5 `.animsm`)。

**关键:武器表按 `animSet` 选,不按模型/材质名猜。** 例如 `foot_arab_line` 的 mesh 是
`matchlock_arquebus`,但 `assembly.json` 里 `animSet = "musket"` → 用 musket 的 1.6616s 枪口事件,
而不是 arquebus 的 0.78s。

解析顺序:

```text
models/assembly.json  combos[<combo>].animSet(逗号分隔,骑乘形如 "sword_r,horse")
  → 逐个查 anim_events.weapons[*].animSets(第一个有事件表的生效,horse/camel 无事件自动跳过)
  → 没有 animSet 时用 combos[<combo>].group 当伪 animSet(artillery / ship 已映射到 cannon / ship_cannon)
  → 都没有 = 该 combo 无攻击事件(如 mount_camelry / mount_elephantry)
```

启动日志会打印:

```text
装配配置: assembly.json combo=21 animSet=21
文明变体: 2 个 tag
动画事件: anim_events.json 武器=14 预设=8 状态机=true 粒子=开
combo[foot_arab_line] 已加载 … animSet=musket 武器事件=musket(4个)
```

## 攻击粒子(v4.4.0)

- **触发**:每帧读 battle 动画控制器的 `current.time`,与上一帧比较跨过事件时间点（处理循环回绕）→ 按 `presets` 发射。
- **battle 相位分桶**:`PHASE = 6`。同一 combo 的 battle 动画按部队 key 的 hash 分到 6 组、起始时间错开 1/6 周期，
  同兵种不再齐步开火；每桶每帧只 update 一次。仅 battle 状态建相位实例（4 关系色 × 6 桶），idle/move/retreat 仍共享。
- **锚点**（模型没有 vfx locator，用骨骼）:

| 事件 `place` | 用哪根骨 |
|---|---|
| `vfx_loc_01` / 空（近战）/ `arrow` | `Unit_Wea_Right_Hand02`（武器尖端，缺失回落 `Unit_Base_Rig_RightHand`） |
| `vfx_loc_02` / `atGrip: true` | `Unit_Wea_Right_Hand01`（握把） |
| 炮 `muzzle` | `barrel_end`（方向 = `barrel_end - barrel_base`） |
| 炮 `fuse` | `cannon_back_j` |
| 船 `ship_port` / `water` | 船体包围盒侧面甲板高度 / 船尾 y=0（船模无炮位节点） |

- **绘制**:每支部队自己的 scissor 视口内、`batch.end()` 之后用 `SpriteBatch` 画（alpha 一遍 + add 一遍），
  世界坐标 → `camera.project` → 视口像素；GL 状态（depth test/depth mask/blend）画完还原。
- **调参**（`anim_events.json` 顶层）:`enabled` / `particleScale` / `particleRate` / `particleMax` /
  `locatorFallback`(自定义骨名;`weapon_tip`=骨启发式) / `battleHoldMs`(战斗结束后继续播攻击的时长,默认 1200,0=关)。

实现细节(排错时看):

- 粒子的 `owner` 用**部队 key 的 hash**(不是渲染下标),候选排序/堆叠去重变化不会把粒子画到别的兵牌视口；
- `battleHoldMs`:游戏状态一离开战斗就切 idle 会让 1.66s 的枪口事件播不到 → 战斗结束后最多再播这么久；
- **首次 tick**:`battlePrev` 初值 -1 时按 0 处理,`t=0` 的事件在进入战斗的第一帧立即触发；
  否则要等整段动画循环回绕(2.5s+ 的枪械动画),短战斗可能完全看不到枪口粒子；
- `presets.stop` 事件直接忽略(一次性 burst，不需要清理循环粒子)；
- 诊断:`lastParticles`=当前存活粒子数、`spawnedParticles`=本帧新发射数、`particleNote`=最近一次 `武器/预设 @秒`。

```text
粒子锚点验证日志(每条 combo 首次加载): combo[foot_arab_line] 已加载 … 武器事件=musket(4个)…
运行期诊断(collectDiag): … fx=on[武器=14 animSet=20 预设=8 状态机=有] …
```

### byId:按具体兵种指定模型(v3.1.0,数组结构兼容)

`unitSelect` 宗教块 / `unitgroup` 的每个兵种条目里可加 `byId`,优先级 **byId > religion > religionGroup > group > level**(`level` 可写在任意层级):

```json
{
  "unitSelect": [
    { "religion": "Catholic",
      "1:Spearmen": { "byId": { "2": "foot_euro_medieval" } } }
  ],
  "unitgroup": {
    "European": {
      "0:Warior": { "byId": { "EliteSwordsman": "foot_euro_line", "4": "foot_euro_line" } }
    }
  }
}
```

- 键可以是 **兵团索引 aID**(= game/units/<类别>.json 里 Army 数组下标,0 起;**推荐**,与语言无关),
  也可以是 **兵团名**(游戏已本地化,如 "精锐长矛兵");
- 兵种对照示例:精锐长矛兵 = Spearmen[2]、精锐剑客 = Warior[4]、骑士 = Cavalry[2]、坦克 = Cavalry[9];
- 建议写在资源目录的 **loadSelect.override.json** 里(旧名 `unitSelect.override.json` 仍兼容;
  只写要覆盖的键即可,启动时合并进 loadSelect.json,deploy-combos 覆盖模型库时**不会丢**)。

**按需加载**:每个 combo 一个独立 `Model`(4 关系色 × 4 状态实例),首次用到才加载
(每帧最多 1 个,不影响流畅度),LRU 上限 10 个;`comboMode:false` 可整体关闭。

## 伪3D地图适配(Pseudo3DMap,v3.0.0)

装了 Pseudo3DMap(伪3D 倾斜地图)时,兵模按**地图局部深度比例**自动缩放(近大远小),
随地图缩放实时连续变化,与倾斜地图吻合;未装补丁(或设置里 `MAP_TILT` 关闭)自动退化为固定尺寸,无需配置。

- 检测:`Game.getTiltK()` 反射(Pseudo3DMap 新增;缺失/关闭 → 0);
- 公式:`ks = getTiltK() × currentScale`(再乘 `tiltStrength`/`nearFar`),兵模尺寸系数
  **`f = (1+ks/2)² / D²`**,其中 `D = 1 + ks·(1 − Y/H)`(Y = 兵牌底边的屏幕 Y,y 向下,0=屏幕最上);
  **以屏幕中线为 1 归一** → 屏幕下方(近)更大、上方(远)更小、平均尺寸不变;
  之所以是 `1/D²`:地图公式 `screenY = Y/D` 的雅可比 `scaleY ∝ (1+ks)/D²`,即地图内容在该 Y 处就是这个放大率,
  兵模跟着它才不会“浮在倾斜平面上”;
  **以兵模接触点(底边)为锚点向上生长**(位置本身已由游戏函数给出倾斜结果,这里只改尺寸);
  尺寸系数整体夹在 **0.5~2 倍**之间);
- 强度:`setting.json` 的 `tiltStrength`(伪3D 总适配强度;0=关闭;上限 2)× `nearFar`(v4.9.12 近大远小比例;
  0=关闭;1=标准;越大越明显,上限 4)。
- 诊断:状态日志新增 `近大远小ks=…(缩放系数=a~b)`,可确认本帧实际用到的尺寸系数区间。

### 渲染窗口视角随地图缩放(v4.9.11:以模型中心为圆心的 3D 环绕)

渲染窗口的相机沿一条**圆心 = 模型竖直中心**的圆弧移动(纯 3D 环绕,不是绕脚底也不是缩放),
`θ` 由**地图缩放**驱动(不看地图倾斜开关 / `getTiltK()` —— 否则地图未开倾斜时完全没反应):

```
u = clamp01( ln(currentScale / 锚点) / ln(6) )          // 锚点 = 兵模开始出现的缩放(modelMinScale 或 0.5×modelMinScaleMul)
θ = viewPitch[1] + (viewPitch[0] − viewPitch[1]) · u    // 拉得越近 → u→1 → θ 趋最小(更平视)
相机位置 = (0, 模型中心Y + R·sinθ, R·cosθ), 朝向 = (0, −sinθ, −cosθ), 上方向 = (0, cosθ, −sinθ)
```

- 配置:`setting.json` 的 `viewPitch: [最小, 最大]`(度;默认 `[12, 55]`);两个值相等 = 关闭;
- **方向与地图一致**:地图拉得越近(伪3D 倾斜越强、越接近“平视”)→ 兵模俯角越小(也更平视);
  缩到最小(地图接近“俯视”)→ 兵模也从上往下看;
- **圆心在模型中心**:正交相机下半径只决定朝向,所以模型中心恒落在窗口中心,俯角变化时
  模型只是「被绕着看」,不会上下漂移;
- **保证不超出窗口**:每个模型在加载时就记录了自己的「竖直中心 / 竖直半高 / 水平半宽
  (XZ 对角线之半 → 任意朝向;含脚下阴影盘)」;每帧按当前俯角算投影半径,
  `camera.zoom = max(1, 投影半径 / 窗口半径×1.04)` 自动拉远 —— 环绕全程不裁切、不跳变
  (数值校验:`.tools\verify\orbitcheck.ps1`,4 种模型形状 × 1°~85° × 3 种窗口宽高比,最大占半窗 48.1%);
- **开销**:几何参数在资源构建时算好,每帧每模型只做几次乘加(无分配、无查表、无着色器重建);
  俯角的 sin/cos 每帧只算一次(同帧多模型共享),`camera.update()` 本来就要调一次,不额外增加;
- 缩小到兵模不显示的缩放(低于 `modelMinScale`)时相机不参与(不画模型);
- 诊断:日志状态行新增 `视角=xx°(scale=yy)`,可直接确认俯角是否在变。
- 调节建议:想要“拉近时几乎侧面平视”就把 `viewPitch[0]` 调小(如 8~12);
  想要“缩小时更强烈的俯视”就把 `viewPitch[1]` 调大(如 60~72);
  想更早进入极端值就调小 `modelMinScaleMul`(锚点变小)。

## 用法与限制

> **构建前置(不进版本库)**:游戏本体 `game.jar` 与宿主 `HotPluginLoader-*.jar` 需自备;libGDX `g3d`
> 类由 Gradle 依赖 `com.badlogicgames.gdx:gdx:1.12.1` 自动提取(`extractG3d` → `build\g3dclasses`)。
> 游戏根默认取上一级目录,可用环境变量 `AOH_GAME_ROOT` 或 `-PgameRoot=` / `-PhostJar=` / `-PgameJar=` 覆盖。

1. `build.bat` → 把 `out\G3DArmy-3.0.1.jar` 放进 `hotplugins\`(**删掉旧版 HotG3DArmyTest/HotG3DArmyPseudo3DMapVersion 的 jar**);
   进对局后**拉近视角**(scale ≥ 0.4/0.5 或进入框选模式)出现兵模(默认全部部队);
2. 开关在 `army.json` 的 `"enabled"`(**v1.6.0 移除右上角「关闭 3D 兵模」按钮**;
   `false` = 不显示;改后「全部重载」生效);
3. 兵模位于方框**上方**(不遮挡数字;v1.4 间距更近);移动自动转向、静止呼吸、
   战斗突刺、**撤退(白旗)小碎步前倾**;
4. **战斗发生时**:双方兵模锚定战斗横幅左右半区、面对面、同一基准线、播放战斗动作;
   同格多部队层叠(如 6/12/10)时只显示首个兵牌的兵模,其开始移动后下一个自动接替;
   **点选任意一颗军牌**(选中框出现)→ 该军牌的兵模立即显示(即使不是首个);
5. 用户模型/动画替换见上一节(目录=`resources/<插件文件主名>/`);想“方框+兵模并存”可把 `modelPx` 调小;
6. **原版方框无法被隐藏**(需 Mixin 补丁);淡出档(0.3~阈值)与淡入渐显未实现(直接切换)。

## 日志(搜 `G3DArmy`)

> v1.4.7:插件诊断(着色器探针/状态行/贴图跟踪)**联动框架「启用日志」开关** ——
> 关闭后不再采集、不再输出(帧内仅一次缓存布尔读,性能零负担);开启后自动恢复。

- `已加载 v3.0.1 G3DArmy …`、`伪3D适配:检测到 Pseudo3DMap…`、`配置:开关=… 兵模尺寸=… 抬升=… 战斗分离=… 堆叠首个=… 模型=… 船模=… 过渡=… 视口高=… 骨骼=… 模型选择=… 伪3D强度=…`、`模型来源: …`
  (含加载失败/模板自检/三态动画匹配结果)、`GL 信息: …`、`兵模覆盖层已挂载…`;
- `状态: scale=… selectMode=… 候选=… 列表=…(战斗=…) 渲染=…`(每 ~15s);
- `渲染错误/部队读取失败: …`(带异常链)。
