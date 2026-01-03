#!/usr/bin/env python3
import click
import requests
import os
import time
import json
import hashlib
from typing import List, Optional, Dict, Any
from rich.console import Console
from rich.table import Table
from rich.progress import Progress, BarColumn, DownloadColumn, TransferSpeedColumn, TextColumn

console = Console()

class ModrinthAPI:
    BASE_URL = "https://api.modrinth.com/v2"
    HEADERS = {"User-Agent": "apt-mc/1.0 (parody-cli)"}

    @staticmethod
    def search(query: str, limit: int = 10) -> List[Dict[str, Any]]:
        facets = '[["project_type:plugin"], ["categories:spigot", "categories:paper", "categories:purpur", "categories:bukkit"]]'
        response = requests.get(
            f"{ModrinthAPI.BASE_URL}/search",
            params={"query": query, "facets": facets, "limit": limit},
            headers=ModrinthAPI.HEADERS
        )
        response.raise_for_status()
        return response.json().get("hits", [])

    @staticmethod
    def get_project(project_id_or_slug: str) -> Optional[Dict[str, Any]]:
        response = requests.get(
            f"{ModrinthAPI.BASE_URL}/project/{project_id_or_slug}",
            headers=ModrinthAPI.HEADERS
        )
        if response.status_code == 404:
            return None
        response.raise_for_status()
        return response.json()

    @staticmethod
    def get_versions(project_id: str, loaders: List[str]) -> List[Dict[str, Any]]:
        res = requests.get(
            f"{ModrinthAPI.BASE_URL}/project/{project_id}/version",
            params={"loaders": json.dumps(loaders)},
            headers=ModrinthAPI.HEADERS
        )
        res.raise_for_status()
        return res.json()

    @staticmethod
    def get_versions_by_hashes(hashes: List[str]) -> Dict[str, Any]:
        if not hashes:
            return {}
        res = requests.post(
            f"{ModrinthAPI.BASE_URL}/version_files",
            json={"hashes": hashes, "algorithm": "sha1"},
            headers=ModrinthAPI.HEADERS
        )
        res.raise_for_status()
        return res.json()

    @staticmethod
    def get_members(project_id_or_slug: str) -> List[Dict[str, Any]]:
        res = requests.get(
            f"{ModrinthAPI.BASE_URL}/project/{project_id_or_slug}/members",
            headers=ModrinthAPI.HEADERS
        )
        res.raise_for_status()
        return res.json()

class PackageManager:
    def __init__(self, plugins_dir: str = "plugins"):
        self.plugins_dir = plugins_dir

    def ensure_dir(self):
        if not os.path.exists(self.plugins_dir):
            os.makedirs(self.plugins_dir)

    def download_file(self, url: str, filename: str, size: int):
        dest_path = os.path.join(self.plugins_dir, filename)
        with requests.get(url, stream=True, headers=ModrinthAPI.HEADERS) as r:
            r.raise_for_status()
            with Progress(
                TextColumn("[bold blue]{task.fields[filename]}", justify="right"),
                BarColumn(bar_width=None),
                "[progress.percentage]{task.percentage:>3.1f}%",
                "•", DownloadColumn(), "•", TransferSpeedColumn(), "•",
                TextColumn("[green]Done[/green]"),
                console=console
            ) as progress:
                task = progress.add_task("download", filename=filename, total=size)
                with open(dest_path, "wb") as f:
                    for chunk in r.iter_content(chunk_size=8192):
                        f.write(chunk)
                        progress.update(task, advance=len(chunk))

    def calculate_sha1(self, filepath: str) -> str:
        sha1 = hashlib.sha1()
        with open(filepath, 'rb') as f:
            while True:
                data = f.read(65536)
                if not data:
                    break
                sha1.update(data)
        return sha1.hexdigest()

    def get_installed_plugins(self) -> Dict[str, str]:
        """Returns a dict of filename -> sha1"""
        if not os.path.exists(self.plugins_dir):
            return {}
        
        plugins = {}
        for f in os.listdir(self.plugins_dir):
            if f.endswith(".jar"):
                full_path = os.path.join(self.plugins_dir, f)
                plugins[f] = self.calculate_sha1(full_path)
        return plugins

@click.group()
def cli():
    """apt-mc: The Advanced Packaging Tool for Minecraft Servers."""
    pass

@cli.command()

@click.argument("package")

def info(package):

    """Show details about a package."""

    try:

        project = ModrinthAPI.get_project(package)

        if not project:

            console.print(f"[red]E: Unable to locate package {package}[/red]")

            return



        # Check installation status

        pm = PackageManager()

        installed_hashes = list(pm.get_installed_plugins().values())

        status = "[red]Not Installed[/red]"

        

        if installed_hashes:

            try:

                # This might be heavy if many plugins, but accurate

                installed_versions = ModrinthAPI.get_versions_by_hashes(installed_hashes)

                for v in installed_versions.values():

                    if v['project_id'] == project['id']:

                        status = "[green]Installed[/green]"

                        break

            except:

                pass



        # Get Dependencies from latest version

        dependencies = "None"

        try:

            versions = ModrinthAPI.get_versions(project['id'], ["spigot", "paper", "purpur", "bukkit"])

            if versions:

                latest = versions[0]

                deps = latest.get("dependencies", [])

                req_deps = [d['project_id'] for d in deps if d.get("dependency_type") == "required" and d.get("project_id")]

                

                if req_deps:

                    dep_names = []

                    for dep_id in req_deps:

                        # Best effort name resolution

                        try:

                            dep_proj = ModrinthAPI.get_project(dep_id)

                            if dep_proj:

                                dep_names.append(dep_proj['slug'])

                            else:

                                dep_names.append(dep_id)

                        except:

                            dep_names.append(dep_id)

                    dependencies = ", ".join(dep_names)

        except:

            pass

        

        author = "Unknown"

        try:

            members = ModrinthAPI.get_members(project['slug'])

            # Find owner or first member

            owner = next((m for m in members if m.get("role") == "Owner"), members[0] if members else None)

            if owner:

                author = owner.get("user", {}).get("username", "Unknown")

        except:

            pass

            

        console.print(f"[bold white]Package:[/bold white] {project['slug']}")

        console.print(f"[bold white]ID:[/bold white] {project['id']}")

        console.print(f"[bold white]Status:[/bold white] {status}")

        console.print(f"[bold white]Author:[/bold white] {author}")

        console.print(f"[bold white]Description:[/bold white] {project['description']}")

        console.print(f"[bold white]License:[/bold white] {project['license']['name'] if project.get('license') else 'Unknown'}")

        console.print(f"[bold white]Categories:[/bold white] {', '.join(project['categories'])}")

        console.print(f"[bold white]Downloads:[/bold white] {project['downloads']}")

        console.print(f"[bold white]Dependencies:[/bold white] {dependencies}")

        console.print(f"[bold white]Website:[/bold white] {project.get('wiki_url') or project.get('source_url') or project.get('discord_url') or 'N/A'}")

        

    except Exception as e:

        console.print(f"[red]E: Failed to fetch info: {e}[/red]")



    





@cli.command("list")

@click.option("--installed", is_flag=True, default=True, help="List installed packages (default).")

def list_packages(installed):

    """List installed packages."""

    # Currently only supports installed

    

    pm = PackageManager()

    installed_plugins = pm.get_installed_plugins()

    

    if not installed_plugins:

        console.print("No plugins installed.")

        return



    console.print("Listing... [green]Done[/green]")

    

    hashes = list(installed_plugins.values())
    try:
        versions_map = ModrinthAPI.get_versions_by_hashes(hashes)
    except Exception as e:
        console.print(f"[red]E: Failed to resolve versions: {e}[/red]")
        return
        
    sha1_to_filename = {v: k for k, v in installed_plugins.items()}
    
    table = Table(box=None, show_header=False, padding=(0, 2))
    table.add_column("Package", style="green")
    table.add_column("Version")
    table.add_column("Status")

    

    for file_sha1, filename in sha1_to_filename.items():

        # Modrinth returns keyed by hash

        version_info = versions_map.get(file_sha1)

        

        if version_info:

            # We need project slug. Version object has project_id.

            # Ideally we resolve project_id -> slug.

            # But that is N requests.

            # For 'apt list', we accept just the filename or project_id if needed.

            # But wait, Modrinth version object might not have project slug.

            # It has 'project_id'.

            # To be fast, we might display project_id or just filename.

            # Let's verify what version object has.

            pkg_name = version_info['project_id'] # Fallback

            ver_num = version_info['version_number']

        else:

            pkg_name = filename

            ver_num = "unknown"



        table.add_row(f"{pkg_name}", ver_num, "[installed]")



    console.print(table)



@cli.command()
def update():
    """Update list of available packages."""
    for i, loader in enumerate(["spigot", "paper", "purpur"], 1):
        with console.status(f"[bold white]Hit:{i} https://api.modrinth.com/v2/search {loader}[/bold white]"):
            time.sleep(0.3)
            console.print(f"Hit:{i} https://api.modrinth.com/v2/search {loader}")
    
    console.print("Reading package lists... [green]Done[/green]")
    console.print("Building dependency tree... [green]Done[/green]")
    console.print("Reading state information... [green]Done[/green]")
    console.print(f"\n{os.getcwd()} is up to date.")

@cli.command()
@click.argument("query")
def search(query):
    """Search for plugins."""
    console.print(f"Sorting... [green]Done[/green]")
    console.print(f"Full Text Search... [green]Done[/green]")

    try:
        hits = ModrinthAPI.search(query)
        if not hits:
            console.print(f"No plugins found for '{query}'.")
            return

        table = Table(show_header=True, header_style="bold magenta")
        table.add_column("Package Name", style="green")
        table.add_column("Description")
        table.add_column("Author", style="cyan")
        table.add_column("Downloads", justify="right")

        for hit in hits:
            desc = hit["description"][:50] + "..." if len(hit["description"]) > 50 else hit["description"]
            table.add_row(hit["slug"], desc, hit["author"], str(hit["downloads"]))
        
        console.print(table)
    except Exception as e:
        console.print(f"[red]E: Failed to search: {e}[/red]")

@cli.command()
@click.argument("packages", nargs=-1)
def install(packages):
    """Install plugins."""
    if not packages:
        console.print("[red]E: No packages specified.[/red]")
        return

    pm = PackageManager()
    pm.ensure_dir()
    
    console.print("Reading package lists... [green]Done[/green]")
    console.print("Building dependency tree... [green]Done[/green]")
    
    to_install = []
    for pkg_slug in packages:
        console.print(f"Check {pkg_slug}...")
        project = ModrinthAPI.get_project(pkg_slug)
        if project:
            to_install.append(project)
        else:
            console.print(f"[red]E: Unable to locate package {pkg_slug}[/red]")

    if not to_install:
        return

    console.print(f"\nThe following NEW packages will be installed:")
    for pkg in to_install:
        console.print(f"  {pkg['slug']}")
    
    console.print(f"\n0 upgraded, {len(to_install)} newly installed, 0 to remove and 0 not upgraded.")
    
    for pkg in to_install:
        try:
            versions = ModrinthAPI.get_versions(pkg['id'], ["spigot", "paper", "purpur", "bukkit"])
            if not versions:
                console.print(f"[red]E: No compatible versions for {pkg['slug']}[/red]")
                continue
            
            latest = versions[0]
            primary_file = next((f for f in latest["files"] if f.get("primary")), latest["files"][0])
            pm.download_file(primary_file["url"], primary_file["filename"], primary_file["size"])
        except Exception as e:
            console.print(f"[red]E: Failed to install {pkg['slug']}: {e}[/red]")

@cli.command()
def upgrade():
    """Upgrade installed plugins."""
    console.print("Reading package lists... [green]Done[/green]")
    console.print("Building dependency tree... [green]Done[/green]")
    console.print("Reading state information... [green]Done[/green]")
    console.print("Calculating upgrades... ", end="")

    pm = PackageManager()
    installed = pm.get_installed_plugins()
    
    if not installed:
        console.print("[green]Done[/green]")
        console.print("0 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.")
        return

    # Bulk lookup hashes
    hashes = list(installed.values())
    try:
        versions_map = ModrinthAPI.get_versions_by_hashes(hashes)
    except Exception as e:
        console.print(f"[red]E: Failed to check for updates: {e}[/red]")
        return

    updates = []
    
    # Map sha1 -> filename
    sha1_to_filename = {v: k for k, v in installed.items()}

    for file_sha1, version_info in versions_map.items():
        if not version_info:
            continue
            
        project_id = version_info['project_id']
        current_version_id = version_info['id']
        
        # Check for latest version of this project
        try:
            # We assume users want spigot/paper plugins
            available_versions = ModrinthAPI.get_versions(project_id, ["spigot", "paper", "purpur", "bukkit"])
            if not available_versions:
                continue
                
            latest = available_versions[0]
            
            if latest['id'] != current_version_id:
                filename = sha1_to_filename.get(file_sha1, "Unknown")
                updates.append({
                    "filename": filename,
                    "project_id": project_id,
                    "current_version": version_info['version_number'],
                    "new_version": latest['version_number'],
                    "latest_obj": latest
                })
        except Exception:
            continue

    console.print("[green]Done[/green]")

    if not updates:
        console.print("0 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.")
        return

    console.print(f"\nThe following packages will be upgraded:")
    for up in updates:
        console.print(f"  {up['filename']} ({up['current_version']} -> {up['new_version']})")

    console.print(f"\n{len(updates)} upgraded, 0 newly installed, 0 to remove and 0 not upgraded.")
    
    # In a real apt parody, we might prompt. For now, let's just do it or require -y. 
    # But for a tool, auto-upgrading on 'upgrade' command is standard behavior for 'apt-get upgrade' (with -y) or prompted.
    # We will simulate the prompt.
    
    if not click.confirm("Do you want to continue?", default=True):
        console.print("Abort.")
        return

    for up in updates:
        latest = up['latest_obj']
        files = latest.get("files", [])
        if not files:
            continue
            
        primary_file = next((f for f in files if f.get("primary")), files[0])
        
        # Remove old file
        old_path = os.path.join(pm.plugins_dir, up['filename'])
        if os.path.exists(old_path):
            os.remove(old_path)
            
        # Download new
        try:
            pm.download_file(primary_file["url"], primary_file["filename"], primary_file["size"])
        except Exception as e:
             console.print(f"[red]E: Failed to upgrade {up['filename']}: {e}[/red]")

@cli.command()
@click.argument("package")
def remove(package):
    """Remove a plugin."""
    console.print(f"Reading package lists... [green]Done[/green]")
    console.print(f"Building dependency tree... [green]Done[/green]")
    
    pm = PackageManager()
    if not os.path.exists(pm.plugins_dir):
        console.print(f"[red]E: Unable to locate package {package}[/red]")
        return
        
    candidates = [f for f in os.listdir(pm.plugins_dir) if f.endswith(".jar") and package.lower() in f.lower()]
    if not candidates:
        console.print(f"[red]E: Unable to locate package {package}[/red]")
        return
        
    if len(candidates) > 1:
        console.print(f"[red]E: Multiple candidates found for {package}. Be more specific.[/red]")
        return
        
    target = candidates[0]
    os.remove(os.path.join(pm.plugins_dir, target))
    console.print(f"Removing {package} ({target})...")

if __name__ == "__main__":
    cli()
