"""Build with Plugin Hub v5's unmodified init script, then inspect and smoke-test its JAR."""
import os
from pathlib import Path
import subprocess
import urllib.request

from validate_release import validate_release


def main():
    root = Path(__file__).resolve().parents[1]
    metadata = dict(line.split("=", 1) for line in
                    (root / "runelite-plugin.properties").read_text().splitlines() if "=" in line)
    if metadata.get("build") != "gradle":
        raise ValueError("Hub must retain the custom resource generation build (build=gradle)")
    tooling = root / ".gradle/hub-tooling"
    tooling.mkdir(parents=True, exist_ok=True)
    if not all((tooling / name).is_file() for name in ("target_init.gradle", "apirecorder.jar")):
        bundle = tooling / "bundle.tar.zst"
        urllib.request.urlretrieve(
            "https://github.com/runelite/plugin-hub-tooling/releases/download/v5/bundle.tar.zst", bundle)
        subprocess.run(["tar", "-xf", str(bundle), "-C", str(tooling),
                        "target_init.gradle", "apirecorder.jar"], check=True)
        bundle.unlink()
    with urllib.request.urlopen(
            "https://raw.githubusercontent.com/runelite/plugin-hub/master/runelite.version") as response:
        version = response.read().decode().strip()
    output = root / "build/plugin-hub"
    output.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env.update({"runelite.pluginhub.package.apirecorder": str(tooling / "apirecorder.jar"),
                "runelite.pluginhub.package.buildDir": str(output),
                "runelite.pluginhub.package.runeliteVersion": version})
    gradle = [str(root / "gradlew.bat")] if os.name == "nt" else ["bash", str(root / "gradlew")]
    print(f"Plugin Hub v5 packaging against RuneLite {version}", flush=True)
    # These are the two tasks invoked by the Hub packager, not releaseJar.
    subprocess.run(gradle + ["--init-script", str(tooling / "target_init.gradle"),
                            "runelitePluginHubPackage", "runelitePluginHubManifest",
                            "--rerun-tasks", "--no-daemon", "--console=plain"],
                   cwd=root, env=env, check=True)
    artifact = output / "plugin.jar"
    count = validate_release(root, artifact)
    subprocess.run(gradle + ["smokeTestArtifact", f"-PpluginArtifact={artifact}",
                            f"-PruneLiteVersion={version}", "--no-daemon", "--console=plain"],
                   cwd=root, check=True)
    print(f"Validated Hub plugin.jar: {count} reviewed translations; artifact smoke passed")
    if count == 0:
        print("Publication still requires human translation review (issue #3).")


if __name__ == "__main__":
    main()
