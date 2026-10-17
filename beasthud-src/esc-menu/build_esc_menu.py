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
def png(im):
 b=io.BytesIO();im.save(b,format='PNG');return b.getvalue()
def bytes_for(im):return png(im)
skins={}
for theme,accent,fill,highlight in [('halloween','#9c45ce','#252039','#f0aaff'),('standard','#29b6f4','#152c41','#a1eeff'),('winter','#a5ecff','#173246','#ffffff')]:
 skin={}
 logo=Image.open(a.repo/f'plugins/BetterHud/assets/beast_{theme}_header.png').convert('RGBA');logo=logo.crop(logo.getbbox());ratio=min(432/logo.width,128/logo.height);logo=logo.resize((round(logo.width*ratio),round(logo.height*ratio)),Image.Resampling.NEAREST)
 header=Image.new('RGBA',(512,128));header.alpha_composite(logo,((512-logo.width)//2,(128-logo.height)//2))
 for tile in range(2):
  part=header.crop((tile*256,0,(tile+1)*256,128));part.putpixel((255,127),(1,1,1,1));skin[f'assets/beastsmp/textures/font/esc_logo_{tile}.png']=png(part)
 dialog_banner=Image.open(a.repo/f'beasthud-src/menu-art/generated/{theme}_dialog_banner.png').convert('RGBA')
 for tile in range(4):
  part=dialog_banner.crop((tile*192,0,(tile+1)*192,176));part.putpixel((191,175),(1,1,1,1));skin[f'assets/beastsmp/textures/font/dialog_active_{tile}.png']=png(part)
 with zipfile.ZipFile(a.repo/'plugins/BetterHud/beastsmp-base.zip') as basepack:
  for icon in range(3):skin[f'assets/beastsmp/textures/font/dialog_active_icon_{icon}.png']=basepack.read(f'assets/beastsmp/textures/font/dialog_icon_{["halloween","standard","winter"].index(theme)}_{icon}.png')
 source=root/('farewell-art.png' if theme=='halloween' else f'farewell-{theme}.png')
 art=Image.open(source).convert('RGBA');art=art.crop(art.getbbox());ratio=min(512/art.width,172/art.height);art=art.resize((round(art.width*ratio),round(art.height*ratio)),Image.Resampling.NEAREST)
 for lang,label,line1,line2 in [('de_de','Bis bald!','DANKE FUERS SPIELEN!','BIS BALD AUF BEASTSMP'),('en_us','See you soon!','THANKS FOR PLAYING!','SEE YOU ON BEASTSMP')]:
  base=Image.new('RGBA',(256,128));text(base,label,128,6,'white');text(base,line1,140,51,'#f4eaff');text(base,line2,140,63,'#f4eaff');text(base,'MC.BEASTSMP.DE',140,75,'#f5b041' if theme=='halloween' else '#9eefff')
  letters=base.resize((512,256),Image.Resampling.NEAREST);im=Image.new('RGBA',(512,256));im.alpha_composite(art,((512-art.width)//2,50));im.alpha_composite(letters)
  for tile in range(2):
   part=im.crop((tile*256,0,(tile+1)*256,256));part.putpixel((255,255),(1,1,1,1));skin[f'assets/beastsmp/textures/font/esc_farewell_{lang}_{tile}.png']=png(part)
  im.save(out/f'farewell_{theme}_{lang}.png')
 for name in ['button','button_highlighted','button_disabled']:
  color='#8b97a8' if name=='button_highlighted' else '#303743' if name=='button_disabled' else '#424b5a'
  im=Image.new('RGBA',(400,40));d=ImageDraw.Draw(im)
  for y in range(40):
   t=y/39;d.line((0,y,399,y),fill=(round(26-10*t),round(32-12*t),round(47-16*t),255))
  d.rectangle((0,0,399,39),outline='#050812',width=2);d.rectangle((2,2,397,37),outline=color,width=2)
  d.line((5,4,394,4),fill='#a8b2c0' if name=='button_highlighted' else '#586170',width=1)
  d.line((5,35,394,35),fill='#0a0e18',width=1)
  skin[f'assets/minecraft/textures/gui/sprites/widget/{name}.png']=png(im)
  skin[f'assets/minecraft/textures/gui/sprites/widget/{name}.png.mcmeta']=json.dumps({'gui':{'scaling':{'type':'nine_slice','width':400,'height':40,'border':4}}}).encode()
 skins[theme]=skin
 folder=a.repo/'plugins/BeastHudThemes/esc-themes';folder.mkdir(exist_ok=True)
 with zipfile.ZipFile(folder/f'{theme}.zip','w',zipfile.ZIP_DEFLATED) as z:
  for key,data in skin.items():
   entry=zipfile.ZipInfo(key);entry.compress_type=zipfile.ZIP_DEFLATED;z.writestr(entry,data)
updates.update(skins['halloween'])
pack=a.repo/'plugins/BetterHud/beastsmp-base.zip';temp=pack.with_suffix('.new.zip')
with zipfile.ZipFile(pack) as old:
 font=json.loads(old.read('assets/minecraft/font/default.json'));font['providers']=[v for v in font['providers'] if not v.get('file','').startswith('beastsmp:font/esc_') and '\uf810' not in v.get('advances',{})]
 font['providers'].append({'type':'space','advances':{'\uf810':-30,'\uf811':-31,'\uf812':-1}})
 for i in range(2):font['providers'].append({'type':'bitmap','file':f'beastsmp:font/esc_logo_{i}.png','height':64,'ascent':44,'chars':[chr(0xf800+i)]})
 for offset,lang in [(0,'de_de'),(2,'en_us')]:
  for i in range(2):font['providers'].append({'type':'bitmap','file':f'beastsmp:font/esc_farewell_{lang}_{i}.png','height':128,'ascent':13,'chars':[chr(0xf802+offset+i)]})
 updates['assets/minecraft/font/default.json']=json.dumps(font,ensure_ascii=False,indent=2).encode()
 for lang in ['de_de','en_us']:
  offset=2
  key=f'assets/minecraft/lang/{lang}.json';data=json.loads(old.read(key));data.update({'menu.game':'\uf800\uf812\uf801','menu.returnToGame':'Continue playing on BeastSMP','menu.disconnect':'\uf810'+chr(0xf802+offset)+'\uf812'+chr(0xf803+offset)+'\uf811','gui.advancements':'Advancements','gui.stats':'Statistics','menu.options':'Options ...','menu.returnToMenu':'Leave'})
  data.update({'beastsmp.menu.events':'Menu','beastsmp.menu.pumpkins':'Pumpkin hunt','beastsmp.menu.designs':'Seasonal designs','beastsmp.menu.christmas':'Christmas','beastsmp.menu.close':'Close'});updates[key]=json.dumps(data,ensure_ascii=False,indent=2).encode()
 with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as new:
  for info in old.infolist():
   if info.filename not in updates and not info.filename.startswith('assets/beastsmp/textures/font/esc_') and '__MACOSX/' not in info.filename and '.DS_Store' not in info.filename:new.writestr(info,old.read(info.filename))
  for key,value in updates.items():new.writestr(key,value)
temp.replace(pack)
print('Three HD ESC themes built; 512px farewell source, 256px displayed width')

langdir=a.repo/'plugins/BeastHudThemes/languages';langdir.mkdir(exist_ok=True)
with zipfile.ZipFile(pack) as base:
 for language in ['en','de']:
  with zipfile.ZipFile(langdir/f'{language}.zip','w',zipfile.ZIP_DEFLATED) as overlay:
   menu=langdir/f'{language}-menu.zip'
   with zipfile.ZipFile(menu) as z:
    for name in z.namelist():
     if not name.startswith("assets/beastsmp/textures/font/dialog_active_"):overlay.writestr(name,z.read(name))
   for locale in ['en_us','de_de']:
    key=f'assets/minecraft/lang/{locale}.json';data=json.loads(base.read(key))
    if language=='de':data.update({'beastsmp.menu.events':'Menu','beastsmp.menu.pumpkins':'Kürbissuche','beastsmp.menu.designs':'Saisonale Designs','beastsmp.menu.christmas':'Weihnachten','beastsmp.menu.close':'Schließen','menu.returnToGame':'Auf BeastSMP weiterspielen','menu.options':'Optionen ...','menu.returnToMenu':'Verlassen','gui.advancements':'Fortschritte','gui.stats':'Statistiken','menu.disconnect':'\uf810\uf802\uf812\uf803\uf811'})
    overlay.writestr(key,json.dumps(data,ensure_ascii=False,indent=2).encode())
print('English and German menu/ESC language overlays built.')
