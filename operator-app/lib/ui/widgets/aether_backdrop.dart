import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../../theme/aether_colors.dart';

class AetherBackdrop extends StatelessWidget {
  const AetherBackdrop({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SizedBox.expand(
      child: ColoredBox(
        color: AetherColors.voidBg,
        child: Stack(
          fit: StackFit.expand,
          children: [
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: RadialGradient(
                  center: Alignment(-0.72, -0.88),
                  radius: 1.25,
                  colors: [Color(0x556D28D9), Color(0x0005040C)],
                ),
              ),
            ),
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: RadialGradient(
                  center: Alignment(0.98, 0.92),
                  radius: 1.15,
                  colors: [Color(0x3822D3EE), Color(0x0005040C)],
                ),
              ),
            ),
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: RadialGradient(
                  center: Alignment(0.15, 0.35),
                  radius: 1.4,
                  colors: [Color(0x14F5C56B), Color(0x0005040C)],
                ),
              ),
            ),
            const CustomPaint(painter: _StaticNoisePainter()),
            child,
          ],
        ),
      ),
    );
  }
}

/// Cheap static grain — no animation, no shaders.
class _StaticNoisePainter extends CustomPainter {
  const _StaticNoisePainter();

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = const Color(0x0AFFFFFF);
    final rng = math.Random(42);
    final count = (size.width * size.height / 2800).clamp(40, 180).toInt();
    for (var i = 0; i < count; i++) {
      final x = rng.nextDouble() * size.width;
      final y = rng.nextDouble() * size.height;
      canvas.drawRect(Rect.fromLTWH(x, y, 1.2, 1.2), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class AetherMark extends StatelessWidget {
  const AetherMark({super.key, this.size = 36});

  /// Live site mark from https://donnernet.de/favicon.svg
  static const assetPath = 'assets/brand/favicon.svg';

  final double size;

  @override
  Widget build(BuildContext context) {
    return SvgPicture.asset(
      assetPath,
      width: size,
      height: size,
      semanticsLabel: 'Aetherion',
    );
  }
}
