/**
 * Hub landmarks — IDs from AetherionHub `HubService.ORIGIN_SPAWN_IDS` + common isles.
 * Positions are prototype layout (not MC block coords).
 */
export type Landmark = {
    id: string;
    name: string;
    blurb: string;
    /** World position in meters. */
    x: number;
    y: number;
    z: number;
    color: number;
    unlockedByDefault?: boolean;
};
export declare const HUB_LANDMARKS: Landmark[];
export declare function landmarkById(id: string): Landmark | undefined;
export declare const SPAWN: Landmark;
