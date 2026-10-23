#!/usr/bin/env python3
"""Prepare pinned build tools in writable cloud directories; never change app files."""
import hashlib
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import urllib.parse
import urllib.request
import zipfile

ROOT = Path("/workspace")
JDK = ROOT / "toolchains/jdk-21.0.13+9"
SDK = ROOT / "android-sdk"

def download(url, destination, algorithm, expected):
    with urllib.request.urlopen(url, timeout=120) as source, open(destination, "wb") as output:
        while block := source.read(1024 * 1024):
            output.write(block)
    digest = hashlib.new(algorithm, Path(destination).read_bytes()).hexdigest()
    if digest != expected:
        raise RuntimeError("Build tool checksum mismatch: " + str(destination))

with tempfile.TemporaryDirectory(prefix="atmos-setup-") as temp:
    if not (JDK / "bin/jlink").exists():
        archive = Path(temp) / "jdk.tar.gz"
        download("https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz", archive, "sha256", "d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c")
        with tarfile.open(archive) as source:
            source.extractall(ROOT / "toolchains", filter="data")
    if not (SDK / "cmdline-tools/latest/bin/sdkmanager").exists():
        archive = Path(temp) / "sdk.zip"
        download("https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip", archive, "sha1", "d313adb7aedccf6cf0cfca51ec180f0059f5f8f8")
        destination = SDK / "cmdline-tools"
        destination.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as source:
            source.extractall(destination)
        (destination / "cmdline-tools").rename(destination / "latest")
        for executable in (destination / "latest/bin").iterdir():
            executable.chmod(0o755)

environment = os.environ.copy()
environment["JAVA_HOME"] = str(JDK)
environment["ANDROID_HOME"] = str(SDK)
environment["ANDROID_USER_HOME"] = str(ROOT / ".android-atmos")
environment["PATH"] = str(JDK / "bin") + os.pathsep + environment["PATH"]
Path(environment["ANDROID_USER_HOME"]).mkdir(exist_ok=True)
proxy = urllib.parse.urlsplit(environment.get("HTTPS_PROXY", ""))
arguments = []
if proxy.hostname:
    arguments = ["--proxy=http", "--proxy_host=" + proxy.hostname, "--proxy_port=" + str(proxy.port or 8080)]
# Use the managed machine's configured CA trust with the downloaded JDK.
environment["JAVA_TOOL_OPTIONS"] = environment.get("JAVA_TOOL_OPTIONS", "") + " -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
subprocess.run([str(SDK / "cmdline-tools/latest/bin/sdkmanager"), "--sdk_root=" + str(SDK), *arguments,
                "platforms;android-35", "build-tools;34.0.0", "build-tools;35.0.0", "platform-tools"],
               input="y\n" * 100, text=True, env=environment, check=True)
print("Android build tools prepared.")
