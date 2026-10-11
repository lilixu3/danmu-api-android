#!/usr/bin/env python3
"""Reinstall the locked JS dependency sources into a fresh archive for Android assets."""
from pathlib import Path
import shutil
import subprocess
import tempfile
from zipfile import ZipFile,ZIP_DEFLATED
root=Path(__file__).resolve().parents[1]
work=root/'build';work.mkdir(exist_ok=True)
stage=Path(tempfile.mkdtemp(prefix='node-dependencies-',dir=work))
for name in ('package.json','package-lock.json'):
    shutil.copy2(root/'runtime/node-dependencies'/name,stage/name)
subprocess.run(['npm','ci','--ignore-scripts','--no-audit','--no-fund'],cwd=stage,check=True)
modules=stage/'node_modules'
for path in modules.rglob('*'):
    if path.is_file() and path.suffix in ('.node','.so','.dll','.exe'):
        raise SystemExit('Unexpected prebuilt native dependency: '+str(path))
with ZipFile(root/'node_modules.zip','w',ZIP_DEFLATED) as archive:
    for path in sorted(modules.rglob('*')):
        if path.is_file() and not path.is_symlink():
            archive.write(path,path.relative_to(stage).as_posix())
subprocess.run([str(root/'gradlew'),':app:refreshBundledNodeModulesFromArchive','--no-daemon','--console=plain'],cwd=root,check=True)
print('Runtime JS sources installed from the locked fresh npm tree:',stage)
