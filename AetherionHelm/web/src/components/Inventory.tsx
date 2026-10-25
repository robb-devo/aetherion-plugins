import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Apple, Backpack, Gem, Heart, MapPin, RefreshCw, Sparkles, Star } from "lucide-react";
import { useApi } from "../lib/api";
import { n0, n1 } from "../lib/format";
import { t } from "../lib/i18n";
import { RARITY_COLOR, component, effectName, iconFor, loadIcons, plain, prettyId, roman } from "../lib/mc";
import type { Inventory as Inv, Item } from "../lib/types";
import { Sheet } from "./overlay";
import { Badge, Banner, Card, CardHead, Empty, IconButton, Meter, Segmented, Skeleton } from "./ui";

function useIcons() {
  const [set, setSet] = useState<Record<string, string> | null>(null);
  useEffect(() => {
    loadIcons().then(setSet);
  }, []);
  return set;
}

interface Tip {
  item: Item;
  x: number;
  y: number;
}

function ItemTooltip({ tip }: { tip: Tip }) {
  const it = tip.item;
  const name = it.name ? component(it.name, { color: it.rarity ? RARITY_COLOR[it.rarity] : it.ench?.length ? "aqua" : "white" }) : (
    <span style={{ color: it.ench?.length ? "#55FFFF" : "#fff" }}>{prettyId(it.id)}</span>
  );
  const left = Math.min(tip.x + 16, window.innerWidth - 350);
  const top = Math.min(tip.y + 12, window.innerHeight - 200);
  return createPortal(
    <div className="mc-tooltip" style={{ left, top }}>
      <div className="name">{name}</div>
      {it.ench?.map((e) => (
        <div key={e.id} style={{ color: "#AAAAAA" }}>
          {prettyId(e.id)} {e.lvl <= 10 ? roman(e.lvl) : e.lvl}
        </div>
      ))}
      {it.lore?.map((l, i) => (
        <div key={i} className="lore">
          {component(l, { color: "dark_purple", i: true })}
        </div>
      ))}
      {it.contents && <div style={{ color: "#AAAAAA" }}>{it.contents.length === 1 ? t("1 stack inside · click to open") : t("{n} stacks inside · click to open", { n: it.contents.length })}</div>}
      {it.unbreakable && <div style={{ color: "#5555FF" }}>{t("Unbreakable")}</div>}
      {it.maxDmg ? (
        <div style={{ color: "#fff" }}>{t("Durability: {left} / {max}", { left: n0(it.maxDmg - (it.dmg ?? 0)), max: n0(it.maxDmg) })}</div>
      ) : null}
      <div className="meta">
        minecraft:{it.id}
        {it.cmd ? ` · CMD ${it.cmd}` : ""}
        {it.count > 1 ? ` · ${it.count}×` : ""}
      </div>
    </div>,
    document.body,
  );
}

function Slot({ item, icons, selected, onTip, onOpen, ghost }: {
  item?: Item | null;
  icons: Record<string, string> | null;
  selected?: boolean;
  onTip: (t: Tip | null) => void;
  onOpen?: (i: Item) => void;
  ghost?: string;
}) {
  if (!item) {
    return (
      <div className={"slot" + (selected ? " selected" : "")}>
        {ghost && icons && iconFor(ghost, icons) && <img className="ghost" src={iconFor(ghost, icons)!} alt="" />}
      </div>
    );
  }
  const src = iconFor(item.id, icons);
  const dur = item.maxDmg ? 1 - (item.dmg ?? 0) / item.maxDmg : null;
  return (
    <div
      className={"slot filled" + (selected ? " selected" : "")}
      onPointerEnter={(e) => onTip({ item, x: e.clientX, y: e.clientY })}
      onPointerMove={(e) => onTip({ item, x: e.clientX, y: e.clientY })}
      onPointerLeave={() => onTip(null)}
      onClick={(e) => {
        if (item.contents && onOpen) onOpen(item);
        else onTip({ item, x: e.clientX, y: e.clientY });
      }}
      title={plain(item.name) || prettyId(item.id)}
    >
      {src ? <img src={src} alt="" /> : <span className="fallback">{prettyId(item.id)}</span>}
      {(item.glint || (item.ench && item.ench.length > 0)) && <span className="glint" />}
      {dur != null && dur < 1 && (
        <span className="dur">
          <i style={{ width: `${dur * 100}%`, background: `hsl(${dur * 120}, 100%, 50%)` }} />
        </span>
      )}
      {item.count > 1 && <span className="cnt">{item.count}</span>}
    </div>
  );
}

function Grid({ items, size, icons, onTip, onOpen, offset = 0, selected }: {
  items: Item[];
  size: number;
  icons: Record<string, string> | null;
  onTip: (t: Tip | null) => void;
  onOpen?: (i: Item) => void;
  offset?: number;
  selected?: number;
}) {
  const by = new Map(items.map((i) => [i.slot ?? -1, i]));
  return (
    <div className="inv">
      {Array.from({ length: size }, (_, i) => (
        <Slot key={i} item={by.get(i + offset)} icons={icons} onTip={onTip} onOpen={onOpen} selected={selected === i + offset} />
      ))}
    </div>
  );
}

export function InventoryView({ uuid, online }: { uuid: string; online: boolean }) {
  const { data, error, loading, reload } = useApi<Inv>(online ? `/api/players/${uuid}/inventory` : null);
  const icons = useIcons();
  const [tip, setTip] = useState<Tip | null>(null);
  const [tab, setTab] = useState<"inv" | "ender">("inv");
  const [open, setOpen] = useState<Item | null>(null);

  if (!online) {
    return (
      <Card>
        <Empty icon={<Backpack />} title={t("Available while online")} text={t("When this player is online, you'll see their inventory, armor and ender chest here.")} />
      </Card>
    );
  }
  if (error) return <Banner tone="red">{error.message}</Banner>;
  if (loading || !data) {
    return (
      <Card>
        <div className="col">
          <Skeleton h={22} w={180} />
          <Skeleton h={200} />
        </div>
      </Card>
    );
  }

  const armorGhost = ["netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"];
  const totalItems = [...data.inventory, ...data.enderchest].reduce((a, i) => a + i.count, 0);
  const worth = valuables([...data.inventory, ...data.armor.filter((a): a is Item => !!a), ...(data.offhand ? [data.offhand] : []), ...data.enderchest]);
  const stacks = (tab === "inv" ? data.inventory : data.enderchest).length;
  const ticksToClock = (d: number) => (d > 1_000_000 ? "∞" : `${Math.floor(d / 20 / 60)}:${String(Math.floor((d / 20) % 60)).padStart(2, "0")}`);

  return (
    <div className="inv-layout">
      <Card>
        <CardHead
          title={tab === "inv" ? t("Inventory") : t("Ender chest")}
          icon={<Backpack />}
          right={
            <>
              <Segmented value={tab} onChange={setTab} options={[{ value: "inv", label: t("Inventory") }, { value: "ender", label: t("Ender chest") }]} />
              <IconButton label={t("Refresh")} onClick={reload}>
                <RefreshCw />
              </IconButton>
            </>
          }
        />
        {tab === "inv" ? (
          <div className="inv-wrap">
            <div className="inv-armor">
              {data.armor.map((a, i) => (
                <Slot key={i} item={a} icons={icons} onTip={setTip} ghost={armorGhost[i]} />
              ))}
              <span className="inv-gap" />
              <Slot item={data.offhand} icons={icons} onTip={setTip} ghost="shield" />
            </div>
            <div className="col" style={{ gap: 12 }}>
              <Grid items={data.inventory} size={27} offset={9} icons={icons} onTip={setTip} onOpen={setOpen} />
              <Grid items={data.inventory} size={9} offset={0} icons={icons} onTip={setTip} onOpen={setOpen} selected={data.selected} />
            </div>
          </div>
        ) : (
          <Grid items={data.enderchest} size={27} icons={icons} onTip={setTip} onOpen={setOpen} />
        )}
        <div className="tiny faint mt">
          {stacks === 1 ? t("1 stack.") : t("{n} stacks.", { n: stacks })} {t("Hover over an item (or tap it) to see its name, enchantments and lore. Click a shulker box to open it.")}
        </div>
      </Card>

      <div className="col" style={{ gap: 16 }}>
        <Card>
          <CardHead title={t("Vitals")} icon={<Heart />} sub={`${prettyId(data.gamemode)} · ${data.world}`} />
          <div className="col" style={{ gap: 14 }}>
            <Bar icon={<Heart className="red" size={15} />} label={t("Health")} value={`${n1(data.health)} / ${n0(data.maxHealth)}`} frac={data.health / Math.max(1, data.maxHealth)} tone="red" />
            <Bar icon={<Apple className="orange" size={15} />} label={t("Hunger")} value={t("{food} / 20 · Saturation {sat}", { food: data.food, sat: n1(data.saturation) })} frac={data.food / 20} tone="orange" />
            <Bar icon={<Star className="green" size={15} />} label={t("Level {n}", { n: data.level })} value={t("{n}% to next level", { n: Math.round(data.exp * 100) })} frac={data.exp} tone="green" />
            <div className="row" style={{ gap: 10 }}>
              <MapPin className="blue" size={15} />
              <span className="small grow">{t("Position")}</span>
              <span className="mono small">{n0(data.x)} {n0(data.y)} {n0(data.z)}</span>
            </div>
          </div>
          {data.effects.length > 0 && (
            <div className="row wrap mt" style={{ gap: 6 }}>
              {data.effects.map((e) => (
                <Badge key={e.type} tone="accent" icon={<Sparkles />}>
                  {effectName(e.type)} {roman(e.amp + 1)} · {ticksToClock(e.dur)}
                </Badge>
              ))}
            </div>
          )}
        </Card>
        <Card>
          <CardHead title={t("Valuables")} icon={<Gem />} sub={t("Inventory, ender chest and shulker boxes combined")} />
          {worth.length === 0 ? (
            <div className="small faint">{t("No valuables found.")}</div>
          ) : (
            <div className="worth">
              {worth.map((w) => (
                <div key={w.id} className="worth-row">
                  <span className="slot mini">{icons && iconFor(w.id, icons) && <img src={iconFor(w.id, icons)!} alt="" />}</span>
                  <span className="small grow">{w.label}</span>
                  <b className="num">{n0(w.count)}</b>
                </div>
              ))}
            </div>
          )}
          <div className="tiny faint mt">
            {totalItems === 1 ? t("1 item in total.") : t("{n} items in total.", { n: n0(totalItems) })} {t("Unusually high counts early in the game often point to duping.")}
          </div>
        </Card>
      </div>

      {open && (
        <Sheet title={plain(open.name) || prettyId(open.id)} sub={open.contents?.length === 1 ? t("1 stack") : t("{n} stacks", { n: open.contents?.length ?? 0 })} icon={<Gem className="accent" />} onClose={() => setOpen(null)}>
          <Grid items={open.contents ?? []} size={27} icons={icons} onTip={setTip} />
        </Sheet>
      )}
      {tip && <ItemTooltip tip={tip} />}
    </div>
  );
}

function Bar({ icon, label, value, frac, tone }: { icon: React.ReactNode; label: string; value: string; frac: number; tone: string }) {
  return (
    <div className="col" style={{ gap: 6 }}>
      <div className="row" style={{ gap: 10 }}>
        {icon}
        <span className="small grow">{label}</span>
        <span className="small faint num">{value}</span>
      </div>
      <Meter value={frac} tone={tone} />
    </div>
  );
}

/** What staff look at first when something smells like duping: the rare stuff, counted through every container. */
const VALUABLE: Record<string, [id: string, label: string, per: number]> = {
  netherite_ingot: ["netherite_ingot", t("Netherite ingots"), 1],
  netherite_block: ["netherite_ingot", t("Netherite ingots"), 9],
  diamond: ["diamond", t("Diamonds"), 1],
  diamond_block: ["diamond", t("Diamonds"), 9],
  emerald: ["emerald", t("Emeralds"), 1],
  emerald_block: ["emerald", t("Emeralds"), 9],
  gold_ingot: ["gold_ingot", t("Gold ingots"), 1],
  gold_block: ["gold_ingot", t("Gold ingots"), 9],
  elytra: ["elytra", t("Elytra"), 1],
  totem_of_undying: ["totem_of_undying", t("Totems"), 1],
  enchanted_golden_apple: ["enchanted_golden_apple", t("Enchanted golden apples"), 1],
  nether_star: ["nether_star", t("Nether stars"), 1],
  beacon: ["beacon", t("Beacons"), 1],
};

function valuables(items: Item[]) {
  const acc = new Map<string, { id: string; label: string; count: number }>();
  const walk = (list: Item[]) => {
    for (const it of list) {
      const v = VALUABLE[it.id] ?? (it.id.endsWith("shulker_box") ? (["shulker_box", t("Shulker boxes"), 1] as const) : null);
      if (v) {
        const row = acc.get(v[0]) ?? { id: v[0], label: v[1], count: 0 };
        row.count += it.count * v[2];
        acc.set(v[0], row);
      }
      if (it.contents) walk(it.contents);
    }
  };
  walk(items);
  return [...acc.values()].sort((a, b) => b.count - a.count);
}
