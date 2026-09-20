import 'package:flutter/material.dart';

import '../../theme/aether_colors.dart';
import '../../theme/aether_theme.dart';

/// Compact SkyCrypt-inspired action / stat tile.
class SkyTile extends StatefulWidget {
  const SkyTile({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.accent = AetherColors.amethyst,
    this.onTap,
    this.enabled = true,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final Color accent;
  final VoidCallback? onTap;
  final bool enabled;

  @override
  State<SkyTile> createState() => _SkyTileState();
}

class _SkyTileState extends State<SkyTile> {
  bool _hot = false;

  @override
  Widget build(BuildContext context) {
    final enabled = widget.enabled && widget.onTap != null;
    final accent = widget.accent;
    return MouseRegion(
      onEnter: enabled ? (_) => setState(() => _hot = true) : null,
      onExit: enabled ? (_) => setState(() => _hot = false) : null,
      child: GestureDetector(
        onTap: enabled ? widget.onTap : null,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
          padding: const EdgeInsets.fromLTRB(12, 12, 12, 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            color: _hot
                ? AetherColors.glassFillStrong
                : const Color(0xFF12101C),
            border: Border.all(
              color: _hot
                  ? accent.withValues(alpha: 0.55)
                  : AetherColors.glassStroke,
            ),
            boxShadow: [
              BoxShadow(
                color: accent.withValues(alpha: _hot ? 0.22 : 0.06),
                blurRadius: _hot ? 16 : 8,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          child: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10),
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      accent.withValues(alpha: 0.35),
                      accent.withValues(alpha: 0.12),
                    ],
                  ),
                  border: Border.all(color: accent.withValues(alpha: 0.35)),
                ),
                child: Icon(widget.icon, color: accent, size: 22),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.title,
                      style: TextStyle(
                        fontWeight: FontWeight.w800,
                        fontSize: 13.5,
                        color: enabled
                            ? AetherColors.white
                            : AetherColors.mist.withValues(alpha: 0.5),
                      ),
                    ),
                    if (widget.subtitle != null) ...[
                      const SizedBox(height: 2),
                      Text(
                        widget.subtitle!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: AetherColors.mist.withValues(
                            alpha: enabled ? 0.9 : 0.4,
                          ),
                          fontSize: 11.5,
                          height: 1.25,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              Icon(
                Icons.chevron_right_rounded,
                color: enabled
                    ? accent.withValues(alpha: 0.8)
                    : AetherColors.mist.withValues(alpha: 0.25),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class SkySectionLabel extends StatelessWidget {
  const SkySectionLabel(this.text, {super.key, this.accent});

  final String text;
  final Color? accent;

  @override
  Widget build(BuildContext context) {
    final color = accent ?? AetherColors.gold;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10, top: 4),
      child: Row(
        children: [
          Container(
            width: 3,
            height: 14,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(99),
            ),
          ),
          const SizedBox(width: 8),
          Text(
            text.toUpperCase(),
            style: TextStyle(
              fontFamily: AetherTheme.cinzel,
              fontSize: 12,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.6,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

class SkyStatChip extends StatelessWidget {
  const SkyStatChip({
    super.key,
    required this.label,
    required this.value,
    this.accent = AetherColors.cyan,
  });

  final String label;
  final String value;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: const Color(0xFF12101C),
        border: Border.all(color: AetherColors.glassStroke),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label.toUpperCase(),
            style: TextStyle(
              color: accent.withValues(alpha: 0.95),
              fontSize: 10,
              fontWeight: FontWeight.w800,
              letterSpacing: 1.0,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: const TextStyle(
              fontFamily: AetherTheme.cinzel,
              fontWeight: FontWeight.w700,
              fontSize: 18,
            ),
          ),
        ],
      ),
    );
  }
}
