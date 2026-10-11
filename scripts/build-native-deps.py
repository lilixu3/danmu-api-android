#!/usr/bin/env python3
"""Compile libc++, libc++abi and libunwind from pinned NDK r29 source, in a fresh build."""
from __future__ import annotations
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request

LLVM_COMMIT = '5e96669f06077099aa41290cdb4c5e6fa0f59349'
ANDROID_COMMIT = '1dab3288f660d43a6cb2479107e2b54b3ab0a2a1'
SOURCE_TREE = '76347840128522bf2342ff200e93aec037cd9660c8080cf82a3b8f3795258429'
PARTS = ('libcxx', 'libcxxabi', 'libunwind', 'runtimes', 'cmake', 'llvm/cmake', 'libc')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tree_digest(source):
    digest = hashlib.sha256()
    for p in sorted(source.rglob('*')):
        relative = p.relative_to(source)
        # Python/CMake checks create host-specific bytecode beside source files.
        # It is generated state, not part of the pinned LLVM source identity.
        if (not p.is_file() or '.git' in relative.parts or '__pycache__' in relative.parts
                or p.suffix in ('.pyc', '.pyo') or p.name == 'source-manifest.json'):
            continue
        digest.update(p.relative_to(source).as_posix().encode() + b'\0' + bytes.fromhex(sha(p)))
    return digest.hexdigest()


def prepare_source(cache):
    source = cache / 'source'
    if source.exists():
        if tree_digest(source) != SOURCE_TREE:
            raise SystemExit(f'Cached libc++ source differs from the pinned source tree: expected {SOURCE_TREE}, got {tree_digest(source)}')
        return source
    cache.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix='llvm-source-', dir=cache))
    def download(part):
        data = cache / (part.replace('/', '-') + '.tar.gz')
        if not data.exists():
            url = f'https://android.googlesource.com/toolchain/llvm-project/+archive/{LLVM_COMMIT}/{part}.tar.gz'
            with urllib.request.urlopen(url, timeout=240) as response:
                data.write_bytes(response.read())
        dest = staging / part
        dest.mkdir(parents=True, exist_ok=True)
        with tarfile.open(data) as archive:
            archive.extractall(dest, filter='data')
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(download, PARTS))
    android_archive = cache / 'llvm-android.tar.gz'
    if not android_archive.exists():
        with urllib.request.urlopen(f'https://android.googlesource.com/toolchain/llvm_android/+archive/{ANDROID_COMMIT}.tar.gz', timeout=240) as response:
            android_archive.write_bytes(response.read())
    android = cache / 'android-toolchain-source'
    android.mkdir(exist_ok=True)
    with tarfile.open(android_archive) as archive:
        archive.extractall(android, filter='data')
    patches = []
    for item in json.loads((android / 'patches/PATCHES.json').read_text()):
        version = item.get('version_range', {})
        if not ((version.get('from') or 0) <= 563880 < (version.get('until') or 10**9)):
            continue
        patch = android / 'patches' / item['rel_patch_path']
        files = re.findall(r'^diff --git a/(\S+) b/', patch.read_text(), re.M)
        if not any(f.startswith(tuple(p + '/' for p in PARTS[:-1])) for f in files):
            continue
        subprocess.run(['git', 'apply', '--whitespace=nowarn', '--exclude=libcxx/docs/*', '--exclude=libcxx/test/*', '--exclude=libcxxabi/test/*', '--exclude=libunwind/test/*', *['--include=' + p + '/*' for p in PARTS[:-1]], str(patch)], cwd=staging, check=True)
        patches.append({'file': item['rel_patch_path'], 'sha256': sha(patch)})
    if tree_digest(staging) != SOURCE_TREE:
        raise SystemExit(f'Downloaded libc++ source differs from the pinned tree: expected {SOURCE_TREE}, got {tree_digest(staging)}')
    (staging / 'source-manifest.json').write_text(json.dumps({'llvmCommit': LLVM_COMMIT, 'androidToolchainCommit': ANDROID_COMMIT, 'sourceTreeSha256': SOURCE_TREE, 'patches': patches}, indent=2) + '\n')
    staging.rename(source)
    return source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--abi', choices=('arm64-v8a', 'armeabi-v7a', 'x86_64'), required=True)
    parser.add_argument('--api', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--jobs', type=int, default=3)
    args = parser.parse_args()
    ndk = args.ndk.resolve()
    if '29.0.14206865' not in (ndk / 'source.properties').read_text():
        raise SystemExit('Native dependencies require the locked NDK r29')
    tag = 'linux-aarch64' if platform.machine() in ('aarch64', 'arm64') else 'linux-x86_64'
    toolchain = ndk / 'toolchains/llvm/prebuilt' / tag
    if not toolchain.is_dir():
        raise SystemExit('Matching native compiler host not found')
    source = args.source.resolve() if args.source else prepare_source(Path(os.environ.get('DANMU_LLVM_SOURCE_CACHE', str(Path.home() / '.cache/danmu-llvm-r29'))))
    if tree_digest(source) != SOURCE_TREE:
        raise SystemExit('libc++ source fingerprint mismatch')
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    work = Path(__file__).resolve().parents[1] / 'build/native-dependencies'
    work.mkdir(parents=True, exist_ok=True)
    build = Path(tempfile.mkdtemp(prefix=f'libcxx-{args.abi}-', dir=work))
    rules = build / 'android-rules.cmake'
    rules.write_text('set(CMAKE_PLATFORM_NO_VERSIONED_SONAME ON)\n')
    triple = {'arm64-v8a': 'aarch64-linux-android', 'armeabi-v7a': 'armv7a-linux-androideabi', 'x86_64': 'x86_64-linux-android'}[args.abi] + str(args.api)
    command = ['cmake', '-G', 'Unix Makefiles', '-S', str(source / 'runtimes'), '-B', str(build), '-C', str(source / 'libcxx/cmake/caches/AndroidNDK.cmake'),
        '-DCMAKE_SYSTEM_NAME=Android', '-DCMAKE_ANDROID_NDK=' + str(ndk), '-DCMAKE_ANDROID_ARCH_ABI=' + args.abi, '-DCMAKE_SYSTEM_VERSION=' + str(args.api), '-DCMAKE_ANDROID_STL_TYPE=none', '-DCMAKE_USER_MAKE_RULES_OVERRIDE=' + str(rules), '-DCMAKE_SYSTEM_PROCESSOR=' + {'arm64-v8a':'aarch64','armeabi-v7a':'arm','x86_64':'x86_64'}[args.abi],
        '-DCMAKE_C_COMPILER=' + str(toolchain / 'bin/clang'), '-DCMAKE_CXX_COMPILER=' + str(toolchain / 'bin/clang++'), '-DCMAKE_ASM_COMPILER=' + str(toolchain / 'bin/clang'),
        '-DCMAKE_C_COMPILER_TARGET=' + triple, '-DCMAKE_CXX_COMPILER_TARGET=' + triple, '-DCMAKE_ASM_COMPILER_TARGET=' + triple,
        '-DCMAKE_SYSROOT=' + str(toolchain / 'sysroot'), '-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY', '-DLIBCXX_HAS_PTHREAD_LIB=OFF', '-DLIBCXX_HAS_GCC_LIB=OFF', '-DLIBCXXABI_HAS_PTHREAD_LIB=OFF', '-DLIBCXXABI_HAS_GCC_LIB=OFF', '-DLIBUNWIND_HAS_PTHREAD_LIB=OFF', '-DLIBUNWIND_HAS_GCC_LIB=OFF', '-DCMAKE_BUILD_TYPE=Release', '-DANDROID_NATIVE_API_LEVEL=' + str(args.api),
        '-DLLVM_ENABLE_RUNTIMES=libunwind;libcxxabi;libcxx', '-DLLVM_INCLUDE_TESTS=OFF', '-DLIBCXX_INCLUDE_TESTS=OFF', '-DLIBCXXABI_INCLUDE_TESTS=OFF', '-DLIBUNWIND_INCLUDE_TESTS=OFF',
        '-DLIBCXX_INCLUDE_BENCHMARKS=OFF', '-DLIBCXX_ENABLE_STATIC=OFF', '-DLIBCXX_ENABLE_SHARED=ON', '-DLIBCXX_ENABLE_ABI_LINKER_SCRIPT=OFF',
        '-DLIBCXX_ENABLE_STATIC_ABI_LIBRARY=ON', '-DLIBCXXABI_ENABLE_SHARED=OFF', '-DLIBCXXABI_USE_LLVM_UNWINDER=ON',
        '-DLIBCXXABI_STATICALLY_LINK_UNWINDER_IN_STATIC_LIBRARY=ON', '-DLIBCXXABI_STATICALLY_LINK_UNWINDER_IN_SHARED_LIBRARY=ON',
        '-DLIBUNWIND_ENABLE_SHARED=OFF', '-DLIBUNWIND_ENABLE_STATIC=ON', '-DLIBCXX_ENABLE_TIME_ZONE_DATABASE=OFF',
        '-DLIBCXX_ABI_NAMESPACE=__ndk1', '-DLIBCXX_ABI_VERSION=1', '-DLIBCXX_LIBRARY_VERSION=', '-DCMAKE_PLATFORM_NO_VERSIONED_SONAME=ON',
        '-DCMAKE_CXX_FLAGS=-DHAS_THREAD_LOCAL -ffunction-sections -fdata-sections',
        '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-soname,libc++_shared.so -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,--gc-sections']
    started = datetime.now(timezone.utc).isoformat()
    subprocess.run(command, check=True)
    subprocess.run(['cmake', '--build', str(build), '--target', 'cxx_shared', '--parallel', str(args.jobs)], check=True)
    candidates = [p for p in build.rglob('libc++_shared.so*') if p.is_file() and p.read_bytes()[:4] == b'\x7fELF']
    if not candidates:
        raise SystemExit('Fresh source build did not produce libc++_shared.so')
    shutil.copyfile(candidates[0], output)
    subprocess.run([str(toolchain / 'bin/llvm-strip'), '--strip-unneeded', str(output)], check=True)
    metadata = {'kind':'source-built', 'abi':args.abi, 'androidApi':args.api, 'ndkVersion':'29.0.14206865', 'llvmCommit':LLVM_COMMIT, 'androidToolchainCommit':ANDROID_COMMIT, 'sourceTreeSha256':SOURCE_TREE, 'components':['libc++','libc++abi','libunwind'], 'startedAt':started, 'completedAt':datetime.now(timezone.utc).isoformat(), 'sha256':sha(output), 'cmakeCommand':command}
    output.with_name('libcxx-build.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(f'Source-built native dependencies: {args.abi} API {args.api} {metadata["sha256"]}')

if __name__ == '__main__':
    main()
