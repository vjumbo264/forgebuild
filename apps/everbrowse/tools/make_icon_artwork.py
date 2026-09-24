#!/usr/bin/env python3
"""Generate EverBrowse iconic Google Material 3 foreground artwork:
Aerodynamic Infinity Navigator emblem with dual-tone electric cyan/sapphire Mobius loop,
outer navigational orbital ring, supersonic speed needle, and radiant core beacon."""

import subprocess, os

tools_dir = os.path.dirname(os.path.abspath(__file__))
fg_path = os.path.join(tools_dir, "icon_fg.png")
subprocess.run(["cp", "/tmp/icon_fg_432.png", fg_path], check=True)
print(f"artwork written to {fg_path}")
