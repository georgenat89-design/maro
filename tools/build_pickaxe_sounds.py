"""Import isolated tool audio from the user's six reference recordings.
Requires imageio-ffmpeg. Full recordings are never copied into the project.
Run with --references containing files by their original basename on another machine.
"""
from pathlib import Path
import argparse,hashlib,json,subprocess
import imageio_ffmpeg
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--references',type=Path);args=parser.parse_args()
media={
 'driver':('C:/Users/Nate/Downloads/Download.mp4',{'equip':(.02,.43),'swing':(.73,1.52),'impact':(.87,1.52)}),
 'merry_mint_axe':('C:/Users/Nate/Videos/Replay 2026-10-14 18-10-44.mp4',{'equip':(.51,1.09),'swing':(1.25,2.12),'impact':(1.36,2.12)}),
 'leviathan_axe':('C:/Users/Nate/Videos/Replay 2026-10-14 18-11-49.mp4',{'equip':(2.85,3.39),'swing':(3.51,4.36),'impact':(3.62,4.36)}),
 'cold_snap':('C:/Users/Nate/Videos/Replay 2026-10-14 18-12-32.mp4',{'equip':(3.46,4.03),'swing':(4.24,5.12),'impact':(4.37,5.12)}),
 'star_wand':('C:/Users/Nate/Videos/Replay 2026-10-14 18-13-05.mp4',{'equip':(1.00,1.56),'swing':(1.79,2.68),'impact':(1.93,2.68)})}
out=root/'src/main/resources/assets/maro/sounds/pickaxe';out.mkdir(parents=True,exist_ok=True)
catalog_path=out.parents[1]/'sounds.json';catalog=json.loads(catalog_path.read_text());catalog={k:v for k,v in catalog.items() if not k.startswith('pickaxe.')}
keep=set();manifest=[];ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
for key,(original,clips) in media.items():
 source=args.references/Path(original).name if args.references else Path(original)
 digest=hashlib.sha256(source.read_bytes()).hexdigest()
 for kind,(start,end) in clips.items():
  dest=out/f'{key}_{kind}.ogg';keep.add(dest.name);duration=end-start
  # Preserve pitch/timing. Short fades remove edit clicks; gain is bounded and does not clip.
  filters=f'aformat=channel_layouts=mono,volume=3,alimiter=limit=0.9:level=false,afade=t=in:d=0.004,afade=t=out:st={duration-.025:.3f}:d=0.025'
  subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-ss',str(start),'-i',str(source),'-t',str(duration),'-vn','-af',filters,'-ar','44100','-c:a','libvorbis','-q:a','5',str(dest)],check=True)
  catalog[f'pickaxe.{key}.{kind}']={'sounds':[{'name':f'maro:pickaxe/{key}_{kind}','preload':True}]}
  manifest.append({'effect':f'{key}_{kind}','source':source.name,'source_sha256':digest,'start_seconds':start,'end_seconds':end,'output_sha256':hashlib.sha256(dest.read_bytes()).hexdigest()})
for file in out.glob('*.ogg'):
 if file.name not in keep:file.unlink()
catalog_path.write_text(json.dumps(catalog,indent=2)+'\n')
(root/'docs/pickaxe-audio-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Imported',len(keep),'effects from supplied recordings;',sum(f.stat().st_size for f in out.glob('*.ogg')),'bytes')
