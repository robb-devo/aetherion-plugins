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
