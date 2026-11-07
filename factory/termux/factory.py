#!/usr/bin/env python3
"""ModulaFactory — menú para la fábrica Modula (Termux).

Reconstruido a partir del script original de Jorge (ModulaFactory v3.0,
2026-11-06). Lógica preservada; un bug corregido:
  - reset_factory borraba `build_lite`, pero el build usa `build_cpu`.
    Ahora borra `build_cpu` y `MOTORES_LISTOS` (los dos directorios reales).

Uso en Termux:
    python factory.py
Opciones: 1 setup · 2 prepare · 3 forjar · 4 reset · 5 pipeline completo.
"""
import os
import shutil
import subprocess
import sys

BASE_DIR = os.environ.get("BASE_DIR", os.path.expanduser("~/modula"))
SCRIPTS = os.path.join(os.path.dirname(os.path.abspath(__file__)))


def run(cmd, cwd=None):
    print(f"\n$ {' '.join(cmd)}")
    r = subprocess.run(cmd, cwd=cwd or BASE_DIR)
    if r.returncode != 0:
        print(f"❌ Falló con código {r.returncode}: {' '.join(cmd)}")
        sys.exit(r.returncode)


def run_step(step):
    if step == "setup":
        run(["bash", os.path.join(SCRIPTS, "setup_factory.sh")])
    elif step == "prepare":
        run(["bash", os.path.join(SCRIPTS, "prepare_modula.sh")])
    elif step == "build":
        # El build siempre prepara primero (inyección del wrapper).
        run_step("prepare")
        run(["bash", os.path.join(SCRIPTS, "build_cpu.sh")])
    elif step == "reset":
        # CORREGIDO: el build usa build_cpu, no build_lite.
        for d in ("build_cpu", "MOTORES_LISTOS"):
            p = os.path.join(BASE_DIR, d)
            if os.path.exists(p):
                shutil.rmtree(p)
                print(f"🗑️  Eliminado: {p}")
        print("✅ Reset completo.")
    else:
        print(f"Paso desconocido: {step}")


def menu():
    print("=" * 50)
    print("  MODULA FACTORY v3.0 — Forja Modula V11 (Termux)")
    print("=" * 50)
    print("  1. Setup (herramientas + NDK + llama.cpp)")
    print("  2. Prepare (inyectar wrapper en llama.cpp)")
    print("  3. Forjar (compilar libmodula_engine.so)")
    print("  4. Reset (borrar build_cpu y MOTORES_LISTOS)")
    print("  5. Pipeline completo (setup + prepare + build)")
    print("  0. Salir")
    while True:
        choice = input("\nElegí una opción: ").strip()
        if choice == "1":
            run_step("setup")
        elif choice == "2":
            run_step("prepare")
        elif choice == "3":
            run_step("build")
        elif choice == "4":
            run_step("reset")
        elif choice == "5":
            run_step("setup")
            run_step("build")
        elif choice == "0":
            print("Chau.")
            break
        else:
            print("Opción inválida.")


if __name__ == "__main__":
    menu()
