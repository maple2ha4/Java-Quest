"""Black-box real API/judge checks. Uses capabilities to distinguish process, Docker and remote."""
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('API_URL', 'http://127.0.0.1:8080')
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

def request(path, data=None, method=None, opener=client):
    req=urllib.request.Request(BASE+path, data=None if data is None else json.dumps(data).encode(),
        headers={'Content-Type':'application/json','X-Quest-Client':'1'},method=method)
    try:
        with opener.open(req,timeout=250) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise AssertionError(f'{path}: HTTP {error.code}: {error.read().decode()}') from error

def source(body):
    return 'public class Main { public static void main(String[] args) throws Exception { '+body+' } }'

def run(code, expected, lesson='hello-world', extra=None):
    time.sleep(2.1)
    files=[{'name':'Main.java','content':code}]+(extra or [])
    response=request('/api/submissions',{'lessonId':lesson,'files':files})
    assert response['result']['status']==expected,response
    print('PASS',expected,lesson)
    return response

capabilities=request('/api/capabilities')
if not capabilities['judgeEnabled']:
    raise SystemExit('Judging is off; start process/docker or an approved remote judge first.')
mode=capabilities.get('judgeMode','remote')
print('Testing real configured judge mode:',mode,flush=True)

email='smoke-'+uuid.uuid4().hex[:10]+'@example.com'
password=uuid.uuid4().hex
request('/api/auth/register',{'email':email,'name':'自动化学习者','password':password})
assert len(request('/api/lessons'))>=10
run(source('System.out.println("Hello, learner!");'),'WRONG_ANSWER')
assert request('/api/me')['completed']==[]
run('public class Main { BROKEN }','COMPILE_ERROR')
assert request('/api/me')['xp']==0
run(source('while (true) {}'),'TIME_LIMIT')
run(source('throw new IllegalStateException("test");'),'RUNTIME_ERROR')
run(source('while (true) System.out.println("x".repeat(1000));'),'OUTPUT_LIMIT')
# Only Docker promises host/network isolation; native process mode must not claim it.
if mode=='docker':
    run(source('var n=java.net.NetworkInterface.getNetworkInterfaces(); while(n.hasMoreElements()) { var i=n.nextElement(); if(!i.isLoopback() && i.isUp()) throw new RuntimeException("external interface"); } if(!java.nio.file.Files.readString(java.nio.file.Path.of("/proc/self/status")).contains("Uid:\t65534")) throw new RuntimeException("root"); if(System.getenv("DB_PASSWORD")!=null) throw new RuntimeException("secret"); try { java.nio.file.Files.writeString(java.nio.file.Path.of("/host-write"),"bad"); throw new RuntimeException("writable root"); } catch(java.nio.file.FileSystemException expected) {} System.out.println("Hello, Java Quest!");'),'PASSED')
elif mode=='process':
    run(source('for(String name:new String[]{"DB_PASSWORD","JUDGE_TOKEN","HTTP_PROXY","HTTPS_PROXY"}) { if(System.getenv(name)!=null) throw new RuntimeException("inherited secret"); } System.out.println("Hello, Java Quest!");'),'PASSED')
    print('Process environment filtering checked; filesystem/network isolation is NOT provided.',flush=True)
else:
    run(source('System.out.println("Hello, Java Quest!");'),'PASSED')
    print('Remote isolation properties were not inspected.',flush=True)
profile=request('/api/me');assert profile['xp']==30 and profile['completed']==['hello-world']
response=run(source('System.out.println(Helper.message());'),'PASSED',extra=[{'name':'Helper.java','content':'public class Helper { static String message() { return "Hello, Java Quest!"; } }'}])
assert response['awardedXp']==0 and response['profile']['xp']==30
request('/api/drafts/variables',{'files':[{'name':'Main.java','content':source('int points=10;points+=5;System.out.println(points);')}]},method='PUT')
request('/api/notes/hello-world',{'body':'println adds a newline'},method='PUT')
request('/api/auth/logout',{},method='POST')
request('/api/auth/login',{'email':email,'password':password})
assert request('/api/me')['xp']==30
assert request('/api/drafts/variables')[0]['content'].find('points+=5')>=0
assert request('/api/notes')[0]['body']=='println adds a newline'
run(request('/api/drafts/variables')[0]['content'],'PASSED','variables')
assert request('/api/me')['xp']==65
assert len(request('/api/submissions'))>=8
solutions = [
 ('types','double result=5.0/2; System.out.println(result);'),
 ('operators','int remainder=17%5; System.out.println(remainder);'),
 ('conditions','int score=60; if(score>=60) System.out.println("PASS"); else System.out.println("RETRY");'),
 ('loops','int sum=0; for(int i=1;i<=5;i++) sum+=i; System.out.println(sum);'),
 ('arrays','int[] values={3,5,8}; int sum=0; for(int v:values) sum+=v; System.out.println(sum);'),
 ('methods',None),
 ('strings','String text="  java  "; System.out.println(text.trim().toUpperCase(java.util.Locale.ROOT));'),
 ('exceptions','try { Integer.parseInt("abc"); } catch(NumberFormatException e) { System.out.println("INVALID"); }')]
for lesson, body in solutions:
    code=source(body) if body else 'public class Main { static int doubleOf(int n) { return n*2; } public static void main(String[] args) { System.out.println(doubleOf(7)); } }'
    run(code,'PASSED',lesson)
assert len(request('/api/me')['completed'])==10
assert request('/api/me')['xp']==430
print('ALL REAL API / '+mode.upper()+' JUDGE SMOKE CHECKS PASSED')
