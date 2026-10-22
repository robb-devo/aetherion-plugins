import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { AlertTriangle, CheckCircle2, Info, X } from "lucide-react";
import { t } from "../lib/i18n";
import { useStore } from "../lib/store";
import { toasts } from "../lib/toast";
import { Button, IconButton, cx } from "./ui";

/** Modal sheet: centred card on desktop, bottom sheet on phones. Escape and the scrim close it.
 * `foot` receives the animated close, so its buttons dismiss the sheet the same way. */
export function Sheet({ title, sub, children, foot, onClose, wide, icon }: {
  title: ReactNode;
  sub?: ReactNode;
  children?: ReactNode;
  foot?: (close: () => void) => ReactNode;
  onClose: () => void;
  wide?: boolean;
  icon?: ReactNode;
}) {
  const [closing, setClosing] = useState(false);
  const close = () => {
    if (closing) return;
    setClosing(true);
    setTimeout(onClose, 200);
  };
  useEffect(() => {
    const k = (e: KeyboardEvent) => e.key === "Escape" && close();
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  });
  return createPortal(
    <div className={cx("scrim", closing && "closing")} onPointerDown={(e) => e.target === e.currentTarget && close()}>
      <div className={cx("glass sheet", wide && "wide")} role="dialog" aria-modal="true">
        <div className="grabber" />
        <div className="sheet-head">
          {icon}
          <div className="grow">
            <h2>{title}</h2>
            {sub && <p>{sub}</p>}
          </div>
          <IconButton label={t("Close")} size="sm" onClick={close} data-dismiss>
            <X />
          </IconButton>
        </div>
        {children && <div className="sheet-body">{children}</div>}
        {foot && <div className="sheet-foot">{foot(close)}</div>}
      </div>
    </div>,
    document.body,
  );
}

/** Yes/no question with an optional danger tone. Resolves through `onConfirm`. */
export function Confirm({ title, text, confirm = t("Confirm"), danger, onConfirm, onClose }: {
  title: string;
  text?: ReactNode;
  confirm?: string;
  danger?: boolean;
  onConfirm: () => Promise<unknown> | void;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  return (
    <Sheet
      title={title}
      sub={text}
      onClose={onClose}
      foot={(close) => (
        <>
          <Button variant="ghost" onClick={close}>{t("Cancel")}</Button>
          <Button
            variant={danger ? "danger" : "primary"}
            busy={busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm();
                close();
              } finally {
                setBusy(false);
              }
            }}
          >
            {confirm}
          </Button>
        </>
      )}
    />
  );
}

export function Toasts() {
  const list = useStore(toasts, (s) => s);
  return createPortal(
    <div className="toasts" aria-live="polite">
      {list.map((n) => (
        <div key={n.id} className={cx("glass toast", n.kind, n.out && "out")}>
          <span className="ic">{n.kind === "ok" ? <CheckCircle2 /> : n.kind === "err" ? <AlertTriangle /> : <Info />}</span>
          <span>{n.text}</span>
        </div>
      ))}
    </div>,
    document.body,
  );
}

export interface MenuItem {
  label: string;
  icon?: ReactNode;
  onClick: () => void;
  danger?: boolean;
  hidden?: boolean;
  sep?: boolean;
}

/** Anchored dropdown. Pass the element it hangs from. */
export function Menu({ anchor, items, onClose }: { anchor: HTMLElement; items: MenuItem[]; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  useLayoutEffect(() => {
    const r = anchor.getBoundingClientRect();
    const mw = ref.current?.offsetWidth ?? 220;
    const mh = ref.current?.offsetHeight ?? 200;
    let left = r.right - mw;
    if (left < 8) left = Math.min(r.left, window.innerWidth - mw - 8);
    let top = r.bottom + 6;
    if (top + mh > window.innerHeight - 8) top = Math.max(8, r.top - mh - 6);
    setPos({ top, left });
  }, [anchor]);
  useEffect(() => {
    const down = (e: PointerEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node) && !anchor.contains(e.target as Node)) onClose();
    };
    const key = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    setTimeout(() => window.addEventListener("pointerdown", down), 0);
    window.addEventListener("keydown", key);
    return () => {
      window.removeEventListener("pointerdown", down);
      window.removeEventListener("keydown", key);
    };
  }, [anchor, onClose]);
  return createPortal(
    <div className="menu" ref={ref} style={pos ? { top: pos.top, left: pos.left } : { visibility: "hidden", top: 0, left: 0 }}>
      {items
        .filter((i) => !i.hidden)
        .map((i, k) =>
          i.sep ? (
            <hr key={k} />
          ) : (
            <button
              key={k}
              className={i.danger ? "danger" : ""}
              onClick={() => {
                onClose();
                i.onClick();
              }}
            >
              {i.icon}
              {i.label}
            </button>
          ),
        )}
    </div>,
    document.body,
  );
}

export function useMenu() {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  return {
    open: (e: React.MouseEvent<HTMLElement>) => setAnchor(e.currentTarget),
    render: (items: MenuItem[]) => (anchor ? <Menu anchor={anchor} items={items} onClose={() => setAnchor(null)} /> : null),
  };
}
