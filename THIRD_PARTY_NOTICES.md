# 第三方许可证

## AndroidLiquidGlass 2.0.1

XNote 使用 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的 `io.github.kyant0:backdrop:2.0.1` 与 Shapes `1.2.1`，并基于上游提交 `65ab177e90e5c1d8c62e70cf7755841982da65f6` 的 catalog 源码纳入 `LiquidBottomTabs`、`LiquidBottomTab`、`LiquidButton` 及其交互辅助实现。XNote 对 catalog 源码补充了包名、代码结构、尺寸、禁用态和无障碍输入，并按项目设计规范调整了控件尺寸和按压高光强度。

Copyright 2025 Kyant

Licensed under the Apache License, Version 2.0. A complete copy is available at [`LICENSES/Apache-2.0.txt`](./LICENSES/Apache-2.0.txt).

## Haze 渐进模糊采样算法

`XNoteProgressiveBlurShader.kt` 的双通道高斯采样改编自 [Haze 1.7.2 的 HazeShaders.kt](https://github.com/chrisbanes/haze/blob/f55d1a4ee8901ad3f26a76e2d7bc7b0be789e84d/haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeShaders.kt)。XNote 保留配对采样与浮点累加，使用项目自己的边缘距离曲线、捕获边界钳制和主题叠色，并接入现有 Backdrop 效果管线。

Copyright 2024, Christopher Banes and the Haze project contributors

Licensed under the Apache License, Version 2.0. A complete copy is available at [`LICENSES/Apache-2.0.txt`](./LICENSES/Apache-2.0.txt).

## Keyline Icons

XNote 使用 [Keyline Icons](https://keylineicons.com/) 的原始 Rounded Stroke 与 Rounded Fill SVG 图标。既有图标来自官方仓库提交 `14cd695f3f2bbe320bbe7a01e65b251df7ba52cf`；编辑排版、表格与图片控制新增图标来自 `b83dfe1909a9916d36d85c1b9d4f324470768c92`。原始项目使用 MIT License。

### MIT License

Copyright (c) 2026 Keyline Icons

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
