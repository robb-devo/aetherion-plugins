import { CLOUD_FS, CLOUD_VS, GLOW_FS, GLOW_VS, SOLID_FS, SOLID_VS } from './shaders.js'

const norm = (v) => { const l = Math.hypot(v[0], v[1], v[2]) || 1; return [v[0] / l, v[1] / l, v[2] / l] }
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

/** Perspective with a vertical lens shift (fractions of the view): the subject can sit above the text. */
function perspective(out, fovY, aspect, near, far, shiftY = 0, shiftX = 0) {
  const t = 1 / Math.tan(fovY / 2)
  out.fill(0)
  out[0] = t / aspect
  out[5] = t
  out[8] = shiftX * 2
  out[9] = shiftY * 2
  out[10] = (far + near) / (near - far)
  out[11] = -1
  out[14] = (2 * far * near) / (near - far)
  return out
}

function lookAt(out, eye, at) {
  const z = norm([eye[0] - at[0], eye[1] - at[1], eye[2] - at[2]])
  const x = norm(cross([0, 1, 0], z))
  const y = cross(z, x)
  out[0] = x[0]; out[1] = y[0]; out[2] = z[0]; out[3] = 0
  out[4] = x[1]; out[5] = y[1]; out[6] = z[1]; out[7] = 0
  out[8] = x[2]; out[9] = y[2]; out[10] = z[2]; out[11] = 0
  out[12] = -dot(x, eye); out[13] = -dot(y, eye); out[14] = -dot(z, eye); out[15] = 1
  return out
}

function mul(out, a, b) {
  for (let i = 0; i < 4; i += 1) {
    const b0 = b[i * 4], b1 = b[i * 4 + 1], b2 = b[i * 4 + 2], b3 = b[i * 4 + 3]
    out[i * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
    out[i * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
    out[i * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
    out[i * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
  }
  return out
}

function program(gl, vs, fs) {
  const p = gl.createProgram()
  for (const [type, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
    const s = gl.createShader(type)
    gl.shaderSource(s, src)
    gl.compileShader(s)
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s))
    gl.attachShader(p, s)
  }
  gl.linkProgram(p)
  if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p))
  const uniforms = {}
  const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS)
  for (let i = 0; i < n; i += 1) {
    const info = gl.getActiveUniform(p, i)
    uniforms[info.name] = gl.getUniformLocation(p, info.name)
  }
  return { p, u: uniforms }
}

/** 4x4 tiles of 16 px grain, one per material family. Original, procedural, no game textures. */
function grainTiles() {
  const N = 64
  const d = new Uint8Array(N * N)
  let seed = 12345
  const R = () => ((seed = (seed * 16807) % 2147483647) / 2147483647)
  const tiles = [
    () => 0.5 + (R() - 0.5) * 0.3,
    () => 0.5 + (R() - 0.5) * 0.9,
    (x, y) => 0.55 + (y % 4 === 3 ? -0.5 : 0) + ((x + ((y >> 2) % 2) * 7) % 16 === 0 ? -0.3 : 0) + (R() - 0.5) * 0.3,
    (x, y) => { const r = y >> 2; return y % 4 === 3 || (x + (r % 2) * 4) % 8 === 7 ? 0.05 : 0.6 + (R() - 0.5) * 0.35 },
    (x, y) => { const r = y >> 3; return y % 8 === 7 || (x + (r % 2) * 8) % 16 === 15 ? 0.1 : 0.62 + (R() - 0.5) * 0.3 },
    (x) => 0.5 + (x % 5 === 0 ? -0.4 : 0) + (R() - 0.5) * 0.4,
    () => (R() < 0.3 ? 0.03 : 0.5 + R() * 0.5),
    () => 0.5 + (R() - 0.5) * 0.8 + (R() < 0.1 ? 0.3 : 0),
    (x, y) => 0.55 + ((x + y) % 8 < 2 ? 0.22 : 0) + (R() - 0.5) * 0.16,
    (x, y) => (x % 16 === 0 || y % 16 === 0 ? 0.05 : 0.6),
    (x, y) => { const a = ((x >> 2) + ((y >> 2) % 2) * 2) % 4; const b = (y >> 2) % 4; return (x % 4 === 3 && a === 0) || (y % 4 === 3 && b % 2 === 0) ? 0.05 : 0.6 + (R() - 0.5) * 0.45 },
  ]
  for (let t = 0; t < 16; t += 1) {
    const f = tiles[t] || tiles[0]
    const tx = t % 4
    const ty = t >> 2
    for (let y = 0; y < 16; y += 1) for (let x = 0; x < 16; x += 1) d[(ty * 16 + y) * N + tx * 16 + x] = Math.max(0, Math.min(255, f(x, y) * 255))
  }
  return { d, N }
}

/** Tileable value-noise fbm: R big soft shapes, G finer. */
function fogNoise(size = 256) {
  const data = new Uint8Array(size * size * 4)
  const lattice = (n, seed) => {
    const a = new Float32Array(n * n)
    let s = seed
    for (let i = 0; i < a.length; i += 1) { s = (s * 16807) % 2147483647; a[i] = s / 2147483647 }
    return a
  }
  const fbm = (x, y, base, octs, seed) => {
    let v = 0, amp = 0.5, tot = 0, n = base
    for (let o = 0; o < octs; o += 1) {
      const L = fbm.cache[`${n}:${seed + o}`] || (fbm.cache[`${n}:${seed + o}`] = lattice(n, seed + o * 977 + 13))
      const fx = (x / size) * n, fy = (y / size) * n
      const ix = Math.floor(fx), iy = Math.floor(fy)
      const tx = fx - ix, ty = fy - iy
      const sx = tx * tx * (3 - 2 * tx), sy = ty * ty * (3 - 2 * ty)
      const i0 = ix % n, i1 = (ix + 1) % n, j0 = iy % n, j1 = (iy + 1) % n
      const v0 = L[j0 * n + i0] + (L[j0 * n + i1] - L[j0 * n + i0]) * sx
      const v1 = L[j1 * n + i0] + (L[j1 * n + i1] - L[j1 * n + i0]) * sx
      v += (v0 + (v1 - v0) * sy) * amp
      tot += amp; amp *= 0.5; n *= 2
    }
    return v / tot
  }
  fbm.cache = {}
  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      const a = fbm(x, y, 4, 5, 4241)
      const b = fbm(x, y, 8, 4, 91801)
      const i = (y * size + x) * 4
      data[i] = Math.max(0, Math.min(255, (a - 0.5) * 2.3 * 127 + 127))
      data[i + 1] = Math.max(0, Math.min(255, (b - 0.5) * 2.3 * 127 + 127))
      data[i + 2] = 0
      data[i + 3] = 255
    }
  }
  return data
}

const UAX = [0, 2, 1, 0, 2, 1]
const VAX = [2, 0, 0, 1, 1, 2]

/** Fetches a gzipped AEM2 mesh and unpacks it into GPU-ready arrays, off the main thread when it can. */
export async function loadMesh(url, onProgress) {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`mesh ${res.status}`)
  const total = Number(res.headers.get('content-length')) || 0
  let bytes
  if (res.body && onProgress) {
    const reader = res.body.getReader()
    const chunks = []
    let got = 0
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      got += value.length
      onProgress(total ? Math.min(1, got / total) : 0)
    }
    bytes = new Uint8Array(got)
    let o = 0
    for (const c of chunks) { bytes.set(c, o); o += c.length }
  } else {
    bytes = new Uint8Array(await res.arrayBuffer())
  }
  return unpack(bytes)
}

let unpacker = null
let unpackFailed = false
function unpack(bytes) {
  if (!unpackFailed && typeof Worker !== 'undefined') {
    try {
      unpacker = unpacker || new Worker(new URL('./mesh.worker.js', import.meta.url), { type: 'module' })
      return new Promise((resolve, reject) => {
        const id = Math.random()
        const done = (fn, value) => { unpacker.removeEventListener('message', onMsg); unpacker.removeEventListener('error', onErr); fn(value) }
        const onMsg = (e) => { if (e.data.id !== id) return; if (e.data.mesh) done(resolve, e.data.mesh); else done(reject, new Error(e.data.error)) }
        const onErr = () => { unpackFailed = true; done(resolve, unpackHere(bytes)) }
        unpacker.addEventListener('message', onMsg)
        unpacker.addEventListener('error', onErr)
        unpacker.postMessage({ id, bytes })
      })
    } catch {
      unpackFailed = true
    }
  }
  return unpackHere(bytes)
}

/** The server may already have undone the gzip; the first two bytes tell. */
export async function unpackHere(bytes) {
  const zipped = bytes[0] === 0x1f && bytes[1] === 0x8b
  const buf = zipped
    ? await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer()
    : bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)
  return parseMesh(buf)
}

export function parseMesh(buf) {
  const dv = new DataView(buf)
  if (dv.getUint32(0, true) !== 0x324d4541) throw new Error('bad mesh')
  const nq = dv.getUint32(4, true), nr = dv.getUint32(8, true), nc = dv.getUint32(12, true), nl = dv.getUint32(16, true)
  const size = [dv.getFloat32(20, true), dv.getFloat32(24, true), dv.getFloat32(28, true)]
  let o = 32
  const regions = []
  for (let i = 0; i < nr; i += 1) {
    regions.push({ start: dv.getUint32(o, true), solid: dv.getUint32(o + 4, true), plants: dv.getUint32(o + 8, true), c: [dv.getFloat32(o + 12, true), dv.getFloat32(o + 16, true), dv.getFloat32(o + 20, true)], r: dv.getFloat32(o + 24, true) })
    o += 28
  }
  const pw = 256
  const ph = (Math.floor((nc - 1) / 256) + 1) * 2
  const pal = new Uint8Array(pw * ph * 4)
  for (let i = 0; i < nc; i += 1) {
    const x = i & 255, y = (i >> 8) * 2
    const a = (y * pw + x) * 4
    pal[a] = dv.getUint8(o); pal[a + 1] = dv.getUint8(o + 1); pal[a + 2] = dv.getUint8(o + 2); pal[a + 3] = 255
    const b = ((y + 1) * pw + x) * 4
    pal[b] = dv.getUint8(o + 3); pal[b + 1] = dv.getUint8(o + 4)
    o += 8
  }
  const lights = new Float32Array(nl * 4)
  for (let i = 0; i < nl; i += 1) {
    lights[i * 4] = dv.getInt16(o, true) + 0.5; lights[i * 4 + 1] = dv.getInt16(o + 2, true) + 0.5; lights[i * 4 + 2] = dv.getInt16(o + 4, true) + 0.5; lights[i * 4 + 3] = dv.getUint8(o + 6)
    o += 8
  }
  const b = new Uint8Array(buf)
  const i16 = (ofs, i) => { const v = b[ofs + i * 2] | (b[ofs + i * 2 + 1] << 8); return v > 32767 ? v - 65536 : v }
  const u16 = (ofs, i) => b[ofs + i * 2] | (b[ofs + i * 2 + 1] << 8)
  const X = o, Y = X + nq * 2, Z = Y + nq * 2, WF = Z + nq * 2, HH = WF + nq * 2, C = HH + nq * 2
  const S01 = C + nq * 2, S23 = S01 + nq, B01 = S23 + nq, B23 = B01 + nq, AO = B23 + nq, AB = AO + nq, C3 = AB + nq
  const verts = new ArrayBuffer(nq * 4 * 12)
  const vi = new Int16Array(verts)
  const vu = new Uint8Array(verts)
  const idx = new Uint32Array(nq * 6)
  const P = [0, 0, 0]
  const aos = [0, 0, 0, 0]
  const sk = [0, 0, 0, 0]
  const bl = [0, 0, 0, 0]
  for (let i = 0; i < nq; i += 1) {
    const x = i16(X, i), y = i16(Y, i), z = i16(Z, i), wf = u16(WF, i), h = u16(HH, i), col = u16(C, i)
    const ao = b[AO + i], ab = b[AB + i], c3 = b[C3 + i]
    const s01 = b[S01 + i], s23 = b[S23 + i], b01 = b[B01 + i], b23 = b[B23 + i]
    sk[0] = s01 >> 4; sk[1] = s01 & 15; sk[2] = s23 >> 4; sk[3] = s23 & 15
    bl[0] = b01 >> 4; bl[1] = b01 & 15; bl[2] = b23 >> 4; bl[3] = b23 & 15
    aos[0] = ao & 3; aos[1] = (ao >> 2) & 3; aos[2] = (ao >> 4) & 3; aos[3] = (ao >> 6) & 3
    const face = wf >> 13, w = wf & 0x1fff
    const base = i * 4
    for (let c = 0; c < 4; c += 1) {
      const u = c === 1 || c === 2 ? w : 0
      const v = c >= 2 ? h : 0
      let px, py, pz
      if (face < 6) {
        P[0] = x; P[1] = y; P[2] = z; P[UAX[face]] += u; P[VAX[face]] += v
        px = P[0]; py = P[1]; pz = P[2]
      } else {
        px = x + u; py = y + v; pz = face === 6 ? z + u : z - u
      }
      const m = (base + c) * 6
      vi[m] = px; vi[m + 1] = py; vi[m + 2] = pz; vi[m + 3] = col
      const n = (base + c) * 12 + 8
      vu[n] = face | (aos[c] << 3); vu[n + 1] = (sk[c] << 4) | bl[c]; vu[n + 2] = ab; vu[n + 3] = c3
    }
    // flip the quad along the darker diagonal so light doesn't crease
    const shade0 = aos[0] + aos[2] + (sk[0] + sk[2]) * 0.2
    const shade1 = aos[1] + aos[3] + (sk[1] + sk[3]) * 0.2
    const t = i * 6
    if (shade0 < shade1) {
      idx[t] = base + 1; idx[t + 1] = base + 2; idx[t + 2] = base + 3; idx[t + 3] = base + 1; idx[t + 4] = base + 3; idx[t + 5] = base
    } else {
      idx[t] = base; idx[t + 1] = base + 1; idx[t + 2] = base + 2; idx[t + 3] = base; idx[t + 4] = base + 2; idx[t + 5] = base + 3
    }
  }
  return { nq, regions, size, pal, pw, ph, lights, verts, idx }
}

export function createWorld(canvas, { antialias = true, preserve = false } = {}) {
  const gl = canvas.getContext('webgl2', { alpha: true, premultipliedAlpha: true, antialias, powerPreference: 'high-performance', preserveDrawingBuffer: preserve })
  if (!gl) return null
  const solid = program(gl, SOLID_VS, SOLID_FS)
  const cloud = program(gl, CLOUD_VS, CLOUD_FS)
  const glow = program(gl, GLOW_VS, GLOW_FS)

  const noiseTex = gl.createTexture()
  gl.activeTexture(gl.TEXTURE2)
  gl.bindTexture(gl.TEXTURE_2D, noiseTex)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, 256, 256, 0, gl.RGBA, gl.UNSIGNED_BYTE, fogNoise(256))
  gl.generateMipmap(gl.TEXTURE_2D)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.REPEAT)

  const { d: grain, N } = grainTiles()
  const grainTex = gl.createTexture()
  gl.activeTexture(gl.TEXTURE1)
  gl.bindTexture(gl.TEXTURE_2D, grainTex)
  gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.R8, N, N, 0, gl.RED, gl.UNSIGNED_BYTE, grain)
  gl.pixelStorei(gl.UNPACK_ALIGNMENT, 4)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.REPEAT)

  const quad = gl.createVertexArray()
  gl.bindVertexArray(quad)
  const qb = gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, qb)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW)
  gl.enableVertexAttribArray(0)
  gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0)
  gl.bindVertexArray(null)

  gl.enable(gl.DEPTH_TEST)
  gl.enable(gl.BLEND)
  gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA)
  gl.clearColor(0, 0, 0, 0)

  const meshes = []
  const P = new Float32Array(16), V = new Float32Array(16), VP = new Float32Array(16)
  const frustum = new Float32Array(24)
  const stats = { tris: 0, draws: 0, regions: 0 }

  function addMesh(mesh, origin = [0, 0, 0], { name = '', intro = 1 } = {}) {
    const vao = gl.createVertexArray()
    gl.bindVertexArray(vao)
    const vb = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, vb)
    gl.bufferData(gl.ARRAY_BUFFER, mesh.verts, gl.STATIC_DRAW)
    gl.enableVertexAttribArray(0)
    gl.vertexAttribIPointer(0, 3, gl.SHORT, 12, 0)
    gl.enableVertexAttribArray(1)
    gl.vertexAttribIPointer(1, 1, gl.UNSIGNED_SHORT, 12, 6)
    gl.enableVertexAttribArray(2)
    gl.vertexAttribIPointer(2, 4, gl.UNSIGNED_BYTE, 12, 8)
    const ib = gl.createBuffer()
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ib)
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, mesh.idx, gl.STATIC_DRAW)
    gl.bindVertexArray(null)

    const pal = gl.createTexture()
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_2D, pal)
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, mesh.pw, mesh.ph, 0, gl.RGBA, gl.UNSIGNED_BYTE, mesh.pal)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST)

    let lvao = null
    if (mesh.lights.length) {
      lvao = gl.createVertexArray()
      gl.bindVertexArray(lvao)
      const lb = gl.createBuffer()
      gl.bindBuffer(gl.ARRAY_BUFFER, lb)
      gl.bufferData(gl.ARRAY_BUFFER, mesh.lights, gl.STATIC_DRAW)
      gl.enableVertexAttribArray(0)
      gl.vertexAttribPointer(0, 4, gl.FLOAT, false, 0, 0)
      gl.bindVertexArray(null)
    }
    const m = { name, vao, lvao, pal, regions: mesh.regions, origin, intro, nl: mesh.lights.length / 4, size: mesh.size, visible: true, cull: null }
    meshes.push(m)
    // free the CPU copies
    mesh.verts = null; mesh.idx = null; mesh.pal = null
    return m
  }

  function setFrustum() {
    const m = VP
    const planes = [
      [m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]],
      [m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]],
      [m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]],
      [m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]],
      [m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]],
      [m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]],
    ]
    planes.forEach((p, i) => {
      const l = Math.hypot(p[0], p[1], p[2])
      frustum[i * 4] = p[0] / l; frustum[i * 4 + 1] = p[1] / l; frustum[i * 4 + 2] = p[2] / l; frustum[i * 4 + 3] = p[3] / l
    })
  }

  function visible(c, r) {
    for (let i = 0; i < 6; i += 1) {
      if (frustum[i * 4] * c[0] + frustum[i * 4 + 1] * c[1] + frustum[i * 4 + 2] * c[2] + frustum[i * 4 + 3] < -r) return false
    }
    return true
  }

  /** view: { eye, at, fov, shiftY } · day: dayAt(t) · time: seconds */
  function render(view, day, time, { intro = 1, fogBoost = 0, haze = [1, 0.9, 0.8], clouds = true } = {}) {
    const w = canvas.width, h = canvas.height
    gl.viewport(0, 0, w, h)
    gl.depthMask(true)
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
    perspective(P, (view.fov * Math.PI) / 180, w / h, 2.5, 7000, view.shiftY || 0, view.shiftX || 0)
    lookAt(V, view.eye, view.at)
    mul(VP, P, V)
    setFrustum()
    const eye = view.eye
    const sunDir = norm(day.sunPos)
    stats.tris = 0; stats.draws = 0; stats.regions = 0

    // solid world
    gl.useProgram(solid.p)
    const u = solid.u
    gl.uniformMatrix4fv(u.uVP, false, VP)
    gl.uniform3fv(u.uCam, eye)
    gl.uniform3fv(u.uSunW, day.sunW)
    gl.uniform3fv(u.uSunCol, day.sunCol)
    gl.uniform3fv(u.uAmbUp, day.ambUp)
    gl.uniform3fv(u.uAmbDn, day.ambDn)
    gl.uniform3fv(u.uBlock, day.block)
    gl.uniform3fv(u.uSunDir, sunDir)
    gl.uniform3fv(u.uSunDirV, sunDir)
    gl.uniform3fv(u.uHaze, haze)
    gl.uniform1f(u.uEmis, day.emis)
    gl.uniform1f(u.uFog, day.fog)
    gl.uniform1f(u.uFogBoost, fogBoost)
    gl.uniform1f(u.uMist, day.mist)
    gl.uniform1f(u.uMistY, 92)
    gl.uniform1f(u.uTime, time)
    gl.uniform1f(u.uExposure, day.exposure)
    gl.uniform1i(u.uPal, 0)
    gl.uniform1i(u.uPat, 1)
    gl.uniform1i(u.uNoise, 2)
    const list = []
    for (const m of meshes) {
      if (!m.visible) continue
      gl.bindVertexArray(m.vao)
      gl.activeTexture(gl.TEXTURE0)
      gl.bindTexture(gl.TEXTURE_2D, m.pal)
      gl.uniform3fv(u.uOrigin, m.origin)
      gl.uniform3f(u.uCenter, m.origin[0] + m.size[0] / 2, 0, m.origin[2] + m.size[2] / 2)
      gl.uniform1f(u.uIntro, Math.min(m.intro, intro))
      list.length = 0
      for (const r of m.regions) {
        const c = [r.c[0] + m.origin[0], r.c[1] + m.origin[1], r.c[2] + m.origin[2]]
        if (!visible(c, r.r + (intro < 1 ? 200 : 0))) continue
        const dx = c[0] - eye[0], dy = c[1] - eye[1], dz = c[2] - eye[2]
        list.push({ r, d: dx * dx + dy * dy + dz * dz })
      }
      list.sort((a, b) => a.d - b.d)
      gl.enable(gl.CULL_FACE)
      for (const { r } of list) {
        gl.drawElements(gl.TRIANGLES, r.solid * 6, gl.UNSIGNED_INT, r.start * 24)
        stats.tris += r.solid * 2; stats.draws += 1
      }
      gl.disable(gl.CULL_FACE)
      for (const { r } of list) {
        if (!r.plants) continue
        gl.drawElements(gl.TRIANGLES, r.plants * 6, gl.UNSIGNED_INT, (r.start + r.solid) * 24)
        stats.tris += r.plants * 2; stats.draws += 1
      }
      stats.regions += list.length
    }

    // the sea of clouds
    if (clouds) {
      gl.depthMask(false)
      gl.useProgram(cloud.p)
      const c = cloud.u
      gl.bindVertexArray(quad)
      gl.uniformMatrix4fv(c.uVP, false, VP)
      gl.uniform3fv(c.uCam, eye)
      gl.uniform3fv(c.uLit, day.cloudLit)
      gl.uniform3fv(c.uDark, day.cloudDark)
      gl.uniform3fv(c.uHaze, haze)
      gl.uniform3fv(c.uSunDir, sunDir)
      gl.uniform1i(c.uNoise, 2)
      gl.uniform1f(c.uTime, time)
      for (const layer of view.clouds || DEFAULT_CLOUDS) {
        gl.uniform1f(c.uY, layer.y)
        gl.uniform1f(c.uR, layer.r)
        gl.uniform1f(c.uCover, layer.cover)
        gl.uniform1f(c.uAlpha, layer.alpha)
        gl.uniform1f(c.uScale, layer.scale)
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
      }
    }

    // lanterns: soft halos, only once it gets dark
    if (day.emis > 0.03) {
      gl.depthMask(false)
      gl.blendFunc(gl.ONE, gl.ONE)
      gl.useProgram(glow.p)
      const g = glow.u
      gl.uniformMatrix4fv(g.uVP, false, VP)
      gl.uniform1f(g.uPx, h * 1.6)
      gl.uniform1f(g.uEmis, day.emis)
      gl.uniform1f(g.uTime, time)
      gl.uniform1f(g.uIntro, intro)
      gl.uniform3f(g.uTint, 1.0, 0.68, 0.36)
      for (const m of meshes) {
        if (!m.visible || !m.lvao) continue
        gl.bindVertexArray(m.lvao)
        gl.uniform3fv(g.uOrigin, m.origin)
        gl.drawArrays(gl.POINTS, 0, m.nl)
      }
      gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA)
    }
    gl.depthMask(true)
    gl.bindVertexArray(null)
  }

  /** Screen position of a world direction (for the square sun and moon). */
  function projectDir(dir) {
    const x = VP[0] * dir[0] + VP[4] * dir[1] + VP[8] * dir[2]
    const y = VP[1] * dir[0] + VP[5] * dir[1] + VP[9] * dir[2]
    const w = VP[3] * dir[0] + VP[7] * dir[1] + VP[11] * dir[2]
    if (w <= 0.0001) return null
    return [(x / w) * 0.5 + 0.5, 0.5 - (y / w) * 0.5]
  }

  /** Looks the same way as the camera, flat: where the horizon crosses the screen. */
  function horizonY(view) {
    const dx = view.at[0] - view.eye[0], dz = view.at[2] - view.eye[2]
    const p = projectDir([dx, 0, dz])
    return p ? p[1] : null
  }

  return { gl, addMesh, render, projectDir, horizonY, stats, meshes }
}

export const DEFAULT_CLOUDS = [
  { y: -70, r: 6000, cover: 0.46, alpha: 1.0, scale: 0.00042 },
  { y: 18, r: 3600, cover: 0.68, alpha: 0.55, scale: 0.00085 },
]
