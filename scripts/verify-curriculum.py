"""Author QA: every published reference must pass and every starter must fail.
Only trusted repository-authored code is used. This is optional tooling, not a runtime dependency.
"""
import argparse,json,os,pathlib,secrets,shutil,subprocess,tempfile,time,urllib.error,urllib.request
root=pathlib.Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser();parser.add_argument('--mode',choices=['process','docker'],required=True)
parser.add_argument('--allow-local-process',action='store_true');parser.add_argument('--port',type=int,default=8098)
args=parser.parse_args()
if args.mode=='process' and not args.allow_local_process:parser.error('Trusted repository code only: explicitly pass --allow-local-process')
jar=root/'backend/target/java-quest-api-0.1.0-judge.jar'
if not jar.is_file():raise SystemExit('Run mvn -f backend/pom.xml verify first')
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java') if os.environ.get('JAVA_HOME') else shutil.which('java')
if not java:raise SystemExit('JDK 21 required')
token=secrets.token_hex(32);env=os.environ.copy();env.update(JUDGE_TOKEN=token,JUDGE_PORT=str(args.port))
client=urllib.request.build_opener(urllib.request.ProxyHandler({}))
command=[str(java),'-jar',str(jar),'--mode='+args.mode]+(['--allow-local-process'] if args.mode=='process' else [])
lessons=json.loads((root/'backend/src/main/resources/lessons.json').read_text(encoding='utf-8'))
solutions=json.loads((root/'backend/src/test/resources/course-solutions.json').read_text(encoding='utf-8'))
with tempfile.TemporaryFile() as log:
 process=subprocess.Popen(command,env=env,cwd=root,stdout=log,stderr=subprocess.STDOUT)
 try:
  ready=False
  for _ in range(100):
   if process.poll() is not None:break
   try:
    with client.open(f'http://127.0.0.1:{args.port}/health',timeout=2) as response:
     ready=json.load(response).get('mode')==args.mode
    if ready:break
   except OSError:time.sleep(.15)
  if not ready:log.seek(0);raise RuntimeError(log.read().decode(errors='replace'))
  for lesson in lessons:
   for label,files in [('starter',lesson['starter']),('reference',solutions[lesson['id']])]:
    if label=='reference':
     by_name={f['name']:f['content'] for f in files}
     for f in lesson['starter']:
      if f['name'] not in lesson['editableFiles']:assert by_name[f['name']]==f['content'],'Reference changed a protected file'
    request=urllib.request.Request(f'http://127.0.0.1:{args.port}/judge',data=json.dumps({'files':files,'tests':lesson['tests'],'entryClass':lesson['entryClass']}).encode(),headers={'Content-Type':'application/json','X-Judge-Token':token,'X-Judge-Mode':args.mode})
    with client.open(request,timeout=240) as response:result=json.load(response)
    assert result['passed']==(label=='reference'),(lesson['id'],label,str(result)[:1200])
   print('PASS starter rejected / reference accepted:',lesson['id'],flush=True)
  print(f'ALL {len(lessons)} PUBLISHED LESSONS VERIFIED ({args.mode}); planned exercises were not executed.',flush=True)
 finally:
  process.terminate()
  try:process.wait(timeout=20)
  except subprocess.TimeoutExpired:process.kill();process.wait()
