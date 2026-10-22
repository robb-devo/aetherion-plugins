import { useEffect, useLayoutEffect, useRef, useState, type ButtonHTMLAttributes, type CSSProperties, type ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { initials, stateInfo } from "../lib/format";
import { tap } from "../lib/native";

export function cx(...c: (string | false | null | undefined)[]) {
  return c.filter(Boolean).join(" ");
}

export function Card(props: { children: ReactNode; className?: string; style?: CSSProperties; onClick?: () => void; strong?: boolean }) {
  return (
    <div className={cx("glass card", props.strong && "strong", props.onClick && "interactive", props.className)} style={props.style} onClick={props.onClick}>
      {props.children}
    </div>
  );
}

export function CardHead(props: { title: ReactNode; icon?: ReactNode; sub?: ReactNode; right?: ReactNode }) {
  return (
    <div className="card-head">
      <h3>
        {props.icon}
        {props.title}
      </h3>
      {props.sub && <span className="sub">{props.sub}</span>}
      {props.right && <div className="right">{props.right}</div>}
    </div>
  );
}

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "danger" | "ghost" | "tinted" | "default";
  tone?: "red" | "green" | "orange";
  size?: "sm" | "lg";
  icon?: ReactNode;
  busy?: boolean;
  block?: boolean;
};

export function Button({ variant = "default", tone, size, icon, busy, block, className, children, onClick, ...rest }: BtnProps) {
  return (
    <button
      className={cx("btn", variant !== "default" && variant, tone, size, block && "block", className)}
      disabled={busy || rest.disabled}
      onClick={(e) => {
        tap("light");
        onClick?.(e);
      }}
      {...rest}
    >
      {busy ? <Loader2 className="spin" /> : icon}
      {children}
    </button>
  );
}

export function IconButton({ label, children, className, size, dot, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string; size?: "sm"; dot?: boolean }) {
  return (
    <button className={cx("icon-btn", size, className)} aria-label={label} title={label} {...rest}>
      {children}
      {dot && <i className="badge-dot" />}
    </button>
  );
}

/** Press and hold for `ms` to fire: for actions that hurt (stop, kill, ban). */
export function HoldButton({ onConfirm, ms = 900, children, className, icon, variant = "danger", size, disabled }: {
  onConfirm: () => void;
  ms?: number;
  children: ReactNode;
  className?: string;
  icon?: ReactNode;
  variant?: BtnProps["variant"];
  size?: "sm" | "lg";
  disabled?: boolean;
}) {
  const fill = useRef<HTMLSpanElement>(null);
  const timer = useRef<number>();
  const start = () => {
    if (disabled) return;
    tap("medium");
    const el = fill.current;
    if (el) {
      el.style.transition = `transform ${ms}ms linear`;
      el.style.transform = "scaleX(1)";
    }
    timer.current = window.setTimeout(() => {
      tap("heavy");
      reset();
      onConfirm();
    }, ms);
  };
  const reset = () => {
    clearTimeout(timer.current);
    const el = fill.current;
    if (el) {
      el.style.transition = "transform 200ms ease";
      el.style.transform = "scaleX(0)";
    }
  };
  return (
    <button
      className={cx("btn hold", variant !== "default" && variant, size, className)}
      onPointerDown={start}
      onPointerUp={reset}
      onPointerLeave={reset}
      onPointerCancel={reset}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onConfirm();
        }
      }}
      onContextMenu={(e) => e.preventDefault()}
      disabled={disabled}
      title="Gedrückt halten"
    >
      <span className="fill" ref={fill} />
      {icon}
      {children}
    </button>
  );
}

export function Badge({ tone, children, icon, outline }: { tone?: string; children: ReactNode; icon?: ReactNode; outline?: boolean }) {
  return (
    <span className={cx("badge", tone, outline && "outline")}>
      {icon}
      {children}
    </span>
  );
}

export function Dot({ tone, pulse }: { tone?: string; pulse?: boolean }) {
  return <i className={cx("dot", tone, pulse && "pulse")} />;
}

export function StateChip({ state }: { state: string }) {
  const s = stateInfo(state);
  return (
    <span className="state-chip">
      <Dot tone={s.tone} pulse={s.pulse && state !== "online"} />
      {s.label}
    </span>
  );
}

export function Segmented<T extends string>({ value, options, onChange, size }: {
  value: T;
  options: { value: T; label: ReactNode; icon?: ReactNode }[];
  onChange: (v: T) => void;
  size?: "sm";
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [thumb, setThumb] = useState<{ left: number; width: number } | null>(null);
  useLayoutEffect(() => {
    const el = ref.current?.querySelector<HTMLButtonElement>("button.on");
    if (el) setThumb({ left: el.offsetLeft, width: el.offsetWidth });
  }, [value, options.length]);
  return (
    <div className={cx("segmented", size)} ref={ref} role="tablist">
      {thumb && <span className="thumb" style={{ left: thumb.left, width: thumb.width }} />}
      {options.map((o) => (
        <button
          key={o.value}
          role="tab"
          aria-selected={o.value === value}
          className={o.value === value ? "on" : ""}
          onClick={() => {
            tap("light");
            onChange(o.value);
          }}
        >
          {o.icon}
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Tabs<T extends string>({ value, tabs, onChange }: { value: T; tabs: { value: T; label: string; icon?: ReactNode; hidden?: boolean }[]; onChange: (v: T) => void }) {
  return (
    <div className="tabs" role="tablist">
      {tabs
        .filter((t) => !t.hidden)
        .map((t) => (
          <button key={t.value} role="tab" aria-selected={t.value === value} className={t.value === value ? "on" : ""} onClick={() => onChange(t.value)}>
            {t.icon}
            {t.label}
          </button>
        ))}
    </div>
  );
}

export function Switch({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label?: string }) {
  return (
    <button
      role="switch"
      aria-checked={on}
      aria-label={label}
      className={cx("switch", on && "on")}
      onClick={() => {
        tap("light");
        onChange(!on);
      }}
    />
  );
}

export function Field({ label, help, children }: { label?: ReactNode; help?: ReactNode; children: ReactNode }) {
  return (
    <div className="field">
      {label && <span className="label">{label}</span>}
      {children}
      {help && <span className="help">{help}</span>}
    </div>
  );
}

export function Empty({ icon, title, text, action }: { icon?: ReactNode; title: string; text?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      {icon}
      <b>{title}</b>
      {text && <span>{text}</span>}
      {action}
    </div>
  );
}

export function Skeleton({ h = 16, w = "100%", r }: { h?: number; w?: number | string; r?: number }) {
  return <div className="skel" style={{ height: h, width: w, borderRadius: r }} />;
}

export function Meter({ value, tone, lg, title }: { value: number; tone?: string; lg?: boolean; title?: string }) {
  const v = Math.max(0, Math.min(1, isFinite(value) ? value : 0));
  return (
    <div className={cx("meter", tone, lg && "lg")} title={title}>
      <i style={{ width: `${v * 100}%` }} />
    </div>
  );
}

/** Number that glides to its new value. */
export function Count({ value, format = (v) => String(Math.round(v)), ms = 650 }: { value: number | null | undefined; format?: (v: number) => string; ms?: number }) {
  const [shown, setShown] = useState(value ?? 0);
  const from = useRef(value ?? 0);
  const raf = useRef<number>();
  useEffect(() => {
    if (value == null || !isFinite(value)) return;
    const start = performance.now();
    const a = from.current;
    const b = value;
    if (a === b) return;
    cancelAnimationFrame(raf.current!);
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / ms);
      const e = 1 - Math.pow(1 - t, 3);
      const v = a + (b - a) * e;
      setShown(v);
      from.current = v;
      if (t < 1) raf.current = requestAnimationFrame(step);
    };
    raf.current = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf.current!);
  }, [value, ms]);
  if (value == null || !isFinite(value)) return <>–</>;
  return <>{format(shown)}</>;
}

export function Stat({ label, value, unit, hint, icon, size, tone }: {
  label: ReactNode;
  value: ReactNode;
  unit?: ReactNode;
  hint?: ReactNode;
  icon?: ReactNode;
  size?: "sm" | "lg";
  tone?: string;
}) {
  return (
    <div className={cx("stat", size)}>
      <span className="label">
        {icon}
        {label}
      </span>
      <span className={cx("value", tone)}>
        {value}
        {unit && <small>{unit}</small>}
      </span>
      {hint && <span className="hint">{hint}</span>}
    </div>
  );
}

const failed = new Set<string>();

export function Avatar({ uuid, name, size, online, kind = "avatar" }: { uuid?: string; name?: string; size?: "xs" | "sm" | "lg" | "xl"; online?: boolean; kind?: "avatar" | "head" }) {
  const id = uuid || name;
  const px = size === "xs" ? 32 : size === "sm" ? 48 : size === "lg" ? 128 : size === "xl" ? 176 : 64;
  const src = id ? `https://mc-heads.net/${kind}/${id}/${px}` : null;
  const [bad, setBad] = useState(!src || failed.has(src));
  return (
    <span className={cx("avatar", size)}>
      {!bad && src ? (
        <img
          src={src}
          alt=""
          loading="lazy"
          onError={() => {
            failed.add(src);
            setBad(true);
          }}
        />
      ) : (
        initials(name)
      )}
      {online && <i className="on" />}
    </span>
  );
}

export function IconTile({ tone, children, size }: { tone?: string; children: ReactNode; size?: "sm" }) {
  return <span className={cx("icon-tile", tone, size)}>{children}</span>;
}

export function KV({ rows }: { rows: [ReactNode, ReactNode][] }) {
  return (
    <dl className="kv">
      {rows.map(([k, v], i) => (
        <div key={i} style={{ display: "contents" }}>
          <dt>{k}</dt>
          <dd>{v ?? "–"}</dd>
        </div>
      ))}
    </dl>
  );
}

export function Banner({ tone = "accent", icon, children, action }: { tone?: string; icon?: ReactNode; children: ReactNode; action?: ReactNode }) {
  return (
    <div className={cx("banner", tone)}>
      {icon && <IconTile tone={tone} size="sm">{icon}</IconTile>}
      <div className="grow">{children}</div>
      {action}
    </div>
  );
}

export function PageHead({ title, sub, actions }: { title: ReactNode; sub?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-head">
      <div style={{ minWidth: 0 }}>
        <h1>{title}</h1>
        {sub && <p>{sub}</p>}
      </div>
      {actions && <div className="actions">{actions}</div>}
    </div>
  );
}

/** Re-renders every `ms` so relative times ("vor 3 Min.") stay true. */
export function useTick(ms = 15000) {
  const [, set] = useState(0);
  useEffect(() => {
    const id = setInterval(() => set((x) => x + 1), ms);
    return () => clearInterval(id);
  }, [ms]);
}

export function useDebounced<T>(v: T, ms = 250): T {
  const [d, setD] = useState(v);
  useEffect(() => {
    const id = setTimeout(() => setD(v), ms);
    return () => clearTimeout(id);
  }, [v, ms]);
  return d;
}
