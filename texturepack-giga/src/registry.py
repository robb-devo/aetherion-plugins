"""All GIGA icon registries in one place: texture name -> builder."""
import importlib

MODULES = ['icons_boss', 'icons_dungeon', 'icons_armor', 'icons_charms', 'icons_ladder', 'icons_ladder_armor', 'icons_tools', 'icons_extra']


def all_icons():
    reg = {}
    for m in MODULES:
        reg.update(importlib.import_module(m).REG)
    return reg
