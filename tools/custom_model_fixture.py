"""Generate the native Custom Model test's original textured GLB humanoid fixture."""
import io
import json
import struct
from pathlib import Path
from PIL import Image

positions, normals, uvs, indices = [], [], [], []
faces = [((1,0,0),[(1,-1,-1),(1,1,-1),(1,1,1),(1,-1,1)]),
         ((-1,0,0),[(-1,-1,1),(-1,1,1),(-1,1,-1),(-1,-1,-1)]),
         ((0,1,0),[(-1,1,-1),(-1,1,1),(1,1,1),(1,1,-1)]),
         ((0,-1,0),[(-1,-1,1),(-1,-1,-1),(1,-1,-1),(1,-1,1)]),
         ((0,0,1),[(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]),
         ((0,0,-1),[(1,-1,-1),(-1,-1,-1),(-1,1,-1),(1,1,-1)])]
for center, extent in [((0,18,0),(4,6,2)),((0,28,0),(4,4,4)),((-6,18,0),(2,6,2)),((6,18,0),(2,6,2)),((-2,6,0),(1.8,6,2)),((2,6,0),(1.8,6,2))]:
    for normal, corners in faces:
        start = len(positions)//3
        for i, corner in enumerate(corners):
            positions.extend(center[j]+corner[j]*extent[j] for j in range(3))
            normals.extend(normal)
            uvs.extend([(0,0),(1,0),(1,1),(0,1)][i])
        indices.extend(start+i for i in [0,1,2,0,2,3])

atlas = Image.new('RGBA',(4,4),(65,175,220,255))
atlas.putpixel((0,0),(230,245,255,255))
stream=io.BytesIO();atlas.save(stream,format='PNG')
chunks=[struct.pack('<%df'%len(positions),*positions),struct.pack('<%df'%len(normals),*normals),struct.pack('<%df'%len(uvs),*uvs),struct.pack('<%dH'%len(indices),*indices),stream.getvalue()]
data=bytearray();views=[]
for chunk in chunks:
    views.append({'buffer':0,'byteOffset':len(data),'byteLength':len(chunk)})
    data.extend(chunk);data.extend(b'\0'*((-len(data))%4))
count=len(positions)//3
doc={'asset':{'version':'2.0'},'scene':0,'scenes':[{'nodes':[0]}],'nodes':[{'mesh':0,'name':'MaroTestHumanoid'}],
     'buffers':[{'byteLength':len(data)}],'bufferViews':views,
     'accessors':[{'bufferView':i,'componentType':5126,'count':count,'type':t} for i,t in enumerate(['VEC3','VEC3','VEC2'])]+[{'bufferView':3,'componentType':5123,'count':len(indices),'type':'SCALAR'}],
     'images':[{'bufferView':4,'mimeType':'image/png'}],'textures':[{'source':0}],
     'materials':[{'pbrMetallicRoughness':{'baseColorTexture':{'index':0},'metallicFactor':0}}],
     'meshes':[{'primitives':[{'attributes':{'POSITION':0,'NORMAL':1,'TEXCOORD_0':2},'indices':3,'material':0}]}]}
text=json.dumps(doc,separators=(',',':')).encode();text+=b' '*((-len(text))%4)
glb=struct.pack('<III',0x46546c67,2,28+len(text)+len(data))+struct.pack('<II',len(text),0x4e4f534a)+text+struct.pack('<II',len(data),0x004e4942)+data
output=Path(__file__).resolve().parents[1]/'src/gametest/resources/assets/maro_test/characters/humanoid.glb'
output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(glb)
