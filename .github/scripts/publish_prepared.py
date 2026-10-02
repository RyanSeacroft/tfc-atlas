"""Check and publish the release files prepared for this Minecraft branch."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


def command(*args, check=True):
    return subprocess.run(args, check=check, text=True, capture_output=True)


def git(*args):
    return command("git", *args).stdout.strip()


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_files():
    manifest = json.loads(Path("releases/prepared.json").read_text())
    properties = dict(
        line.split("=", 1)
        for line in Path("gradle.properties").read_text().splitlines()
        if "=" in line and not line.lstrip().startswith("#")
    )
    version = properties["mod_version"]
    minecraft = properties["minecraft_version"]
    expected_tag = f"v{version}-mc{minecraft}"
    if manifest["tag"] != expected_tag:
        raise RuntimeError("These prepared files belong to another version. Use Draft release to build a new release.")
    branch = os.environ.get("ATLAS_BRANCH") or git("branch", "--show-current")
    if manifest["branch"] != branch:
        raise RuntimeError("The selected Minecraft branch does not match the prepared release.")
    folder = Path("releases") / f"{version}-mc{minecraft}"
    for name, expected_hash in manifest["assets"].items():
        if Path(name).name != name or sha256(folder / name) != expected_hash:
            raise RuntimeError(f"Release file checksum failed: {name}")
    archive_path = folder / manifest["source"]
    with zipfile.ZipFile(archive_path) as archive:
        tracked = git("ls-files", "-z").split("\0")
        project_files = {name for name in tracked if name and not name.startswith("releases/")}
        expected_names = project_files | {"releases/" + manifest["jar"]}
        if set(archive.namelist()) != expected_names:
            raise RuntimeError("The source ZIP does not contain the complete project.")
        for name in project_files:
            if archive.read(name) != Path(name).read_bytes():
                raise RuntimeError(f"Source changed since this release was prepared: {name}. Use Draft release for a new build.")
        if archive.read("releases/" + manifest["jar"]) != (folder / manifest["jar"]).read_bytes():
            raise RuntimeError("The source ZIP contains a different JAR.")
    print(f"Verified {manifest['title']}", flush=True)
    return manifest, folder


def publish(manifest, folder):
    tag = manifest["tag"]
    commit = git("rev-parse", "HEAD")
    remote_tag = command("git", "ls-remote", "--exit-code", "--tags", "origin", f"refs/tags/{tag}", check=False)
    if remote_tag.returncode == 0:
        command("git", "fetch", "--no-tags", "origin", f"refs/tags/{tag}")
        if git("rev-parse", "FETCH_HEAD^{commit}") != commit:
            raise RuntimeError("This release tag already points to another commit. It will not be moved.")
    elif remote_tag.returncode != 2:
        raise RuntimeError(remote_tag.stderr or "Cannot check the remote release tag.")

    existing = command("gh", "release", "view", tag, "--json", "assets,isDraft,url", check=False)
    if existing.returncode == 0:
        release = json.loads(existing.stdout)
        names = {asset["name"] for asset in release["assets"]}
        missing = []
        with tempfile.TemporaryDirectory() as temporary:
            for name, expected_hash in manifest["assets"].items():
                if name not in names:
                    missing.append(str(folder / name))
                    continue
                command("gh", "release", "download", tag, "--pattern", name, "--dir", temporary)
                if sha256(Path(temporary) / name) != expected_hash:
                    raise RuntimeError(f"An existing release has different bytes for {name}. It will not be replaced.")
        if missing:
            command("gh", "release", "upload", tag, *missing)
        state = "draft" if release["isDraft"] else "published"
        print(f"The {state} release already has the prepared files: {release['url']}")
        return

    assets = [str(folder / name) for name in manifest["assets"]]
    result = command(
        "gh", "release", "create", tag, *assets,
        "--target", commit,
        "--title", manifest["title"],
        "--notes-file", str(folder / "NOTES.md"),
        "--latest=" + str(manifest["latest"]).lower(),
    )
    print(result.stdout.strip())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check local files without contacting GitHub.")
    args = parser.parse_args()
    manifest, folder = verify_files()
    if not args.check:
        publish(manifest, folder)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        detail = error.stderr if isinstance(error, subprocess.CalledProcessError) else str(error)
        raise SystemExit(detail)
