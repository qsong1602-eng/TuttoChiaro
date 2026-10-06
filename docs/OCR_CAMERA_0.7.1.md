# 0.7.1 拍照裁剪旋转错位修复 · 2026-09-27

用户在 vivo X300 Pro 上发现：取景框已裁剪，但裁剪方向像被旋转 90°，范围与取景框不一致。

## 原因与修复

0.7.0 使用 PreviewView OutputTransform 与 ImageProxyTransformFactory 的默认缓冲区变换组合。它没有保留两个输出相对传感器的完整方向关系。当 JPEG 硬件先旋转照片像素、CameraX 将剩余 rotationDegrees 更新为 0 时，预览仍可能采用另一缓冲区方向，按归一化裁剪框关联会错位。

0.7.1 快门冻结 PreviewView.sensorToViewTransform，并使用本次 ImageProxy.imageInfo.sensorToBufferTransformMatrix：

`view → inverse(sensorToView) → sensor → sensorToBuffer → capture pixels`

在实际照片缓冲区中裁剪，再只应用本次图片剩余的 rotationDegrees。CameraX 已经做过的 JPEG 硬件旋转不会再被当作预览旋转重复使用。继续使用同一 ViewPort、向内取整、无效坐标拒绝，不回退识别整图。

原 Android 测试使用同一 ImageProxyTransformFactory 正反生成预览与裁剪坐标，而且预览/拍照方向相同，因此不能覆盖用户设备上的差异。重写后的测试独立定义传感器、预览和照片坐标，覆盖 4 种预览方向 × 4 种硬件旋转 × 3 个不对称选区，共 48 组逐像素检查；还检查框外及不可逆变换拒绝。

另增加 OcrCameraGeometryTest：在真实设备上通过 PreviewView + ImageCapture 拍摄，检查三个框在 1×/3× 下映射到正向 ViewPort 后的位置和尺寸，并记录旧/新裁剪坐标。测试不保存照片或进行 OCR。该测试验证几何关系，不能代替用户对具体路牌、手持运动及物理长焦的视觉验收。

## 验证

33 项 JVM 测试与 lint 通过，九个 APK 的签名和所有模型 SHA 校验通过。已覆盖安装到 vivo X300 Pro，版本 0.7.1 / code 8。最终 4 个 Android 测试全部通过，包含 48 组像素旋转选区、两个拒绝测试及六次真实拍照几何检查。

真机照片缓冲区为 1440×1920，剩余 rotationDegrees=0；旧算法把横向窄框映为约 190×1728，修复后为约 777×422。三个框在 1×/3× 下的最大映射边界偏差为 0.221344 像素，之后再向内取整。测试在竖屏默认逻辑后摄下完成；物理长焦、实际路牌内容和手持移动场景仍需独立视觉验收。

首次 ActivityScenario 启动停滞，改用外部前台启动；首轮真实拍摄的断言混合了 ViewPort 元数据取整和像素向内取整误差，改为分别检查后通过。产品裁剪代码没有为该断言变更。最终日志 `.tooling/temp/camera-tests-0.7.1-final.log`，完整记录见 HANDOFF.md 与 BUILD_0.7.1_VERIFICATION.json。

## 官方实现依据

- [CameraX ProcessingInput2Packet：JPEG HAL 旋转时同步更新 sensorToBufferTransform](https://raw.githubusercontent.com/androidx/androidx/androidx-main/camera/camera-core/src/main/java/androidx/camera/core/imagecapture/ProcessingInput2Packet.java)
- [CameraX JpegImage2Result：向应用交付更新后的图像信息](https://raw.githubusercontent.com/androidx/androidx/androidx-main/camera/camera-core/src/main/java/androidx/camera/core/imagecapture/JpegImage2Result.java)
- [PreviewView](https://developer.android.com/reference/androidx/camera/view/PreviewView)

本项目使用 CameraX 1.4.2；以上链接解释机制，具体 API 以本地依赖编译和设备测试为准。
