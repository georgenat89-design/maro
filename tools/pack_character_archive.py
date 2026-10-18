"""Package a supplied glTF ZIP as a self-contained GLB, retaining source and attribution."""
from pathlib import Path, PurePosixPath
from zipfile import ZipFile
import hashlib,json,struct,sys

root=Path(__file__).resolve().parents[1]
archive=Path(sys.argv[1]); key=sys.argv[2]
source=root/'tools/model-sources'/key;source.mkdir(parents=True,exist_ok=True)
with ZipFile(archive) as z:
    d=json.loads(z.read('scene.gltf')); binary=bytearray(z.read('scene.bin'))
    for name in ['license.txt','scene.gltf','scene.bin']+[i['uri'] for i in d.get('images',[])]:
        path=PurePosixPath(name)
        if path.is_absolute() or '..' in path.parts or '\\' in name or ':' in name:raise ValueError('Non-local model asset')
        dest=source/str(path);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(name))
    assert len(d['buffers'])==1
    d['buffers'][0].pop('uri',None)
    if key=='donald_trump':
        # This supplied FBX export has inverted, differently scaled body primitives;
        # its hair and joint bind positions already use the intended character units.
        # Restore the body using the original object's uniform scale. Its source
        # normals point inward after this reflection, so reverse those as well.
        import numpy as np
        def accessor(index):
            a=d['accessors'][index];view=d['bufferViews'][a['bufferView']]
            width={'SCALAR':1,'VEC3':3}[a['type']]
            dtype=np.dtype({5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']])
            return np.ndarray((a['count'],width),dtype=dtype,buffer=binary,
                offset=view.get('byteOffset',0)+a.get('byteOffset',0),
                strides=(view.get('byteStride',width*dtype.itemsize),dtype.itemsize))
        for mesh in d['meshes']:
            for primitive in mesh['primitives']:
                positions=accessor(primitive['attributes']['POSITION'])
                if np.max(np.abs(positions))<1000:continue
                name=mesh['name'].split('_')[0]
                original=next(n for n in d['nodes'] if n.get('name')==name)
                scale=original.get('scale',[1,1,1]);assert max(scale)-min(scale)<1e-6
                positions*=float(scale[0])/-10000
                a=d['accessors'][primitive['attributes']['POSITION']]
                a['min']=positions.min(axis=0).tolist();a['max']=positions.max(axis=0).tolist()
                accessor(primitive['attributes']['NORMAL'])[:]*=-1
    for image in d.get('images',[]):
        data=z.read(image.pop('uri'))
        while len(binary)%4:binary.append(0)
        image['bufferView']=len(d['bufferViews'])
        if data.startswith(b'\x89PNG\r\n\x1a\n'):image['mimeType']='image/png'
        elif data.startswith(b'\xff\xd8\xff'):image['mimeType']='image/jpeg'
        else:raise ValueError('Unsupported embedded texture format')
        d['bufferViews'].append({'buffer':0,'byteOffset':len(binary),'byteLength':len(data)})
        binary.extend(data)
    if key=='lebron':
        # The OBJ includes an unattached ball at the origin. The wearable character uses game-held items.
        for node in d['nodes']:
            if node.get('mesh')==2:node.pop('mesh')
    for material in d.get('materials',[]):
        legacy=material.get('extensions',{}).get('KHR_materials_pbrSpecularGlossiness')
        if legacy and 'pbrMetallicRoughness' not in material:
            material['pbrMetallicRoughness']={'baseColorFactor':legacy.get('diffuseFactor',[1,1,1,1]),
                'metallicFactor':0,'roughnessFactor':1-legacy.get('glossinessFactor',1)}
            if 'diffuseTexture' in legacy:material['pbrMetallicRoughness']['baseColorTexture']=legacy['diffuseTexture']
    if 'extensionsRequired' in d:
        d['extensionsRequired']=[e for e in d['extensionsRequired'] if e!='KHR_materials_pbrSpecularGlossiness']
    while len(binary)%4:binary.append(0)
    d['buffers'][0]['byteLength']=len(binary)
    encoded=json.dumps(d,separators=(',',':')).encode();encoded+=b' '*((-len(encoded))%4)
    glb=struct.pack('<III',0x46546c67,2,28+len(encoded)+len(binary))+struct.pack('<II',len(encoded),0x4e4f534a)+encoded+struct.pack('<II',len(binary),0x004e4942)+binary
    dest=root/'src/main/resources/assets/maro/characters'/f'{key}.glb';dest.write_bytes(glb)
    (root/'LICENSES'/f'{key}-CC-BY-4.0.txt').write_bytes(z.read('license.txt'))
    metadata={'source_archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'glb_sha256':hashlib.sha256(glb).hexdigest(),'attribution':d['asset'].get('extras',{}),'changes':'Embedded local buffers/textures; legacy diffuse material colors mapped to metallic-roughness where needed.'+(' LeBron unattached basketball omitted from wearable character.' if key=='lebron' else '')}
    if key=='donald_trump':metadata['changes']+=' Repaired inverted per-object body scale and inward normals from the supplied FBX export; source rig and running animation retained.'
    (root/'docs'/f'{key}-model.json').write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    print(key,len(glb),'bytes')
