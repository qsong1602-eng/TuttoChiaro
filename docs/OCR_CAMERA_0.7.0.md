# 0.7.0 拍路牌裁剪与镜头 · 2026-09-27

> 后续用户真机反馈发现 90° 裁剪错位，已由 0.7.1 修复并完成设备几何检查，见 [修复记录](OCR_CAMERA_0.7.1.md)。下文保留 0.7.0 当时的实现与验证范围。

## 已实现

原实现只有屏幕上的取景框，OCR 接收整张 `MediaImage`，导致框外路牌和杂文混入翻译。新版预览和拍照使用同一 CameraX ViewPort；快门时冻结取景框及预览变换，映射到拍照缓冲区坐标。先真实裁剪像素，再旋转为正向，最后将裁剪位图交给 ML Kit。无效范围报错，不回退识别全图。

- 拍摄前切换窄框、标准框、大框；框外遮暗。框相对于实际预览区域居中，三个范围分别适合单行、普通路牌和较大版面。
- 拍摄后显示实际裁剪照片和可编辑识别文字，可重拍或确认“翻译框内文字”。未确认不会开始翻译；照片不写入相册或文件。
- 变焦滑杆及设备范围内的 1× / 2× / 3× / 5× 快捷倍率；快门等待变焦请求完成。拍照/识别时锁定框和镜头选择。
- 枚举可访问的后置彩色相机及默认逻辑后摄下的物理镜头，以焦距和传感器宽度计算相对视角，显示明显更窄的长焦候选。预览和拍照同时请求相同 physicalCameraId；独立公开的镜头直接选择 CameraSelector。打不开的长焦回到自动镜头并提示。
- 未开放独立长焦时保留系统变焦并明确说明可能含数码裁切；系统可能自行切物理镜头，但不会将放大倍率冒充已经验证的光学长焦。
- 拍照分辨率优先接近且不高于 1920×1440，避免在高像素手机上解码数千万像素照片造成内存压力。设备不提供较低模式时 CameraX 可以选择更高模式。
- 页面退出、后台及重新绑定使旧请求失效；相机图片及时关闭，OCR 完成前不回收其位图，已交给界面的照片由界面释放引用后回收。

## 验证范围

JVM 测试覆盖裁剪边界向内取整、图像边界、无效/非有限坐标。新增 Android 图像测试通过合成像素覆盖 0°/90°/180°/270°、不同预览/照片分辨率、非全幅 ViewPort 以及框外像素排除。后者已纳入测试 APK，**需要设备执行，不能以编译成功代替运行通过**。

本轮 ADB 未检测到手机。待设备测试：

1. 在框外放置另一张路牌，分别拍摄三个框，检查确认照片和识别文本只包含框内内容。
2. 横竖屏及不同倍率拍摄，检查框边、旋转、镜头切换后裁剪一致；小屏/大字体检查操作区滚动。
3. vivo X300 Pro 和 iQOO 11 Pro 分别记录公开镜头、长焦是否真实启用、变焦范围和绑定失败回退。用遮挡镜头或设备相机诊断确认实际物理镜头，不能只凭倍率按钮认定。
4. 识别中返回/后台/重新进入、重拍及连续多次拍摄，检查没有旧结果覆盖新页面、崩溃或图像资源泄漏。
5. 运行 `com.tuttochiaro.offline.data.OcrCropInstrumentedTest`，保存实际结果。

模型和 Latin OCR 语言范围不变。本轮没有增加中文/日文/韩文 OCR。

## 官方依据

- [CameraX 输出与坐标变换](https://developer.android.com/media/camera/camerax/transform-output)
- [CoordinateTransform：同一 ViewPort 的输出坐标映射](https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform)
- [ImageProxyTransformFactory](https://developer.android.com/reference/androidx/camera/view/transform/ImageProxyTransformFactory)
- [Camera2Interop 物理镜头请求](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2Interop.Extender#setPhysicalCameraId(java.lang.String))

实装依赖仍为 CameraX 1.4.2，API 已对照本地依赖校验。官方相机 API 支持并不等于厂商向本应用开放全部镜头。
