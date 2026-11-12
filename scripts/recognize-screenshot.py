import sys,json,re,subprocess,tempfile,os
from pathlib import Path
from PIL import Image,ImageOps
import numpy as np
from difflib import SequenceMatcher
ROOT=Path(__file__).resolve().parents[1]
OCR_ASSETS=ROOT/'assets'/'ocr'
def norm(s):return re.sub(r'[\s·•・:：，,。()（）]','',s)
def ocr(p):
 r=subprocess.run(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'scripts/screenshot-ocr.ps1'),'-ImagePath',str(p)],capture_output=True,timeout=40)
 if r.returncode:raise RuntimeError('Windows Chinese OCR failed')
 return json.loads(r.stdout.decode('utf-8-sig'))
def sig(im):
 a=np.array(im.convert('RGB').resize((32,32)),dtype=float)[8:24,8:24].reshape(-1);a-=a.mean();return a/(np.linalg.norm(a)+1e-8)
def season_labels(im, directory):
 catalog=ROOT/'backend/src/main/resources/season-talents.json'
 if not catalog.is_file():return []
 def key(text):return re.sub(r'[^\w\u4e00-\u9fff]|_', '', text)
 names={}
 for talent in json.loads(catalog.read_text(encoding='utf-8')):
  names.setdefault(key(talent['name']),[]).append(talent)
 sheet=Image.new('RGB',(410,90*15),'white')
 for slot,center in enumerate((2060,2220,2380)):
  crop=im.crop((center-82,952,center+82,990)).convert('L')
  gray=np.asarray(crop)
  contrast=ImageOps.autocontrast(crop,cutoff=2)
  variants=[Image.fromarray(np.where(gray>cutoff,0,255).astype('uint8')) for cutoff in (160,175,205)]
  variants.extend((contrast,ImageOps.invert(contrast)))
  for variant,tile in enumerate(variants):
   sheet.paste(tile.resize((328,76)),(25,(slot*5+variant)*90))
 path=directory/'season.png';sheet.save(path)
 lines=ocr(path)['lines'];result=[]
 for slot in range(3):
  labels=[''.join(line['text'] for line in lines if line['words'] and line['words'][0]['y']//90==slot*5+i) for i in range(5)]
  exact={t['id']:label for label in labels for t in names.get(key(label),[])}
  if len(exact)==1:
   ident,label=next(iter(exact.items()))
   result.append({'slot':slot,'id':ident,'text':label})
  elif any(map(key,labels)):
   result.append({'slot':slot,'id':None,'text':max(labels,key=lambda label:len(key(label)))})
 return result
def run(path,mode):
 im=Image.open(path).convert('RGB').resize((2560,1440));raw=ocr(path)
 rules=json.loads((ROOT/'backend/src/main/resources/rules.json').read_text(encoding='utf-8'));cards=rules['cards'];groups={}
 for c in cards:
  if c['id']>=0:groups.setdefault(norm(c['name']),[]).append(c)
 replay=mode=='record';coords=[]
 for side in range(2 if replay else 1):
  for slot in range(8):coords.append((side,'deck',slot, int((163+287*slot) if replay else (80+310*slot)), (445+600*side) if replay else 435,30,190))
 if not replay:
  # Search exposed name rails independently; overlapped cards remain separate candidates.
  a=np.asarray(im);white=(a[1050:1260,:,0]>190)&(a[1050:1260,:,1]>190)&(a[1050:1260,:,2]>175);scores=white.sum(axis=0)
  peaks=[]
  for x in np.argsort(scores)[::-1]:
   if scores[x]<65:break
   if x<650 or x>1950 or any(abs(int(x)-p)<95 for p in peaks):continue
   peaks.append(int(x))
   if len(peaks)>=16:break
  for i,x in enumerate(sorted(peaks)):coords.append((0,'hand',i,max(0,x-15),1065,45,220))
 sheet=Image.new('RGB',(700,max(80,len(coords)*65)),(255,255,255))
 for i,(_,_,_,x,y,w,h) in enumerate(coords):
  crop=im.crop((x,y,x+w,y+h));a=np.asarray(crop);mask=(a[:,:,0]>195)&(a[:,:,1]>195)&(a[:,:,2]>185);crop=Image.fromarray(np.where(mask,0,255).astype('uint8')).convert('RGB')
  a=np.asarray(crop.convert('L'));active=(a<100).sum(axis=1)>2
  for j in range(1,len(active)-2):
   if active[j-1] and active[j+1]:active[j]=True
  segments=[];start=None
  for j,on in enumerate(list(active)+[False]):
   if on and start is None:start=j
   if not on and start is not None:
    if 10<=j-start<=55:segments.append((start,j))
    start=None
  for k,(lo,hi) in enumerate(segments[:9]):sheet.paste(crop.crop((0,lo,w,hi)).resize((48,42)),(k*54+5,i*65+10))
 # Intermediate image stays in request-local temporary storage.
 with tempfile.TemporaryDirectory(prefix='yxp-rails-') as d:
  stat=None;season=[]
  if not replay:
   stat=Image.new('RGB',(700,150),'white')
   for k,box in enumerate([(180,260,285,310),(450,260,565,310)]):
    crop=im.crop(box);a=np.asarray(crop);mask=(a[:,:,0]>190)&(a[:,:,1]>190)&(a[:,:,2]>190);tile=Image.fromarray(np.where(mask,0,255).astype('uint8')).resize((260,100));stat.paste(tile,(k*320,20))
   q=Path(d)/'stats.png';stat.save(q);stat=ocr(q)
   season=season_labels(im,Path(d))

  p=Path(d)/'rails.png';sheet.save(p);rails=ocr(p)
 art=[]
 for p in (OCR_ASSETS/'import-card-templates').glob('Card_*.png'):
  m=re.match(r'Card_(\d+)(?:_|$)',p.stem)
  if m:
   id=int(m[1]);known=[c for c in cards if c['id']==id]
   if known:art.append((id,sig(Image.open(p))))
 artIds=[i for i,_ in art];artMatrix=np.array([v for _,v in art])
 templates=[]
 for p in (OCR_ASSETS/'import-templates').glob('Icon_Talent_*.png'):
  m=re.fullmatch(r'Icon_Talent_(\d+)',p.stem)
  if m and any(t['id']==int(m[1]) for t in rules['talentCatalog']):templates.append((int(m[1]),sig(Image.open(p))))
 cmap_path=OCR_ASSETS/'import-character-icon-map.json'
 cmap=json.loads(cmap_path.read_text(encoding='utf-8')) if cmap_path.is_file() else []
 talents={t['id']:t for t in rules['talentCatalog']};rows=[]
 for side in range(2 if replay else 1):
  deck=[None]*8;hand=[];warnings=[];details=[]
  for i,(owner,kind,slot,x,y,w,h) in enumerate(coords):
   if owner!=side:continue
   texts=[l['text'] for l in rails['lines'] if l['words'] and i*65<=l['words'][0]['y']<(i+1)*65];word=norm(''.join(texts))
   ranked=sorted(((max(SequenceMatcher(None,word,name).ratio(),1.0 if name in word else 0.0),name) for name in groups if name),reverse=True)[:3]
   if not word or not ranked or ranked[0][0]<.48:continue
   score,name=ranked[0];variants=groups[name];c=min(variants,key=lambda c:c['rarity'])
   if kind=='deck':deck[slot]=c['id']
   else:hand.append(c['id'])
   details.append({'kind':kind,'slot':slot,'text':word,'confidence':round(score,3),'candidates':[groups[n][0]['id'] for _,n in ranked]})
  warnings.append('公开版使用文字识别，请手动核对卡牌等级、角色和图标仙命。')
  if not replay:warnings.append('手牌识别仅覆盖露出的图案和文字；重叠遮挡可能漏牌，请核对数量和顺序。')
  positions=[(1818+119*i,320+570*side) for i in range(5)] if replay else [(360,1380),(220,1365),(105,1270),(96,1135),(158,1007)]
  icons=[]
  for cx,cy in positions:
   vectors=np.array([sig(im.crop((cx+dx-r,cy+dy-r,cx+dx+r,cy+dy+r))) for dx in [-12,0,12] for dy in [-12,0,12] for r in [44,49,54]]);rank=sorted(((float((vectors@t).max()),id) for id,t in templates),reverse=True)[:3]
   candidates=[{'id':id,'name':talents.get(id,{}).get('name',str(id)),'confidence':round(score,3),'supported':talents.get(id,{}).get('supported',False)} for score,id in rank]
   icons.append({'id':rank[0][1] if rank and rank[0][0]>.55 else None,'candidates':candidates})
  for slot in range(8) if art else ():
   x=(195+287*slot) if replay else (125+310*slot);y=(400+570*side) if replay else 390
   vectors=np.array([sig(im.crop((x+dx,y+dy,x+dx+200,y+dy+225))) for dx in [-20,0,20] for dy in [-10,0,10]]);scores=(artMatrix@vectors.T).max(axis=1);order=np.argsort(scores)[::-1];rank=[];seen=set()
   for index in order:
    id=artIds[index];base=id-id//10000%100*10000
    if base in seen:continue
    seen.add(base);rank.append((float(scores[index]),id))
    if len(rank)==3:break
   if rank and rank[0][0]>.58:
    id=rank[0][1];base=id-id//10000%100*10000;variants=[c for c in cards if c['id']-c['variant']*10000==base]
    words=[w for l in raw['lines'] for w in l['words'] if (150+287*slot if replay else 80+310*slot)*raw['width']/2560<=w['x']<(410+287*slot if replay else 330+310*slot)*raw['width']/2560 and (625+570*side if replay else 625)*raw['height']/1440<=w['y']<(810+570*side if replay else 810)*raw['height']/1440]
    numbers=re.findall(r'\d+',' '.join(w['text'] for w in sorted(words,key=lambda w:(round(w['y']/20),w['x']))))
    def gradeScore(c):
     desc=re.sub(r'<[^>]*>','',c['desc']);ns=re.findall(r'\d+',desc);return SequenceMatcher(None,numbers,ns).ratio()
    if numbers and variants:id=max(variants,key=gradeScore)['id']
    deck[slot]=id
    details.append({'kind':'deck','slot':slot,'text':'本地卡面模板','confidence':round(rank[0][0],3),'candidates':[id for _,id in rank]})
  character=None
  if icons[0]['id']:
   matches=[c['characterId'] for c in cmap if any(t['id']==icons[0]['id'] for t in c['candidates'])]
   if len(matches)==1:character=matches[0]
  sy=raw['height']/1440;sx=raw['width']/2560
  lines=[l for l in raw['lines'] if l['words'] and ((280+570*side)*sy<l['words'][0]['y']<(355+570*side)*sy)] if replay else [l for l in raw['lines'] if l['words'] and 235*sy<l['words'][0]['y']<335*sy]
  txt=norm(''.join(l['text'] for l in lines))
  def num(label):
   m=re.search(label+r'[：:]?(\d+)',txt);return int(m[1]) if m else None
  hp=None;experience=None;destiny=None
  if not replay and stat:
   for l in stat['lines']:
    for w in l['words']:
     if w['text'].isdigit():
      if w['x']<300:experience=int(w['text'])
      else:hp=int(w['text'])
  if replay:
   for l in lines:
    for word in l['words']:
     if word['text'].isdigit():
      x=word['x']/sx
      if 700<x<760 and destiny is None:destiny=int(word['text'])
      if 1230<x<1350:experience=int(word['text'])
      if 1550<x<1680:hp=int(word['text'])
  if not replay:
   for l in lines:
    for word in l['words']:
     if word['text'].isdigit():
      if 180*sx<word['x']<300*sx:experience=int(word['text'])
      if 440*sx<word['x']<580*sx:hp=int(word['text'])
  if not replay:
   pixels=np.asarray(im,dtype=float);gradient=np.abs(pixels[1080:1240,1:]-pixels[1080:1240,:-1]).mean(axis=2);strength=(gradient>25).mean(axis=0);edges=[]
   for x in np.argsort(strength)[::-1]:
    if 650<x<2450 and strength[x]>.48 and all(abs(int(x)-old)>30 for old in edges):edges.append(int(x))
   starts=[x for x in edges if any(180<right-x<285 for right in edges)]
   points=[(x+dx,y) for x in starts for dx in [35,45,55] for y in [1035,1055,1075]];ranked=[]
   if points and art:
    vectors=np.array([sig(im.crop((x,y,x+200,y+225))) for x,y in points]);scores=artMatrix@vectors.T
    for j,(x,y) in enumerate(points):
     i=int(np.argmax(scores[:,j]));score=float(scores[i,j])
     if score>.65:ranked.append((score,x,artIds[i]))
   accepted=[]
   for score,x,id in sorted(ranked,reverse=True):
    if not any(abs(x-old[0])<160 for old in accepted):accepted.append((x,id))
   if accepted:hand=[id for x,id in sorted(accepted)]
   exclusive={talents[i['id']].get('charId',0) for i in icons if i['id'] in talents and talents[i['id']].get('charId',0)}
   if character is None and len(exclusive)==1:character=next(iter(exclusive))
   for l in raw['lines']:
    for w in l['words']:
     if 550*sx<w['x']<640*sx and w['y']<100*sy and w['text'].isdigit():destiny=int(w['text'])
  realm=next((i+1 for i,n in enumerate(['炼气','筑基','金丹','元婴','化神','返虚']) if n in norm(''.join(l['text'] for l in raw['lines']))),None)
  if any(c is None for c in deck):warnings.append('部分卡槽未识别，请逐槽补齐；不按识别数量压缩槽位。')
  rows.append({'realm':realm,'cards':deck,'hand':hand,'hp':hp,'destiny':destiny,'experience':experience,'characterId':character,'icons':icons,'details':details,'warnings':warnings})
  if not replay:rows[-1]['seasonTalents']=season
 return {'rows':rows,'lines':raw['lines'],'width':raw['width'],'height':raw['height'],'mode':mode}
if __name__=='__main__':
 sys.stdout.reconfigure(encoding='utf-8');print(json.dumps(run(sys.argv[1],sys.argv[2]),ensure_ascii=False))


