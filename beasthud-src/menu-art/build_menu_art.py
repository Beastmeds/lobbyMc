from PIL import Image, ImageDraw
from pathlib import Path
import json, zipfile, io
import argparse
parser=argparse.ArgumentParser();parser.add_argument('repo',type=Path);parser.add_argument('--language',choices=['en','de'],default='en');parser.add_argument('--overlay',type=Path);parser.add_argument('--client-jar',type=Path);args=parser.parse_args();repo=args.repo
assets=repo/'plugins/BetterHud/assets'; out=Path(__file__).parent/'generated';out.mkdir(exist_ok=True)
# A small pixel alphabet keeps menu labels legible at Minecraft's native GUI scale.
glyphs={
'A':['010','101','111','101','101'],'B':['110','101','110','101','110'],'C':['011','100','100','100','011'],'D':['110','101','101','101','110'],'E':['111','100','110','100','111'],'F':['111','100','110','100','100'],'G':['011','100','101','101','011'],'H':['101','101','111','101','101'],'I':['111','010','010','010','111'],'J':['001','001','001','101','010'],'K':['101','101','110','101','101'],'L':['100','100','100','100','111'],'M':['10001','11011','10101','10101','10001'],'N':['1001','1101','1011','1001','1001'],'O':['010','101','101','101','010'],'P':['110','101','110','100','100'],'R':['110','101','110','101','101'],'S':['011','100','010','001','110'],'T':['111','010','010','010','010'],'U':['101','101','101','101','111'],'V':['101','101','101','101','010'],'W':['10101','10101','10101','11011','10001'],'X':['101','101','010','101','101'],'Y':['101','101','010','010','010'],'Z':['111','001','010','100','111'],' ':['00']*5}
native={}
if args.client_jar:
 with zipfile.ZipFile(args.client_jar) as jar:
  spec=next(v for v in json.loads(jar.read('assets/minecraft/font/include/default.json'))['providers'] if v.get('file')=='minecraft:font/ascii.png')
  sheet=Image.open(io.BytesIO(jar.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
  for y,row in enumerate(spec['chars']):
   for x,c in enumerate(row):
    tile=sheet.crop((x*8,y*8,x*8+8,y*8+8));bounds=tile.getbbox();native[c]=tile.crop((0,0,bounds[2],8)) if bounds else Image.new('RGBA',(3,8))
def caption(im,s,cx,y,color,maxwidth=66):
 if native:
  glyphs_native=[native[c] for c in s];width=sum(g.width+1 for g in glyphs_native)-1
  if width<=maxwidth:
   x=round(cx-width/2)
   for glyph in glyphs_native:
    solid=Image.new('RGBA',glyph.size,color);solid.putalpha(glyph.getchannel('A'));im.alpha_composite(solid,(x,y));x+=glyph.width+1
   return
 text(ImageDraw.Draw(im),s,cx,y+1,color)
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
providers=[{'type':'space','advances':{'\ue000':-24,'\ue010':-1,'\ue011':-257}}]
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
 for kind,heading in enumerate(['LOBBY','SERVER','EVENTS','EFFECTS','DESIGNS','SETTINGS','LANGUAGE','CHAT','DISPLAY','BOTS']):
  if args.language=='de':heading={'EFFECTS':'EFFEKTE','SETTINGS':'SPRACHE'}.get(heading,heading)
  base=Image.new('RGBA',(256,256));d=ImageDraw.Draw(base)
  d.rectangle((0,0,175,221),fill='#090c16');d.rectangle((4,4,171,129),fill='#111323',outline='#37344c')
  d.line((7,130,168,130),fill=light)
  # The player inventory retains its usual coordinates after a six-row menu.
  d.rectangle((4,131,171,218),fill='#b0b8ca')
  for y in [144,162,180,202]:
   for x in range(8,169,18):
    d.rectangle((x-1,y-1,x+16,y+16),fill='#536277');d.line((x-1,y-1,x+16,y-1),fill='#334254');d.line((x-1,y-1,x-1,y+16),fill='#334254');d.line((x,y+16,x+16,y+16),fill='#d4deeb')
  # Illustrated cards: generous image area, restrained frame, captions with native pixel lettering.
  layouts=[]
  if kind==9:
   labels=['SCHEDULE','MANUAL','DAY','NIGHT','DAYTIME','NIGHTTIME'] if args.language=='en' else ['ZEITPLAN','MANUELL','TAG','NACHT','TAGZEIT','NACHTZEIT']
   for i,label in enumerate(labels):
    x=8+(i%3)*54;y=42 if i<3 else 78;w=52;h=36
    d.rectangle((x-1,y-1,x+w,y+h),fill='#050710');d.rectangle((x,y,x+w-1,y+h-1),fill='#1a1b31',outline=accent);d.line((x+2,y+1,x+w-3,y+1),fill=light)
    caption(base,label,x+w//2,y+26,'#ffffff',50);layouts.append((i,(x+2,y+2,w-4,23)))
  elif kind in [3,5,7,8]:
   labels={3:['AUTO','SNOW','FOG','OFF'],5:['LANGUAGE','CHAT','DISPLAY','EFFECTS'],7:['CHAT','JOINS','TIPS','WELCOME'],8:['HUD','SYMBOLS','SOUNDS','TITLE']}[kind]
   if args.language=='de':labels=[{'EFFECTS':'EFFEKTE','SNOW':'SCHNEE','FOG':'NEBEL','OFF':'AUS','PUMPKINS':'KUERBISSE','ENGLISH':'ENGLISCH','GERMAN':'DEUTSCH','BACK':'ZURUECK','SETTINGS':'OPTIONEN','SERVERS':'SERVER','LANGUAGE':'SPRACHE','DISPLAY':'ANZEIGE','JOINS':'BEITRITT','TIPS':'TIPPS','WELCOME':'WILLKOMMEN','ANIMATIONS':'ANIMATION','SYMBOLS':'SYMBOLE','TITLE':'TITEL'}.get(v,v) for v in labels]
   for i,label in enumerate(labels):
    x=8 if i%2==0 else 98;y=42 if i<2 else 78;w=72;h=36
    d.rectangle((x-1,y-1,x+w,y+h),fill='#050710');d.rectangle((x,y,x+w-1,y+h-1),fill='#1a1b31',outline=accent);d.line((x+2,y+1,x+w-3,y+1),fill=light)
    caption(base,label,x+w//2,y+26,'#ffffff');layouts.append((i,(x+4,y+2,w-8,23)))
  else:
   labels={0:['SERVERS','DESIGNS','SETTINGS'],1:['BEASTSMP','SMP','LOBBY'],2:['PUMPKINS','DESIGNS','WINTER'],4:['HALLOWEEN','STANDARD','WINTER'],6:['ENGLISH','GERMAN','BACK']}[kind]
   if args.language=='de':labels=[{'EFFECTS':'EFFEKTE','SNOW':'SCHNEE','FOG':'NEBEL','OFF':'AUS','PUMPKINS':'KUERBISSE','ENGLISH':'ENGLISCH','GERMAN':'DEUTSCH','BACK':'ZURUECK','SETTINGS':'OPTIONEN','SERVERS':'SERVER','LANGUAGE':'SPRACHE','DISPLAY':'ANZEIGE','JOINS':'BEITRITT','TIPS':'TIPPS','WELCOME':'WILLKOMMEN','ANIMATIONS':'ANIMATION','SYMBOLS':'SYMBOLE','TITLE':'TITEL'}.get(v,v) for v in labels]
   for i,label in enumerate(labels):
    x=8+i*54;y=42;w=52;h=72
    d.rectangle((x-1,y-1,x+w,y+h),fill='#050710');d.rectangle((x,y,x+w-1,y+h-1),fill='#1a1b31',outline=accent);d.line((x+2,y+1,x+w-3,y+1),fill=light)
    caption(base,label,x+26,104,'#ffffff',48);layouts.append((i,(x+2,44,w-4,52)))
  # Home keeps Events; preference pages keep a real Back control.
  if kind==0:
   d.rectangle((7,114,61,130),fill='#222039',outline=accent);caption(base,'EVENTS',34,118,light)
  else:
   d.rectangle((7,114,24,130),fill='#222039',outline=accent);d.line([(18,118),(12,123),(18,128)],fill=light,width=2);d.line((12,123,21,123),fill=light)
  d.rectangle((79,114,97,130),fill='#4d1529',outline='#ef6388');caption(base,'X',88,118,'#ffffff')
  if kind in [1,2,4]:
   d.rectangle((151,114,168,130),fill='#222039',outline=accent);caption(base,'S',160,118,light)
  shifted=Image.new('RGBA',(256,256));shifted.alpha_composite(base,(0,20));q=ImageDraw.Draw(shifted);q.rectangle((0,0,175,23),fill='#090c16');q.rectangle((4,4,171,25),fill='#111323');q.rectangle((3,22,172,25),fill='#111323');im=shifted.resize((512,512),Image.Resampling.NEAREST)
  for i,box in layouts:
   box=(box[0],box[1]+20,box[2],box[3])
   if kind==0:src=motif(season,[0,1,3][i])
   elif kind==1:src=motif(season,0)
   elif kind==2:src=motif(0,2) if i==0 else motif(season,1) if i==1 else motif(2,2)
   elif kind==4:src=motif([0,1,2][i],0)
   else:src=motif(season,3) if i==0 else motif(2,3) if i==1 else motif(0,3) if i==2 else motif(1,3)
   paste_hd(im,src,box)
   if kind==4:paste_hd(im,logo(['halloween','standard','winter'][i]),(box[0],101,box[2],12))
   if kind==3 and i==3:
    q=ImageDraw.Draw(im);x,y,w,h=box;q.line((2*x+10,2*y+5,2*(x+w)-10,2*(y+h)-5),fill='#f26d88',width=4)
  expanded=Image.new('RGBA',(512,512));q=ImageDraw.Draw(expanded);q.rectangle((0,0,415,483),fill='#090c16',outline=accent,width=4);q.rectangle((7,7,408,476),outline='#39354d',width=2);expanded.alpha_composite(im,(32,0));im=expanded
  paste_hd(im,logo(theme),(8,4,192,50))
  q=ImageDraw.Draw(im);q.rectangle((0,0,415,483),outline=accent,width=4);q.rectangle((7,7,408,476),outline='#39354d',width=2)
  name=f'{theme}_{kind}';im.crop((0,0,416,484)).save(out/f'{name}_preview.png')
  for tile in range(4):
   tx=tile%2;ty=tile//2;part=im.crop((tx*256,ty*256,(tx+1)*256,(ty+1)*256));part.putpixel((255,255),(1,1,1,1))
   path=f'assets/beastsmp/textures/font/menu_{name}_{tile}.png';buf=io.BytesIO();part.save(buf,format='PNG');updates[path]=buf.getvalue()
   providers.append({'type':'bitmap','file':f'beastsmp:font/menu_{name}_{tile}.png','height':128,'ascent':33-ty*128,'chars':[chr(0xe100+season*40+kind*4+tile)]})
# Per-player ON/OFF badges overlay the four preference cards without changing click slots.
for mask in range(16):
 badge=Image.new('RGBA',(256,256));d=ImageDraw.Draw(badge)
 for i in range(4):
  x=16+(8 if i%2==0 else 98)+51;y=20+(42 if i<2 else 78)+3;on=bool(mask&(1<<i));label=('AN' if on else 'AUS') if args.language=='de' else ('ON' if on else 'OFF')
  d.rectangle((x,y,x+19,y+10),fill='#205e3c' if on else '#6a2036',outline='#8aefb0' if on else '#ff9eb1');caption(badge,label,x+10,y+1,'#ffffff',18)
 badge=badge.resize((512,512),Image.Resampling.NEAREST)
 for tile in range(4):
  tx=tile%2;ty=tile//2;part=badge.crop((tx*256,ty*256,(tx+1)*256,(ty+1)*256));part.putpixel((255,255),(1,1,1,1));buf=io.BytesIO();part.save(buf,format='PNG');path=f'assets/beastsmp/textures/font/menu_state_{mask}_{tile}.png';updates[path]=buf.getvalue()
  providers.append({'type':'bitmap','file':f'beastsmp:font/menu_state_{mask}_{tile}.png','height':128,'ascent':33-ty*128,'chars':[chr(0xe600+mask*4+tile)]})
# Admin-only footer and schedule indicator use transparent overlays.
for name,code,state in [('admin',0xe700,None),('schedule_off',0xe710,False),('schedule_on',0xe714,True)]:
 badge=Image.new('RGBA',(256,256));d=ImageDraw.Draw(badge)
 if state is None:
  d.rectangle((131,134,184,150),fill='#222039',outline='#ae73ec');caption(badge,'BOTS',158,138,'#ffffff')
 else:
  x=55;y=65;label=('AN' if state else 'AUS') if args.language=='de' else ('ON' if state else 'OFF')
  d.rectangle((x,y,x+19,y+10),fill='#205e3c' if state else '#6a2036',outline='#8aefb0' if state else '#ff9eb1');caption(badge,label,x+10,y+1,'#ffffff',18)
 badge=badge.resize((512,512),Image.Resampling.NEAREST)
 for tile in range(4):
  tx=tile%2;ty=tile//2;part=badge.crop((tx*256,ty*256,(tx+1)*256,(ty+1)*256));part.putpixel((255,255),(1,1,1,1));buf=io.BytesIO();part.save(buf,format='PNG');updates[f'assets/beastsmp/textures/font/menu_{name}_{tile}.png']=buf.getvalue()
  providers.append({'type':'bitmap','file':f'beastsmp:font/menu_{name}_{tile}.png','height':128,'ascent':33-ty*128,'chars':[chr(code+tile)]})
# Wide, inventory-free dialog header; each tile stays within Minecraft bitmap limits.
dialog_providers=[{'type':'space','advances':{'\ue010':-1}}]
for season,(theme,accent,light) in enumerate([('halloween','#aa52dd','#f2bc62'),('standard','#3baee6','#b0edff'),('winter','#86cfdf','#eefaff')]):
 banner=Image.new('RGBA',(960,220));d=ImageDraw.Draw(banner)
 for y in range(10,210):
  t=(y-10)/200;d.line((12,y,947,y),fill=(round(16-6*t),round(21-7*t),round(34-10*t),252))
 d.rounded_rectangle((6,8,953,211),radius=24,outline=accent,width=2)
 d.line((34,207,925,207),fill=light,width=1)
 d.line((34,12,925,12),fill='#344055',width=1)
 paste_hd(banner,motif(season,2),(9,14,74,80));paste_hd(banner,motif(season,2),(397,14,74,80))
 paste_hd(banner,logo(theme),(91,4,298,94))
 for x in [179,225,735,781]:d.polygon([(x,190),(x+3,193),(x,196),(x-3,193)],fill=light)
 banner.save(out/f'{theme}_dialog_banner.png')
 for tile in range(4):
  part=banner.crop((tile*240,0,(tile+1)*240,220));part.putpixel((239,219),(1,1,1,1));buf=io.BytesIO();part.save(buf,format='PNG');updates[f'assets/beastsmp/textures/font/dialog_{theme}_{tile}.png']=buf.getvalue()
  dialog_providers.append({'type':'bitmap','file':f'beastsmp:font/dialog_{theme}_{tile}.png','height':110,'ascent':8,'chars':[chr(0xe800+season*4+tile)]})
for season in range(3):
 for icon in range(4):
  src=motif(season,icon);src.thumbnail((32,32),Image.Resampling.LANCZOS);im=Image.new('RGBA',(32,32));im.alpha_composite(src,((32-src.width)//2,(32-src.height)//2));buf=io.BytesIO();im.save(buf,format='PNG');updates[f'assets/beastsmp/textures/font/dialog_icon_{season}_{icon}.png']=buf.getvalue()
  dialog_providers.append({'type':'bitmap','file':f'beastsmp:font/dialog_icon_{season}_{icon}.png','height':18,'ascent':13,'chars':[chr(0xe900+season*4+icon)]})
for tile in range(4):
 updates[f'assets/beastsmp/textures/font/dialog_active_{tile}.png']=updates[f'assets/beastsmp/textures/font/dialog_halloween_{tile}.png']
 dialog_providers.append({'type':'bitmap','file':f'beastsmp:font/dialog_active_{tile}.png','height':110,'ascent':8,'chars':[chr(0xe8f0+tile)]})
for icon in range(3):
 updates[f'assets/beastsmp/textures/font/dialog_active_icon_{icon}.png']=updates[f'assets/beastsmp/textures/font/dialog_icon_0_{icon}.png']
 dialog_providers.append({'type':'bitmap','file':f'beastsmp:font/dialog_active_icon_{icon}.png','height':18,'ascent':13,'chars':[chr(0xea00+icon)]})
if args.client_jar:
 with zipfile.ZipFile(args.client_jar) as jar:
  spec=next(v for v in json.loads(jar.read('assets/minecraft/font/include/default.json'))['providers'] if v.get('file')=='minecraft:font/ascii.png')
  updates['assets/beastsmp/textures/font/dialog_text_ascii.png']=jar.read('assets/minecraft/textures/font/ascii.png')
 updates['assets/beastsmp/font/dialog_text.json']=json.dumps({'providers':[{'type':'bitmap','file':'beastsmp:font/dialog_text_ascii.png','height':11,'ascent':9,'chars':spec['chars']},{'type':'reference','id':'minecraft:default'}]},ensure_ascii=False,indent=2).encode()
updates['assets/beastsmp/font/dialog_menu.json']=json.dumps({'providers':dialog_providers},ensure_ascii=False,indent=2).encode()
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
preview=Image.new('RGB',(416*5,484),'#080b14')
for i in range(5):preview.paste(Image.open(out/f'halloween_{i}_preview.png'),(416*i,0))
preview.save(out/'preview_all.png');print('Updated illustrated menu pack',pack)
