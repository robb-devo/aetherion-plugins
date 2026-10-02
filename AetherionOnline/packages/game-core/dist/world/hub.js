/**
 * Hub landmarks — IDs from AetherionHub `HubService.ORIGIN_SPAWN_IDS` + common isles.
 * Positions are prototype layout (not MC block coords).
 */
export const HUB_LANDMARKS = [
    {
        id: "harbour",
        name: "Harbour",
        blurb: "Anker Harbour — market and the docks.",
        x: 0,
        y: 0,
        z: 0,
        color: 0x4a90d9,
        unlockedByDefault: true,
    },
    {
        id: "ore_ridge",
        name: "Ore Ridge",
        blurb: "Coal hill near the harbour.",
        x: 42,
        y: 2,
        z: -18,
        color: 0x6b6b6b,
    },
    {
        id: "mines",
        name: "Mines",
        blurb: "Deep shafts and ore loops.",
        x: 70,
        y: 1,
        z: -40,
        color: 0x8b7355,
    },
    {
        id: "capital",
        name: "Capital",
        blurb: "City square and traders.",
        x: -35,
        y: 0,
        z: 20,
        color: 0xc9a227,
    },
    {
        id: "forage_isle",
        name: "Forage Isle",
        blurb: "Wooded chop loops.",
        x: -55,
        y: 1,
        z: -45,
        color: 0x3d8b4a,
    },
    {
        id: "farm",
        name: "Farm",
        blurb: "Crop fields and regen plots.",
        x: 30,
        y: 0,
        z: 55,
        color: 0xb8a05a,
    },
    {
        id: "fishing",
        name: "Fishing",
        blurb: "Pier and deep water.",
        x: 15,
        y: 0,
        z: -60,
        color: 0x2a6f9c,
    },
    {
        id: "borderlands",
        name: "Borderlands",
        blurb: "Wildlife combat outskirts.",
        x: 90,
        y: 3,
        z: 30,
        color: 0x8b3a3a,
    },
    {
        id: "colosseum",
        name: "Colosseum",
        blurb: "Arena grounds.",
        x: -80,
        y: 2,
        z: 10,
        color: 0xa67c52,
    },
    {
        id: "eldervale",
        name: "Elder Vale",
        blurb: "Ancient paths toward Amethyst.",
        x: -20,
        y: 4,
        z: 80,
        color: 0x7b5ea7,
    },
];
export function landmarkById(id) {
    return HUB_LANDMARKS.find((l) => l.id === id);
}
export const SPAWN = HUB_LANDMARKS[0];
