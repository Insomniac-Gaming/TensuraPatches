# InsomniacServerPatches

NeoForge 1.21.1 compatibility patches for Insomniac Gaming's Tensura modpack.
Fixes are grouped independently; upstream mods are **not** repackaged.

For published release notes, see [CHANGELOG.md](CHANGELOG.md). Pending changes
are in [changes/](changes/).

## Current patch behavior

### Tensura 2.0.1.3 skill unlock capacity

Normal paid skill unlocks require both the modified maximum magicules and the
base maximum to cover the unlock cost. The base maximum must also retain the
attribute's minimum after payment. This prevents temporary capacity bonuses
from letting a player buy a skill that sends their base capacity into debt.
Free grants, temporary skills, creative grants, and intrinsic skills still use
Tensura's existing bypasses. Existing negative base capacity is not repaired.

`/gamerule mpSkillUnlockRequirementPercent` defaults to `100`. Set it above
100 to require more capacity without changing the amount charged. For example,
`200` requires more than twice the paid unlock cost in both capacities, plus
the attribute minimum in base capacity. Values below 100 act as 100. Tensura's
`mpSkillCost` still determines the amount actually deducted.

This hook is pinned to Tensura 2.0.1.3's unlock handler. Check it again before
updating Tensura. A local build verifies packaging, but an in-game purchase
test is needed before server deployment.

### Automatic costly skill offers

`/gamerule mpAutoSkillOfferThresholdPercent` defaults to `25`. An automatic
unlock pauses when its permanent **base maximum** magicule charge is strictly
above that percentage of the player's base maximum before payment. Tensura EP
unlocks use the charge after `mpSkillCost`; FoxAblaze's custom acquisitions use
their configured permanent charge. Current-magicule-only costs do not prompt.

The chat offer has **Let it form** and **Suppress** buttons. `/skilloffers`
lists outstanding offers; `/skilloffers accept <skill-id>` and
`/skilloffers suppress <skill-id>` are command fallbacks. Suppression hides
repeat prompts while keeping the offer available. Offers persist across logout
and clear on Tensura's full reset, including the configured rebirth path.
Acceptance checks eligibility and capacity again and uses the original grant
path. An increased cost percentage requires a second acceptance.

Covered paths are Tensura's automatic EP scan (including add-on skills),
Mysticism 2.2.1's separate EP scan, and FoxAblaze 1.0.6.4's automatic
Veldora, Hell Sniper, Mammon, and Velgrynd acquisitions. Player-selected
evolutions, items, gacha, and current-magicule-only grants remain immediate.
These hooks are pinned to the inspected JAR versions. In-game testing on a
disposable world is required before putting this build on a live server.

### Tensura Horizons 0.31 (original patch)

Disables the automatic Sovereign Vessel acquisition and True Sovereign
awakening through the status menu or `/crosshorizon`. Existing Sovereign data
is not deleted. The separate Sovereign skill from Tensura Lower is unaffected.

Also clears Horizons' Void Spirit contract when Tensura performs a full reset
(including ReRebirth's configured `resetMode: everything`). Full resets remove
the granted void magic; clearing the contract lets prayer at a Praying Path
award a new Void Spirit and its magic. Existing contracts are not changed until
that player completes a full reset.

### SlimeThroneExtras 2.1.2.1 compatibility

The pack uses **ReRebirth/ReReroll**, not SlimeThroneExtras, for rebirth, skill
rerolls, and skill locks. SlimeThroneExtras currently overrides the original
Tensura lock list and forces the `resetPerSkillLock` gamerule to zero.

This patch:
- Prevents SlimeThrone's storage, scroll, reincarnation, and gamerule mixins
  from loading, retaining Tensura/ReRebirth behavior instead.
- Disables SlimeThrone's additional skill-removal/lock-restoration handler and
  its periodic gamerule enforcement, without disabling general sync hooks.
- On clients, removes SlimeThrone's own skill-lock button and prestige tab,
  their replacement reset requirements, and the now-unreachable screen hook.
- Stops SlimeThrone's quest and prestige listeners, quest scheduling and
  assignment, and its unused FTB Quests bridge. The `/stextras schedule`,
  `required`, `player`, `quests`, and `race` branches are hidden, as is
  `/stextras debug soulgrade-levels`. Useful debug tools, including
  `check-lootr`, and `/stextras wiki` remain.

**Preserved:** SlimeThrone's added races and their ordinary evolution chains,
skills and their working menus, Lootr/Charybdis integration, other items, and
nonconflicting gameplay. Existing SlimeThrone quest and Soul Grade data are
not erased or migrated. Quest definitions, player storage, and network
registrations stay loaded so existing data can be saved safely and restored by
removing this patch. They no longer provide active quest progression.

The filter is **pinned to 2.1.2.1** and checks the exact upstream mixin names.
It uses Mixin internals to remove overrides before they're prepared, so
revalidate it on every SlimeThrone/Mixin/NeoForge update. If the early filter
does not succeed, mod initialization aborts rather than silently running with
unsafe lock behavior. Do not use this on production without a world backup and
functional testing of locked skills through a reset.

### Installation

Put the server release in the dedicated server modpack. Clients need the
version shown as **Compatible client version** in that release's notes; the
0.2.0 client supplies the SlimeThrone screen and button cleanup. Server-only
updates can retain that client version when they use existing chat and command
behavior. Test a client login and the affected interaction before rollout.
Leave the original mods installed:
Tensura Horizons 0.31, and (if used) SlimeThroneExtras 2.1.2.1.

The SlimeThrone dependency is optional so the Horizons-only patch still works
in environments without SlimeThrone. With SlimeThrone present, only 2.1.2.1 is
accepted.

The pending 0.2.1 release targets client 0.2.0. That combination has not yet
been verified by a client login; the disposable server startup check alone does
not establish multiplayer compatibility.

### Verification

1. Confirm startup logs contain `Disabled 6 SlimeThrone ... mixins and 2
   obsolete UI mixins`, without mixin selection exceptions.
2. Confirm the server reaches RCON **and** the client reaches its main menu.
3. In-game, check that the old SlimeThrone skill-lock button and prestige tab
   are absent, the removed `/stextras` branches are absent from completion,
   and the Imitator skill menu still opens.
4. As a prestige-two test player, compare `/rebirth` with the ReReroll skill
   pool, check `/rebirth locks`, and perform one reset on a backed-up test
   character to verify locks survive. The 60-versus-73 available-skills issue
   may be separate from SlimeThrone's lock overrides.
5. On a disposable world, verify player join, dimension change, and save/reload
   do not reactivate quests or discard saved quest data. Generate a new
   Charybdis structure to confirm Lootr conversion; startup alone cannot
   establish that the processor ran.
6. With a disposable player who has a Void Spirit, run a full reset and check
   `/voidspirit status` shows no contract. Pray at a Praying Path until a new
   Void Spirit is awarded, then check the corresponding void magic is present.

## Build and CI

Build with Java 21: `./gradlew clean build`. The published JAR is under
`build/libs/`; upstream mods and their assets are **not** bundled.

Woodpecker keeps the disposable server startup and client title-screen checks,
then runs a separate in-engine GameTest and real-client player scenario. See
[CI setup](ci/README.md) for details. The CI-only test mods are not part of the
published mod JAR.
