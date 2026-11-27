# Sculptor-M27UA2-GravRot
Sculptor M27UA2智慧屏重力旋转修正

GravRot —— SCULPTOR M27UA2 智慧屏重力旋转修正
==========================================

背景
----
本机加速度计绕垂直轴装了 180°（X、Z 反号，Y 正常）。
结果：横屏完全正常；竖屏必然错 180°。

系统侧证据：
  mPortraitRotation = ROTATION_270   (厂商静态表，正确)
  mProposedRotation = 1 (ROTATION_90) (传感器路径，错 180°)

GravRot 读取加速度计原始值，按下面的映射修正后写回 USER_ROTATION：
  |y| 主导, y > 0  ->  rotation 0  (横屏)
  |y| 主导, y < 0  ->  rotation 2  (倒置横屏)
  |x| 主导, x > 0  ->  rotation 3  (竖屏，X 轴反号)
  |x| 主导, x < 0  ->  rotation 1  (竖屏另一侧)


重装系统 / 恢复出厂后的安装步骤
-------------------------------
1. 安装 gravrot.apk（需允许"安装未知应用"）
2. 打开 GravRot
3. 点【1. 授予「修改系统设置」权限】→ 在跳出的页面里打开开关
4. 回到 App，服务会自动启动（显示"运行中 ✓"）

必须有第 3 步，否则 App 能跑但改不了旋转。
这一步只能手动做：Google 从 Android 6 起就不允许普通应用自行申请该权限。

可选：设置 → 应用 → GravRot → 电池 → 不受限制
（本机在安装时已通过 adb 加入设备待机白名单，恢复出厂后需要重做）


安装后自检
----------
打开 App 应看到：
  权限: 已授予 ✓
  服务: 运行中 ✓
   accelerometer_rotation = 0

把屏幕顺时针转到竖屏，1~2 秒内画面应自动转正。


注意
----
* 只适用于这台传感器装反的机器。装到正常设备上会导致竖屏反向。
* minSdk 23 / targetSdk 30，Android 11 及以上可用。
* APK 用 debug.keystore 签名。若要发布新版本覆盖安装，
  必须继续用同一个 keystore，否则需先卸载旧版。
  debug.keystore 密码：android   别名：androiddebugkey


文件说明
--------
gravrot.apk          已签名安装包
debug.keystore       签名密钥（务必保留）
src/                 源码
AndroidManifest.xml  清单
build.ps1            构建脚本（需 JDK 17 + build-tools 33 + platform 30）

