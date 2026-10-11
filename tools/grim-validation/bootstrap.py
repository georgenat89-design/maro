"""Build an isolated Paper/Grim native-client fixture from official pinned downloads."""
import argparse,hashlib,json,os,pathlib,secrets,shutil,socket,subprocess,time,urllib.request

PAPER_URL='https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar'
PAPER_SHA256='5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba'
GRIM_URL='https://cdn.modrinth.com/data/LJNGWSvH/versions/1FIGlM6Q/grimac-bukkit-2.3.73.jar'
GRIM_SHA512='bf9be1194eb45afe1dec6bd8e98d078dacf41539025c8aabe24d31337cbea86625774e30842568b47f34ca174472c724c7876cc30793945e7408b03609672655'
PAPER_API_URL='https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-20260511.115010-91.jar'
USER_AGENT='Maro-builder-validation/1.0 (https://github.com/georgenat89-design/maro)'

def java_tool(name):
    suffix='.exe' if os.name=='nt' else ''
    home=os.environ.get('JAVA_HOME')
    path=pathlib.Path(home)/'bin'/(name+suffix) if home else None
    if path and path.is_file():return str(path)
    found=shutil.which(name)
    if not found:raise RuntimeError('Java21 JDK required: set JAVA_HOME or put '+name+' on PATH')
    return found

def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':USER_AGENT}),timeout=90) as response:return response.read()

def download(url,path,algorithm,digest):
    if not path.exists() or hashlib.new(algorithm,path.read_bytes()).hexdigest()!=digest:
        data=fetch(url)
        if hashlib.new(algorithm,data).hexdigest()!=digest:raise RuntimeError('Official artifact checksum mismatch: '+path.name)
        path.write_bytes(data)

def flags():return subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime',type=pathlib.Path,required=True,help='New disposable runtime directory; no live Minecraft profile')
    parser.add_argument('--game-port',type=int,default=25585);parser.add_argument('--rcon-port',type=int,default=25586);parser.add_argument('--start',action='store_true')
    args=parser.parse_args();root=args.runtime.resolve();root.mkdir(parents=True,exist_ok=True)
    if (root/'server-process.json').exists():raise RuntimeError('Runtime already contains a server PID record; stop and inspect it before reusing')
    for port in (args.game_port,args.rcon_port):
        with socket.socket() as sock:sock.bind(('127.0.0.1',port))
    server=root/'server';plugins=server/'plugins';plugins.mkdir(parents=True,exist_ok=True)
    download(PAPER_URL,server/'paper.jar','sha256',PAPER_SHA256)
    download(GRIM_URL,plugins/'grimac-bukkit-2.3.73.jar','sha512',GRIM_SHA512)
    api_digest=fetch(PAPER_API_URL+'.sha512').decode().strip().split()[0]
    download(PAPER_API_URL,root/'paper-api.jar','sha512',api_digest)
    password=secrets.token_hex(24)
    (root/'connection.json').write_text(json.dumps({'gamePort':args.game_port,'rconPort':args.rcon_port,'password':password}),encoding='utf-8')
    (server/'eula.txt').write_text('eula=true\n',encoding='utf-8')
    properties={'server-ip':'127.0.0.1','server-port':args.game_port,'online-mode':'false','enforce-secure-profile':'false','spawn-protection':0,'max-players':1,'view-distance':5,'simulation-distance':5,'level-type':'minecraft:flat','generate-structures':'false','generator-settings':'{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":2}],"biome":"minecraft:plains"}','gamemode':'survival','difficulty':'peaceful','motd':'Maro isolated Grim fixture','enable-rcon':'true','rcon.port':args.rcon_port,'rcon.password':password,'broadcast-rcon-to-ops':'false'}
    (server/'server.properties').write_text(''.join(str(key)+'='+str(value)+'\n' for key,value in properties.items()),encoding='utf-8')
    source=pathlib.Path(__file__).resolve().parent
    for script in ('server-manager.py','rcon.py'):shutil.copy2(source/script,root/script)
    shutil.copytree(source/'fixture-src',root/'fixture-src',dirs_exist_ok=True)
    # First startup installs Paper's official libraries and generates untouched Grim defaults.
    log=root/'bootstrap-server.log'
    with log.open('w',encoding='utf-8') as output:
        process=subprocess.Popen([java_tool('java'),'-Xms512M','-Xmx1536M','-jar','paper.jar','--nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,creationflags=flags())
        deadline=time.monotonic()+180
        try:
            while time.monotonic()<deadline:
                if process.poll() is not None:raise RuntimeError('Paper bootstrap failed: '+str(log))
                if 'Done (' in log.read_text(encoding='utf-8',errors='replace'):break
                time.sleep(.25)
            else:raise RuntimeError('Paper bootstrap timed out: '+str(log))
        finally:
            if process.poll() is None:process.stdin.write('stop\n');process.stdin.flush();process.wait(timeout=90)
    classes=root/'fixture-classes';classes.mkdir(exist_ok=True)
    jars=[root/'paper-api.jar',plugins/'grimac-bukkit-2.3.73.jar',*sorted((server/'libraries').rglob('*.jar'))]
    subprocess.run([java_tool('javac'),'--release','21','-proc:none','-encoding','UTF-8','-classpath',os.pathsep.join(map(str,jars)),'-d',str(classes),str(root/'fixture-src/GrimFixture.java')],cwd=root,check=True)
    shutil.copy2(root/'fixture-src/plugin.yml',classes/'plugin.yml')
    subprocess.run([java_tool('jar'),'--create','--file',str(plugins/'maro-grim-fixture.jar'),'-C',str(classes),'.'],cwd=root,check=True)
    manifest={'paper':{'version':'1.21.11-132','url':PAPER_URL,'sha256':PAPER_SHA256},'grim':{'version':'2.3.73','url':GRIM_URL,'sha512':GRIM_SHA512},'paperApi':{'url':PAPER_API_URL,'sha512':api_digest},'defaults':{name:hashlib.sha256((plugins/'GrimAC'/name).read_bytes()).hexdigest() for name in ('config.yml','punishments.yml')}}
    (root/'artifact-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('Fixture compiled; localhost game port',args.game_port,'RCON port',args.rcon_port,'runtime',root)
    if args.start:
        process=subprocess.Popen([os.sys.executable,str(root/'server-manager.py')],cwd=root,creationflags=flags());print('Started localhost fixture manager PID',process.pid)

if __name__=='__main__':main()
