# apt-mc

**The Advanced Packaging Tool for Minecraft Servers, made with pure spite.**

`apt-mc` is a Spigot/Paper plugin that brings the familiar `apt` command-line experience to Minecraft server management. It allows you to search, install, upgrade, and manage plugins directly from the game chat or server console, powered by the [Modrinth API](https://modrinth.com/).

## Features

-   **📦 Install & Remove**: Easily install plugins by their Modrinth slug, Hangar slug, or GitHub repo (`user/repo`) and remove them by filename.
-   **🔍 Search**: Search the Modrinth and Hangar databases for plugins directly from chat.
-   **🛡️ Dry Run**: Use the `--dry-run` flag with most commands to see what *would* happen without making any changes.
-   **🔄 Upgrade System**: 
    -   `apt upgrade` checks all installed plugins against Modrinth.
    -   Downloads updates to the `update/` folder for safe installation on restart.
    -   Intelligently  handles file replacement.
-   **📋 State Management**:
    -   **Export**: Save your server's plugin state to a YAML file for backup or replication.
    -   **Export**: Save your server's plugin state (versions + configurations) to a YAML file.
    -   **Import**: Restore a server's state, including plugin files and configurations.
    -   **Config Bundling**: Automatically bundles configuration files (filtered by extension) into the export.
    -   **Unrecognised Plugins**: graceful handling of custom jars not found on Modrinth.
-   **⚡ High Performance**:
    -   **Parallel Processing**: Downloads and API checks run asynchronously and in parallel.
    -   **Persistent Caching**: Caches plugin metadata to `plugins/apt-mc/cache.json`.
-   **🖥️ User Friendly**:
    -   **Action Bar Integration**: Shows download progress bars in the action bar.
    -   **Console Throttling**: intelligently updates console logs.

## Commands

Most commands support the `--dry-run` flag to simulate actions without modifying files.

| Command | Description |
| :--- | :--- |
| `/apt update` | Updates the package index (simulation/cache refresh). |
| `/apt upgrade` | Downloads updates for all installed plugins. |
| `/apt install <plugin>` | Installs one or more plugins (e.g., `/apt install viaversion`). |
| `/apt remove <plugin>` | Removes a plugin JAR file. |
| `/apt search <query>` | Searches Modrinth for plugins. |
| `/apt list` | Lists installed plugins and their versions. |
| `/apt info <plugin>` | Shows detailed metadata, author, and dependencies. |
| `/apt export [file]` | Exports state (plugins + configs) to a YAML manifest. |
| `/apt import [file]` | Imports plugins and restores configurations from a manifest. |

## Configuration

The `config.yml` file allows you to tweak the interface and export settings:

```yaml
# Whether to use the action bar for status updates and progress bars.
use-action-bar: true

# Interval in seconds to update progress in the console.
console-progress-interval: 5

# Enable references to the song "APT." by Rosé & Bruno Mars
apt-song-references: true

# Export configuration
export:
  # Mode: 'whitelist' or 'blacklist'
  filter-mode: blacklist
  # Extensions to filter
  extensions:
    - jar
    - log
    - lock
    - old
```

## Manifest Format

The export file structure:

```yaml
project-details:
  title: My Server
  author: Admin

plugins:
  - file: ViaVersion-5.2.1.jar
    source: "modrinth:viaversion/5.2.1"

unrecognised:
  - CustomPlugin.jar

configs:
  - plugin: ViaVersion
    path: config.yml
    data: "base64encodedstring..."
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