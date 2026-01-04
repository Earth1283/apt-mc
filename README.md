# apt-mc

**The Advanced Packaging Tool for Minecraft Servers, made with pure spite.**

`apt-mc` is a Spigot/Paper plugin that brings the familiar `apt` command-line experience to Minecraft server management. It allows you to search, install, upgrade, and manage plugins directly from the game chat or server console, powered by the [Modrinth API](https://modrinth.com/).

## Features

-   **📦 Install & Remove**: Easily install plugins by their Modrinth slug and remove them by filename.
-   **🔍 Search**: Search the Modrinth database for plugins directly from chat.
-   **🔄 Upgrade System**: 
    -   `apt upgrade` checks all installed plugins against Modrinth.
    -   Downloads updates to the `update/` folder for safe installation on restart.
    -   Intelligently  handles file replacement.
-   **📋 State Management**:
    -   **Export**: Save your server's plugin state to a YAML file for backup or replication.
    -   **Import**: Restore a server's state or replicate a setup on a new server with one command.
    -   **Portable Format**: Uses simple identifiers (e.g., `modrinth:viaversion/5.2.1`) for easy sharing.
-   **⚡ High Performance**:
    -   **Parallel Processing**: Downloads and API checks run asynchronously and in parallel.
    -   **Persistent Caching**: Caches plugin metadata to `plugins/apt-mc/cache.json` to minimize API limits and speed up commands.
-   **🖥️ User Friendly**:
    -   **Action Bar Integration**: Shows download progress bars in the action bar to keep chat clean.
    -   **Console Throttling**: intelligently updates console logs to avoid spamming the log file.

## Commands

| Command | Description |
| :--- | :--- |
| `/apt update` | Updates the package index (simulation/cache refresh). |
| `/apt upgrade` | Downloads updates for all installed plugins. |
| `/apt install <plugin>` | Installs one or more plugins (e.g., `/apt install viaversion`). |
| `/apt remove <plugin>` | Removes a plugin JAR file. |
| `/apt search <query>` | Searches Modrinth for plugins. |
| `/apt list` | Lists installed plugins and their versions (resolved via Modrinth). |
| `/apt info <plugin>` | Shows detailed metadata, author, and dependencies for a plugin. |
| `/apt export [file]` | Exports the current plugin state to a YAML manifest (default: `apt-manifest.yml`). |
| `/apt import [file]` | Imports and installs plugins from a YAML manifest. |

## Configuration

The `config.yml` file allows you to tweak the interface:

```yaml
# Whether to use the action bar for status updates and progress bars.
# Set to false to force all output to the chat.
use-action-bar: true

# Interval in seconds to update progress in the console.
console-progress-interval: 5

# Enable references to the song "APT." by Rosé & Bruno Mars
apt-song-references: true
```

## Manifest Format

The export file uses a simple YAML structure, allowing you to manually define plugins by Project ID or version.

```yaml
project-details:
  title: My Server
  author: Admin

plugins:
  # Install specific version
  ViaVersion: "modrinth:viaversion/5.2.1"
  
  # Install latest version
  Sodium: "modrinth:sodium/latest"
```

## Installation

1.  Download the latest release JAR.
2.  Place it in your server's `plugins` folder.
3.  Restart the server.

## Building from Source

This project uses Gradle.

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.