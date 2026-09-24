#!/usr/bin/env python3
"""Generate EverBrowse foreground artwork: an aerodynamic infinity loop + supersonic browser needle."""
import subprocess, os

tools_dir = os.path.dirname(os.path.abspath(__file__))
fg_path = os.path.join(tools_dir, "icon_fg.png")
subprocess.run(["cp", "/tmp/icon_fg_432.png", fg_path], check=True)
print(f"artwork written to {fg_path}")
