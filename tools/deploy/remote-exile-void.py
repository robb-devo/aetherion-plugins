#!/usr/bin/env python3
"""Exile into the void, smooth arrival, weapon tempo shown; server side. deploy-exile-void.ps1 uploads this next to
env.json, exilevoid.py and jars/ and calls one step at a time over ssh. Every swapped jar keeps a backup under
<ops>/backups/exile-void-<ts>/; the exile world keeps a full copy next to it (aetherion_exile.void2-backup-<ts>)."""
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

STAGE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(STAGE, 'env.json'), encoding='utf-8') as f:
    ENV = json.load(f)
SRV = ENV['servers']
BACKUP = ENV['ops'] + '/backups/exile-void-' + ENV['ts']
RCON = ENV['ops'] + '/ae-rcon.py'
BACKENDS = ['hub', 'mmor', 'mmod', 'mmoc', 'mmoi']

# Where each plugin lives (PROJECT_STATE / LIVE_STATE); a server only gets a jar it already has.
TARGETS = {
    'AetherionCore': ['hub', 'mmor', 'mmod', 'mmoc', 'mmoi'],
    'AetherionItems': ['mmor', 'mmod', 'mmoc', 'mmoi'],
    'AetherionQuests': ['mmor', 'mmod', 'mmoc', 'mmoi'],
}
OURS = '|'.join(TARGETS)
UUID = r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
START = r'Starting minecraft server version'


def die(msg):
    print('STOP: ' + msg)
    sys.exit(1)


def plain(text):
    return re.sub(chr(167) + '.', '', text or '').strip()


def rcon(alias, cmd):
    try:
        r = subprocess.run(['python3', RCON, alias, cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           universal_newlines=True, timeout=60)
        return plain(r.stdout)
    except subprocess.TimeoutExpired:
        return '(rcon timed out)'


def plugin_name(path):
    try:
        with zipfile.ZipFile(path) as z:
            names = z.namelist()
            for desc in ('paper-plugin.yml', 'plugin.yml'):
                if desc in names:
                    m = re.search(r'^name:\s*["\']?([^"\'\s]+)', z.read(desc).decode('utf-8', 'replace'), re.M)
                    if m:
                        return m.group(1)
    except (zipfile.BadZipFile, OSError):
        pass
    return None


def jars_of(alias, name):
    return [f for f in glob.glob(os.path.join(SRV[alias], 'plugins', '*.jar')) if plugin_name(f) == name]


def code_of(path):
    """CRC of every class in a jar: the same source builds the same classes, whatever the jar's timestamps."""
    try:
        with zipfile.ZipFile(path) as z:
            return {i.filename: i.CRC for i in z.infolist() if i.filename.endswith('.class')}
    except (zipfile.BadZipFile, OSError):
        return {}


def swap_in(src, dest):
    """Replace dest by a copy of src through a rename, so a running server keeps reading its open old file."""
    tmp = dest + '.exile-void.tmp'
    shutil.copyfile(src, tmp)
    st = os.stat(dest)
    os.chown(tmp, st.st_uid, st.st_gid)
    os.chmod(tmp, st.st_mode & 0o7777)
    os.replace(tmp, dest)


def sha1(path):
    h = hashlib.sha1()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def read(path):
    try:
        with open(path, encoding='utf-8', errors='replace') as f:
            return f.read()
    except OSError:
        return ''


def log_lines(alias):
    return read(os.path.join(SRV[alias], 'logs', 'latest.log')).splitlines()


def since_start(alias):
    """The log from the last server start on (mmo-r's log is not cut at a restart and repeats most lines)."""
    lines = log_lines(alias)
    for i in range(len(lines) - 1, -1, -1):
        if re.search(START, lines[i]):
            return lines[i:]
    return lines


def after_tag(line):
    return line[line.find(']: ') + 3:] if ']: ' in line else line


def show(lines, rx, prefix='  '):
    seen = set()
    for l in lines:
        t = after_tag(l)
        if re.search(rx, t) and t not in seen:
            seen.add(t)
            print(prefix + t[:400])
    return len(seen)


def running(d):
    d = os.path.realpath(d)
    for pid in os.listdir('/proc'):
        if not pid.isdigit():
            continue
        try:
            with open('/proc/%s/comm' % pid) as f:
                if 'java' not in f.read():
                    continue
            cwd = os.path.realpath('/proc/%s/cwd' % pid)
            with open('/proc/%s/cmdline' % pid, 'rb') as f:
                cmd = f.read().decode('utf-8', 'replace')
        except OSError:
            continue
        if cwd == d or d + '/' in cmd:
            return True
    return False


def stopped(alias, seconds=180):
    deadline = time.time() + seconds
    while running(SRV[alias]):
        if time.time() > deadline:
            return False
        time.sleep(2)
    return True


def chown_tree(top, uid, gid):
    os.lchown(top, uid, gid)
    for root, dirs, files in os.walk(top):
        for n in dirs + files:
            os.lchown(os.path.join(root, n), uid, gid)


# ---------------------------------------------------------------------------------------------------- steps

def check():
    for alias in BACKENDS:
        if not os.path.isdir(os.path.join(SRV[alias], 'plugins')):
            die('no plugins folder for ' + alias)
    if not os.path.isfile(RCON):
        die('missing ' + RCON)
    jars = sorted(glob.glob(os.path.join(STAGE, 'jars', '*.jar')))
    staged = [plugin_name(j) for j in jars]
    if sorted(n for n in staged if n) != sorted(TARGETS):
        die('staged jars are %s, expected %s' % (staged, sorted(TARGETS)))
    for name in sorted(TARGETS):
        for alias in TARGETS[name]:
            found = jars_of(alias, name)
            if len(found) > 1:
                die('%s has %d %s jars (%s): exactly one allowed, clean that up first'
                    % (alias, len(found), name, ', '.join(os.path.basename(f) for f in found)))
    print('server side ok: 5 backends, rcon, %d jars' % len(jars))


def jars():
    staged = {plugin_name(j): j for j in glob.glob(os.path.join(STAGE, 'jars', '*.jar'))}
    done = 0
    for name in sorted(TARGETS):
        new_code = code_of(staged[name])
        for alias in TARGETS[name]:
            found = jars_of(alias, name)
            if len(found) != 1:
                print('  skip %-5s %-16s (%s)' % (alias, name, 'not installed there' if not found else 'two jars'))
                continue
            dest = found[0]
            old = code_of(dest)
            changed = sum(1 for k in set(old) | set(new_code) if old.get(k) != new_code.get(k))
            bdir = os.path.join(BACKUP, alias)
            os.makedirs(bdir, exist_ok=True)
            shutil.copy2(dest, os.path.join(bdir, os.path.basename(dest)))
            swap_in(staged[name], dest)
            # Paper reuses a remapped copy of the old jar unless it is gone.
            remapped = os.path.join(SRV[alias], 'plugins', '.paper-remapped')
            for cached in glob.glob(os.path.join(remapped, '**', os.path.basename(dest)), recursive=True):
                os.remove(cached)
            done += 1
            print('  %-5s %-16s -> %s  sha1 %s  %s' % (alias, name, os.path.basename(dest), sha1(dest)[:8],
                  'live code was already main' if changed == 0 else 'live code was OLDER: %d classes differ' % changed))
    print('%d jars swapped, old ones in %s' % (done, BACKUP))


def void():
    if running(SRV['mmoc']):
        print('EXILE NOT CHANGED: mmo-c is still running')
        return
    r = subprocess.run(['python3', os.path.join(STAGE, 'exilevoid.py'), 'go'], env=dict(os.environ, EXV_D=SRV['mmoc'] + '/'),
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True)
    print('  mmoc  ' + r.stdout.strip().replace('\n', '\n        '))


def wait(aliases):
    deadline = time.time() + 600
    for alias in aliases:
        while True:
            out = rcon(alias, 'list')
            if re.search(r'there are|players online|online:', out, re.I):
                print('  %-5s up' % alias)
                break
            if time.time() > deadline:
                print('  %-5s not answering yet (%s)' % (alias, out[:80]))
                break
            time.sleep(6)


def after_boot():
    for alias in BACKENDS:
        lines = since_start(alias)
        bad = [l for l in lines if re.search(r'Error occurred while enabling|Could not load .plugins|Ambiguous plugin name', l)]
        ours = [l for l in lines if re.search(r'\[(%s)\].*(Exception|ERROR|SEVERE)' % OURS, l)
                or re.search(r'(ERROR|SEVERE).*\b(%s)\b' % OURS, l)]
        enabled = [n for n in sorted(TARGETS) if any('Enabling %s' % n in l for l in lines)]
        print('  %-5s enabled: %s; boot log: %s' % (alias, ', '.join(enabled) or '-',
              'clean' if not bad and not ours else '%d plugin errors' % (len(bad) + len(ours))))
        show((bad + ours)[:6], r'.', '        ')
    print('Exile (mmoc):')
    show(since_start('mmoc'), r'Couldn.t load chunk|[Cc]orrupt|chunk.*(error|Exception)|Error loading chunk', '  ')


def main():
    if len(sys.argv) < 2:
        die('usage: remote.py check|jars|void|wait <aliases>|after-boot')
    step, args = sys.argv[1], sys.argv[2:]
    if step == 'check':
        check()
    elif step == 'jars':
        jars()
    elif step == 'void':
        void()
    elif step == 'wait':
        wait(args or BACKENDS)
    elif step == 'after-boot':
        after_boot()
    else:
        die('unknown step ' + step)


if __name__ == '__main__':
    main()