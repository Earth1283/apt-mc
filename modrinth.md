# apt-mc 📦

**The Advanced Packaging Tool for Minecraft Servers.**

> *Stop manually downloading JARs. Start managing plugins like a pro.*

`apt-mc` brings the familiar, powerful experience of the Linux command line (`apt-get`) to your Spigot/Paper server. Search, install, update, and manage your plugins directly from the game chat or console, all powered by the robust **Modrinth API**.

---

## ✨ Features

- **🚀 Seamless Installation**: Install plugins by name (`/apt install viaversion`) or bulk install multiple plugins at once.
- **🧠 Smart Dependency Resolution**: Automatically detects and installs required dependencies for you.
- **🔄 Auto-Updates**: Run `/apt upgrade` to scan your entire `plugins` folder, compare hashes with Modrinth, and download the latest versions automatically.
- **⚡ High Performance**:
  - **Parallel Downloads**: Updates and installs happen asynchronously.
  - **Persistent Caching**: Caches plugin metadata to minimize API usage and speed up commands.
- **🛡️ Safe Updates**: Updates are downloaded to the standard `update/` folder, ensuring a safe swap upon the next server restart.
- **🖥️ Clean UI**:
  - Uses the **Action Bar** for progress updates to prevent chat spam.
  - Intelligent console throttling keeps your logs readable.

---

## 🛠️ Commands

| Command | Description |
| :--- | :--- |
| `/apt install <plugin...>` | Install one or more plugins (e.g., `/apt install luckperms vault`). Auto-resolves dependencies. |
| `/apt remove <plugin>` | Delete a plugin JAR file from the server. |
| `/apt upgrade` | Check **all** installed plugins for updates and download the latest versions. |
| `/apt search <query>` | Search the Modrinth database for plugins. |
| `/apt info <plugin>` | View detailed metadata, author, license, and dependencies. |
| `/apt list` | List all installed plugins and their resolved versions. |
| `/apt update` | Refresh the package cache (simulated parody command). |
| `/apt help` | Show the help menu. |

---

## ⚙️ Configuration

The `config.yml` allows you to customize the interface:

```yaml
# Whether to use the action bar for status updates and progress bars.
# Set to false to force all output to the chat.
use-action-bar: true

# Interval in seconds to update progress in the console.
console-progress-interval: 5
```

---

## 📥 Installation

1.  Download the latest **apt-mc** JAR from the Versions tab.
2.  Drop it into your server's `plugins` folder.
3.  Restart your server.
4.  Run `/apt update` (just for fun!) and start installing.

---

## 🤝 Open Source

This project is open source! We welcome contributions, bug reports, and feature requests.

*   **License**: MIT (or your chosen license)
*   **Build System**: Gradle

---

*Note: This plugin acts as a bridge to the Modrinth API. Please ensure you comply with the licenses of the plugins you install.*
