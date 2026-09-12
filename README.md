# NexusMobShield v0.1.0

Blocks the vanilla `/kill` command against protected entities, from **every possible source** --
a player, the console, or a hidden command block you haven't found yet -- without needing to know
who's running it or where it's hiding. Built in response to an active incident: unauthorized
command blocks somewhere on the map were mass-killing mobs (and could have just as easily been
pointed at players) faster than they could be found and removed by hand.

## How it actually works

Bukkit fires a real event -- `EntityDamageEvent` with `DamageCause.KILL` -- specifically and only
for the vanilla `/kill` command, and it respects that event being cancelled. This plugin listens
for exactly that cause and cancels it for protected entities. Every other kind of damage (combat,
fall damage, drowning, lava, your other plugins' own effects) is completely untouched -- this
plugin cannot see or affect anything except literal `/kill` commands.

**Important limitation, stated plainly:** this does not find, disable, or remove the rogue command
block(s) themselves, and it can't stop them from running other commands (`/give`, `/tp`, `/ban`,
worldedit-style commands, anything else). It only makes `/kill` stop *working* against whatever
you've told it to protect. Whoever placed those command blocks still has them, and depending on
what let them place/edit command blocks in the first place, they may still be able to do other
damage. Treat this as an immediate stopgap for the specific symptom you're seeing right now, not
a fix for how they got command-block access to begin with -- see "what to do next" below.

## Commands (all require `nexusmobshield.admin`)

- `/nexusmobshield status` -- protection on/off, whether players are protected, total blocked
  since the server started, and whether a temporary bypass is currently open.
- `/nexusmobshield toggle` -- turn protection fully on/off.
- `/nexusmobshield allow [seconds]` -- opens a temporary window (default 30s) where `/kill` works
  normally again, for when *you* legitimately want to run one yourself. Closes automatically.
- `/nexusmobshield log [count]` -- the most recent blocked attempts (entity/player, world,
  coordinates, time) -- default 15, kept in memory (see below), capped at the last 200 overall.
  If these cluster around the same coordinates, that's a strong hint about where the command
  block actually is.
- `/nexusmobshield reload` -- reload config.yml.

## Config (`plugins/NexusMobShield/config.yml`)

- `settings.enabled` -- master switch.
- `settings.protect-players` -- **defaults to true.** Whatever can run unrestricted `/kill`
  commands against mobs could just as easily target players next; this closes that door too by
  default. Turn it off if you specifically only want mobs protected.
- `settings.exempt-entity-types` -- Bukkit entity type names to leave unprotected, for anything
  one of your other plugins intentionally removes with a real `/kill`.
- `heartbeat.*` -- how often blocked-kill counts get summarized into one log line/NexusHeartbeat
  nudge, instead of one message per kill (see "why no per-kill logging" below).

## Why no per-kill logging or disk log

Whatever's doing this can run fast enough to kill 125+ entities in a single tick. Writing one
console line or one disk-log line per blocked kill would just trade the original console-spam
problem for an identical one caused by this plugin instead. So: the recent-attempts list
(`/nexusmobshield log`) lives in memory only (cleared on restart, capped at 200 entries), and
everything else is a periodic aggregated count (`heartbeat.summary-interval-seconds`, default 60)
-- "blocked 4,812 kill attempts in the last 60 seconds" as one line, not 4,812 lines.

## What to do next (this plugin is a stopgap, not the fix)

Two things worth doing alongside this:

1. **Find and physically remove the actual command block(s).** You already have NexusCmdTracker
   built for exactly this -- it scans the world for command blocks and lets you browse/filter/
   teleport to them. `/nexusmobshield log` may help narrow down *where* to look, since a command
   block that kills mobs near itself (a common way to write one) will show up as repeated
   coordinates close together.
2. **Figure out how unauthorized players got command-block access at all.** Placing and editing
   command blocks normally requires operator permission in vanilla Minecraft -- if non-op players
   are doing it, something is granting that permission it shouldn't (a permissions config, a
   different plugin, or a compromised/leaked op account), and that's the actual security hole.
   This plugin doesn't touch that at all; it only neutralizes this one specific symptom.

## Build

```
mvn clean package
```

Produces `target/NexusMobShield-0.1.0.jar`. Requires Java 21 and network access to
`repo.papermc.io` / Maven Central. See CHANGES.md for how this was verified in the sandbox
(full compile against a hand-written stub library, plus a standalone check of the blocked-kill
ring buffer and counters) -- run your own `mvn clean package` against the real Paper API.
