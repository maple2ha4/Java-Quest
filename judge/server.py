"""Trusted local coordinator. Learner code runs only inside restricted Docker containers."""
import hmac
import io
import json
import os
import re
import subprocess
import tarfile
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

IMAGE = os.environ.get('JUDGE_IMAGE', 'eclipse-temurin:21-jdk')
TOKEN = os.environ.get('JUDGE_TOKEN', '')
CAPACITY = threading.BoundedSemaphore(2)
MAX_OUTPUT = 65536


def bounded_run(command, payload, timeout):
    """Drain both pipes without unbounded memory; kill on timeout/output overflow."""
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    output = {'stdout': bytearray(), 'stderr': bytearray()}
    lock = threading.Lock()
    overflow = threading.Event()

    def feed():
        try:
            process.stdin.write(payload)
            process.stdin.close()
        except (BrokenPipeError, OSError):
            pass

    def drain(pipe, key):
        try:
            while True:
                chunk = os.read(pipe.fileno(), 8192)
                if not chunk:
                    break
                with lock:
                    available = MAX_OUTPUT - sum(map(len, output.values()))
                    output[key].extend(chunk[:max(0, available)])
                    if len(chunk) > available:
                        overflow.set()
        except (OSError, ValueError):
            pass

    # Threads work for anonymous subprocess pipes on both Windows and POSIX.
    readers = [threading.Thread(target=drain, args=(process.stdout, 'stdout'), daemon=True),
               threading.Thread(target=drain, args=(process.stderr, 'stderr'), daemon=True)]
    for reader in readers:
        reader.start()
    writer = threading.Thread(target=feed, daemon=True)
    writer.start()
    deadline = time.monotonic() + timeout
    reason = None
    while process.poll() is None:
        if overflow.wait(.03):
            reason = 'OUTPUT_LIMIT'
            break
        if time.monotonic() > deadline:
            reason = 'TIME_LIMIT'
            break
    if reason:
        process.kill()
    process.wait(timeout=3)
    for reader in readers:
        reader.join(timeout=1)
    if overflow.is_set():
        reason = 'OUTPUT_LIMIT'
    process.stdout.close()
    process.stderr.close()
    return process.returncode, {k: bytes(v).decode('utf-8', errors='replace') for k, v in output.items()}, reason


def run_case(files, case):
    name = 'jq-' + uuid.uuid4().hex
    archive = io.BytesIO()
    with tarfile.open(fileobj=archive, mode='w') as tar:
        for filename, content in [(f['name'], f['content']) for f in files] + [('.input', case.get('input', ''))]:
            data = content.encode()
            entry = tarfile.TarInfo(filename)
            entry.size, entry.mode, entry.uid, entry.gid = len(data), 0o600, 65534, 65534
            tar.addfile(entry, io.BytesIO(data))
    command = ['docker', 'run', '--name', name, '--pull=never', '--rm', '-i',
               '--network=none', '--read-only', '--user=65534:65534',
               '--cap-drop=ALL', '--security-opt=no-new-privileges:true',
               '--pids-limit=64', '--memory=256m', '--memory-swap=256m', '--cpus=1',
               '--ulimit=nofile=128:128', '--ulimit=fsize=16384:16384',
               '--tmpfs=/work:rw,nosuid,nodev,size=64m,mode=1777', '--workdir=/work',
               IMAGE, 'sh', '-c',
               'tar xf -; timeout 15s javac -J-Xmx96m -proc:none -encoding UTF-8 *.java 2> .compile; '
               'rc=$?; if [ "$rc" -ne 0 ]; then cat .compile >&2; [ "$rc" -eq 124 ] && exit 124; exit 65; fi; '
               'exec timeout -k 1s 3s java -Xmx96m -XX:MaxMetaspaceSize=64m -XX:ActiveProcessorCount=1 Main < .input']
    start = time.monotonic()
    try:
        code, output, reason = bounded_run(command, archive.getvalue(), 25)
        if code == 125:
            raise RuntimeError('Docker could not create the sandbox: ' + output['stderr'][:1000])
        status = reason or ('PASSED' if code == 0 else 'COMPILE_ERROR' if code == 65 else 'TIME_LIMIT' if code in (124, 137) else 'RUNTIME_ERROR')
        if status == 'PASSED' and normalize(output['stdout']) != normalize(case['expected']):
            status = 'WRONG_ANSWER'
        return dict(status=status, passed=status == 'PASSED', expected=case['expected'], input=case.get('input', ''),
                    **output, durationMs=round((time.monotonic()-start)*1000))
    finally:
        subprocess.run(['docker', 'rm', '-f', name], capture_output=True, timeout=10)


def normalize(value):
    return value.replace('\r\n', '\n').rstrip()


def validate(data):
    if data.get('buildKind','javac')!='javac':
        raise ValueError('Framework/configuration labs require the Java judge jar')
    if data.get('entryClass', 'Main') != 'Main':
        raise ValueError('Packaged projects require the Java judge jar; legacy Python judge supports Main only')
    files = data.get('files', [])
    if not 1 <= len(files) <= 8 or sum(len(f.get('content', '')) for f in files) > 32768:
        raise ValueError('Invalid source size')
    names = [f['name'] for f in files]
    if 'Main.java' not in names or len(set(names)) != len(names) or any(not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,60}\.java', n) for n in names):
        raise ValueError('Invalid source filename')
    if not 1 <= len(data.get('tests', [])) <= 5:
        raise ValueError('Invalid tests')


class Handler(BaseHTTPRequestHandler):
    def send(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path != '/health':
            return self.send(404, {'message': 'Not found'})
        try:
            subprocess.run(['docker', 'image', 'inspect', IMAGE], check=True, capture_output=True, timeout=5)
            return self.send(200, {'status': 'ok', 'image': IMAGE})
        except Exception:
            return self.send(503, {'message': 'Sandbox image or Docker unavailable'})

    def do_POST(self):
        if self.path != '/judge':
            return self.send(404, {'message': 'Not found'})
        if not TOKEN or not hmac.compare_digest(self.headers.get('X-Judge-Token', ''), TOKEN):
            return self.send(401, {'message': 'Unauthorized'})
        if not CAPACITY.acquire(blocking=False):
            return self.send(429, {'message': 'Judge is busy'})
        try:
            length = int(self.headers.get('Content-Length', 0))
            if not 0 < length <= 200000:
                raise ValueError('Invalid body size')
            data = json.loads(self.rfile.read(length))
            validate(data)
            results = []
            for case in data['tests']:
                result = run_case(data['files'], case)
                results.append(result)
                if result['status'] in ('COMPILE_ERROR', 'TIME_LIMIT', 'OUTPUT_LIMIT'):
                    break
            passed = len(results) == len(data['tests']) and all(x['passed'] for x in results)
            self.send(200, {'status': 'PASSED' if passed else next(x['status'] for x in results if not x['passed']),
                            'passed': passed, 'tests': results, 'totalTests': len(data['tests'])})
        except (ValueError, KeyError, TypeError) as error:
            self.send(400, {'message': str(error)})
        except Exception as error:
            print(type(error).__name__, str(error), flush=True)
            self.send(503, {'message': 'Sandbox unavailable; no code was run on the host'})
        finally:
            CAPACITY.release()


if __name__ == '__main__':
    if len(TOKEN) < 32:
        raise SystemExit('JUDGE_TOKEN must contain at least 32 characters')
    ThreadingHTTPServer(('127.0.0.1', int(os.environ.get('JUDGE_PORT', '8091'))), Handler).serve_forever()
