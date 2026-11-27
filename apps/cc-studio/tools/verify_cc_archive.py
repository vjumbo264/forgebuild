#!/usr/bin/env python3
"""Verify that the native ZIP produced exactly the entries accepted by the API 1 validator."""
import sys
from zipfile import ZipFile

with ZipFile(sys.argv[1]) as native, ZipFile(sys.argv[2]) as validated:
    names = native.namelist()
    assert len(names) == len(set(names)) and names[0] == 'manifest.json'
    assert set(names) == set(validated.namelist())
    assert native.testzip() is None
    for name in names:
        assert native.read(name) == validated.read(name), name
    print(f'Native archive: {len(names)} validator-identical entries')
