/** The Helm mark: a ship's wheel (hub, rim, eight spokes with handles), drawn to sit on the violet tile. */
export function HelmMark({ size = 20 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" stroke="currentColor" strokeLinecap="round" aria-hidden>
      <circle cx="12" cy="12" r="6.6" fill="none" strokeWidth={1.7} />
      <circle cx="12" cy="12" r="2.5" stroke="none" />
      <path
        fill="none"
        strokeWidth={1.45}
        d="M12 9.8V2.6M13.56 10.44l5.09-5.09M14.2 12h7.2M13.56 13.56l5.09 5.09M12 14.2v7.2M10.44 13.56l-5.09 5.09M9.8 12H2.6M10.44 10.44 5.35 5.35"
      />
      <g stroke="none">
        <circle cx="12" cy="1.7" r="1.3" />
        <circle cx="19.28" cy="4.72" r="1.3" />
        <circle cx="22.3" cy="12" r="1.3" />
        <circle cx="19.28" cy="19.28" r="1.3" />
        <circle cx="12" cy="22.3" r="1.3" />
        <circle cx="4.72" cy="19.28" r="1.3" />
        <circle cx="1.7" cy="12" r="1.3" />
        <circle cx="4.72" cy="4.72" r="1.3" />
      </g>
    </svg>
  );
}
