# KedamaSurvivors

A Vampire Survivors-style roguelite minigame plugin for Paper/Spigot 1.21.8+, blending elements of Vampire Survivors and CS: Arms Race.

**[Quick Start Guide](Docs/Quick-Start.md)** | [Configuration Reference](Docs/Configuration-Reference.md) | [Commands Reference](Docs/Commands-Reference.md)

## Overview

KedamaSurvivors is a standalone Paper plugin that provides a complete roguelite game loop:

- **Select starter equipment** → **Ready up** → **Teleport to combat world** → **Fight enemies** → **Level up** → **Upgrade equipment** → **Survive!**

Players choose their starting weapon and helmet, then enter pre-generated combat worlds where enemies spawn around them. Killing enemies grants XP and coins. Filling the XP bar prompts an upgrade choice between weapon or helmet, progressing through equipment tiers with roguelike randomization.

### Key Features

- **Team System**: Create teams of up to 5 players, explore together, respawn to teammates
- **Equipment Progression**: Multiple equipment groups with leveled item pools
- **Scaling Difficulty**: Enemy level based on average player level and player count in radius
- **PVP Protection**: Player damage disabled by default (configurable)
- **Perma-Score**: Persistent score stored in vanilla scoreboard for cross-server rewards
- **Wandering Merchants**: Vanilla villager traders spawn randomly with configurable trades
- **No Dependencies**: Works standalone, integrates with other plugins via command templates

## Game Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Join Prep  │ ──▶ │   Select    │ ──▶ │    Ready    │
│    Area     │     │  Equipment  │     │    Check    │
└─────────────┘     └─────────────┘     └─────────────┘
                                               │
                    ┌──────────────────────────┘
                    ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Teleport   │ ──▶ │   Combat    │ ──▶ │   Level Up  │
│  to Arena   │     │    Loop     │     │   Upgrade   │
└─────────────┘     └─────────────┘     └─────────────┘
                          │                    │
                          ▼                    │
                    ┌─────────────┐            │
                    │    Death    │ ◀──────────┘
                    │  → Prep     │
                    └─────────────┘
```

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/vrs starter` | Open starter equipment selection GUI |
| `/vrs ready` | Toggle ready status to enter combat |
| `/vrs quit` | Leave current run (no death penalty) |
| `/vrs status` | Show current game status |
| `/vrs upgrade power\|defense` | Choose upgrade during gameplay |

### Team Commands

| Command | Description |
|---------|-------------|
| `/vrs team create [name]` | Create a new team (auto-generates name if not provided) |
| `/vrs team invite <player>` | Invite a player to your team |
| `/vrs team join <team>` | Join a team you're invited to |
| `/vrs team leave` | Leave your current team |
| `/vrs team kick <player>` | Kick a player from your team |
| `/vrs team disband` | Disband your team |
| `/vrs team list` | List team members and status |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/vrs join enable\|disable` | Toggle global game entry |
| `/vrs world enable\|disable <world>` | Enable/disable combat worlds |
| `/vrs reload` | Reload configuration |
| `/vrs force start\|stop <target>` | Force start/stop runs |
| `/vrs item capture <type> <group> <level> <id>` | Capture held item as template |
| `/vrs merchant spawn [world] [template]` | Spawn a merchant |
| `/vrs spawner pause\|resume [world]` | Control enemy spawning |
| `/vrs debug player\|perf\|run` | Debug information |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `vrs.player` | Basic player commands | true |
| `vrs.team.*` | Team management | true |
| `vrs.admin` | All admin commands | op |
| `vrs.cooldown.bypass` | Bypass death cooldown | op |

## Configuration

See [Configuration Reference](Docs/Configuration-Reference.md) for complete configuration options.

Key configuration files:
- `config.yml` - Main plugin configuration
- `lang/zh_CN.yml` - Chinese language file (default)
- `data/items/*.yml` - Captured item NBT templates
- `data/runtime/*.json` - Player/team state persistence

## Development

### Requirements

- Java 21+
- Gradle 8.5+
- Paper API 1.21.4+

### Building

```bash
./gradlew build
```

The compiled plugin JAR will be in `build/libs/`.

### Project Structure

```
src/main/java/cat/nyaa/survivors/
├── KedamaSurvivorsPlugin.java  # Main plugin class
├── config/                      # Configuration handling
├── command/                     # Command implementations
├── service/                     # Game logic services
├── model/                       # Data models
├── listener/                    # Event listeners
├── scoreboard/                  # Sidebar display
├── i18n/                        # Internationalization
└── util/                        # Utilities
```

### Documentation

- [Development Specification](Docs/Development-Spec.md) - Technical design document
- [Configuration Reference](Docs/Configuration-Reference.md) - All config options
- [Commands Reference](Docs/Commands-Reference.md) - Complete command documentation
- [Design Overview](Docs/Design-Overview.md) - High-level design document

## Integration

KedamaSurvivors is designed to work standalone but integrates well with:

- **RPGItems-reloaded**: Custom weapons and armor (via NBT capture)
- **Multiverse**: World management (via command templates)
- **EssentialsX**: Spawn management (external to this plugin)

All integration is done through configurable command templates with placeholder variables:
```yaml
teleport:
  enterCommand: "mv tp ${player} ${world}:${x},${y},${z}"
```

## License

MIT License

## Credits

- NyaaCat Development Team
- Claude Code (AI-assisted development)
