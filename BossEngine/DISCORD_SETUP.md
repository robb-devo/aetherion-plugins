# Aetherion Discord — English base (DE as small extra)

Primary language matches the MC server: **English**.  
German lines are optional extras for DE players — not a second full UI.

## On join
No language picker for v1 (keeps setup simple / less bot surface).  
Everyone sees English. Short DE notes only where helpful (rules / info).

Later (optional): Discord Onboarding with DE/EN roles if Community mode is on.

## Channels (English names)
| Channel | Purpose |
|---------|---------|
| rules | Rules EN + short DE |
| info | IP, version, what Aetherion is |
| roles | Optional ping roles only |
| chat | MC ↔ Discord sync (MMO-R / MMO-D) |
| link | Paste `/discord link` codes here (no DM needed) |
| media | Screenshots |
| support | Help |
| announcements | News (read-only) |
| status | Short updates |
| staff-chat | Staff |
| mc-console | DiscordSRV console (MMO-R) |
| mc-logs | Logs |
| Lobby / Dungeon | Voice |

## Ranks (unchanged — from MC)
Adventurer … Aetherion, MVP++, Admin — same LuckPerms groups.

## Bot (official only)
- **One shared bot** named `Aetherion` (BOT tag in the member list) — not per player.
- DiscordSRV runs on **MMO-R** (Hub disabled). Shared link DB = MariaDB `discordsrv`.
- **MMO-D** needs a second bot app (`Aetherion D`) — same DiscordSRV token on two servers is unsupported.
- Link flow: in-game `/discord link` → paste code in `#link`.
- Never automate with a user account token.
