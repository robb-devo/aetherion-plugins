// Every place the camera can ever stand, for the baker's view culling. Writes "x y z" lines.
// usage: node views.mjs <out.txt>
import { writeFileSync } from 'node:fs'
import { ROUTE } from '../src/world/route.js'

const cr = (a, b, c, d, t) => 0.5 * (2 * b + (c - a) * t + (2 * a - b * 5 + c * 4 - d) * t * t + (3 * b - a - c * 3 + d) * t * t * t)
const out = []
const add = (p) => out.push(p.map((v) => v.toFixed(1)).join(' '))

for (const portrait of [false, true]) {
  const K = ROUTE.map((k) => (portrait && k.p ? { ...k, ...k.p } : k))
  for (let i = 0; i < K.length - 1; i += 1) {
    const A = K[Math.max(0, i - 1)], B = K[i], C = K[i + 1], D = K[Math.min(K.length - 1, i + 2)]
    for (let s = 0; s < 4; s += 1) {
      const t = s / 4
      const eye = [0, 1, 2].map((j) => cr(A.eye[j], B.eye[j], C.eye[j], D.eye[j], t))
      const at = [0, 1, 2].map((j) => cr(A.at[j], B.at[j], C.at[j], D.at[j], t))
      add(eye)
      // the camera breathes and follows the pointer a little: cover a step to each side
      const dx = eye[0] - at[0], dz = eye[2] - at[2]
      const d = Math.hypot(dx, dz) || 1
      const side = d * 0.03
      add([eye[0] + (-dz / d) * side, eye[1], eye[2] + (dx / d) * side])
      add([eye[0] - (-dz / d) * side, eye[1], eye[2] - (dx / d) * side])
    }
  }
}
// the hero can be dragged all the way around
const C = [172, 182, 190]
for (const [r, y] of [[540, 160], [540, 240], [540, 320], [660, 230], [660, 330]]) {
  for (let a = 0; a < 32; a += 1) {
    const t = (a / 32) * Math.PI * 2
    add([C[0] + Math.cos(t) * r, y, C[2] + Math.sin(t) * r])
  }
}
writeFileSync(process.argv[2], out.join('\n') + '\n')
console.log(`${out.length} viewpoints`)
