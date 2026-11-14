/** Public files, relative in a preview build (the page sits under some other URL). */
const BASE = import.meta.env.BASE_URL || '/'
export const asset = (path) => `${BASE}${String(path).replace(/^\//, '')}`
