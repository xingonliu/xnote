# 第三方许可证

## AndroidLiquidGlass 2.0.1

XNote 使用 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的 `io.github.kyant0:backdrop:2.0.1` 与 Shapes `1.2.1`，并基于上游提交 `65ab177e90e5c1d8c62e70cf7755841982da65f6` 的 catalog 源码纳入 `LiquidBottomTabs`、`LiquidBottomTab`、`LiquidButton` 及其交互辅助实现。XNote 对 catalog 源码补充了包名、代码结构、尺寸、禁用态和无障碍输入；为避免近白背景发生加法混合亮度饱和，将 `LiquidButton` 的全区域按压白光从 `0.08` 降为 `0.04`，并将无 RuntimeShader 时的全区域兜底白光从 `0.25` 降为 `0.125`，其余材质配方保持上游实现。

Copyright 2025 Kyant

Licensed under the Apache License, Version 2.0. A complete copy is available at [`LICENSES/Apache-2.0.txt`](./LICENSES/Apache-2.0.txt).

## liquid_glass_widgets 1.2.3

XNote 的 Flutter 工程使用 [`liquid_glass_widgets`](https://github.com/sdegenaar/liquid_glass_widgets) 1.2.3。该版本以 MIT License 发布。

### MIT License

Copyright (c) 2024–2026 Sebastian Degenaar

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Cupertino Icons

XNote 的 Flutter 工程使用 [`cupertino_icons`](https://pub.dev/packages/cupertino_icons) 提供 `liquid_glass_widgets` 组件所需的 Cupertino 图标字体。该包以 MIT License 发布。

### MIT License

Copyright (c) 2016 Vladimir Kharlampidi

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Keyline Icons

XNote 使用并转换了 [Keyline Icons](https://keylineicons.com/) 官方仓库提交 `14cd695f3f2bbe320bbe7a01e65b251df7ba52cf` 的 Rounded Stroke 与 Rounded Fill SVG 图标。原始项目使用 MIT License。

### MIT License

Copyright (c) 2026 Keyline Icons

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
