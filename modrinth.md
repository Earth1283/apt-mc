# 📦 apt-mc
**Package Management for Spigot/Paper Servers**

> *Stop manually dragging JARs. Start managing plugins like a pro.*

`apt-mc` brings the power of the command line to Minecraft. Search, install, update, and backup your plugins directly from chat or console—powered by the **Modrinth API**.

---

## 🔥 Features at a Glance

### 🚀 **Install & Update**
- **One-Command Install**: `/apt install viaversion`, `/apt install user/repo` (GitHub), or Hangar slugs.
- **Smart Upgrades**: `/apt upgrade` checks **all** plugins against Modrinth and downloads updates automatically.
- **Dependency Handling**: Automatically installs required libraries.
- **Dry Run Safety**: Use `--dry-run` with any command to see changes before they happen.

### 💾 **Backup & Restore**
- **Full State Export**: Save your plugin list **and configurations** to a single YAML file.
- **Instant Restore**: `/apt import backup.yml` restores your server entire plugin setup — `.zip` manifests auto-detected too, no manual unzip needed.
- **Migration Ready**: Perfect for moving servers or syncing dev/prod environments.
- **Flexible Export**: `--include-all` bundles unrecognised jars too (use at your own risk), `--compress` zips it down, `--no-configs` skips config bundling.

### ⚡ **Performance First**
- **Async & Parallel**: Downloads happen in the background. No lag.
- **Smart Caching**: Caches metadata to keep commands instant.
- **Clean UI**: meaningful progress bars in the Action Bar, no chat spam.

---

## 🛠️ Cheat Sheet

| Command | Action |
| :--- | :--- |
| `/apt install <name>` | Install plugins (e.g. `/apt install luckperms`) |
| `/apt remove <name>` | Delete a plugin JAR |
| `/apt upgrade` | Update all plugins to latest version |
| `/apt search <query>` | Find plugins on Modrinth |
| `/apt export [file] [--include-all\|--compress\|--no-configs]` | Save plugins + configs to file |
| `/apt import [file]` | Restore plugins + configs from file (`.yml`/`.zip`) |

---

## ⚙️ Simple Config
Permissions? None needed for OP. Config? Minimal.

```yaml
# config.yml
use-action-bar: true               # Keep chat clean
console-progress-interval: 5       # No log spam
export:
  filter-mode: blacklist           # Don't export boring files
  extensions: [jar, log, lock]
```

---

### 📥 **Get Started**
1. Download the JAR.
2. Drop it in `plugins/`.
3. Restart & run `/apt update`.

**[Report Bugs](https://github.com/Earth1283/apt-mc/issues) • [Source Code](https://github.com/Earth1283/apt-mc)**
