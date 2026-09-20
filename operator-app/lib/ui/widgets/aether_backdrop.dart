import 'package:flutter/material.dart';

import '../../theme/aether_colors.dart';

class AetherBackdrop extends StatelessWidget {
  const AetherBackdrop({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SizedBox.expand(
      child: ColoredBox(
        color: AetherColors.voidBg,
        child: DecoratedBox(
          decoration: const BoxDecoration(
            gradient: RadialGradient(
              center: Alignment(-0.7, -0.85),
              radius: 1.2,
              colors: [Color(0x667C3AED), Color(0x0007060F)],
            ),
          ),
          child: DecoratedBox(
            decoration: const BoxDecoration(
              gradient: RadialGradient(
                center: Alignment(0.95, 0.95),
                radius: 1.1,
                colors: [Color(0x4022D3EE), Color(0x0007060F)],
              ),
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}

class AetherMark extends StatelessWidget {
  const AetherMark({super.key, this.size = 36});

  final double size;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(painter: _GemPainter()),
    );
  }
}

class _GemPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final gem = Path()
      ..moveTo(size.width * 0.5, size.height * 0.08)
      ..lineTo(size.width * 0.88, size.height * 0.38)
      ..lineTo(size.width * 0.5, size.height * 0.92)
      ..lineTo(size.width * 0.12, size.height * 0.38)
      ..close();
    final fill = Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [AetherColors.amethyst, AetherColors.cyan],
      ).createShader(Offset.zero & size);
    canvas.drawPath(gem, fill);
    canvas.drawPath(
      gem,
      Paint()
        ..color = const Color(0x66FFFFFF)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.2,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
