# Offline sims (not part of the plugin build)

Plain `main()` programs that drive the real classes without a server. Use them after tuning `ai.*` / moves
to see how fast the Gnomon learns, exploits, gets out-played, and how focus rotates in a party.

| Sim | What it shows |
|-----|---------------|
| `BrainSim` | one player dodges left every time, then switches to right at round 22: notice → teach window → exploit (hits) → *outplayed* → forget → re-learn |
| `PartySim` | a melee hugger + a ranged orbiter: per-player reads, focus rotation, which moves get picked and which read they spend |
| `rig/RigSim` | rig math: bone transforms, 2-bone IK lengths, gait across the dial (no overstretch, no NaN), bob rest height, plumb-drop landing time/accuracy |

Run from `AetherionGnomon/` after `mvn -DskipTests package` (PowerShell):

```powershell
$m2 = "$env:USERPROFILE\.m2\repository"
$cp = "target\classes;$m2\io\papermc\paper\paper-api\1.21.1-R0.1-SNAPSHOT\paper-api-1.21.1-R0.1-SNAPSHOT.jar;$m2\org\joml\joml\1.10.5\joml-1.10.5.jar;$m2\com\google\guava\guava\32.1.2-jre\guava-32.1.2-jre.jar"
javac -proc:none -cp $cp -d target\sim tools\sim\BrainSim.java tools\sim\PartySim.java tools\sim\de\aetherion\gnomon\rig\RigSim.java
java -cp "target\sim;$cp" BrainSim
java -cp "target\sim;$cp" PartySim
java -cp "target\sim;$cp" de.aetherion.gnomon.rig.RigSim
```

The BrainSim/PartySim configs are inline at the top of each file (same fields as `config.yml ai.*`).
