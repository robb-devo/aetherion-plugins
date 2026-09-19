# Ready-to-create Discord structure (paste / create manually if needed)

## Categories + channels
WILLKOMMEN
  #regeln
  #info
  #rollen

COMMUNITY
  #chat          ← DiscordSRV global
  #media
  #support

SERVER
  #ankuendigungen
  #status

TEAM (Admin only)
  #team-chat
  #mc-console    ← DiscordSRV console
  #mc-logs

VOICE
  Lobby
  Dungeon

## Roles (create bottom → top in Discord UI)
### Discord-only
Bot
Verified
Updates
Playtest

### Game ranks (exact LuckPerms names — Role Sync)
Adventurer
Veteran
Champion
Legend
Mythwright
Aetherborn
Celestine
Sovereign
Ascendant
Empyrean
Eternal
Aetherion
MVP++
Admin

## After create
1. Enable Developer Mode → copy channel IDs for #chat and #mc-console
2. Copy role IDs for sync map
3. Upload DiscordSRV.jar to Hub plugins/ in Crafty
4. Put BotToken + IDs into config / synchronization
