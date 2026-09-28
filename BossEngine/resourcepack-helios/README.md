# Helios Requiem sound pack (optional)

Put mono `.ogg` files into `assets/helios/sounds/` with the names from `sounds.json`
(`heartbeat.ogg`, `star_hum.ogg`, ...), merge into your server pack, then map them in
`plugins/BossEngine/helios.yml`:

```yaml
sounds:
  overrides:
    entity.warden.heartbeat: helios:heartbeat
    block.beacon.ambient: helios:star_hum
    entity.warden.sonic_charge: helios:seismic_whine
    entity.warden.sonic_boom: helios:seismic_boom
    entity.guardian.attack: helios:lance_charge
    block.end_portal_frame.fill: helios:portal_open
    entity.generic.explode: helios:supernova
    block.note_block.flute: helios:requiem_pad
```

Any vanilla key the fight uses can be overridden; unmapped keys keep the vanilla layering.
