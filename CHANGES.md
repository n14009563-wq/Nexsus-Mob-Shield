# NexusMobShield changelog

## v0.2.0 -- fix: mobs were still being killed despite v0.1.0

Built from a direct bug report on the deployed v0.1.0: "this plug in that we built, and it's not
keeping mobs from being killed. It's still allowing mobs to be killed. The only mob that I want to
have killed is creepers. That's the only one that is permanently turned off. All the other ones, I
want them to be forced back into the game because the console log is still killing them. There's
something out there that's auto killing them." -- followed by a console screenshot showing
`[@: Killed 89 entities]` repeating, confirming the incident was still ongoing.

**Root cause (most likely): a priority-tier race, the same class of bug already found and fixed
once this session in NexusSpawnShield.** v0.1.0 registered its damage-cancelling handler at
`HIGHEST` priority. Bukkit does NOT guarantee execution order between two listeners at the same
priority tier -- it only guarantees that `MONITOR` runs after every other tier, period. A
`HIGHEST` listener can lose a race against another `HIGHEST` listener, which is the most plausible
explanation for cancellation intermittently not holding. Fixed by moving to `MONITOR` and forcing
both sides of the outcome explicitly (`setCancelled(true)` **and** `setDamage(0)`), rather than
trusting cancellation alone.

**Defense-in-depth on top of that, in case the priority fix alone isn't the whole story:** a new
`EntityDeathEvent` handler checks, at the moment a mob actually dies, whether its last damage
cause was exactly the blocked `/kill`. If so -- meaning the cancellation genuinely didn't hold --
a same-type replacement is spawned back in at the same location a tick later. This is intentionally
narrow: it only engages for that one specific damage cause, so real combat, fall damage, drowning,
lava, and every other death mode are completely unaffected and remain normal, permanent deaths, same
as v0.1.0. Rate-limited to `recovery.max-per-minute` (default 40) so a fast `/kill` loop can't turn
into a runaway spawn loop of its own.

**New requirement, implemented as live-manageable rather than YAML-only:** CREEPER is now exempt
by default (`exempt-entity-types: [CREEPER]`) -- the one mob type explicitly meant to stay
killable. New `/nexusmobshield exempt list|add <TYPE>|remove <TYPE>` commands manage the exempt
list at runtime, persisting to config.yml immediately -- no manual file editing or restart needed
for future changes. (A server upgrading from v0.1.0 does need one manual
`/nexusmobshield exempt add CREEPER`, since Bukkit's config-defaults mechanism doesn't overwrite a
key that's already present -- called out in the README.)

**Also fixed:** `pom.xml` had no `<build>` section at all, meaning `${project.version}` in
`plugin.yml` was never actually being filtered/replaced by a real Maven build -- every build would
have shipped a plugin.yml literally containing the string `${project.version}` instead of `0.1.0`.
Added the missing `<build><resources><resource><filtering>true</filtering></resource></resources></build>`
block.

**Verification:** extended the stub library (added `EntityDamageEvent.getDamage()/setDamage()`,
already had `EntityDeathEvent`, `LivingEntity.getLastDamageCause()`, `World.spawnEntity()`, and the
scheduler's `runTaskLater`) and recompiled the whole plugin with `javac -Xlint:all -Werror`: 0
errors, 0 warnings.

## v0.1.0 -- first release, built during an active incident

Built from: "players have been able to get into the server and create command blocks that I can
no longer find. These command blocks are killing mobs at a high rate... I need to make it to
where it can bypass command blocks that are trying to kill mobs. It needs to override this."

The console screenshot that prompted this showed `[@: Killed 125 entities]` repeating once a
tick -- that exact phrasing is the vanilla `/kill` command's own success message, which confirmed
this is specifically `/kill @e[...]` being run on a loop, not a third-party mob-clearing plugin.

**Design decision, confirmed before building:** should this also protect players from `/kill`, or
mobs only as literally asked? Went with **both** -- whatever's running unrestricted `/kill`
commands right now could just as easily be pointed at players next, and the "utmost security,
no exceptions" standard set earlier this session for NexusGate applies here too. A per-command
bypass (`/nexusmobshield allow [seconds]`) exists for whenever the admin legitimately wants to run
a real `/kill` themselves.

**How it works:** Bukkit fires `EntityDamageEvent` with `DamageCause.KILL` specifically for the
vanilla `/kill` command, from any source (player, console, or a command block), and respects that
event being cancelled. A `HIGHEST`-priority listener cancels it for protected entities and leaves
every other damage cause completely untouched -- combat, fall damage, this server's other
plugins, all unaffected.

**Explicitly NOT solved by this release, on purpose:** finding/removing the actual rogue command
block(s) (that's NexusCmdTracker's job -- already built, scans the world for command blocks), and
figuring out how unauthorized players got command-block placement/edit access in the first place
(a permissions/security question outside this plugin's scope, but the more important one long
term). README calls both of these out explicitly so they don't get lost once the immediate
symptom stops.

**Performance design, given the actual observed rate (125+ kills/tick):** no per-blocked-kill
logging to console or disk -- only a capped 200-entry in-memory ring buffer (`/nexusmobshield
log`) plus a periodic aggregated summary (default every 60s) to console and NexusHeartbeat.
Logging one line per blocked kill at the rate this was observed running would have just traded
one console-spam problem for an identical one caused by the fix itself.

**Verification:** built a stub library covering this plugin's full Bukkit/Paper surface (reusing
and extending the stub set from this session's NexusPennywise build, given the overlap) and
compiled the whole tree with `javac -Xlint:all -Werror`: 0 errors, 0 warnings. Separately ran a
standalone check of the blocked-kill ring buffer and counters (confirms the 200-entry cap actually
rolls off the oldest entries rather than the newest, and that the periodic-summary counter drains
to zero and doesn't double-count) against the compiled classes directly.
