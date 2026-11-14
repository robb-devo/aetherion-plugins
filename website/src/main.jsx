import { StrictMode } from 'react'
import { createRoot, hydrateRoot } from 'react-dom/client'
import './index.css'
import './home.css'
import App from './App.jsx'

const root = document.getElementById('root')
const app = (
  <StrictMode>
    <App />
  </StrictMode>
)
// Pages baked at build time wake up in place; anything else (the /servers app, say) renders fresh.
const baked = document.querySelector('meta[name="x-baked"]')?.getAttribute('content')
const here = window.location.pathname.replace(/\/+$/, '') || '/'
if (root.firstElementChild && baked === here) hydrateRoot(root, app)
else {
  root.textContent = ''
  createRoot(root).render(app)
}
