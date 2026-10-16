#!/usr/bin/env python3
"""Audit APK ELF/ZIP alignment and the pinned Node runtime, without extracting it."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


def elf_segments(data, page_size):
    if len(data) < 64 or data[:4] != b'\x7fELF' or data[4] not in (1, 2) or data[5] != 1:
        raise ValueError('expected a little-endian ELF shared library')
    is64 = data[4] == 2
    phoff = struct.unpack_from('<Q' if is64 else '<I', data, 32 if is64 else 28)[0]
    size, count = struct.unpack_from('<HH', data, 54 if is64 else 42)
    if size < (56 if is64 else 32) or not count or phoff + size * count > len(data):
        raise ValueError('invalid ELF program header table')
    loads, relro, writable, protections = [], [], [], []
    for index in range(count):
        fields = struct.unpack_from('<IIQQQQQQ' if is64 else '<IIIIIIII', data, phoff + size * index)
        if is64:
            kind, flags, offset, address, _, _, memory, alignment = fields
        else:
            kind, offset, address, _, _, memory, flags, alignment = fields
        if kind == 1:
            if alignment < page_size or offset % page_size != address % page_size:
                raise ValueError(f'PT_LOAD does not meet {page_size} byte alignment')
            loads.append(hex(alignment))
            if flags & 2:
                writable.append((address, address + memory))
        elif kind == 0x6474e552:
            relro.append(hex(address + memory))
            protections.append((address, address + memory))
    if not loads:
        raise ValueError('missing PT_LOAD')
    # Bionic rounds RELRO down/up to page boundaries. Prebuilt AndroidX
    # libraries may put unused space up to the next page; that is valid as
    # long as rounding does not make any other writable data read-only.
    for start, end in protections:
        lower, upper = start // page_size * page_size, (end + page_size - 1) // page_size * page_size
        for begin, finish in writable:
            if max(begin, lower) < min(finish, start) or max(begin, end) < min(finish, upper):
                raise ValueError('rounded GNU_RELRO overlaps writable data outside RELRO')
    return {'loadAlignment': loads, 'relroEnd': relro, 'relroPaddingSafe': True}


def audit(apk, checksums, allow_pinned_x86_frpc=False, frpc_checksums=None):
    report = {'apk': str(apk), 'bytes': apk.stat().st_size,
              'sha256': hashlib.file_digest(apk.open('rb'), 'sha256').hexdigest(),
              'zipAlignment': 'passed', 'libraries': {}, 'compatibilityLimits': []}
    abis = set()
    with zipfile.ZipFile(apk) as archive, apk.open('rb') as raw:
        for entry in archive.infolist():
            raw.seek(entry.header_offset)
            header = raw.read(30)
            if header[:4] != b'PK\x03\x04':
                raise ValueError(f'invalid local ZIP header: {entry.filename}')
            name_size, extra_size = struct.unpack_from('<HH', header, 26)
            offset = entry.header_offset + 30 + name_size + extra_size
            is_library = entry.filename.startswith('lib/') and entry.filename.endswith('.so')
            if entry.compress_type == zipfile.ZIP_STORED and not entry.is_dir():
                required = 16384 if is_library else 4
                if offset % required:
                    raise ValueError(f'{entry.filename}: ZIP data offset is not {required} byte aligned')
            if not is_library:
                continue
            if entry.filename.endswith(('/libdanmu_outbound.so', '/libmihomo.so', '/libmihomo_launcher.so')):
                raise ValueError('removed outbound helper is still packaged')
            _, abi, name = entry.filename.split('/', 2)
            abis.add(abi)
            data = archive.read(entry)
            digest = hashlib.sha256(data).hexdigest()
            expected = checksums.get(f'libnode/bin/{abi}/{name}')
            if name == 'libnode.so' and (not expected or digest != expected):
                raise ValueError(f'{entry.filename}: pinned libnode SHA-256 mismatch')
            page_size = 16384 if abi in ('arm64-v8a', 'x86_64') else 4096
            # The official static Linux/amd64 frpc is executed after extraction.
            # Keep strict 16 KB validation as the default. A release targeting
            # normal x86_64 kernels can explicitly retain this pinned executable,
            # while reporting its lack of experimental 16 KB emulation support.
            pinned_x86_frpc = (allow_pinned_x86_frpc and abi == 'x86_64'
                               and name == 'libfrpc.so' and frpc_checksums
                               and digest == frpc_checksums.get(f'{abi}/{name}'))
            if pinned_x86_frpc:
                if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01':
                    raise ValueError('pinned x86_64 frpc must be a 64-bit little-endian ELF')
                if struct.unpack_from('<HH', data, 16) != (2, 62):
                    raise ValueError('pinned x86_64 frpc must be an x86_64 ET_EXEC executable')
                phoff = struct.unpack_from('<Q', data, 32)[0]
                phsize, phcount = struct.unpack_from('<HH', data, 54)
                if phsize < 56 or not phcount or phoff + phsize * phcount > len(data):
                    raise ValueError('invalid pinned frpc program header table')
                if any(struct.unpack_from('<I', data, phoff + i * phsize)[0] in (2, 3)
                       for i in range(phcount)):
                    raise ValueError('pinned x86_64 frpc must have no dynamic loader or dependencies')
                page_size = 4096
                report['compatibilityLimits'].append(
                    'Pinned static x86_64 frpc supports 4 KB kernels; '
                    'experimental x86_64 16 KB page-size emulation is not supported.')
            try:
                segments = elf_segments(data, page_size)
            except ValueError as error:
                raise ValueError(f'{entry.filename}: {error}') from error
            report['libraries'][entry.filename] = {
                'bytes': len(data), 'sha256': digest, **segments,
                'checkedPageSize': page_size,
                'packaging': 'stored' if entry.compress_type == zipfile.ZIP_STORED else 'compressed/extracted',
                'zipDataOffset': offset,
            }
    if not abis:
        raise ValueError('APK contains no native libraries')
    for abi in abis:
        for name in ('libnode.so', 'libnative-lib.so', 'libc++_shared.so'):
            if f'lib/{abi}/{name}' not in report['libraries']:
                raise ValueError(f'{abi}: required library missing: {name}')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--checksums', type=Path,
                        default=Path(__file__).resolve().parent.parent / 'app/native-runtime.sha256')
    parser.add_argument('--allow-pinned-x86-frpc', action='store_true',
                        help='accept only the pinned static x86_64 frpc at 4 KB and report its limitation')
    args = parser.parse_args()
    checksums = {}
    for line in args.checksums.read_text().splitlines():
        if line.strip() and not line.startswith('#'):
            digest, name = line.split(None, 1)
            checksums[name.strip()] = digest
    try:
        frpc_checksums = {}
        if args.allow_pinned_x86_frpc:
            manifest = Path(__file__).resolve().parent.parent / 'runtime/frp/SHA256SUMS'
            for line in manifest.read_text().splitlines():
                if line.strip() and not line.startswith('#'):
                    digest, name = line.split(None, 1)
                    frpc_checksums[name.strip()] = digest
        print(json.dumps(audit(args.apk, checksums, args.allow_pinned_x86_frpc, frpc_checksums), indent=2))
    except (ValueError, OSError, struct.error, zipfile.BadZipFile) as error:
        parser.exit(1, f'APK_NATIVE_AUDIT_FAILED: {error}\n')


if __name__ == '__main__':
    main()
