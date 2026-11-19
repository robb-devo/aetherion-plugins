import { asset } from '../lib/asset.js'

/** The mark as an image: nav, footer, chrome. Files live in public/brand/. */
export function BrandImg({ className = 'h-8 w-8', tile = false, alt = '' }) {
  return <img src={asset(tile ? 'brand/aetherion-icon.svg' : 'brand/aetherion-mark.svg')} alt={alt} className={className} draggable="false" />
}
