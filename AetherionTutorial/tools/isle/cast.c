/* Preview ray caster for the Morningbell generator.
 *
 *   cast blocks.u16 sx sy sz table.bin atlas.bin out.rgb W H mode cx cy cz yaw pitch fov sunyaw sunpitch ss
 *
 * blocks.u16: uint16 [y][z][x]; table.bin: one Entry per palette id; atlas.bin: n * 16*16*4 RGBA.
 * mode 0 = perspective (fov in degrees), mode 1 = orthographic (fov = blocks across the image width).
 * Camera angles as in Minecraft: yaw 0 looks south (+z), 90 west (-x); pitch positive looks down.
 * Coordinates are grid-local (the Python wrapper subtracts the world origin).
 *
 * Build: gcc -O2 -fopenmp -o cast cast.c -lm
 */
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define F_CUTOUT 1
#define F_TRANSLUCENT 2
#define F_EMISSIVE 4
#define F_WATER 8
#define F_NOSHADOW 16

typedef struct {
    int32_t flags;
    int32_t nboxes;
    int32_t tex[3]; /* top, side, bottom */
    int32_t axis;   /* 0 y, 1 x, 2 z */
    float ttop[3];
    float tside[3];
    float boxes[8][6];
} Entry;

static uint16_t *G;
static int SX, SY, SZ;
static Entry *T;
static int NT;
static uint8_t *A;
static int NA;
static float SUN[3];
static int MODE;

static inline int cell(int x, int y, int z) {
    if (x < 0 || y < 0 || z < 0 || x >= SX || y >= SY || z >= SZ) return 0;
    return G[((size_t)y * SZ + z) * SX + x];
}

static inline int opaque_full(int id) {
    if (!id || id >= NT) return 0;
    const Entry *e = &T[id];
    if (e->nboxes != 1 || (e->flags & (F_CUTOUT | F_TRANSLUCENT | F_WATER))) return 0;
    const float *b = e->boxes[0];
    return b[0] <= 0.001f && b[1] <= 0.001f && b[2] <= 0.001f && b[3] >= 0.999f && b[4] >= 0.999f && b[5] >= 0.999f;
}

typedef struct {
    int hit;
    float t;
    int id;
    int x, y, z;
    float n[3];
    float p[3];     /* hit point, grid coordinates */
    float water_t;  /* first water surface along the ray, or -1 */
    float water_n1; /* normal y of the water entry */
} Hit;

static int box_hit(const float *b, const float *o, const float *d, float tmin, float *tout, float *nout) {
    float t0 = tmin, t1 = 1e30f;
    int axis = -1, sign = 0;
    for (int i = 0; i < 3; i++) {
        float lo = b[i], hi = b[i + 3];
        if (fabsf(d[i]) < 1e-9f) {
            if (o[i] < lo || o[i] > hi) return 0;
            continue;
        }
        float inv = 1.0f / d[i];
        float ta = (lo - o[i]) * inv, tb = (hi - o[i]) * inv;
        int s = -1;
        if (ta > tb) { float tmp = ta; ta = tb; tb = tmp; s = 1; }
        if (ta > t0) { t0 = ta; axis = i; sign = s; }
        if (tb < t1) t1 = tb;
        if (t0 > t1) return 0;
    }
    if (axis < 0) return 0; /* started inside */
    *tout = t0;
    nout[0] = nout[1] = nout[2] = 0;
    nout[axis] = (float)sign;
    return 1;
}

static void sample(const Entry *e, const float *n, const float *local, float *rgba) {
    int face; /* 0 top, 1 side, 2 bottom */
    float u, v;
    int ay = 1;
    if (e->axis == 1) ay = 0; else if (e->axis == 2) ay = 2;
    float na = n[ay];
    if (na > 0.5f) face = 0; else if (na < -0.5f) face = 2; else face = 1;
    if (fabsf(n[1]) > 0.5f) { u = local[0]; v = local[2]; }
    else if (fabsf(n[0]) > 0.5f) { u = local[2]; v = 1.0f - local[1]; }
    else { u = local[0]; v = 1.0f - local[1]; }
    if (e->axis == 1 && face == 1 && fabsf(n[1]) > 0.5f) { float tmp = u; u = v; v = tmp; }
    int ti = e->tex[face];
    if (ti < 0 || ti >= NA) { rgba[0] = 1; rgba[1] = 0; rgba[2] = 1; rgba[3] = 1; return; }
    int px = (int)(u * 16.0f), py = (int)(v * 16.0f);
    if (px < 0) px = 0; if (px > 15) px = 15;
    if (py < 0) py = 0; if (py > 15) py = 15;
    const uint8_t *t = A + ((size_t)ti * 256 + py * 16 + px) * 4;
    rgba[0] = t[0] / 255.0f; rgba[1] = t[1] / 255.0f; rgba[2] = t[2] / 255.0f; rgba[3] = t[3] / 255.0f;
    const float *tint = face == 1 ? e->tside : e->ttop;
    if (face == 2) tint = e->tside;
    if (tint[0] >= 0) { rgba[0] *= tint[0]; rgba[1] *= tint[1]; rgba[2] *= tint[2]; }
}

/* March a ray. shadow=1: only asks "is the sun blocked", returns transmittance in out->t. */
static void march(const float *o, const float *d, float tmax, Hit *out, int shadow) {
    out->hit = 0; out->water_t = -1; out->water_n1 = 0;
    float tenter = 0, texit = 1e30f;
    float size[3] = {(float)SX, (float)SY, (float)SZ};
    for (int i = 0; i < 3; i++) {
        if (fabsf(d[i]) < 1e-9f) {
            if (o[i] < 0 || o[i] >= size[i]) { out->t = 1; return; }
            continue;
        }
        float inv = 1.0f / d[i];
        float ta = (0 - o[i]) * inv, tb = (size[i] - o[i]) * inv;
        if (ta > tb) { float tmp = ta; ta = tb; tb = tmp; }
        if (ta > tenter) tenter = ta;
        if (tb < texit) texit = tb;
    }
    if (tenter > texit || texit < 0) { out->t = 1; return; }
    if (texit > tmax) texit = tmax;
    float t = tenter > 0 ? tenter + 1e-4f : 0;
    float pos[3] = {o[0] + d[0] * t, o[1] + d[1] * t, o[2] + d[2] * t};
    int c[3] = {(int)floorf(pos[0]), (int)floorf(pos[1]), (int)floorf(pos[2])};
    int step[3];
    float tnext[3], tdelta[3];
    for (int i = 0; i < 3; i++) {
        if (c[i] < 0) c[i] = 0;
        if (c[i] >= (int)size[i]) c[i] = (int)size[i] - 1;
        if (d[i] > 0) { step[i] = 1; tnext[i] = t + (c[i] + 1 - pos[i]) / d[i]; tdelta[i] = 1.0f / d[i]; }
        else if (d[i] < 0) { step[i] = -1; tnext[i] = t + (c[i] - pos[i]) / d[i]; tdelta[i] = -1.0f / d[i]; }
        else { step[i] = 0; tnext[i] = 1e30f; tdelta[i] = 1e30f; }
    }
    int last = -1; /* axis of the face we entered through */
    if (tenter > 0) {
        /* entry face of the grid */
        float best = -1;
        for (int i = 0; i < 3; i++) {
            if (fabsf(d[i]) < 1e-9f) continue;
            float ta = ((d[i] > 0 ? 0 : size[i]) - o[i]) / d[i];
            if (ta > best) { best = ta; last = i; }
        }
    }
    float trans = 1.0f;
    int guard = 0;
    while (t < texit && guard++ < 4096) {
        int id = cell(c[0], c[1], c[2]);
        if (id && id < NT && T[id].nboxes > 0) {
            const Entry *e = &T[id];
            float th = t, n[3] = {0, 0, 0};
            int got = 0;
            int full = e->nboxes == 1 && e->boxes[0][0] <= 0.001f && e->boxes[0][1] <= 0.001f
                    && e->boxes[0][2] <= 0.001f && e->boxes[0][3] >= 0.999f && e->boxes[0][4] >= 0.999f
                    && e->boxes[0][5] >= 0.999f;
            if (full) {
                if (last >= 0) { n[last] = (float)-step[last]; got = 1; th = t; }
            } else {
                float lo[3] = {o[0] - c[0], o[1] - c[1], o[2] - c[2]};
                float best = 1e30f, bn[3];
                for (int b = 0; b < e->nboxes; b++) {
                    float tb, nb[3];
                    if (box_hit(e->boxes[b], lo, d, t - 2e-3f, &tb, nb) && tb < best) {
                        best = tb; bn[0] = nb[0]; bn[1] = nb[1]; bn[2] = nb[2];
                    }
                }
                float cellexit = tnext[0] < tnext[1] ? (tnext[0] < tnext[2] ? tnext[0] : tnext[2])
                                                    : (tnext[1] < tnext[2] ? tnext[1] : tnext[2]);
                if (best < cellexit + 1e-3f) { got = 1; th = best; n[0] = bn[0]; n[1] = bn[1]; n[2] = bn[2]; }
            }
            if (got) {
                float hp[3] = {o[0] + d[0] * th, o[1] + d[1] * th, o[2] + d[2] * th};
                float local[3] = {hp[0] - c[0], hp[1] - c[1], hp[2] - c[2]};
                for (int i = 0; i < 3; i++) { if (local[i] < 0) local[i] = 0; if (local[i] > 0.9999f) local[i] = 0.9999f; }
                if (e->flags & F_WATER) {
                    if (!shadow && out->water_t < 0) { out->water_t = th; out->water_n1 = n[1]; }
                    got = 0; /* keep marching through water */
                } else if (shadow) {
                    if (!(e->flags & F_NOSHADOW)) {
                        if (e->flags & (F_CUTOUT | F_TRANSLUCENT)) {
                            float rgba[4];
                            sample(e, n, local, rgba);
                            trans *= rgba[3] < 0.5f ? 1.0f : 0.55f;
                            if ((e->flags & F_TRANSLUCENT)) trans *= 0.9f;
                        } else {
                            out->t = 0; return;
                        }
                        if (trans < 0.08f) { out->t = 0; return; }
                    }
                    got = 0;
                } else {
                    float rgba[4];
                    sample(e, n, local, rgba);
                    if ((e->flags & F_CUTOUT) && rgba[3] < 0.5f) {
                        got = 0;
                    } else {
                        out->hit = 1; out->t = th; out->id = id;
                        out->x = c[0]; out->y = c[1]; out->z = c[2];
                        out->n[0] = n[0]; out->n[1] = n[1]; out->n[2] = n[2];
                        out->p[0] = hp[0]; out->p[1] = hp[1]; out->p[2] = hp[2];
                        return;
                    }
                }
            }
        }
        int a = tnext[0] < tnext[1] ? (tnext[0] < tnext[2] ? 0 : 2) : (tnext[1] < tnext[2] ? 1 : 2);
        t = tnext[a];
        tnext[a] += tdelta[a];
        c[a] += step[a];
        last = a;
        if (c[a] < 0 || c[a] >= (int)size[a]) break;
    }
    out->t = shadow ? trans : 1e30f;
}

static void sky(const float *d, float *rgb) {
    float h = d[1];
    if (h < 0) h = 0;
    float k = powf(1.0f - h, 3.0f);
    /* early morning: pale gold at the horizon, soft blue above */
    float top[3] = {0.42f, 0.62f, 0.93f}, hor[3] = {0.98f, 0.86f, 0.70f};
    for (int i = 0; i < 3; i++) rgb[i] = top[i] + (hor[i] - top[i]) * k;
    float sd = d[0] * SUN[0] + d[1] * SUN[1] + d[2] * SUN[2];
    if (sd > 0) {
        float glow = powf(sd, 24.0f) * 0.55f + powf(sd, 900.0f) * 2.0f;
        rgb[0] += glow * 1.0f; rgb[1] += glow * 0.85f; rgb[2] += glow * 0.55f;
    }
}

static float ao_at(const Hit *h) {
    /* smooth-lighting style occlusion from the 8 cells around the face, one block out along the normal */
    int ax = fabsf(h->n[0]) > 0.5f ? 0 : (fabsf(h->n[1]) > 0.5f ? 1 : 2);
    int u = (ax + 1) % 3, v = (ax + 2) % 3;
    int base[3] = {h->x, h->y, h->z};
    base[ax] += h->n[ax] > 0 ? 1 : -1;
    float fu = h->p[u] - floorf(h->p[u]), fv = h->p[v] - floorf(h->p[v]);
    float occ[2][2];
    for (int iu = 0; iu < 2; iu++) for (int iv = 0; iv < 2; iv++) {
        int su = iu ? 1 : -1, sv = iv ? 1 : -1;
        int c1[3] = {base[0], base[1], base[2]}, c2[3] = {base[0], base[1], base[2]}, c3[3] = {base[0], base[1], base[2]};
        c1[u] += su; c2[v] += sv; c3[u] += su; c3[v] += sv;
        int s1 = opaque_full(cell(c1[0], c1[1], c1[2])), s2 = opaque_full(cell(c2[0], c2[1], c2[2]));
        int s3 = opaque_full(cell(c3[0], c3[1], c3[2]));
        int n = (s1 && s2) ? 3 : s1 + s2 + s3;
        occ[iu][iv] = 1.0f - 0.2f * n;
    }
    float a0 = occ[0][0] + (occ[1][0] - occ[0][0]) * fu, a1 = occ[0][1] + (occ[1][1] - occ[0][1]) * fu;
    return a0 + (a1 - a0) * fv;
}

static void shade(const float *o, const float *d, float *rgb) {
    Hit h;
    march(o, d, 1e30f, &h, 0);
    float skyc[3];
    sky(d, skyc);
    if (!h.hit && h.water_t < 0) { rgb[0] = skyc[0]; rgb[1] = skyc[1]; rgb[2] = skyc[2]; return; }
    float col[3] = {skyc[0], skyc[1], skyc[2]};
    float dist = 1e30f;
    if (h.hit) {
        const Entry *e = &T[h.id];
        float local[3] = {h.p[0] - h.x, h.p[1] - h.y, h.p[2] - h.z};
        for (int i = 0; i < 3; i++) { if (local[i] < 0) local[i] = 0; if (local[i] > 0.9999f) local[i] = 0.9999f; }
        float rgba[4];
        sample(e, h.n, local, rgba);
        float face = h.n[1] > 0.5f ? 1.0f : (h.n[1] < -0.5f ? 0.5f : (fabsf(h.n[2]) > 0.5f ? 0.8f : 0.66f));
        float light = 1.0f;
        if (!(e->flags & F_EMISSIVE)) {
            float ndl = h.n[0] * SUN[0] + h.n[1] * SUN[1] + h.n[2] * SUN[2];
            float sun = 0;
            if (ndl > 0 || (e->flags & F_CUTOUT)) {
                float so[3] = {h.p[0] + h.n[0] * 0.02f + SUN[0] * 0.01f, h.p[1] + h.n[1] * 0.02f + SUN[1] * 0.01f,
                               h.p[2] + h.n[2] * 0.02f + SUN[2] * 0.01f};
                Hit s;
                march(so, SUN, 160.0f, &s, 1);
                sun = s.t * ((e->flags & F_CUTOUT) ? fmaxf(0.55f, ndl) : ndl);
            }
            float ao = (e->nboxes == 1 && !(e->flags & F_CUTOUT)) ? ao_at(&h) : 0.92f;
            float amb = 0.50f * face * ao;
            /* warm low sun, cool ambient */
            col[0] = rgba[0] * (amb * 0.92f + sun * 0.80f);
            col[1] = rgba[1] * (amb * 0.97f + sun * 0.68f);
            col[2] = rgba[2] * (amb * 1.10f + sun * 0.50f);
        } else {
            col[0] = rgba[0] * 1.2f; col[1] = rgba[1] * 1.1f; col[2] = rgba[2] * 0.95f;
        }
        (void)light;
        dist = h.t;
    }
    if (h.water_t >= 0) {
        float depth = (h.hit ? h.t : h.water_t + 40.0f) - h.water_t;
        if (depth < 0) depth = 0;
        float absorb = 1.0f - expf(-depth * 0.22f);
        float shallow[3] = {0.36f, 0.78f, 0.86f}, deep[3] = {0.10f, 0.36f, 0.62f};
        float wc[3];
        for (int i = 0; i < 3; i++) wc[i] = shallow[i] + (deep[i] - shallow[i]) * fminf(1.0f, depth / 14.0f);
        for (int i = 0; i < 3; i++) col[i] = col[i] * (1 - absorb) * (0.75f + 0.25f * wc[i]) + wc[i] * absorb;
        if (h.water_n1 > 0.5f) {
            float fres = powf(1.0f - fabsf(d[1]), 4.0f) * 0.75f;
            float rd[3] = {d[0], -d[1], d[2]}, sk[3];
            sky(rd, sk);
            for (int i = 0; i < 3; i++) col[i] = col[i] * (1 - fres) + sk[i] * fres;
        }
        dist = h.water_t;
    }
    /* distance haze */
    float fog = 1.0f - expf(-dist * 0.0038f);
    if (fog > 0.85f) fog = 0.85f;
    if (MODE == 1) fog = 0;
    float hz[3];
    float hd[3] = {d[0], 0.04f, d[2]};
    float len = sqrtf(hd[0] * hd[0] + hd[1] * hd[1] + hd[2] * hd[2]);
    hd[0] /= len; hd[1] /= len; hd[2] /= len;
    sky(hd, hz);
    for (int i = 0; i < 3; i++) rgb[i] = col[i] * (1 - fog) + hz[i] * fog;
}

static void *slurp(const char *path, size_t *n) {
    FILE *f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "cannot open %s\n", path); exit(2); }
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    void *buf = malloc(len > 0 ? len : 1);
    if (fread(buf, 1, len, f) != (size_t)len) { fprintf(stderr, "short read %s\n", path); exit(2); }
    fclose(f);
    *n = (size_t)len;
    return buf;
}

int main(int argc, char **argv) {
    if (argc < 20) { fprintf(stderr, "usage: see header\n"); return 1; }
    size_t n;
    SX = atoi(argv[2]); SY = atoi(argv[3]); SZ = atoi(argv[4]);
    G = slurp(argv[1], &n);
    if (n != (size_t)SX * SY * SZ * 2) { fprintf(stderr, "grid size mismatch\n"); return 2; }
    T = slurp(argv[5], &n); NT = (int)(n / sizeof(Entry));
    A = slurp(argv[6], &n); NA = (int)(n / 1024);
    int W = atoi(argv[8]), H = atoi(argv[9]), mode = atoi(argv[10]);
    MODE = mode;
    float cam[3] = {(float)atof(argv[11]), (float)atof(argv[12]), (float)atof(argv[13])};
    float yaw = (float)atof(argv[14]) * (float)M_PI / 180.0f, pitch = (float)atof(argv[15]) * (float)M_PI / 180.0f;
    float fov = (float)atof(argv[16]);
    float syaw = (float)atof(argv[17]) * (float)M_PI / 180.0f, spitch = (float)atof(argv[18]) * (float)M_PI / 180.0f;
    int ss = atoi(argv[19]);
    if (ss < 1) ss = 1;
    /* sun direction: where the light comes FROM is -forward of (syaw, spitch) looking at the sun */
    SUN[0] = -sinf(syaw) * cosf(spitch); SUN[1] = -sinf(spitch); SUN[2] = cosf(syaw) * cosf(spitch);
    float fwd[3] = {-sinf(yaw) * cosf(pitch), -sinf(pitch), cosf(yaw) * cosf(pitch)};
    float right[3] = {-cosf(yaw), 0, -sinf(yaw)};
    float up[3] = {right[1] * fwd[2] - right[2] * fwd[1], right[2] * fwd[0] - right[0] * fwd[2],
                   right[0] * fwd[1] - right[1] * fwd[0]};
    /* make up point upwards */
    if (up[1] < 0) { up[0] = -up[0]; up[1] = -up[1]; up[2] = -up[2]; }
    uint8_t *img = malloc((size_t)W * H * 3);
    float aspect = (float)W / (float)H;
    float tanh_ = tanf(fov * (float)M_PI / 360.0f);
    #pragma omp parallel for schedule(dynamic, 4)
    for (int py = 0; py < H; py++) {
        for (int px = 0; px < W; px++) {
            float acc[3] = {0, 0, 0};
            for (int sy = 0; sy < ss; sy++) for (int sx = 0; sx < ss; sx++) {
                float u = ((px + (sx + 0.5f) / ss) / W * 2 - 1);
                float v = (1 - (py + (sy + 0.5f) / ss) / H * 2);
                float o[3], d[3];
                if (mode == 0) {
                    float ax = u * tanh_ * aspect, ay = v * tanh_;
                    for (int i = 0; i < 3; i++) { d[i] = fwd[i] + right[i] * ax + up[i] * ay; o[i] = cam[i]; }
                    float len = sqrtf(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                    d[0] /= len; d[1] /= len; d[2] /= len;
                } else {
                    float half = fov * 0.5f;
                    for (int i = 0; i < 3; i++) {
                        d[i] = fwd[i];
                        o[i] = cam[i] + right[i] * u * half + up[i] * v * half / aspect - fwd[i] * 600.0f;
                    }
                }
                float rgb[3];
                shade(o, d, rgb);
                acc[0] += rgb[0]; acc[1] += rgb[1]; acc[2] += rgb[2];
            }
            for (int i = 0; i < 3; i++) {
                float c = acc[i] / (ss * ss);
                c = c / (1.0f + 0.18f * c) * 1.18f; /* gentle shoulder */
                if (c < 0) c = 0; if (c > 1) c = 1;
                img[((size_t)py * W + px) * 3 + i] = (uint8_t)(c * 255.0f + 0.5f);
            }
        }
    }
    FILE *f = fopen(argv[7], "wb");
    fwrite(img, 1, (size_t)W * H * 3, f);
    fclose(f);
    return 0;
}
