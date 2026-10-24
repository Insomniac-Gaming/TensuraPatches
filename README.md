# InsomniacServerPatches

NeoForge 1.21.1 compatibility patches for Insomniac Gaming's Tensura modpack.
Fixes are grouped independently; upstream mods are **not** repackaged.

For published release notes, see [CHANGELOG.md](CHANGELOG.md). Pending changes
are in [changes/](changes/).

## Current patch behavior

### Tensura Horizons 0.31 (original patch)

Disables the automatic Sovereign Vessel acquisition and True Sovereign
awakening through the status menu or `/crosshorizon`. Existing Sovereign data
is not deleted. The separate Sovereign skill from Tensura Lower is unaffected.

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

Put `InsomniacServerPatches-<version>.jar` in **both** the dedicated server and
client modpacks, then restart. The client copy is required to remove the
obsolete SlimeThrone screens and buttons. Leave the original mods installed:
Tensura Horizons 0.31, and (if used) SlimeThroneExtras 2.1.2.1.

The SlimeThrone dependency is optional so the Horizons-only patch still works
in environments without SlimeThrone. With SlimeThrone present, only 2.1.2.1 is
accepted.

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

## Build and CI

Build with Java 21: `./gradlew clean build`. The published JAR is under
`build/libs/`; upstream mods and their assets are **not** bundled.

The Woodpecker startup checks cover a disposable dedicated server through
RCON and client title screen, not actual player login or reset actions.
See [CI setup](ci/README.md) for details. The CI-only title probe is not part of
the published mod JAR.
