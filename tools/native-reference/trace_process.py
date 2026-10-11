"""Bounded, read-only Frida observations described by a private hook plan."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import threading

SOURCE = r"""
const plan = PLAN;
const counts = {};
let emitted = 0;
function value(p, kind) {
  try {
    if (kind === 'utf8') return p.isNull() ? null : p.readUtf8String(512);
    if (kind === 'utf16') return p.isNull() ? null : p.readUtf16String(512);
    if (kind === 'u32') return p.toUInt32();
    return p.toString();
  } catch (_) { return '<unreadable>'; }
}
for (const hook of plan.hooks) {
  const address = hook.rva !== undefined
    ? Process.mainModule.base.add(hook.rva)
    : Process.getModuleByName(hook.module).getExportByName(hook.export);
  Interceptor.attach(address, {
    onEnter(args) {
      counts[hook.name] = (counts[hook.name] || 0) + 1;
      this.record = null;
      if (emitted >= plan.limit || counts[hook.name] > (hook.limit || plan.limit)) return;
      this.record = {name: hook.name, address: address.toString(),
        thread: this.threadId, ecx: this.context.ecx.toString(), args: {}};
      for (const [index, kind] of Object.entries(hook.args || {}))
        this.record.args[index] = value(args[Number(index)], kind);
    },
    onLeave(result) {
      if (!this.record || emitted >= plan.limit) return;
      if (hook.nonzero && result.isNull()) return;
      this.record.result = value(result, hook.result || 'pointer');
      emitted++;
      send(this.record);
    }
  });
}
rpc.exports = {summary() { return {counts, emitted, arch: Process.arch,
  module: Process.mainModule.name, base: Process.mainModule.base.toString()}; }};
send({ready: true, hooks: plan.hooks.length, arch: Process.arch});
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pid', type=int)
    parser.add_argument('--plan', required=True)
    parser.add_argument('--executable', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--seconds', type=float, default=30)
    parser.add_argument('--frida-path')
    args = parser.parse_args()
    if not 0 < args.seconds <= 300:
        parser.error('duration must be in (0, 300] seconds')
    plan = json.loads(Path(args.plan).read_text(encoding='utf-8'))
    digest = hashlib.sha256(Path(args.executable).read_bytes()).hexdigest()
    if digest != plan['sha256']:
        raise ValueError('hook plan executable hash does not match')
    if not 1 <= plan['limit'] <= 10000 or not 1 <= len(plan['hooks']) <= 64:
        raise ValueError('plan exceeds capture bounds')
    if args.frida_path:
        sys.path.insert(0, args.frida_path)
    import frida
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    errors = []
    session = frida.attach(args.pid)
    try:
        identity = session.create_script('rpc.exports={path(){return Process.mainModule.path;}}')
        identity.load()
        if Path(identity.exports_sync.path()).resolve() != Path(args.executable).resolve():
            raise ValueError('PID is not running the specified executable')
        identity.unload()
        script = session.create_script(SOURCE.replace('PLAN', json.dumps(plan), 1))
        with out.open('w', encoding='utf-8') as stream:
            def receive(message, _data):
                stream.write(json.dumps(message, ensure_ascii=False) + '\n')
                stream.flush()
                if message['type'] == 'error':
                    errors.append(message)
            script.on('message', receive)
            script.load()
            print(json.dumps({'ready': True, 'pid': args.pid, 'output': str(out)}), flush=True)
            threading.Event().wait(args.seconds)
            summary = script.exports_sync.summary()
            stream.write(json.dumps({'summary': summary}) + '\n')
            script.unload()
        print(json.dumps(summary))
        if errors:
            raise RuntimeError('Frida script error; inspect private trace')
    finally:
        session.detach()


if __name__ == '__main__':
    main()
