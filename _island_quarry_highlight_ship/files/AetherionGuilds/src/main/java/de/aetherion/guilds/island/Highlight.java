package de.aetherion.guilds.island;

import de.aetherion.guilds.logistics.LogisticsService;
import de.aetherion.guilds.menu.BuildMenu;
import de.aetherion.guilds.menu.GuildProjectMenu;
import de.aetherion.guilds.menu.LandMenu;
import de.aetherion.guilds.menu.StarterSelectMenu;
import de.aetherion.guilds.project.GuildProjectService;
import de.aetherion.guilds.structure.PlacementService;
import de.aetherion.guilds.structure.StructureService;
import de.aetherion.guilds.template.PasteService;
import de.aetherion.guilds.template.TemplateLibrary;

/** The island-highlight services and menus in one handle, so commands take a single extra setter. */
public record Highlight(
        TemplateLibrary templates,
        PasteService paste,
        HostService hosts,
        LandService land,
        UnlockService unlock,
        StructureService structures,
        LogisticsService logistics,
        PlacementService placement,
        GuildProjectService projects,
        StarterSelectMenu starterMenu,
        BuildMenu buildMenu,
        LandMenu landMenu,
        GuildProjectMenu projectMenu
) {
}
