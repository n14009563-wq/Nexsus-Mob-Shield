# NexusMobShield changelog

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
