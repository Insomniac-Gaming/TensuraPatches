# Changelog

Changes are listed by release, with an emphasis on what players and server
operators will notice. For setup details, see [README.md](README.md).

## [0.2.0] - 2026-10-24

### New: SlimeThroneExtras compatibility

- **Restored Tensura and ReRebirth's skill-lock handling.** SlimeThroneExtras
  previously intercepted the original lock system, which could interfere with
  ReRebirth's locks and resets.
- **Removed SlimeThrone's competing reset-scroll behavior.** ReRebirth and
  ReReroll can manage character rebirths and skill rolls without SlimeThrone's
  extra prestige checks overriding the original mechanics.
- **Removed obsolete SlimeThrone menus on clients.** Its separate skill-lock
  button, prestige tab, and conflicting reset requirements no longer appear.
- **Stopped SlimeThrone from forcing the `resetPerSkillLock` gamerule to zero.**

### Kept from SlimeThroneExtras

- Added races, including their normal evolution paths.
- Blacksmith, Keen Nose, and Imitator skills.
- Lootr support for Charybdis cave loot.
- Existing items and nonconflicting content. The patch does not delete player
  progression or automatically migrate stored data.

### Also included: Tensura Horizons patch

- Prevents automatic Sovereign Vessel acquisition and True Sovereign awakening.
  Existing character data is left intact, and Tensura Lower's separate
  Sovereign skill is not affected.

### Installation and compatibility

- **Install this patch on the server and every client** to get both the
  mechanics fix and the cleaned-up interfaces.
- Requires Minecraft 1.21.1, NeoForge 21.1.x, and Tensura Horizons 0.31.
- Works with SlimeThroneExtras **2.1.2.1**. Other SlimeThroneExtras versions
  are not supported; the Horizons-only patch works without it.
- Keep the original Tensura, ReRebirth, ReReroll, and Horizons mods installed.
  If you use SlimeThroneExtras, keep it installed too; this patch does not
  replace its content.
- Back up the world before changing mods on the live server.

### Testing status

- Automated tests confirm that a disposable Minecraft server starts, reaches
  RCON, and activates the SlimeThrone compatibility filter. The client pipeline
  checks that the game reaches its main menu.
- **Actual skill-lock survival through a reset still needs in-game testing.**
  The reported 60-versus-73 skill-pool mismatch may be a separate ReReroll
  issue; this release does not claim to fix it.
