"""Shared GIGA materials. One palette, so families read as one product."""
from forge import Mat, ramp, hx, mix, INK

# --------------------------------------------------------------------------- metals
STEEL = ramp('#a7b0c4', n=7, at=3, lo=0.22, hi=0.98, shadow_h=285, light_h=210, ls=0.2)
IRON_DARK = ramp('#585468', n=7, at=3, lo=0.17, hi=0.86, shadow_h=290)
IRON = ramp('#8a8798', n=7, at=3, lo=0.20, hi=0.95, shadow_h=290, light_h=230, ls=0.2)
RUST = ramp('#8a5a44', n=7, at=3, lo=0.18, hi=0.86, shadow_h=320, ss=0.35, light_h=60, ls=0.35)
GOLD = ramp('#e2b13c', n=7, at=3, lo=0.24, hi=0.98, shadow_h=35, ss=0.55, light_h=100, ls=0.5)
SILVER = ramp('#c9cfdc', n=7, at=4, lo=0.24, hi=0.99, shadow_h=280, light_h=220)
BRONZE = ramp('#c07c45', n=7, at=3, lo=0.22, hi=0.94, shadow_h=20, ss=0.5, light_h=80, ls=0.45)
BRASS = ramp('#c9a24a', n=7, at=3, lo=0.22, hi=0.96, shadow_h=30, ss=0.5, light_h=95, ls=0.45)
COPPER = ramp('#d0784a', n=7, at=3, lo=0.22, hi=0.95, shadow_h=15, ss=0.5, light_h=70, ls=0.4)
SUNSTEEL = ramp('#f0cf7a', n=7, at=4, lo=0.30, hi=0.995, shadow_h=40, ss=0.6, light_h=100, ls=0.6, c_hi=0.3)

# --------------------------------------------------------------------------- organics
BONE = ramp('#ddd3c0', n=7, at=4, lo=0.26, hi=0.99, shadow_h=300, ss=0.45, light_h=95, ls=0.3)
ENAMEL = ramp('#ddd5ea', n=7, at=4, lo=0.24, hi=0.99, shadow_h=295, ss=0.5)
PORCELAIN = ramp('#ece6ee', n=7, at=4, lo=0.30, hi=0.995, shadow_h=300, ss=0.6, c_hi=0.2)
SILK = ramp('#e8e4f2', n=6, at=3, lo=0.40, hi=0.995, shadow_h=290)
WOOD = ramp('#8a5a36', n=6, at=3, lo=0.20, hi=0.80, shadow_h=20, ss=0.45, light_h=70, ls=0.35)
WOOD_DARK = ramp('#5a3a2a', n=6, at=2, lo=0.15, hi=0.70, shadow_h=320, ss=0.4, light_h=60)
LEATHER_DARK = ramp('#4a2f3a', n=6, at=2, lo=0.16, hi=0.72, shadow_h=300)
LEATHER = ramp('#7a4a32', n=6, at=3, lo=0.18, hi=0.78, shadow_h=10, ss=0.45, light_h=70)
CLOTH_RED = ramp('#a8323a', n=6, at=3, lo=0.18, hi=0.85, shadow_h=320, light_h=30)
CHITIN = ramp('#3a2a44', n=6, at=2, lo=0.12, hi=0.70, shadow_h=290, light_h=300)
ROT = ramp('#7d8a3a', n=7, at=3, lo=0.18, hi=0.92, shadow_h=60, ss=0.4, light_h=110)
FLESH = ramp('#a0565a', n=6, at=3, lo=0.20, hi=0.85, shadow_h=330, light_h=40)

# --------------------------------------------------------------------------- arcane
VOID = ramp('#2c2142', n=7, at=2, lo=0.12, hi=0.80, shadow_h=290, light_h=300, c_hi=0.85)
LILAC = ramp('#9d86cf', n=7, at=3, lo=0.22, hi=0.97, shadow_h=290, light_h=310)
AMETHYST = ramp('#9b59e0', n=7, at=3, lo=0.18, hi=0.97, shadow_h=285, light_h=320, c_hi=0.55)
RIFT = ramp('#b04dff', n=6, at=2, lo=0.30, hi=0.99, shadow_h=290, light_h=320, c_hi=0.35)
AETHER = ramp('#5cc2d6', n=7, at=3, lo=0.20, hi=0.99, shadow_h=265, light_h=180)
AETHER_GLASS = ramp('#8fd9ec', n=7, at=4, lo=0.30, hi=0.995, shadow_h=275, ss=0.5, light_h=190, c_hi=0.3)
ICE = ramp('#7fd3f0', n=7, at=3, lo=0.22, hi=0.99, shadow_h=265, light_h=190)
EMBER = ramp('#f08a2c', n=7, at=3, lo=0.24, hi=0.99, shadow_h=20, ss=0.6, light_h=95, ls=0.6)
MAGMA = ramp('#ff6a1f', n=6, at=2, lo=0.40, hi=0.995, shadow_h=20, light_h=95, ls=0.7, c_hi=0.3)
CRIMSON = ramp('#c8323e', n=7, at=3, lo=0.18, hi=0.95, shadow_h=330, light_h=40, ls=0.4)
JADE = ramp('#4fbf7f', n=7, at=3, lo=0.20, hi=0.97, shadow_h=230, light_h=110)
SUN = ramp('#ffcf4a', n=7, at=4, lo=0.30, hi=0.995, shadow_h=40, ss=0.7, light_h=100, ls=0.5, c_hi=0.25)
SAKURA = ramp('#f2a1c6', n=7, at=4, lo=0.26, hi=0.99, shadow_h=330)
ROSE = ramp('#e07aa0', n=6, at=3, lo=0.25, hi=0.97, shadow_h=320)
STORM = ramp('#4f8fe8', n=7, at=3, lo=0.20, hi=0.99, shadow_h=275, light_h=200)
BOLT = ramp('#8fd6ff', n=6, at=3, lo=0.40, hi=0.995, shadow_h=260, light_h=190, c_hi=0.25)
SCULK = ramp('#1f6f78', n=7, at=3, lo=0.14, hi=0.95, shadow_h=250, light_h=190)
POISON = ramp('#7ccf3a', n=7, at=3, lo=0.20, hi=0.97, shadow_h=170, light_h=110)
GLITCH_C = ramp('#38e8f0', n=5, at=2, lo=0.40, hi=0.99, shadow_h=250)
GLITCH_M = ramp('#ff3fb8', n=5, at=2, lo=0.35, hi=0.99, shadow_h=300)

WHITE = (255, 255, 255)


def glint(c, t=0.6):
    return mix(c, WHITE, t)
