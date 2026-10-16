from pathlib import Path
from PIL import Image,ImageDraw,ImageColor
import zipfile,json,io,argparse
p=argparse.ArgumentParser();p.add_argument('repo',type=Path);p.add_argument('--client-jar',type=Path,required=True);a=p.parse_args()
root=Path(__file__).parent;out=root/'generated';out.mkdir(exist_ok=True)
with zipfile.ZipFile(a.client_jar) as jar:
 definitions=json.loads(jar.read('assets/minecraft/font/include/default.json'))['providers']
 spec=next(d for d in definitions if d.get('file')=='minecraft:font/ascii.png')
 sheet=Image.open(io.BytesIO(jar.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
 chars={}
 for y,row in enumerate(spec['chars']):
  for x,c in enumerate(row):
   tile=sheet.crop((x*8,y*8,x*8+8,y*8+8));bounds=tile.getbbox();chars[c]=tile.crop((0,0,bounds[2],8)) if bounds else Image.new('RGBA',(3,8))
def text(im,s,cx,y,color):
 glyphs=[chars.get(c,chars['?']) for c in s];width=sum(g.width+1 for g in glyphs)-1;x=round(cx-width/2)
 for glyph in glyphs:
  solid=Image.new('RGBA',glyph.size,color);solid.putalpha(glyph.getchannel('A'));im.alpha_composite(solid,(x,y));x+=glyph.width+1
updates={}
def put_png(name,im):
 b=io.BytesIO();im.save(b,format='PNG');updates[name]=b.getvalue()
logo=Image.open(a.repo/'plugins/BetterHud/assets/beast_halloween_header.png').convert('RGBA');logo=logo.crop(logo.getbbox());ratio=min(216/logo.width,64/logo.height);logo=logo.resize((round(logo.width*ratio),round(logo.height*ratio)),Image.Resampling.NEAREST)
header=Image.new('RGBA',(256,64));header.alpha_composite(logo,((256-logo.width)//2,(64-logo.height)//2));header.putpixel((255,63),(1,1,1,1));put_png('assets/beastsmp/textures/font/esc_logo.png',header)
art=Image.open(root/'farewell-art.png').convert('RGBA');art=art.crop(art.getbbox());art.thumbnail((256,86),Image.Resampling.NEAREST)
for lang,label,line1,line2 in [('de_de','Bis bald!','DANKE FUERS SPIELEN!','BIS BALD AUF BEASTSMP'),('en_us','See you soon!','THANKS FOR PLAYING!','SEE YOU ON BEASTSMP')]:
 im=Image.new('RGBA',(256,128));text(im,label,128,6,'white');im.alpha_composite(art,((256-art.width)//2,25));text(im,line1,140,51,'#f4eaff');text(im,line2,140,63,'#f4eaff');text(im,'MC.BEASTSMP.DE',140,75,'#f5b041');im.putpixel((255,127),(1,1,1,1));put_png(f'assets/beastsmp/textures/font/esc_farewell_{lang}.png',im);im.resize((1024,512),Image.Resampling.NEAREST).save(out/f'farewell_{lang}.png')
for name,accent,fill in [('button','#9c45ce','#252039'),('button_highlighted','#f0aaff','#44315e'),('button_disabled','#534360','#1b1824')]:
 im=Image.new('RGBA',(200,20));d=ImageDraw.Draw(im);d.rectangle((0,0,199,19),fill='#090810');d.rectangle((1,1,198,18),fill=fill,outline=accent);d.line((3,2,196,2),fill='#786188' if name!='button_highlighted' else '#f2c4ff');d.line((3,17,196,17),fill='#151021');d.rectangle((3,3,4,16),fill=accent);d.rectangle((195,3,196,16),fill=accent)
 put_png(f'assets/minecraft/textures/gui/sprites/widget/{name}.png',im)
 updates[f'assets/minecraft/textures/gui/sprites/widget/{name}.png.mcmeta']=json.dumps({'gui':{'scaling':{'type':'nine_slice','width':200,'height':20,'border':3}}}).encode()
pack=a.repo/'plugins/BetterHud/beastsmp-base.zip';temp=pack.with_suffix('.new.zip')
with zipfile.ZipFile(pack) as old:
 font=json.loads(old.read('assets/minecraft/font/default.json'));font['providers']=[v for v in font['providers'] if not v.get('file','').startswith('beastsmp:font/esc_') and '\uf810' not in v.get('advances',{})]
 font['providers'] += [{'type':'space','advances':{'\uf810':-30,'\uf811':-31}}, {'type':'bitmap','file':'beastsmp:font/esc_logo.png','height':64,'ascent':44,'chars':['\uf800']}, {'type':'bitmap','file':'beastsmp:font/esc_farewell_de_de.png','height':128,'ascent':13,'chars':['\uf801']}, {'type':'bitmap','file':'beastsmp:font/esc_farewell_en_us.png','height':128,'ascent':13,'chars':['\uf802']}]
 updates['assets/minecraft/font/default.json']=json.dumps(font,ensure_ascii=False,indent=2).encode()
 for lang in ['de_de','en_us']:
  key=f'assets/minecraft/lang/{lang}.json';data=json.loads(old.read(key));data.update({'menu.game':'\uf800','menu.returnToGame':'Auf BeastSMP weiterspielen' if lang=='de_de' else 'Continue playing on BeastSMP','menu.disconnect':'\uf810'+('\uf801' if lang=='de_de' else '\uf802')+'\uf811','menu.options':'Optionen ...' if lang=='de_de' else 'Options ...','menu.returnToMenu':'Verlassen' if lang=='de_de' else 'Leave'})
  updates[key]=json.dumps(data,ensure_ascii=False,indent=2).encode()
 with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as new:
  for info in old.infolist():
   if info.filename not in updates and '__MACOSX/' not in info.filename and '.DS_Store' not in info.filename:new.writestr(info,old.read(info.filename))
  for key,value in updates.items():new.writestr(key,value)
temp.replace(pack)
print('ESC menu merged into',pack)
