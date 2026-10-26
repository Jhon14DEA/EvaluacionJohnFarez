"""Read-only Swagger UI verification against packaged services and isolated SQL."""
from pathlib import Path
import os,socket,subprocess,time,urllib.request
from java_runtime import java25, isolated_database
JAVA = java25()
database = isolated_database()
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'tmp';OUT.mkdir(exist_ok=True)
for port in (8080,):
 with socket.socket() as sock:sock.bind(('127.0.0.1',port))
processes=[];logs=[]
try:
 for name,port in [('Cuentas',8080)]:
  cwd=ROOT.parent/('EvaluacionJohnFarez-'+name)
  log=(OUT/f'ca10-{name}.log').open('w');logs.append(log)
  env=os.environ.copy();env['DB_NAME']=database
  command=[JAVA,'-Dquarkus.http.host=127.0.0.1',f'-Dquarkus.http.port={port}',
    '-Dquarkus.flyway.migrate-at-start=false','-jar','target/quarkus-app/quarkus-run.jar']
  process=subprocess.Popen(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
  processes.append(process)
  deadline=time.monotonic()+55
  while time.monotonic()<deadline:
   if process.poll() is not None:raise RuntimeError(f'{name} failed; inspect tmp log')
   try:
    with urllib.request.urlopen(f'http://127.0.0.1:{port}/q/openapi?format=json',timeout=2):break
   except OSError:time.sleep(.5)
  else:raise TimeoutError(f'Port {port}')
 result=subprocess.run(['node','scripts/verify-ca10.mjs'],cwd=ROOT)
 raise SystemExit(result.returncode)
finally:
 for process in reversed(processes):
  if process.poll() is None:process.terminate();process.wait(timeout=15)
 for log in logs:log.close()
