import { unpackHere } from './engine.js'

/** Decompress and build the vertex arrays away from the page: scrolling never waits on a mesh. */
self.onmessage = async ({ data: { id, bytes } }) => {
  try {
    const mesh = await unpackHere(bytes)
    self.postMessage({ id, mesh }, [mesh.verts, mesh.idx.buffer, mesh.pal.buffer, mesh.lights.buffer])
  } catch (error) {
    self.postMessage({ id, error: String(error) })
  }
}
