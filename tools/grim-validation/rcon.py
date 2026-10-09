import json,pathlib,socket,struct,sys
root=pathlib.Path(__file__).resolve().parent
secret=json.loads((root/'connection.json').read_text(encoding='utf-8'))
def packet(sock,identity,kind,payload):
    data=struct.pack('<ii',identity,kind)+payload.encode()+b'\0\0'
    sock.sendall(struct.pack('<i',len(data))+data)
def read(sock):
    def exact(n):
        result=b''
        while len(result)<n:
            chunk=sock.recv(n-len(result))
            if not chunk:raise RuntimeError('RCON connection closed')
            result+=chunk
        return result
    length=struct.unpack('<i',exact(4))[0]
    data=exact(length)
    return struct.unpack('<ii',data[:8]),data[8:-2].decode(errors='replace')
def command(text):
    with socket.create_connection(('127.0.0.1',secret['rconPort']),timeout=10) as sock:
        packet(sock,1,3,secret['password'])
        header,response=read(sock)
        if header[0]<0:raise RuntimeError('Local fixture RCON authorization failed')
        packet(sock,2,2,text)
        header,response=read(sock)
        return response
if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    print(command(' '.join(sys.argv[1:])))
