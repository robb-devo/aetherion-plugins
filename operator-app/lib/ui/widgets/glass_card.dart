import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../platform/platform_info.dart';
import '../../theme/aether_colors.dart';

class GlassCard extends StatefulWidget {
  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.onTap,
    this.accent,
  });

  final Widget child;
  final EdgeInsets padding;
  final VoidCallback? onTap;
  final Color? accent;

  @override
  State<GlassCard> createState() => _GlassCardState();
}

class _GlassCardState extends State<GlassCard> {
  bool _hot = false;

  bool get _cheapHover => !kIsWeb && isWindowsDesktop;

  @override
  Widget build(BuildContext context) {
    final accent = widget.accent ?? AetherColors.amethyst;
    final border = _hot ? accent.withValues(alpha: 0.55) : AetherColors.glassStroke;
    final card = AnimatedContainer(
      duration: Duration(milliseconds: _cheapHover ? 90 : 220),
      curve: Curves.easeOutCubic,
      padding: widget.padding,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: _hot ? AetherColors.glassFillStrong : AetherColors.glassFill,
        border: Border.all(color: border),
        boxShadow: _cheapHover
            ? const []
            : [
                BoxShadow(
                  color: accent.withValues(alpha: _hot ? 0.18 : 0.08),
                  blurRadius: _hot ? 22 : 12,
                  offset: const Offset(0, 8),
                ),
              ],
      ),
      child: widget.child,
    );

    return MouseRegion(
      onEnter: (_) => setState(() => _hot = true),
      onExit: (_) => setState(() => _hot = false),
      child: widget.onTap == null
          ? card
          : Material(
              color: Colors.transparent,
              child: InkWell(
                onTap: widget.onTap,
                borderRadius: BorderRadius.circular(18),
                child: card,
              ),
            ),
    );
  }
}

class FadeSlide extends StatelessWidget {
  const FadeSlide({super.key, required this.child, required this.animation});

  final Widget child;
  final Animation<double> animation;

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: CurvedAnimation(parent: animation, curve: Curves.easeOutCubic),
      child: SlideTransition(
        position: Tween<Offset>(
          begin: const Offset(0, 0.04),
          end: Offset.zero,
        ).animate(CurvedAnimation(parent: animation, curve: Curves.easeOutCubic)),
        child: child,
      ),
    );
  }
}
