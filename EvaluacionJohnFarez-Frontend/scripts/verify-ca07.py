"""Starts own backend (Cuentas) instance and Angular dev server; closes both on exit.
Requires the compiled backend, SQL Server and Microsoft Edge. Creates new test accounts and movements only; no migrations or resets.
"""
from pathlib import Path
import os,socket,subprocess,time,urllib.request
from java_runtime import java25, isolated_database
JAVA = java25()
database = isolated_database()
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'tmp';OUT.mkdir(exist_ok=True)
for port in (8080,4200):
 with socket.socket() as sock:sock.bind(('127.0.0.1',port))
processes=[];logs=[]
try:
 configs=[(ROOT.parent/'EvaluacionJohnFarez-Cuentas',
   [JAVA,'-Dquarkus.http.host=127.0.0.1','-Dquarkus.http.port=8080',
    '-Dquarkus.flyway.migrate-at-start=false','-jar','target/quarkus-app/quarkus-run.jar'],8080,'/api/cuentas'),
   (ROOT,['node','node_modules/@angular/cli/bin/ng.js','serve','--host','127.0.0.1','--port','4200'],4200,'/')]
 for cwd,command,port,path in configs:
  log=(OUT/f'process-{port}.log').open('w');logs.append(log)
  env=os.environ.copy();env['DB_NAME']=database
  process=subprocess.Popen(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
  processes.append(process)
  deadline=time.monotonic()+55
  while time.monotonic()<deadline:
   if process.poll() is not None:raise RuntimeError(f'Process {port} failed; inspect tmp log')
   try:
    with urllib.request.urlopen(f'http://127.0.0.1:{port}{path}',timeout=2):break
   except OSError:time.sleep(.5)
  else:raise TimeoutError(f'Port {port}')
 result=subprocess.run(['node','scripts/verify-ca07.mjs'],cwd=ROOT)
 raise SystemExit(result.returncode)
finally:
 for process in reversed(processes):
  if process.poll() is None:process.terminate();process.wait(timeout=15)
 for log in logs:log.close()
