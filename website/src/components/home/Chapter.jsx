/**
 * A stretch of the day. `tone` says what the world behind it looks like:
 * day  ink on a warm fog bank
 * dusk paper on a violet one
 * dark paper on the night
 * It sets colours only. The fog bank itself is drawn by `.bank`.
 */
export default function Chapter({ id, chapter, tone = 'day', className = '', children }) {
  return (
    <section id={id} data-chapter={chapter ?? id} data-mood={tone} className={`ch ${className}`}>
      {children}
    </section>
  )
}

/** Title and text in their fog bank. */
export function Bank({ title, children, side = 'left', className = '', titleId }) {
  return (
    <div className={`bank bank-${side} ${className}`}>
      <div className="bank-in">
        {title ? (
          <h2 id={titleId} className="ch-title">
            {title}
          </h2>
        ) : null}
        {children}
      </div>
    </div>
  )
}
