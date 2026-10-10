"""Launch the disposable localhost fixture; graceful stop through rcon.py stop."""
import json,os,pathlib,shutil,subprocess,time
root=pathlib.Path(__file__).resolve().parent;server=root/'server'
home=os.environ.get('JAVA_HOME');java=str(pathlib.Path(home)/'bin'/('java.exe' if os.name=='nt' else 'java')) if home else shutil.which('java')
if not java:raise RuntimeError('Set JAVA_HOME to a Java21 JDK')
with (root/'server-console.log').open('a',encoding='utf-8') as output:
    process=subprocess.Popen([java,'-Xms512M','-Xmx1536M','-jar','paper.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    (root/'server-process.json').write_text(json.dumps({'pid':process.pid,'directory':str(server),'jar':str(server/'paper.jar')}),encoding='utf-8')
    while process.poll() is None:
        for command in sorted(root.glob('command-*.txt')):
            payload=command.read_text(encoding='utf-8').strip();process.stdin.write(payload+'\n');process.stdin.flush();command.unlink()
        time.sleep(.1)
    (root/'server-exit.txt').write_text(str(process.returncode),encoding='utf-8')
