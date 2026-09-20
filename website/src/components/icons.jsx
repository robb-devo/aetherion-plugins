const stroke = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.7,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
}

export function FeatureIcon({ id }) {
  const common = { viewBox: '0 0 24 24', className: 'h-6 w-6', 'aria-hidden': true }
  switch (id) {
    case 'skills':
      return (
        <svg {...common}>
          <path {...stroke} d="M12 3 v4 M12 17 v4 M3 12 h4 M17 12 h4" />
          <circle {...stroke} cx="12" cy="12" r="3.2" />
          <path {...stroke} d="M7.2 7.2 9.4 9.4 M14.6 14.6 16.8 16.8 M16.8 7.2 14.6 9.4 M9.4 14.6 7.2 16.8" />
        </svg>
      )
    case 'islands':
      return (
        <svg {...common}>
          <path {...stroke} d="M4 16c2-4 4-6 8-6s6 2 8 6" />
          <path {...stroke} d="M8 16c.6-2 1.5-3 4-3s3.4 1 4 3" />
          <path {...stroke} d="M12 7 V10" />
          <path {...stroke} d="M10 8.5 12 6.2 14 8.5" />
        </svg>
      )
    case 'amethyst':
      return (
        <svg {...common}>
          <path {...stroke} d="M12 3 19 10 12 21 5 10 Z" />
          <path {...stroke} d="M12 7 16 10.5 12 17 8 10.5 Z" />
        </svg>
      )
    case 'quests':
      return (
        <svg {...common}>
          <path {...stroke} d="M7 4.5 h8.5 a2 2 0 0 1 2 2 V19 l-4.2-2.1 L9.1 19 V6.5 a2 2 0 0 1 2-2" />
          <path {...stroke} d="M10 8.5 h5 M10 11.5 h5" />
        </svg>
      )
    case 'market':
      return (
        <svg {...common}>
          <path {...stroke} d="M4 9 h16 l-1.2 9.2 a2 2 0 0 1-2 1.8 H7.2 a2 2 0 0 1-2-1.8 Z" />
          <path {...stroke} d="M8 9 V6.5 a4 4 0 0 1 8 0 V9" />
        </svg>
      )
    case 'pads':
      return (
        <svg {...common}>
          <path {...stroke} d="M5 17c4-9 10-9 14 0" />
          <path {...stroke} d="M12 6 v4 M10.2 8.2 12 6.2 13.8 8.2" />
          <path {...stroke} d="M6 17 h12" />
        </svg>
      )
    case 'dungeons':
      return (
        <svg {...common}>
          <path {...stroke} d="M5 20 V9.5 L12 4 l7 5.5 V20" />
          <path {...stroke} d="M10 20 v-6 h4 v6" />
          <path {...stroke} d="M9 11.2 h.01 M15 11.2 h.01" />
        </svg>
      )
    default:
      return null
  }
}

export function DiscordIcon({ className = 'h-4 w-4' }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true" fill="currentColor">
      <path d="M19.3 5.2A17.4 17.4 0 0 0 14.9 4l-.2.4c1.6.4 3 .9 4.3 1.7-1.7-1-3.6-1.7-5.6-2.1l-.5-.1h-.1A14.7 14.7 0 0 0 12 3.8a14.7 14.7 0 0 0-1.8.2h-.1l-.5.1c-2 .4-3.9 1.1-5.6 2.1C5.3 5.3 6.8 4.7 8.3 4.3L8.1 4A17.4 17.4 0 0 0 4.7 5.2C2.2 8.8 1.5 12.3 1.7 15.7c1.9 1.4 3.7 2.3 5.5 2.8l.7-1.1c-1.2-.4-2.3-1-3.3-1.7.3.2.6.4 1 .6 2.2 1.2 4.6 1.8 7.1 1.8s4.9-.6 7.1-1.8c.3-.2.6-.4 1-.6-1 .7-2.1 1.3-3.3 1.7l.7 1.1c1.8-.5 3.6-1.4 5.5-2.8.3-4-.5-7.5-2.7-10.5ZM9.2 14.3c-.8 0-1.5-.8-1.5-1.7s.7-1.7 1.5-1.7 1.5.8 1.5 1.7-.7 1.7-1.5 1.7Zm5.6 0c-.8 0-1.5-.8-1.5-1.7s.7-1.7 1.5-1.7 1.5.8 1.5 1.7-.6 1.7-1.5 1.7Z" />
    </svg>
  )
}
