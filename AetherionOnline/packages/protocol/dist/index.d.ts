export type Vec3 = {
    x: number;
    y: number;
    z: number;
};
export type ClientMessage = {
    type: "hello";
    name: string;
} | {
    type: "input";
    seq: number;
    forward: number;
    strafe: number;
    yaw: number;
    dt: number;
};
export type ServerMessage = {
    type: "welcome";
    id: string;
    spawn: Vec3;
    landmarks: Array<{
        id: string;
        name: string;
        x: number;
        y: number;
        z: number;
        color: number;
    }>;
    level: number;
    title: string;
} | {
    type: "state";
    tick: number;
    you: {
        id: string;
        x: number;
        y: number;
        z: number;
        yaw: number;
    };
    others: Array<{
        id: string;
        name: string;
        x: number;
        y: number;
        z: number;
        yaw: number;
    }>;
} | {
    type: "chat";
    from: string;
    text: string;
};
export declare const DEFAULT_PORT = 2567;
