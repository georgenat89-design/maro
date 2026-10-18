"""Convert the supplied CC-BY SpongeBob mesh to Maro ANC and a portable animated GLB.

Requires numpy and Pillow. Reads only the four named model/license files from the ZIP.
Usage: python tools/import_spongebob.py path/to/spongebob_squarepants.zip
"""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import hashlib, json, math, struct, sys, zlib
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
archive = Path(sys.argv[1])
source = ROOT / 'tools/model-sources/spongebob'
source.mkdir(parents=True, exist_ok=True)
with ZipFile(archive) as z:
    for name in ['license.txt', 'scene.gltf', 'scene.bin', 'textures/spongebob_baseColor.png']:
        path = source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(z.read(name))
doc = json.loads((source / 'scene.gltf').read_text(encoding='utf-8'))
raw = (source / 'scene.bin').read_bytes()
def accessor(index):
    a = doc['accessors'][index]; view = doc['bufferViews'][a['bufferView']]
    n = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3}[a['type']]
    return np.frombuffer(raw, dtype={5126: '<f4', 5125: '<u4'}[a['componentType']], count=a['count']*n,
        offset=view.get('byteOffset', 0)+a.get('byteOffset', 0)).reshape(-1, n).copy()
pos = accessor(0)[:, [0, 2, 1]]; pos[:, 2] *= -1
normals = accessor(1)[:, [0, 2, 1]]; normals[:, 2] *= -1
uv = accessor(2); faces = accessor(3).reshape(-1, 3)
# The source consists of separate arms/legs plus a connected sponge body and face.
# Assign whole welded components: a face feature is never cut off by a height heuristic.
_, ids = np.unique(np.round(pos, 5), axis=0, return_inverse=True)
parent = np.arange(ids.max()+1)
def find(i):
    while parent[i] != i:
        parent[i] = parent[parent[i]]; i = parent[i]
    return i
for a,b,c in ids[faces]:
    root = find(a); parent[find(b)] = root; parent[find(c)] = root
roots = np.array([find(i) for i in ids]); group = np.ones(len(pos), dtype=int)
center = (pos.min(0)+pos.max(0))/2
for root in np.unique(roots):
    indices = np.where(roots == root)[0]; lo = pos[indices].min(0); hi = pos[indices].max(0)
    if hi[1]-lo[1] < .12 and lo[1] > .4 and hi[1] < .53:
        if hi[0] < center[0]-.15: group[indices] = 2
        elif lo[0] > center[0]+.15: group[indices] = 3
    elif hi[1] < .34:
        group[indices] = 4 if (lo[0]+hi[0])/2 < center[0] else 5
pivots = {1: np.array([center[0], .805, center[2]]),
          2: np.array([-.207, .483, .015]), 3: np.array([.361, .483, .015]),
          4: np.array([-.042, .307, .015]), 5: np.array([.196, .307, .015])}
grips = {k: v.copy() for k,v in pivots.items()}
grips[2] = np.array([-.50,.473,.015]); grips[3] = np.array([.654,.473,.015])
for bone, angle in [(2, 80), (3, -80)]:
    t = math.radians(angle); rotation = np.array([[math.cos(t),-math.sin(t),0],[math.sin(t),math.cos(t),0],[0,0,1]])
    indices = group == bone
    pos[indices] = (pos[indices]-pivots[bone])@rotation.T+pivots[bone]
    normals[indices] = normals[indices]@rotation.T
    grips[bone] = (grips[bone]-pivots[bone])@rotation.T+pivots[bone]
shift = np.array([-center[0], -pos[:,1].min(), -center[2]])
scale = 30/(pos[:,1].max()-pos[:,1].min())
pos = (pos+shift)*scale
pivots = {k:(v+shift)*scale for k,v in pivots.items()}
grips = {k:(v+shift)*scale for k,v in grips.items()}
pivots[1] = np.array([0,24,0]); grips[1] = pivots[1]
parts = {}
for bone in pivots:
    selected = faces[np.all(group[faces] == bone, axis=1)]
    vertices, remap = np.unique(selected, return_inverse=True)
    assert len(vertices), bone
    parts[bone] = (vertices, remap.reshape(-1,3))
assert sum(len(f) for _,f in parts.values()) == len(faces)
# Repeat the 128px source texture over a 512px atlas, preserving negative/repeated UVs.
texture = Image.open(source/'textures/spongebob_baseColor.png').convert('RGBA')
assert texture.size == (128,128)
atlas = np.tile(np.asarray(texture), (4,4,1)); mapped_uv = (uv+np.array([2,1]))/4
assert mapped_uv.min() >= 0 and mapped_uv.max() <= 1
output = BytesIO()
def write(fmt,*values): output.write(struct.pack('>'+fmt,*values))
write('IIffff??II', 1095648072,3,0,30,float(pivots[4][1]),.20,True,False,3,len(parts))
for bone,(vertices,local_faces) in parts.items():
    p=pos[vertices]; n=normals[vertices]; tex=mapped_uv[vertices]; lo=p.min(0); hi=np.maximum(p.max(0),lo+.001)
    write('B',bone)
    write('6f',*pivots[bone],*grips[bone]); write('6f',*lo,*hi)
    write('ff?',.6 if bone in [2,3] else .55 if bone in [4,5] else 1,40 if bone in [2,3] else 30 if bone in [4,5] else 0,False)
    # The source material is double-sided. Backfaces get the opposite normal.
    for level in range(3):
        count=len(vertices); write('I',count*2)
        packed=np.rint((p-lo)/(hi-lo)*65535).clip(0,65535).astype('>u2').tobytes()
        output.write(packed+packed)
        output.write(np.rint(np.vstack([n,-n])*127).clip(-127,127).astype('i1').tobytes())
        output.write(np.rint(np.vstack([tex,tex])*65535).clip(0,65535).astype('>u2').tobytes())
        tris=np.vstack([local_faces,local_faces[:,::-1]+count])
        write('I',len(tris)); output.write(tris.astype('>u2').tobytes())
        write('II',0,0)  # no transparent layer
argb=atlas[:,:,[3,0,1,2]].astype('i2'); filtered=argb.copy(); filtered[:,1:]-=argb[:,:-1]
pixels=zlib.compress(filtered.astype('u1').tobytes(),9)
write('III',512,512,len(pixels)); output.write(pixels)
target=ROOT/'src/main/resources/assets/maro/characters'; target.mkdir(parents=True,exist_ok=True)
(target/'spongebob.anc').write_bytes(output.getvalue())
# Portable glTF with independent limb nodes and a one-second walk cycle.
g={'asset':{'version':'2.0','generator':'Maro SpongeBob importer','extras':doc['asset']['extras']},
   'buffers':[{}],'bufferViews':[],'accessors':[],'meshes':[],'nodes':[{'name':'SpongeBob','children':[]}],
   'scenes':[{'nodes':[0]}],'scene':0,'materials':doc['materials'],'samplers':doc['samplers'],
   'textures':doc['textures'],'images':[]}
binary=bytearray()
def data(array,kind,component):
    while len(binary)%4: binary.append(0)
    a=np.asarray(array); offset=len(binary); binary.extend(a.tobytes())
    view=len(g['bufferViews']); g['bufferViews'].append({'buffer':0,'byteOffset':offset,'byteLength':a.nbytes})
    entry={'bufferView':view,'componentType':component,'count':len(a),'type':kind}
    if component==5126: entry.update(min=a.reshape(len(a),-1).min(0).tolist(),max=a.reshape(len(a),-1).max(0).tolist())
    g['accessors'].append(entry); return len(g['accessors'])-1
node_ids={}
for bone,(vertices,triangles) in parts.items():
    attrs={'POSITION':data(((pos[vertices]-pivots[bone])/16).astype('<f4'),'VEC3',5126),
           'NORMAL':data(normals[vertices].astype('<f4'),'VEC3',5126),'TEXCOORD_0':data(uv[vertices].astype('<f4'),'VEC2',5126)}
    index=data(triangles.astype('<u2').reshape(-1),'SCALAR',5123)
    mesh=len(g['meshes']); g['meshes'].append({'primitives':[{'attributes':attrs,'indices':index,'material':0}]})
    node_ids[bone]=len(g['nodes']);g['nodes'][0]['children'].append(node_ids[bone])
    g['nodes'].append({'name':{1:'Body',2:'RightArm',3:'LeftArm',4:'RightLeg',5:'LeftLeg'}[bone],'mesh':mesh,'translation':(pivots[bone]/16).tolist()})
times=np.linspace(0,1,33).astype('<f4'); time_acc=data(times,'SCALAR',5126); anim={'name':'Walk','samplers':[],'channels':[]}
for bone in [2,3,4,5]:
    angle=np.sin(times*2*math.pi)*(.42 if bone in [2,3] else .55)*(1 if bone in [2,5] else -1)
    q=np.stack([np.sin(angle/2),np.zeros_like(angle),np.zeros_like(angle),np.cos(angle/2)],axis=1).astype('<f4')
    out=data(q,'VEC4',5126);sampler=len(anim['samplers']);anim['samplers'].append({'input':time_acc,'output':out,'interpolation':'LINEAR'})
    anim['channels'].append({'sampler':sampler,'target':{'node':node_ids[bone],'path':'rotation'}})
g['animations']=[anim]
while len(binary)%4:binary.append(0)
image_offset=len(binary);image=(source/'textures/spongebob_baseColor.png').read_bytes();binary.extend(image)
g['images']=[{'bufferView':len(g['bufferViews']),'mimeType':'image/png'}]
g['bufferViews'].append({'buffer':0,'byteOffset':image_offset,'byteLength':len(image)})
while len(binary)%4:binary.append(0)
g['buffers'][0]['byteLength']=len(binary); encoded=json.dumps(g,separators=(',',':')).encode();encoded+=b' '*((-len(encoded))%4)
glb=struct.pack('<III',0x46546c67,2,28+len(encoded)+len(binary))+struct.pack('<II',len(encoded),0x4e4f534a)+encoded+struct.pack('<II',len(binary),0x004e4942)+binary
(target/'spongebob.glb').write_bytes(glb)
(ROOT/'LICENSES/SpongeBob-CC-BY-4.0.txt').write_bytes((source/'license.txt').read_bytes())
manifest={'source_archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'source_vertices':len(pos),'source_triangles':len(faces),
    'render_triangles':len(faces)*2,'parts':{str(k):len(f) for k,(_,f) in parts.items()},'atlas':[512,512],
    'changes':'Z-up to Y-up, centered and scaled to 1.875m; arms lowered 80 degrees; whole-component limb rig and walk animation; repeated UVs retained. Face/body geometry preserved.',
    'anc_sha256':hashlib.sha256(output.getvalue()).hexdigest(),'glb_sha256':hashlib.sha256(glb).hexdigest()}
(ROOT/'docs/spongebob-model.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps(manifest,indent=2))
