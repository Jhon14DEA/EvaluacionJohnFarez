"""Resolve and validate the JDK used by the verification launchers."""
from pathlib import Path
import os
import re
import subprocess


def java25():
    home = os.environ.get("JAVA_HOME")
    if not home:
        raise RuntimeError("Define JAVA_HOME con la ruta de un JDK 25 antes de ejecutar las pruebas.")
    binary = Path(home) / "bin" / ("java.exe" if os.name == "nt" else "java")
    compiler = binary.with_name("javac.exe" if os.name == "nt" else "javac")
    if not binary.is_file() or not compiler.is_file():
        raise RuntimeError(f"JAVA_HOME no apunta a un JDK completo: {home}")
    result = subprocess.run([str(binary), "-version"], capture_output=True, text=True, timeout=15)
    version = re.search(r'version "(\d+)', result.stdout + result.stderr)
    if result.returncode != 0 or not version or version.group(1) != "25":
        raise RuntimeError("Se requiere JDK 25. Revisa JAVA_HOME; no se ha iniciado ninguna aplicacion.")
    return str(binary)


def isolated_database():
    database = os.environ.get("TEST_DB_NAME", "").strip()
    if not re.fullmatch(r"[A-Za-z0-9_]+", database) or database.casefold() in {
        "evaluacion_john_farez", "master", "model", "msdb", "tempdb"
    }:
        raise RuntimeError("TEST_DB_NAME debe identificar una base aislada existente, nunca la principal.")
    return database
