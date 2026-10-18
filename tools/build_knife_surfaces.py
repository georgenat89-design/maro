"""Deterministic original material atlas: brushed steel, carbon, rubber and woven grips."""
from pathlib import Path
from PIL import Image
import math,random
target=Path(__file__).resolve().parents[1]/'src/main/resources/assets/maro/textures/misc'
target.mkdir(parents=True,exist_ok=True)
for finish in ('satin','damascus'):
    rng=random.Random(9471); image=Image.new('RGBA',(256,64*9));px=image.load()
    for material in range(9):
        lines=[rng.uniform(-7,7) for _ in range(64)]
        for y in range(64):
            for x in range(256):
                noise=rng.uniform(-2.5,2.5)
                if material<=2:
                    value=242+lines[y]+noise
                    if finish=='damascus' and material<2:
                        phase=y*.74+4*math.sin(x/47)+2*math.sin(x/19+y/21)
                        value=214+28*math.sin(phase)+8*math.sin(phase*2.1)+noise
                    if material==2:value=251+noise
                    # Tiny isolated machining marks, aligned with the grind.
                    if material==0 and y in (11,36,51) and 20<(x+17*y)%117<43:value-=9
                elif material==3:
                    cell=(x//5+y//5)%2
                    value=222+14*cell+6*math.sin((x if cell else y)*1.2)+noise
                elif material==4:value=220+9*((x//3+y//3)%2)+noise
                elif material==5:value=243+lines[y]*.6+noise
                elif material==6:value=245+noise
                elif material==7:value=224+11*math.sin(x*.24+math.sin(y*.31)*2)+5*math.sin(y*.8)+noise
                else:value=241+4*math.sin(x*.12+math.sin(y*.2))+noise
                value=max(0,min(255,round(value)));px[x,material*64+y]=(value,value,value,255)
    image.save(target/('knife_'+finish+'.png'),optimize=True)
    print(finish,(target/('knife_'+finish+'.png')).stat().st_size)
