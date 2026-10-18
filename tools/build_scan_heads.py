"""Fit photographic likenesses to the CC BY 3.0 Lee Perry-Smith head scan.

Offline tool: numpy, scipy, pillow, opencv, mediapipe, trimesh,
fast-simplification. Runtime receives regular indexed triangle meshes and UV
atlases, without Python, an inference model or an external service.
"""
import argparse
import json
import struct
from pathlib import Path
import numpy as np
import cv2
from PIL import Image, ImageDraw
from scipy.interpolate import LinearNDInterpolator, NearestNDInterpolator, RBFInterpolator
from scipy.ndimage import gaussian_filter, distance_transform_edt
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from portrait_image_tools import bilinear, OVAL
import fast_simplification
import trimesh

HAIR={'arnold':'gray','david_laid':'beanie','lebron':'crop','ishowspeed':'twists','kai_cenat':'locs',
      'jeffrey_epstein':'gray','p_diddy':'crop','michael_jordan':'bald','kobe_bryant':'crop',
      'shaquille_oneal':'bald','stephen_curry':'curls','kevin_durant':'crop','lionel_messi':'side',
      'cristiano_ronaldo':'swept','mike_tyson':'crop','bruce_lee':'fringe','the_rock':'bald',
      'john_cena':'crop','hitler_historical':'side'}

def smooth(a,b,x):
    t=np.clip((x-a)/(b-a),0,1);return t*t*(3-2*t)

def normals(vertices,faces):
    mesh=trimesh.Trimesh(vertices=vertices,faces=faces,process=False)
    n=np.array(mesh.vertex_normals)
    # UV seams duplicate vertices; share normals across coincident geometry.
    _,mapping=np.unique(np.round(vertices,5),axis=0,return_inverse=True)
    summed=np.zeros((mapping.max()+1,3));np.add.at(summed,mapping,n)
    n=summed[mapping];n/=np.maximum(np.linalg.norm(n,axis=1,keepdims=True),1e-8)
    return n

def clip_neck(vertices,faces,uv,height=22.7):
    points=[];coords=[];polys=[]
    for face in faces:
        poly=[(vertices[i],uv[i]) for i in face];clipped=[]
        for a,b in zip(poly,poly[1:]+poly[:1]):
            inside=a[0][1]>=height;other=b[0][1]>=height
            if inside:clipped.append(a)
            if inside!=other:
                t=(height-a[0][1])/(b[0][1]-a[0][1]);clipped.append((a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])))
        if len(clipped)<3:continue
        start=len(points)
        for p,t in clipped:points.append(p);coords.append(t)
        for i in range(1,len(clipped)-1):polys.append([start,start+i,start+i+1])
    vertices=np.array(points);uv=np.array(coords);faces=np.array(polys)
    # Weld identical positions/UVs, preserving the scan's intentional UV seam.
    _,indices,inverse=np.unique(np.round(np.column_stack((vertices,uv)),6),axis=0,return_index=True,return_inverse=True)
    vertices=vertices[indices];faces=inverse[faces];uv=uv[indices]
    _,weld=np.unique(np.round(vertices,5),axis=0,return_inverse=True)
    counts={};oriented={}
    for face in faces:
        for a,b in zip(face,np.roll(face,-1)):
            edge=tuple(sorted((int(weld[a]),int(weld[b]))));counts[edge]=counts.get(edge,0)+1;oriented[edge]=(a,b)
    border=[oriented[e] for e,n in counts.items() if n==1 and abs(vertices[oriented[e][0],1]-height)<1e-4 and abs(vertices[oriented[e][1],1]-height)<1e-4]
    if border:
        centre=np.mean(vertices[np.unique(np.array(border))],axis=0);centre[1]=height
        pv=vertices.tolist();tv=uv.tolist();ff=faces.tolist();ci=len(pv);pv.append(centre);tv.append([1008/1024,1088/1024])
        for a,b in border:
            ia=len(pv);pv.extend([vertices[a],vertices[b]]);tv.extend([[1008/1024,1088/1024]]*2)
            triangle=[ci,ia,ia+1]
            if np.cross(vertices[a]-centre,vertices[b]-centre)[1]>0:triangle=[ci,ia+1,ia]
            ff.append(triangle)
        vertices=np.array(pv);faces=np.array(ff);uv=np.array(tv)
    return vertices,faces,uv

def photo_fit(key,root,detector):
    path=root/(key+'.png')
    if not path.exists():path=root/(key+'.jpg')
    rgba=np.array(Image.open(path).convert('RGBA'));image=rgba[:,:,:3]
    result=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=np.ascontiguousarray(image)))
    raw=np.array([[v.x,v.y,v.z] for v in result.face_landmarks[0]])[:468]
    raw*=np.array([image.shape[1],-image.shape[0],-image.shape[1]])
    origin=raw[168].copy();rotation=np.array(result.facial_transformation_matrixes[0])[:3,:3]
    target=(raw-origin)@rotation;scale=4.95/(target[10,1]-target[152,1])
    centre=(target[234,0]+target[454,0])*.5;target[:,0]-=centre;target*=scale
    chin=target[152,1];target[:,1]+=25.05-chin
    width=target[454,0]-target[234,0];xs=np.clip(width,3.65,4.65)/width;target[:,0]*=xs
    front=1.55-target[10,2]*.90;target[:,2]=target[:,2]*.90+front
    skin=np.median(np.concatenate([bilinear(image,np.array([raw[n,0]]),np.array([-raw[n,1]])) for n in [50,101,118,280,330,347]]),axis=0)
    # Remove broad photographic illumination so the real normals can light the
    # skin. Fine features, pores, eye colour and expressions stay in the albedo.
    tiny=cv2.resize(image.astype(np.float32),(256,256))
    luminance=np.mean(tiny,axis=2);illum=cv2.GaussianBlur(luminance,(0,0),13)
    illum=cv2.resize(illum,(image.shape[1],image.shape[0]))
    face_luma=np.mean(skin);gain=np.clip(face_luma/np.maximum(illum,1),.88,1.16)
    image=np.clip(image.astype(np.float32)*gain[...,None],0,255)
    return image,rgba[:,:,3],target,raw,origin,rotation,scale,centre,chin,xs,front,skin

def tube(points,radius,vertices,faces,uv,seed,sides=5):
    start=len(vertices);rng=np.random.default_rng(seed)
    for i,p in enumerate(points):
        direction=points[min(i+1,len(points)-1)]-points[max(0,i-1)]
        direction/=max(np.linalg.norm(direction),1e-6)
        basis=np.cross(direction,[0,0,1])
        if np.linalg.norm(basis)<.01:basis=np.cross(direction,[1,0,0])
        basis/=np.linalg.norm(basis);other=np.cross(direction,basis)
        r=radius*(1-.65*(i/(len(points)-1))**3)
        for j in range(sides):
            a=j/sides*np.pi*2
            vertices.append(p+r*(basis*np.cos(a)+other*np.sin(a)))
            uv.append([.05+(seed%60)/70+j/sides*.012,(1024+8+i/(len(points)-1)*112)/1152])
    for i in range(len(points)-1):
        for j in range(sides):
            a=start+i*sides+j;b=start+i*sides+(j+1)%sides;c=a+sides;d=b+sides
            faces.extend([[a,c,b],[b,c,d]])
    for j in range(1,sides-1):
        faces.append([start,start+j+1,start+j])
        end=start+(len(points)-1)*sides;faces.append([end,end+j,end+j+1])

def make_hair(vertices,faces,uv,kind,head_normals,key,eligible_mask=None):
    if kind not in ['locs','curls','twists']:return vertices,faces,uv
    points=vertices.tolist();tris=faces.tolist();coords=uv.tolist()
    seed=sum(ord(c) for c in key);rng=np.random.default_rng(seed)
    if kind=='locs':
        for i in range(56):
            angle=rng.uniform(.85,5.43);x=1.92*np.sin(angle);z=1.88*np.cos(angle)-.2
            y=30.3+rng.uniform(-.3,.5);length=rng.uniform(7,10)
            path=np.array([[x+.15*np.sin(t*7+i),y-t*length,z+.12*np.sin(t*9+i)] for t in np.linspace(0,1,15)])
            tube(path,rng.uniform(.065,.09),points,tris,coords,i)
    else:
        eligible=np.flatnonzero((vertices[:,1]>29.6)&(head_normals[:,1]>-.1)&(eligible_mask if eligible_mask is not None else True))
        if len(eligible)==0:return vertices,faces,uv
        count=360 if kind=='curls' else 190
        for i,index in enumerate(rng.choice(eligible,count,replace=True)):
            p=vertices[index];n=head_normals[index]
            length=rng.uniform(.10,.20) if kind=='curls' else rng.uniform(.20,.40)
            tangent=np.cross(n,[1,0,0]);tangent/=max(np.linalg.norm(tangent),1e-6)
            other=np.cross(n,tangent);r=.035 if kind=='curls' else .025
            path=np.array([p+n*(.01+length*t)+tangent*np.sin(t*2*np.pi)*r+other*(1-np.cos(t*2*np.pi))*r for t in np.linspace(0,1,6)])
            tube(path,.010 if kind=='curls' else .025,points,tris,coords,i,sides=4)
    return np.array(points),np.array(tris),np.array(coords)

def lod(vertices,faces,uv,target):
    if len(faces)<=target:return vertices,faces,uv
    p,f,collapses=fast_simplification.simplify(vertices,faces,target_count=target,return_collapses=True)
    _,_,mapping=fast_simplification.replay_simplification(vertices.astype(np.float32),faces.astype(np.int32),collapses)
    coords=np.zeros((len(p),2));counts=np.zeros(len(p))
    np.add.at(coords,mapping,uv);np.add.at(counts,mapping,1)
    coords/=np.maximum(counts[:,None],1)
    return p,f,coords

def read_head(path):
    data=path.read_bytes();magic,skin,count=struct.unpack_from('>III',data);offset=12;last=None
    for level in range(count):
        nv,nt=struct.unpack_from('>II',data,offset);offset+=8
        arrays=[]
        for size in [nv*3,nv*3,nv*2]:
            arrays.append(np.frombuffer(data,dtype='>f4',count=size,offset=offset).astype(float));offset+=size*4
        faces=np.frombuffer(data,dtype='>i4',count=nt*3,offset=offset).astype(int).reshape(-1,3);offset+=nt*12
        last=(arrays[0].reshape(-1,3),faces,arrays[2].reshape(-1,2))
    return skin,last

def write_head(path,skin,vertices,faces,uv):
    with path.open('wb') as stream:
        stream.write(struct.pack('>III',0x4D534831,skin,3))
        for count in [4800,10500,len(faces)]:
            p,f,t=lod(vertices,faces,uv,count);nr=normals(p,f)
            stream.write(struct.pack('>II',len(p),len(f)))
            for a in [p,nr,t]:stream.write(np.asarray(a,dtype='>f4').tobytes())
            stream.write(np.asarray(f,dtype='>i4').tobytes())

def refresh_hair(key,root,output,detector,segmenter):
    skin,(vertices,faces,uv)=read_head(output/(key+'.mesh'))
    fibre=np.flatnonzero((uv[:,1]>1024/1152)&(uv[:,0]<.94))
    count=int(fibre[0]) if len(fibre) else len(vertices)
    vertices=vertices[:count];uv=uv[:count];faces=faces[np.max(faces,axis=1)<count]
    vertices[:,0]/=.8;vertices[:,1]=24+(vertices[:,1]+.7-24)/.8;vertices[:,2]/=.8
    image,alpha,target,raw,origin,rotation,scale,centre,chin,xs,front,_=photo_fit(key,root,detector)
    result=segmenter.segment(mp.Image(image_format=mp.ImageFormat.SRGB,data=np.ascontiguousarray(np.clip(image,0,255).astype('uint8'))))
    categories=cv2.resize(result.category_mask.numpy_view(),(image.shape[1],image.shape[0]),interpolation=cv2.INTER_NEAREST)
    canonical=vertices.copy();canonical[:,0]/=xs;canonical[:,1]-=25.05-chin;canonical[:,2]=(canonical[:,2]-front)/.9
    canonical/=scale;canonical[:,0]+=centre;projected=canonical@rotation.T+origin
    sx=np.clip(projected[:,0].astype(int),0,image.shape[1]-1);sy=np.clip(-projected[:,1].astype(int),0,image.shape[0]-1)
    normal=normals(vertices,faces);hair_mask=(categories[sy,sx]==1)|(normal[:,2]<.05)
    vertices,faces,uv=make_hair(vertices,faces,uv,HAIR[key],normal,key,hair_mask)
    vertices[:,0]*=.8;vertices[:,1]=24+(vertices[:,1]-24)*.8-.7;vertices[:,2]*=.8
    write_head(output/(key+'.mesh'),skin,vertices,faces,uv)
    print('Refined hair',key,len(faces),flush=True)

def bake_atlas(key,output,vertices,faces,uv,normal,scan_color,photo_data,categories):
    image,alpha,target,raw,origin,rotation,scale,centre,chin,xs,front,skin=photo_data
    kind=HAIR[key]
    attrs=np.column_stack((vertices,normal))
    pixels,mask=rasterize(uv*1023,faces,attrs,1024,1024)
    position=pixels[:,:,:3];n=pixels[:,:,3:]
    mapped=LinearNDInterpolator(target[:,:2],np.column_stack((raw[:,0],-raw[:,1])))(position[:,:,:2])
    canonical=position.copy();canonical[:,:,0]/=xs;canonical[:,:,1]-=25.05-chin;canonical[:,:,2]=(canonical[:,:,2]-front)/.9
    canonical/=scale;canonical[:,:,0]+=centre
    projected=canonical@rotation.T+origin
    valid=np.isfinite(mapped[:,:,0]);sx=np.where(valid,mapped[:,:,0],projected[:,:,0]);sy=np.where(valid,mapped[:,:,1],-projected[:,:,1])
    photo=bilinear(image,sx,sy)
    ix=np.clip(sx.astype(int),0,image.shape[1]-1);iy=np.clip(sy.astype(int),0,image.shape[0]-1)
    tags=categories[iy,ix]
    foreground=np.isin(tags,[1,2,3])|((tags==4)&(position[:,:,1]>29.7))
    foreground=foreground*(alpha[iy,ix]/255)
    original=cv2.resize(scan_color.astype(np.float32),(1024,1024))
    # Keep the scan's ear folds and skin microstructure, with each person's tone.
    baseline=np.array([209,162,143]);base_color=np.clip(original/baseline*skin,0,255)
    hair_samples=image[(categories==1)]
    hair=np.percentile(hair_samples,35,axis=0) if len(hair_samples)>10 else np.array([35,28,24])
    if kind=='beanie':hair=np.array([24,24,27])
    theta=np.arctan2(position[:,:,0],position[:,:,2]+.2)
    line=30.05 if kind=='crop' else 29.6
    line-=.8*(1-np.cos(theta))*.5
    hair_weight=smooth(line-.08,line+.08,position[:,:,1]) if kind!='bald' else np.zeros(mask.shape)
    if kind=='crop':hair_weight=smooth(29.3,31.6,position[:,:,1])*.24
    yy,xx=np.mgrid[0:1024,0:1024];grain=np.random.default_rng(14).normal(0,1.4,(1024,1024,1))
    fibre=(np.sin(xx*.91+np.sin(yy*.03)*2)*2)[...,None]
    hair_color=np.clip(hair+grain+fibre,0,255)
    base_color=base_color*(1-hair_weight[:,:,None])+hair_color*hair_weight[:,:,None]
    coverage=smooth(-.05,.55,n[:,:,2])*smooth(24.7,25.2,position[:,:,1])
    coverage=np.maximum(coverage,.8*((tags==1)|(tags==4))*smooth(29.6,30,position[:,:,1])*smooth(-.2,.15,n[:,:,2]))
    coverage*=foreground
    # Feather only the outer camera/segmentation border; no rectangular decal.
    valid_colour=(foreground>.75)&mask&(n[:,:,2]>-.15)
    _,nearest_photo=distance_transform_edt(~valid_colour,return_indices=True)
    photo[~valid_colour]=photo[nearest_photo[0][~valid_colour],nearest_photo[1][~valid_colour]]
    coverage=cv2.GaussianBlur(coverage.astype(np.float32),(0,0),6.0)
    # Blend colour gradients over a wider band than pores/eye/skin detail.
    photo_low=cv2.GaussianBlur(photo.astype(np.float32),(0,0),12)
    base_low=cv2.GaussianBlur(base_color.astype(np.float32),(0,0),12)
    low_coverage=cv2.GaussianBlur(coverage,(0,0),18)
    colour=base_color+coverage[:,:,None]*(photo-photo_low-base_color+base_low)+low_coverage[:,:,None]*(photo_low-base_low)
    _,nearest=distance_transform_edt(~mask,return_indices=True)
    colour[~mask]=colour[nearest[0][~mask],nearest[1][~mask]]
    atlas=np.zeros((1152,1024,3),np.uint8);atlas[:1024]=np.clip(colour,0,255).astype('uint8')
    band_y,band_x=np.mgrid[0:128,0:1024]
    band=(np.sin(band_x*2.4)+np.sin(band_x*.37+band_y*.045))*3
    atlas[1024:]=np.clip(hair+band[:,:,None],0,255).astype('uint8')
    atlas[1024:,992:]=np.clip(skin,0,255).astype('uint8')
    Image.fromarray(atlas).save(output/(key+'.png'))
    return atlas,tags

def refresh_texture(key,root,output,detector,segmenter):
    _,(vertices,faces,uv)=read_head(output/(key+'.mesh'))
    fibre=np.flatnonzero((uv[:,1]>1024/1152)&(uv[:,0]<.94))
    count=int(fibre[0]) if len(fibre) else len(vertices)
    vertices=vertices[:count];uv=uv[:count];faces=faces[np.max(faces,axis=1)<count]
    vertices[:,0]/=.8;vertices[:,1]=24+(vertices[:,1]+.7-24)/.8;vertices[:,2]/=.8
    uv[:,1]*=1152/1024
    photo_data=photo_fit(key,root,detector);image=photo_data[0]
    result=segmenter.segment(mp.Image(image_format=mp.ImageFormat.SRGB,data=np.ascontiguousarray(np.clip(image,0,255).astype('uint8'))))
    categories=cv2.resize(result.category_mask.numpy_view(),(image.shape[1],image.shape[0]),interpolation=cv2.INTER_NEAREST)
    scan_color=np.array(Image.open(root/'head-scan/Map-COL.jpg').convert('RGB'))
    bake_atlas(key,output,vertices,faces,uv,normals(vertices,faces),scan_color,photo_data,categories)
    print('Blended scalp',key,flush=True)

def build_head(key,source,root,output,detector,segmenter,template):
    base,faces,uv,anchors,scan_color=template
    photo_data=photo_fit(key,root,detector)
    image,alpha,target,raw,origin,rotation,scale,centre,chin,xs,front,skin=photo_data
    kind=HAIR[key];base=base.copy();anchors=anchors.copy()
    result=segmenter.segment(mp.Image(image_format=mp.ImageFormat.SRGB,data=np.ascontiguousarray(np.clip(image,0,255).astype('uint8'))))
    categories=cv2.resize(result.category_mask.numpy_view(),(image.shape[1],image.shape[0]),interpolation=cv2.INTER_NEAREST)
    yy,xx=np.mgrid[:image.shape[0],:image.shape[1]]
    left,right=np.min(raw[:,0]),np.max(raw[:,0]);eye_y=-np.mean(raw[[33,263],1])
    region=(xx>left)&(xx<right)&(yy<eye_y)&(alpha>100)&np.isin(categories,[1,2,3,4])
    rows=np.flatnonzero(np.sum(region,axis=1)>max(6,(right-left)*.08))
    crown=31.3
    if len(rows):
        top=int(rows[0]);col=np.mean(np.flatnonzero(region[top]))
        point=(np.array([col,-top,raw[10,2]])-origin)@rotation
        crown=np.clip(point[1]*scale+25.05-chin,30.8,32.0)
    upper=np.maximum(base[:,1]-30,0);base[:,1]=np.minimum(base[:,1],30)+upper*(crown-30)/max(base[:,1].max()-30,.1)
    ratio=(target[454,0]-target[234,0])/(anchors[454,0]-anchors[234,0]);base[:,0]*=ratio;anchors[:,0]*=ratio
    selected=np.unique(np.r_[np.arange(0,468,3),OVAL,[33,133,263,362,61,291,0,17,13,14,1,2,4,168]])
    delta=target[selected]-anchors[selected];delta[:,2]*=.35
    fixed=np.array([[-2,28,-2],[2,28,-2],[0,31.5,-1.8],[0,23,-.5],[-1,24,-.7],[1,24,-.7]])
    morph=RBFInterpolator(np.vstack((anchors[selected],fixed)),np.vstack((delta,np.zeros_like(fixed))),kernel='thin_plate_spline',smoothing=.035)
    weight=smooth(-.8,.9,base[:,2])*smooth(23,25,base[:,1])*(1-.65*smooth(30.2,32.0,base[:,1]))
    vertices=base+morph(base)*weight[:,None]
    neck=1-smooth(23.0,25.0,vertices[:,1])
    vertices[:,0]=vertices[:,0]*(1-neck)+1.15*np.tanh(vertices[:,0]/1.15)*neck
    vertices[:,2]*=1-.20*neck
    vertices,faces,uv=clip_neck(vertices,faces,uv)
    normal=normals(vertices,faces)
    atlas,tags=bake_atlas(key,output,vertices,faces,uv,normal,scan_color,photo_data,categories)
    vx=np.clip((uv[:,0]*1023).astype(int),0,1023);vy=np.clip((uv[:,1]*1023).astype(int),0,1023)
    hair_mask=(tags[vy,vx]==1)|(normal[:,2]<.05)
    uv[:,1]*=1024/1152
    vertices,faces,uv=make_hair(vertices,faces,uv,kind,normal,key,hair_mask)
    # Shared head-to-body transform; these are ordinary mesh coordinates now.
    vertices[:,0]*=.8;vertices[:,1]=24+(vertices[:,1]-24)*.8-.7;vertices[:,2]*=.8
    skin_argb=(255<<24)|(int(skin[0])<<16)|(int(skin[1])<<8)|int(skin[2])
    write_head(output/(key+'.mesh'),skin_argb,vertices,faces,uv)
    preview,_,_,_=render(vertices,faces,uv,atlas)
    preview.save(output/(key+'-preview.jpg'))
    print(key,len(vertices),len(faces),'skin',hex(skin_argb),flush=True)


def load_scan(path):
    data=path.read_bytes();size,_=struct.unpack_from('<II',data,12)
    document=json.loads(data[20:20+size]);binary=data[28+size:]
    def array(index):
        a=document['accessors'][index];view=document['bufferViews'][a['bufferView']]
        count={'SCALAR':1,'VEC2':2,'VEC3':3}[a['type']]
        dtype={5123:'<u2',5126:'<f4'}[a['componentType']]
        return np.frombuffer(binary,dtype=dtype,count=a['count']*count,offset=view.get('byteOffset',0)+a.get('byteOffset',0)).reshape(-1,count).copy()
    primitive=document['meshes'][0]['primitives'][0]
    return array(primitive['attributes']['POSITION']),array(primitive['indices']).reshape(-1,3),array(primitive['attributes']['TEXCOORD_0'])


def rasterize(screen,faces,values,width,height,depth=None):
    """Z-buffer or UV rasterizer with interpolated per-vertex attributes."""
    result=np.zeros((height,width,values.shape[1]),np.float32)
    buffer=np.full((height,width),-1e10,np.float32)
    for tri in faces:
        a,b,c=screen[tri]
        x0=max(0,int(np.floor(min(a[0],b[0],c[0]))));x1=min(width-1,int(np.ceil(max(a[0],b[0],c[0]))))
        y0=max(0,int(np.floor(min(a[1],b[1],c[1]))));y1=min(height-1,int(np.ceil(max(a[1],b[1],c[1]))))
        if x1<x0 or y1<y0:continue
        denom=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(denom)<1e-7:continue
        yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
        wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/denom
        wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/denom
        wc=1-wa-wb
        z=wa*depth[tri[0]]+wb*depth[tri[1]]+wc*depth[tri[2]] if depth is not None else np.ones_like(wa)
        mask=(wa>=-1e-5)&(wb>=-1e-5)&(wc>=-1e-5)&(z>buffer[y0:y1+1,x0:x1+1])
        patch=wa[...,None]*values[tri[0]]+wb[...,None]*values[tri[1]]+wc[...,None]*values[tri[2]]
        result[y0:y1+1,x0:x1+1][mask]=patch[mask];buffer[y0:y1+1,x0:x1+1][mask]=z[mask]
    return result,buffer>-1e9


def render(vertices,faces,uv,texture,yaw=0,size=640):
    angle=np.deg2rad(yaw);rotation=np.array([[np.cos(angle),0,np.sin(angle)],[0,1,0],[-np.sin(angle),0,np.cos(angle)]])
    projected=vertices@rotation.T
    scale=(size-70)/np.ptp(projected[:,1]);centre=(projected.min(0)+projected.max(0))*.5
    screen=np.column_stack(((projected[:,0]-centre[0])*scale+size/2,size/2-(projected[:,1]-centre[1])*scale))
    values=np.column_stack((uv,vertices))
    pixels,mask=rasterize(screen,faces,values,size,size,projected[:,2])
    rgb=bilinear(texture,pixels[...,0]*(texture.shape[1]-1),pixels[...,1]*(texture.shape[0]-1))
    rgb[~mask]=[25,33,44]
    return Image.fromarray(np.clip(rgb,0,255).astype('uint8')),pixels[...,2:],mask,(screen,scale,centre)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--references',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--inspect',action='store_true');parser.add_argument('--one');parser.add_argument('--refresh-hair',action='store_true');parser.add_argument('--refresh-textures',action='store_true');args=parser.parse_args();args.output.mkdir(exist_ok=True,parents=True)
    vertices,faces,uv=load_scan(args.references/'head-scan/LeePerrySmith.glb')
    uv[:,1]=1-uv[:,1]
    texture=np.array(Image.open(args.references/'head-scan/Map-COL.jpg').convert('RGB'))
    if args.inspect:
        board=Image.new('RGB',(640*3,640*2));draw=ImageDraw.Draw(board)
        for i,yaw in enumerate([0,90,180,270,45,-45]):
            image,_,_,_=render(vertices,faces,uv,texture,yaw);board.paste(image,(i%3*640,i//3*640));draw.text((i%3*640+10,i//3*640+10),str(yaw),fill='white')
        board.save(args.output/'scan-views.jpg');return
    options=vision.FaceLandmarkerOptions(base_options=python.BaseOptions(model_asset_path=str(args.references/'face_landmarker.task')),num_faces=1,output_facial_transformation_matrixes=True,min_face_detection_confidence=.25,min_face_presence_confidence=.25)
    segment_options=vision.ImageSegmenterOptions(base_options=python.BaseOptions(model_asset_path=str(args.references/'selfie_multiclass.tflite')),output_category_mask=True)
    with vision.FaceLandmarker.create_from_options(options) as detector,vision.ImageSegmenter.create_from_options(segment_options) as segmenter:
        if args.refresh_textures:
            sources=json.loads((args.references/'web-sources.json').read_text())
            for key in sources:
                if args.one and key!=args.one:continue
                refresh_texture(key,args.references,args.output,detector,segmenter)
            return
        if args.refresh_hair:
            sources=json.loads((args.references/'web-sources.json').read_text())
            for key in sources:
                if args.one and key!=args.one:continue
                refresh_hair(key,args.references,args.output,detector,segmenter)
            return
        frame,positions,mask,_=render(vertices,faces,uv,texture)
        frame.save(args.output/'template-front.jpg')
        result=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=np.array(frame)))
        if not result.face_landmarks:raise RuntimeError('Template face not detected')
        landmark=np.array([[v.x,v.y] for v in result.face_landmarks[0]])[:468]*640
        anchors=bilinear(positions,landmark[:,0],landmark[:,1])
        factor=4.95/(anchors[10,1]-anchors[152,1]);offset=anchors[168,0]
        vertices[:,0]-=offset;anchors[:,0]-=offset;vertices*=factor;anchors*=factor
        dy=25.05-anchors[152,1];dz=1.55-anchors[10,2]
        vertices+=np.array([0,dy,dz]);anchors+=np.array([0,dy,dz])
        template=(vertices,faces,uv,anchors,texture)
        sources=json.loads((args.references/'web-sources.json').read_text())
        for key,source in sources.items():
            if args.one and key!=args.one:continue
            build_head(key,source,args.references,args.output,detector,segmenter,template)
        (args.output/'sources.json').write_text(json.dumps(sources,indent=2),encoding='utf-8')
        (args.output/'HEAD_SCAN_LICENSE.txt').write_bytes((args.references/'head-scan/LeePerrySmith_License.txt').read_bytes())


if __name__=='__main__':main()
