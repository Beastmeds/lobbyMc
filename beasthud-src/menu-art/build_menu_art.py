from PIL import Image, ImageDraw
from pathlib import Path
import json, zipfile, io
import argparse
parser=argparse.ArgumentParser();parser.add_argument('repo',type=Path);args=parser.parse_args();repo=args.repo
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
providers=[{'type':'space','advances':{'\ue000':-8}}]
for idx,(theme,accent,light,header) in enumerate([
 ('halloween','#a72cdd','#f5b041','beast_halloween_header.png'),
 ('standard','#29a7ef','#9ff3ff','beast_standard_header.png'),
 ('winter','#75dce9','#edffff','beast_winter_header.png')]):
 im=Image.new('RGBA',(256,256));d=ImageDraw.Draw(im)
 # One inventory-sized background, drawn through a title glyph. The player's slots remain usable.
 d.rectangle((0,0,175,203),fill='#0b0d19');d.rectangle((1,1,174,202),outline=accent,width=2);d.rectangle((4,4,171,109),fill='#141526',outline='#34324d')
 d.line((7,109,168,109),fill=light)
 # Native inventory coordinates: main storage y126,144,162, hotbar y184.
 d.rectangle((4,113,171,200),fill='#b0b8ca')
 for y in [126,144,162,184]:
  for x in range(8,169,18):
   d.rectangle((x-1,y-1,x+16,y+16),fill='#536277');d.line((x-1,y-1,x+16,y-1),fill='#334254');d.line((x-1,y-1,x-1,y+16),fill='#334254');d.line((x,y+16,x+16,y+16),fill='#d4deeb')
 sprite(im,header,(38,0,100,28));text(d,'LOBBY MENU',88,30,light)
 for x,y,label,icon in [(23,37,'SERVER','compass/n.png'),(95,37,'DESIGN','gem.png'),(23,73,'EVENTS','pumpkin.png'),(95,73,'EFFEKTE','beast_snowflake.png' if theme=='winter' else 'star.png')]:
  d.rectangle((x-1,y-1,x+58,y+27),fill='#080b14',outline='#080b14');d.rectangle((x,y,x+57,y+26),fill='#22243b',outline=accent);d.line((x+2,y+1,x+55,y+1),fill=light)
  sprite(im,icon,(x+20,y+3,18,13))
  if label=='SERVER':
   d.rectangle((x+20,y+3,x+37,y+15),fill='#22243b');d.ellipse((x+23,y+3,x+35,y+15),fill='#39495a',outline=light);d.polygon([(x+29,y+4),(x+26,y+10),(x+29,y+9)],fill='#ff626c');d.polygon([(x+29,y+14),(x+32,y+8),(x+29,y+9)],fill='#d5f7ff')
  text(d,label,x+29,y+19,'#ffffff')
 # Close is a narrow button between the bottom cards, matching slot 40.
 d.rectangle((81,91,94,104),fill='#2a172c',outline=accent);text(d,'X',88,95,'#ffd8e1')
 if theme=='halloween':
  sprite(im,'pumpkin.png',(7,6,23,22));sprite(im,'pumpkin.png',(146,6,23,22));sprite(im,'beast_bat_0.png',(6,29,17,10));sprite(im,'beast_bat_2.png',(154,29,17,10))
 elif theme=='winter':
  for x in [10,22,154,166]:
   d.polygon([(x-3,5),(x+3,5),(x,16 if x%2 else 21)],fill=light)
  sprite(im,'beast_snowflake.png',(8,23,13,13));sprite(im,'beast_snowflake.png',(155,23,13,13))
 else:
  sprite(im,'gem.png',(9,8,20,20));sprite(im,'gem.png',(147,8,20,20));d.line((9,30,28,30),fill=accent);d.line((147,30,166,30),fill=accent)
 path=out/f'{theme}.png';im.save(path)
 providers.append({'type':'bitmap','file':f'beastsmp:font/menu_{theme}.png','ascent':13,'height':256,'chars':[chr(0xe001+idx)]})
font=json.dumps({'providers':providers},ensure_ascii=False,indent=2).encode()
blank=Image.new('RGBA',(16,16));buf=io.BytesIO();blank.save(buf,format='PNG')
updates={'assets/beastsmp/font/lobby_menu.json':font,
 'assets/beastsmp/textures/item/menu_blank.png':buf.getvalue(),
 'assets/beastsmp/models/item/menu_blank.json':json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'beastsmp:item/menu_blank'}}).encode(),
 'assets/beastsmp/items/menu_blank.json':json.dumps({'model':{'type':'minecraft:model','model':'beastsmp:item/menu_blank'}}).encode()}
for theme in ['halloween','standard','winter']:updates[f'assets/beastsmp/textures/font/menu_{theme}.png']=(out/f'{theme}.png').read_bytes()
pack=repo/'plugins/BetterHud/beastsmp-base.zip';temp=pack.with_suffix('.new.zip')
with zipfile.ZipFile(pack) as old,zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as new:
 for info in old.infolist():
  if info.filename not in updates:new.writestr(info,old.read(info.filename))
 for name,data in updates.items():new.writestr(name,data)
temp.replace(pack)
preview=Image.new('RGB',(176*3*3,204*3),'#080b14')
for i,t in enumerate(['halloween','standard','winter']):preview.paste(Image.open(out/f'{t}.png').crop((0,0,176,204)).resize((528,612),Image.Resampling.NEAREST),(i*528,0))
preview.save(out/'preview.png')
print('Updated base pack:',pack)
