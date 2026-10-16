from PIL import Image, ImageDraw
from pathlib import Path
import json, zipfile, io
import argparse
parser=argparse.ArgumentParser();parser.add_argument('repo',type=Path);parser.add_argument('--language',choices=['en','de'],default='en');parser.add_argument('--overlay',type=Path);args=parser.parse_args();repo=args.repo
assets=repo/'plugins/BetterHud/assets'; out=Path(__file__).parent/'generated';out.mkdir(exist_ok=True)
# A small pixel alphabet keeps menu labels legible at Minecraft's native GUI scale.
glyphs={
'A':['010','101','111','101','101'],'B':['110','101','110','101','110'],'C':['011','100','100','100','011'],'D':['110','101','101','101','110'],'E':['111','100','110','100','111'],'F':['111','100','110','100','100'],'G':['011','100','101','101','011'],'H':['101','101','111','101','101'],'I':['111','010','010','010','111'],'K':['101','101','110','101','101'],'L':['100','100','100','100','111'],'M':['10001','11011','10101','10101','10001'],'N':['1001','1101','1011','1001','1001'],'O':['010','101','101','101','010'],'P':['110','101','110','100','100'],'R':['110','101','110','101','101'],'S':['011','100','010','001','110'],'T':['111','010','010','010','010'],'U':['101','101','101','101','111'],'V':['101','101','101','101','010'],'W':['10101','10101','10101','11011','10001'],'X':['101','101','010','101','101'],'Y':['101','101','010','010','010'],'Z':['111','001','010','100','111'],' ':['00']*5}
def text(d,s,cx,y,color):
 widths=[len(glyphs[c][0])+1 for c in s]; x=cx-(sum(widths)-1)//2
 for c,w in zip(s,widths):
  for yy,row in enumerate(glyphs[c]):
   for xx,v in enumerate(row):
    if v=='1':d.point((x+xx,y+yy),fill=color)
  x+=w
def sprite(im,name,box):
 src=Image.open(assets/name).convert('RGBA');src=src.crop(src.getbbox());src.thumbnail((box[2],box[3]),Image.Resampling.NEAREST)
 im.alpha_composite(src,(box[0]+(box[2]-src.width)//2,box[1]+(box[3]-src.height)//2))
providers=[{'type':'space','advances':{'\ue000':-8,'\ue010':-1,'\ue011':-257}}]
updates={}
atlas=Image.open(Path(__file__).parent/'menu-motifs.png').convert('RGBA')
def paste_hd(im,src,box):
 src=src.copy();ratio=min(box[2]*2/src.width,box[3]*2/src.height);src=src.resize((max(1,round(src.width*ratio)),max(1,round(src.height*ratio))),Image.Resampling.LANCZOS)
 im.alpha_composite(src,(box[0]*2+(box[2]*2-src.width)//2,box[1]*2+(box[3]*2-src.height)//2))
def motif(row,col):
 return atlas.crop((col*384,row*1024//3,(col+1)*384,(row+1)*1024//3))
def logo(theme):
 f={'halloween':'beast_halloween_header.png','standard':'beast_standard_header.png','winter':'beast_winter_header.png'}[theme]
 im=Image.open(assets/f).convert('RGBA');return im.crop(im.getbbox())
for season,(theme,accent,light) in enumerate([('halloween','#a72cdd','#f5b041'),('standard','#29a7ef','#9ff3ff'),('winter','#75dce9','#edffff')]):
 for kind,heading in enumerate(['LOBBY','SERVER','EVENTS','EFFECTS','DESIGNS','SETTINGS']):
  if args.language=='de':heading={'EFFECTS':'EFFEKTE','SETTINGS':'SPRACHE'}.get(heading,heading)
  base=Image.new('RGBA',(256,256));d=ImageDraw.Draw(base)
  d.rectangle((0,0,175,221),fill='#090c16');d.rectangle((1,1,174,220),outline=accent,width=2);d.rectangle((4,4,171,129),fill='#111323',outline='#37344c')
  d.line((7,130,168,130),fill=light)
  # The player inventory retains its usual coordinates after a six-row menu.
  d.rectangle((4,131,171,218),fill='#b0b8ca')
  for y in [144,162,180,202]:
   for x in range(8,169,18):
    d.rectangle((x-1,y-1,x+16,y+16),fill='#536277');d.line((x-1,y-1,x+16,y-1),fill='#334254');d.line((x-1,y-1,x-1,y+16),fill='#334254');d.line((x,y+16,x+16,y+16),fill='#d4deeb')
  # Illustrated cards: generous image area, restrained frame, captions with native pixel lettering.
  layouts=[]
  if kind in [0,3]:
   labels=['SERVER','DESIGNS','EVENTS','EFFECTS'] if kind==0 else ['AUTO','SNOW','FOG','OFF']
   if args.language=='de':labels=[{'EFFECTS':'EFFEKTE','SNOW':'SCHNEE','FOG':'NEBEL','OFF':'AUS','PUMPKINS':'KUERBISSE','ENGLISH':'ENGLISCH','GERMAN':'DEUTSCH','BACK':'ZURUECK'}.get(v,v) for v in labels]
   for i,label in enumerate(labels):
    x=8 if i%2==0 else 98;y=38 if i<2 else 74;w=70;h=32
    d.rectangle((x-1,y-1,x+w,y+h),fill='#050710');d.rectangle((x,y,x+w-1,y+h-1),fill='#1a1b31',outline=accent);d.line((x+2,y+1,x+w-3,y+1),fill=light)
    text(d,label,x+49,y+14,'#ffffff');layouts.append((i,(x+2,y+2,30,28)))
  else:
   labels={1:['BEASTSMP','SMP','LOBBY'],2:['PUMPKINS','DESIGNS','WINTER'],4:['HALLOWEEN','STANDARD','WINTER'],5:['ENGLISH','GERMAN','BACK']}[kind]
   if args.language=='de':labels=[{'EFFECTS':'EFFEKTE','SNOW':'SCHNEE','FOG':'NEBEL','OFF':'AUS','PUMPKINS':'KUERBISSE','ENGLISH':'ENGLISCH','GERMAN':'DEUTSCH','BACK':'ZURUECK'}.get(v,v) for v in labels]
   for i,label in enumerate(labels):
    x=8+i*55;y=40;w=50;h=66
    d.rectangle((x-1,y-1,x+w,y+h),fill='#050710');d.rectangle((x,y,x+w-1,y+h-1),fill='#1a1b31',outline=accent);d.line((x+2,y+1,x+w-3,y+1),fill=light)
    text(d,label,x+25,94,'#ffffff');layouts.append((i,(x+2,44,w-4,47)))
  # Footer controls occupy their own final row.
  d.rectangle((7,110,24,127),fill='#222039',outline=accent);d.line([(18,114),(12,119),(18,124)],fill=light,width=2);d.line((12,119,21,119),fill=light)
  d.rectangle((151,110,168,127),fill='#222039',outline=accent);text(d,'E',160,117,light)
  text(d,heading,88,112,light);text(d,'X',88,122,'#ffc6d7')
  shifted=Image.new('RGBA',(256,256));shifted.alpha_composite(base,(0,20));q=ImageDraw.Draw(shifted);q.rectangle((0,0,175,23),fill='#090c16');q.rectangle((1,1,174,24),outline=accent,width=2);q.rectangle((4,4,171,25),fill='#111323');q.rectangle((3,22,172,25),fill='#111323');im=shifted.resize((512,512),Image.Resampling.NEAREST)
  paste_hd(im,logo(theme),(10,0,156,54))
  for i,box in layouts:
   box=(box[0],box[1]+20,box[2],box[3])
   if kind==0:src=motif(season,i)
   elif kind==1:src=motif(season,0)
   elif kind==2:src=motif(0,2) if i==0 else motif(season,1) if i==1 else motif(2,2)
   elif kind==4:src=motif([0,1,2][i],0)
   else:src=motif(season,3) if i==0 else motif(2,3) if i==1 else motif(0,3) if i==2 else motif(1,3)
   paste_hd(im,src,box)
   if kind==4:paste_hd(im,logo(['halloween','standard','winter'][i]),(box[0],101,box[2],12))
   if kind==3 and i==3:
    q=ImageDraw.Draw(im);x,y,w,h=box;q.line((2*x+10,2*y+5,2*(x+w)-10,2*(y+h)-5),fill='#f26d88',width=4)
  name=f'{theme}_{kind}';im.crop((0,0,352,484)).save(out/f'{name}_preview.png')
  for tile in range(4):
   tx=tile%2;ty=tile//2;part=im.crop((tx*256,ty*256,(tx+1)*256,(ty+1)*256));part.putpixel((255,255),(1,1,1,1))
   path=f'assets/beastsmp/textures/font/menu_{name}_{tile}.png';buf=io.BytesIO();part.save(buf,format='PNG');updates[path]=buf.getvalue()
   providers.append({'type':'bitmap','file':f'beastsmp:font/menu_{name}_{tile}.png','height':128,'ascent':33-ty*128,'chars':[chr(0xe100+season*24+kind*4+tile)]})
updates['assets/beastsmp/font/lobby_menu.json']=json.dumps({'providers':providers},ensure_ascii=False,indent=2).encode()
blank=Image.new('RGBA',(16,16));buf=io.BytesIO();blank.save(buf,format='PNG')
updates.update({'assets/beastsmp/textures/item/menu_blank.png':buf.getvalue(),'assets/beastsmp/models/item/menu_blank.json':json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'beastsmp:item/menu_blank'}}).encode(),'assets/beastsmp/items/menu_blank.json':json.dumps({'model':{'type':'minecraft:model','model':'beastsmp:item/menu_blank'}}).encode()})
if args.overlay:
 args.overlay.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(args.overlay,'w',zipfile.ZIP_DEFLATED) as z:
  for key,value in updates.items():z.writestr(key,value)
 print('Built menu language overlay',args.overlay);raise SystemExit(0)
pack=repo/'plugins/BetterHud/beastsmp-base.zip';temp=pack.with_suffix('.new.zip')
with zipfile.ZipFile(pack) as old,zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as new:
 for info in old.infolist():
  if info.filename not in updates and (not info.filename.startswith('assets/beastsmp/textures/font/menu_') or info.filename.endswith('/menu_logo.png')):new.writestr(info,old.read(info.filename))
 for name,data in updates.items():new.writestr(name,data)
temp.replace(pack)
preview=Image.new('RGB',(352*5,484),'#080b14')
for i in range(5):preview.paste(Image.open(out/f'halloween_{i}_preview.png'),(352*i,0))
preview.save(out/'preview_all.png');print('Updated illustrated menu pack',pack)
