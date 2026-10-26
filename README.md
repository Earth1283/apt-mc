# apt-mc

**The Advanced Packaging Tool for Minecraft Servers, made with pure spite.**

`apt-mc` is a Spigot/Paper plugin that brings the familiar `apt` command-line experience to Minecraft server management. It allows you to search, install, upgrade, and manage plugins directly from the game chat or server console, powered by the [Modrinth API](https://modrinth.com/).

## Features

-   **📦 Install & Remove**: Easily install plugins by their Modrinth slug, Hangar slug, or GitHub repo (`user/repo`) and remove them by filename.
-   **🔍 Search**: Search the Modrinth and Hangar databases for plugins directly from chat.
-   **🌳 Dependency Graph**: Inspect installed plugins with `/apt graph`, including optional dependencies, missing requirements, runtime status, and Adventure hover tooltips. Scans are asynchronous, cached, and work offline.
-   **🛡️ Dry Run**: Use the `--dry-run` flag with most commands to see what *would* happen without making any changes.
-   **🔄 Upgrade System**: 
    -   `apt upgrade` checks all installed plugins against Modrinth.
    -   Downloads updates to the `update/` folder for safe installation on restart.
    -   Intelligently  handles file replacement.
-   **📋 State Management**:
    -   **Export**: Save your server's plugin state (versions + configurations) to a YAML file.
    -   **Import**: Restore a server's state, including plugin files and configurations. Auto-detects `.zip` manifests, no manual decompression needed.
    -   **Config Bundling**: Automatically bundles configuration files (filtered by extension) into the export. Skip with `--no-configs`.
    -   **Unrecognised Plugins**: graceful handling of custom jars not found on Modrinth. Bundle them anyway with `--include-all` (use at your own risk).
    -   **Compression**: `--compress` zips the manifest to cut file size.
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
| `/apt graph [plugin] [--reverse] [--required-only] [--refresh] [--page N]` | Shows installed plugin dependency trees, with interactive details in chat. |
| `/apt info <plugin>` | Shows detailed metadata, author, and dependencies. |
| `/apt export [file] [--include-all] [--compress] [--no-configs]` | Exports state (plugins + configs) to a YAML manifest. |
| `/apt import [file]` | Imports plugins and restores configurations from a manifest (`.yml` or `.zip`, auto-detected). |

### Export flags

| Flag | Effect |
| :--- | :--- |
| `--include-all` | Embeds unrecognised (non-Modrinth) plugin jars as base64 into the manifest instead of just listing their names. Bundles arbitrary jars into the file — use at your own risk. |
| `--compress` | Zips the manifest to `<file>.zip` instead of writing plain `.yml`. Reduces file size, especially with bundled configs/jars. |
| `--no-configs` | Skips config bundling, exporting plugin versions only. |

### Dependency graphs

```text
/apt graph
/apt graph MyPlugin
/apt graph MyPlugin --reverse
/apt graph MyPlugin --required-only
/apt graph --refresh
/apt graph --page 2
```

Optional dependencies are visible by default. Missing required dependencies show `[missing!]`; absent optional integrations show `[optional] [absent]`. `--reverse` shows installed plugins that depend on the selected plugin (and can also inspect a missing dependency by name). `--required-only` hides optional relationships.

Hover over a plugin to see its version, JAR filename, description, authors, dependency relationship, and runtime status. Clicking a name suggests its graph command; pagination links also suggest commands. Console output includes essential labels and page instructions without requiring hover support.

The graph reads `plugin.yml` or `paper-plugin.yml` from JARs directly inside the plugins directory, without loading classes or contacting an API. Paper descriptors take precedence when both exist. It understands `provides` aliases and Paper bootstrap/server dependencies. Load-order declarations are shown separately in tooltips, rather than treated as dependencies. Plugins that are disabled or not loaded remain visible, and a differing runtime version is labeled explicitly. Unreadable descriptors and duplicate plugin names produce warnings. Runtime-only integrations that are not declared in metadata cannot appear in the graph; staged JARs in `update/` are not included.

Scanning and graph rendering run on background workers. Metadata is cached by path, size, and modification time; the graph is reused for 30 seconds by default. Concurrent requests share a scan, and apt file changes invalidate the graph. After the cache expires, only changed JARs are reparsed. `--refresh` forces descriptor rereads, including replacements with unchanged size/timestamps. This cache lives in memory and starts empty after restart. Tab completion uses cached names without disk access.

```yaml
graph:
  cache-seconds: 30 # 0 checks the directory on every request; maximum 3600
  page-size: 14    # 5–40 rows per page
  max-depth: 8    # 1–20 dependency levels
```

Cycles and repeated subtrees are labeled; rendering is capped at 2,000 tree rows. All graph labels, tooltip text, errors, and navigation messages are customizable under `graph` in `messages.yml`, with command help under `usage.graph`. Plugin metadata is inserted as literal text. Restart after changing graph settings or messages.

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
  # only present with --include-all, for plugins not found on Modrinth
  - file: CustomPlugin.jar
    source: "embedded"
    data: "base64encodedjar..."

# unresolved plugins, listed here instead of embedded when --include-all is not used
unrecognised:
  - CustomPlugin.jar

configs:
  - plugin: ViaVersion
    path: config.yml
    data: "base64encodedstring..."
```

With `--compress`, this same YAML is written as a single entry inside a `.zip` instead of a plain `.yml` file. `/apt import` reads either format transparently.

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
