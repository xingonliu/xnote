import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

// -- Type Definitions

class GlassPocView extends StatelessWidget {
  const GlassPocView({super.key});

  // -- Functions

  void _showToast(BuildContext context) {
    GlassToast.show(
      context,
      message: 'Liquid Glass 反馈层工作正常',
      icon: const Icon(CupertinoIcons.check_mark_circled_solid),
      type: GlassToastType.success,
      quality: GlassQuality.standard,
    );
  }

  Future<void> _showSheet(BuildContext context) async {
    await GlassModalSheet.show<void>(
      context: context,
      quality: GlassQuality.standard,
      initialState: GlassSheetState.half,
      builder: (sheetContext) => SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              Text(
                'GlassModalSheet',
                style: Theme.of(sheetContext).textTheme.headlineSmall,
              ),
              const SizedBox(height: 12),
              const Text(
                '弹层直接来自 liquid_glass_widgets，并保留系统返回、拖动关闭和减少动画行为。',
              ),
              const SizedBox(height: 20),
              Align(
                alignment: Alignment.centerRight,
                child: GlassIconButton(
                  icon: const Icon(CupertinoIcons.xmark),
                  onPressed: () => Navigator.of(sheetContext).pop(),
                  quality: GlassQuality.standard,
                  semanticLabel: '关闭弹层',
                  useOwnLayer: true,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildActionPanel(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface.withValues(alpha: 0.84),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text('浮层与反馈', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            const Text('按钮、Toast 与 Modal Sheet 都直接调用库 API。'),
            const SizedBox(height: 20),
            GlassToolbar(
              key: const Key('glass-poc-toolbar'),
              quality: GlassQuality.standard,
              children: <Widget>[
                GlassIconButton(
                  icon: const Icon(CupertinoIcons.bold),
                  onPressed: () => _showToast(context),
                  quality: GlassQuality.standard,
                  semanticLabel: '工具栏粗体示例',
                ),
                GlassIconButton(
                  icon: const Icon(CupertinoIcons.arrow_uturn_left),
                  onPressed: () => _showToast(context),
                  quality: GlassQuality.standard,
                  semanticLabel: '工具栏撤销示例',
                ),
              ],
            ),
            const SizedBox(height: 20),
            Wrap(
              spacing: 12,
              runSpacing: 12,
              children: <Widget>[
                GlassIconButton(
                  key: const Key('show-glass-toast'),
                  icon: const Icon(CupertinoIcons.bell),
                  onPressed: () => _showToast(context),
                  quality: GlassQuality.standard,
                  semanticLabel: '显示玻璃提示',
                  useOwnLayer: true,
                ),
                GlassIconButton(
                  key: const Key('show-glass-sheet'),
                  icon: const Icon(CupertinoIcons.rectangle_stack),
                  onPressed: () => unawaited(_showSheet(context)),
                  quality: GlassQuality.standard,
                  semanticLabel: '显示玻璃弹层',
                  useOwnLayer: true,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAdaptationPanel(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final orientation =
        mediaQuery.orientation == Orientation.portrait ? '竖屏' : '横屏';
    final animation = mediaQuery.disableAnimations ? '减少动画' : '标准动画';
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface.withValues(alpha: 0.84),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text('自适应输入', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            Text('方向：$orientation'),
            Text('文字缩放：${mediaQuery.textScaler.scale(1).toStringAsFixed(2)}×'),
            Text('动效偏好：$animation'),
            const SizedBox(height: 12),
            const Text(
              '根节点启用自适应质量，滚动内容不请求 premium；系统无障碍设置由库直接处理。',
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, BoxConstraints constraints) {
    final panels = <Widget>[
      _buildActionPanel(context),
      _buildAdaptationPanel(context),
    ];
    if (constraints.maxWidth >= 720) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          for (var index = 0; index < panels.length; index += 1) ...<Widget>[
            Expanded(child: panels[index]),
            if (index != panels.length - 1) const SizedBox(width: 16),
          ],
        ],
      );
    }
    return Column(
      children: <Widget>[
        for (var index = 0; index < panels.length; index += 1) ...<Widget>[
          panels[index],
          if (index != panels.length - 1) const SizedBox(height: 16),
        ],
      ],
    );
  }

  // -- Lifecycle Hooks

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: <Widget>[
        Positioned.fill(
          child: LayoutBuilder(
            builder: (context, constraints) => SingleChildScrollView(
              key: const Key('glass-poc-scroll-view'),
              padding: const EdgeInsets.fromLTRB(20, 88, 20, 160),
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 960),
                  child: _buildContent(context, constraints),
                ),
              ),
            ),
          ),
        ),
        const Positioned(
          top: 0,
          left: 0,
          right: 0,
          height: 92,
          child: IgnorePointer(
            child: ProgressiveBlur(
              maxSigma: 16,
              direction: ProgressiveBlurDirection.topToBottom,
            ),
          ),
        ),
      ],
    );
  }
}
