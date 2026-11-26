/* Layout study only. Media processing remains in Woka's existing engines. */
const icons={};
const VIDEO_VIEW='350 100 1000 562.5';
function icon(name){
  const glyph=phosphor[name];
  if(glyph)return `<svg viewBox="${glyph[0]}" fill="currentColor" aria-hidden="true">${glyph[1]}</svg>`;
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name]||''}</svg>`;
}
function escapeHtml(value){return String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function photoSvg(view='350 55 1000 720'){return `<svg viewBox="${view}" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><image href="../../../assets/screenshots/photo-viewer.png" width="1440" height="900"/></svg>`;}
function button(action,label,cls='secondary',extra=''){return `<button data-action="${action}" class="${cls}" ${extra}>${label}</button>`;}
function scrub(key,label,value=0,min=-100,max=100,step=1,unit='',glyph=key){
  const def=defaultValue(key),detent=def>min&&def<max?`<i class="detent" style="--at:${(def-min)/(max-min)*100}%"></i>`:'';
  return `<div class="control scrub" data-control="${key}"><span class="scrub-fill"></span>${detent}<span class="scrub-thumb"></span><span class="scrub-icon">${icon(glyph)}</span><span class="scrub-label">${label}</span>${button('reset-param',icon('reset'),'control-reset',`data-param="${key}" aria-label="Reset ${label}" data-tip="Reset to ${def}${unit}"`)}<input class="number" type="number" data-value="${key}" value="${value}" min="${min}" max="${max}" step="${step}" aria-label="${label}, exact value"><span class="unit">${unit}</span><input class="scrub-input" type="range" data-value="${key}" min="${min}" max="${max}" step="${step}" value="${value}" aria-label="${label}" data-tip="${escapeHtml(parameterHints[key]||label)}"></div>`;
}
function toggle(key,label,checked=false,glyph=null){return `<label class="toggle">${glyph?`<span class="scrub-icon">${icon(glyph)}</span>`:''}<span class="toggle-label">${label}</span><input data-value="${key}" type="checkbox" ${checked?'checked':''}></label>`;}
function well(...rows){return `<div class="well">${rows.join('')}</div>`;}
function section(label,content){return `<section class="group"><h2 class="group-label">${label}</h2>${content}</section>`;}
function wellButton(action,glyph,label,cls='',extra=''){return `<button data-action="${action}" class="well-button ${cls}" ${extra}>${icon(glyph)}<span>${label}</span></button>`;}
function textAction(action,glyph,label,cls=''){return `<button data-action="${action}" class="text-action ${cls}">${icon(glyph)}<span>${label}</span></button>`;}
function segmented(key,items){return `<div class="segmented" data-indicator="${key}">${items.map(([action,label,selected,extra=''])=>button(action,label,selected?'selected':'',extra)).join('')}</div>`;}
function val(key,fallback=0){return settings()[key]??fallback;}
function toolList(){
  if(state.mode!=='video')return [['styles','Styles'],['adjust','Adjust'],['crop','Transform'],['markup','Markup']];
  return [...(selectedClip()?[['clip','Clip']]:[]),['styles','Styles'],['adjust','Adjust'],['crop','Transform'],['markup','Markup'],['sound','Sound']];
}
function scopeHtml(){
  const audio=selectedAudio(),clip=selectedClip(),caption=state.selection==='caption';
  if(state.mode==='video'){
    if(!clip&&!audio&&!caption)return `<nav class="scope" aria-label="Editing"><span class="crumb current">Whole video</span></nav>`;
    const name=audio?escapeHtml(audio.name):clip?`${clipLabel(clip)} <span class="crumb-meta">${clip.duration.toFixed(1)} s</span>`:'Text';
    return `<nav class="scope" aria-label="Editing">${button('scope-all','Whole video','crumb','data-tip="Back to settings for the whole video (Esc)"')}<span class="crumb-sep">${icon('chevronRight')}</span><span class="crumb current">${name}</span></nav>`;
  }
  if(!state.frames||currentMedia().gif)return '';
  // Said only when All frames is about to throw work away (ME-126): the other frames' own edits.
  const replaced=state.frameScope==='all'?state.frames.filter((frame,index)=>index!==state.frameIndex&&Object.keys(frame.settings).length).length:0;
  const note=replaced?`<p class="scope-note">Replaces the separate edit${replaced>1?'s':''} on ${replaced} frame${replaced>1?'s':''}</p>`:'';
  return `<nav class="scope">${segmented('frame-scope',[['frame-scope','All frames',state.frameScope==='all','data-scope="all"'],['frame-scope',`Frame ${state.frameIndex+1}`,state.frameScope==='single','data-scope="single"']])}${note}</nav>`;
}
function renderInspector(){
  const previous=rememberPanel(),audio=selectedAudio(),clip=selectedClip();
  if(state.tool==='clip'&&!clip)state.tool='styles';
  document.querySelector('#scope').innerHTML=scopeHtml();
  const tools=document.querySelector('#tools'),hadClip=!!tools.querySelector('[data-tool="clip"]');
  tools.innerHTML=audio?'':toolList().map(([id,name])=>`<button data-tool="${id}" class="${state.tool===id?'selected':''}" aria-pressed="${state.tool===id}">${icon(id)}<span>${name}</span></button>`).join('');
  tools.hidden=!!audio;
  const clipTab=tools.querySelector('[data-tool="clip"]');
  if(clipTab&&!hadClip&&!reducedMotion())clipTab.animate([{flexGrow:0,opacity:0},{flexGrow:1,opacity:1}],{duration:260,easing:'cubic-bezier(.22,1,.36,1)'}).finished.then(()=>syncIndicators()).catch(()=>{});
  const content=audio?audioPanel(audio):({clip:clipPanel,adjust:adjustPanel,crop:cropPanel,styles:stylesPanel,markup:markupPanel,sound:soundPanel}[state.tool]||stylesPanel)();
  document.querySelector('#panel').innerHTML=content;
  restorePanel(previous);
  syncIndicators();
}
function adjustPanel(){
  const groups={light:[['exposure','Exposure'],['brightness','Brightness'],['contrast','Contrast'],['highlights','Highlights'],['shadows','Shadows'],['blackPoint','Black point']],colour:[['warmth','Warmth'],['tint','Tint'],['saturation','Saturation'],['vibrance','Vibrance']],detail:[['sharpness','Sharpness'],['noise','Noise']]};
  return `${wellButton('auto','auto','Auto-enhance','auto-button','data-tip="Let Woka balance light and colour. You can fine-tune afterwards."')}${segmented('adjust-group',[['adjust-group','Light',state.group==='light','data-group="light"'],['adjust-group','Colour',state.group==='colour','data-group="colour"'],['adjust-group','Detail',state.group==='detail','data-group="detail"']])}${well(...groups[state.group].map(([key,label])=>scrub(key,label,val(key))))}${textAction('reset-adjust','resetAll','Reset adjustments')}`;
}
function ratioTile(ratio){
  const shapes={'1:1':[22,22],'4:3':[26,19.5],'3:2':[27,18],'16:9':[28,15.75]};
  let shape;
  if(ratio==='Free')shape='<span class="ratio-shape free"></span>';
  else if(ratio==='Original')shape=`<span class="ratio-icon">${icon('image')}</span>`;
  else{let [w,h]=shapes[ratio];if(state.portrait)[w,h]=[h,w];shape=`<span class="ratio-shape" style="width:${w}px;height:${h}px"></span>`;}
  return `<button data-action="ratio" data-ratio="${ratio}" class="ratio-tile ${state.ratio===ratio?'selected':''}" aria-pressed="${state.ratio===ratio}">${shape}<span>${ratio}</span></button>`;
}
function dialHtml(value){
  let labels='';
  for(let deg=-45;deg<=45;deg+=15)labels+=`<span style="left:${(deg+45)*8}px">${deg}</span>`;
  return `<div class="dial ${value===0?'at-zero':''}" data-dial="straighten" role="slider" tabindex="0" aria-label="Straighten" aria-valuemin="-45" aria-valuemax="45" aria-valuenow="${value}" data-tip="Drag sideways to level the horizon. Double-click to reset."><output class="dial-readout">${value.toFixed(1)}°</output><div class="dial-window"><div class="dial-scale" style="--v:${value}">${labels}</div><i class="dial-needle"></i></div></div>`;
}
function cropPanel(){
  return `${section('Shape',`<div class="ratio-grid">${['Free','Original','1:1','4:3','3:2','16:9'].map(ratioTile).join('')}</div>${segmented('orientation',[['orientation',`${icon('landscape')}<span>Landscape</span>`,!state.portrait,'data-portrait="false"'],['orientation',`${icon('portrait')}<span>Portrait</span>`,state.portrait,'data-portrait="true"']])}`)}${section('Straighten',dialHtml(state.straighten||0))}<div class="button-pair">${wellButton('rotate-left','rotate','Rotate left')}${wellButton('rotate','rotateRight','Rotate right')}</div><div class="button-pair">${wellButton('flip','flip','Flip horizontal')}${wellButton('flip-v','flipVertical','Flip vertical')}</div><div class="crop-foot"><span class="crop-size" id="crop-dimensions">${cropDimensions()}</span>${textAction('reset-crop','reset','Reset')}</div>`;
}
/* Styles as the app has them (RD-203): Undertone and Look, each one row of four tiles with
   arrows, then the strengths and the look's shape as scrub rows. */
const UNDERTONES=[['neutral','Neutral',''],['amber','Amber','sepia(.3) saturate(1.1)'],['gold','Gold','sepia(.45) saturate(1.2) hue-rotate(-8deg)'],['rose-gold','Rose Gold','sepia(.3) hue-rotate(-20deg) saturate(1.1)'],['bright','Bright','brightness(1.12) saturate(1.1)'],['cool-rose','Cool Rose','hue-rotate(-30deg) saturate(.9)']];
const LOOKS=[['original','Original',''],['vivid','Vivid','saturate(1.45) contrast(1.08)'],['dramatic','Dramatic','contrast(1.35) saturate(.85)'],['warm','Warm','sepia(.25) saturate(.85)'],['cool','Cool','hue-rotate(15deg) saturate(.7)'],['mono','Mono','grayscale(1)'],['noir','Noir','grayscale(1) contrast(1.4)'],['silvertone','Silvertone','grayscale(1) brightness(1.1) contrast(.9)']];
function stylePager(group,options,fallback){
  const view=state.mode==='video'?VIDEO_VIEW:undefined,chosen=val(group,fallback);
  const tiles=options.map(([key,label,filter])=>`<button data-action="style" data-group="${group}" data-style="${key}" class="style-tile ${chosen===key?'selected':''}" aria-pressed="${chosen===key}"><span class="style-thumb" style="filter:${filter}">${photoSvg(view)}</span><span class="style-name">${label}</span></button>`).join('');
  const arrow=(dir,glyph,label)=>`<button class="pager-arrow" data-action="style-page" data-dir="${dir}" aria-label="${label}">${icon(glyph)}</button>`;
  return `<div class="style-pager">${arrow(-1,'chevronLeft','Previous')}<div class="style-row">${tiles}</div>${arrow(1,'chevronRight','Next')}</div>`;
}
function stylesPanel(){
  // Only what can do something: Palette with an undertone, the rest with a look (the author, 2026-11-25).
  const undertone=val('undertone','neutral')!=='neutral',look=val('style','original')!=='original';
  const rows=[undertone&&scrub('palette','Palette',val('palette',100),0,100,1,'%','undertone'),look&&scrub('intensity','Intensity',val('intensity',100),0,100,1,'%'),look&&scrub('vividness','Vividness',val('vividness'),-100,100,1,'','vibrance'),look&&scrub('lookBrightness','Brightness',val('lookBrightness'),-100,100,1,'','brightness')].filter(Boolean);
  return section('Undertone',stylePager('undertone',UNDERTONES,'neutral'))+section('Look',stylePager('style',LOOKS,'original'))+(rows.length?`<div class="style-well">${well(...rows)}</div>`:'');
}
/* Markup as the app has it (RD-203): the pen, then its colour, size and — for text — style
   and plate. Text is placed by clicking the picture with the Text pen, and edited there. */
function swatchRow(key,colors,fallback){return `<div class="swatch-row">${colors.map(c=>button('swatch','',`${val(key,fallback)===c?'selected':''} ${c==='none'?'none':''}`,`style="--swatch:${c==='none'?'transparent':c}" data-key="${key}" data-color="${c}" aria-label="${c==='none'?'No plate':'Colour '+c}"`)).join('')}</div>`;}
function markupPanel(){
  const kind=val('markType','text'),text=kind==='text',faces=val('faces',[]);
  const types=segmented('mark-type',[['text','Text'],['arrow','Arrow'],['rect','Rectangle'],['ellipse','Ellipse'],['pen','Pen']].map(([key,label])=>['mark-type',icon(key),kind===key,`data-type="${key}" aria-label="${label}" data-tip="${label}"`]));
  const hint=text&&!state.captionVisible?'<p class="panel-hint">Click the picture to add text.</p>':'';
  const size=text?scrub('textSize','Text size',val('textSize',24),12,72,1,'px','textSize'):scrub('strokeWidth','Width',val('strokeWidth',6),1,24,1,'px','pen');
  const style=text?section('Style',`<div class="segmented face-row">${[['bold','B'],['italic','I'],['underline','U'],['strike','S']].map(([face,label])=>button('face',`<span class="face-${face}">${label}</span>`,faces.includes(face)?'selected':'',`data-face="${face}" aria-pressed="${faces.includes(face)}"`)).join('')}</div>`):'';
  const plate=val('plate','none');
  const background=text?section('Background',swatchRow('plate',['none','#151515','#ffffff','#ffd60a','#ff9f0a','#64d2ff'],'none'))+(plate==='none'?'':well(scrub('corner','Corner',val('corner',25),0,100,1,'%','free'))):'';
  const timing=state.mode==='video'&&state.captionVisible?section('Timing',well(scrub('appearsAt','Appears at',val('appearsAt'),0,32,.1,'s','clips'),scrub('holdsFor','Holds for',val('holdsFor',6),0,32,.1,'s','clips'),scrub('fadeIn','Fade in',val('fadeIn'),0,3,.1,'s','fadeIn'),scrub('fadeOut','Fade out',val('fadeOut'),0,3,.1,'s','fadeOut'))+wellButton('appear-here','play','Appear at play head')):'';
  return `${types}${hint}${section('Colour',swatchRow('textColor',['#ffffff','#ffd60a','#ff9f0a','#ff6961','#64d2ff','#151515'],'#ffffff'))}${well(size)}${style}${background}${timing}`;
}
function soundPanel(){
  const clip=selectedClip();
  return `${well(toggle('mute','Mute',val('mute',false),'mute'),scrub('volume','Volume',val('volume',100),0,200,1,'%'))}${section('Fades',well(scrub('fadeIn','Fade in',val('fadeIn'),0,10,.1,'s'),scrub('fadeOut','Fade out',val('fadeOut'),0,10,.1,'s')))}${clip?'':well(toggle('even','Even loudness',val('even',false)))+wellButton('add-audio','music','Add music…')}`;
}
function clipPanel(){
  const clip=selectedClip();
  const pair=`<div class="button-pair">${wellButton('split','clips','Split','','data-tip="Split at the playhead (S)"')}${wellButton('delete-clip','trash','Delete','danger','data-tip="Delete this clip (Del)"')}</div>`;
  if(clip.freeze)return `${pair}${textAction('clip-menu','more','More actions')}`;
  return `${well(scrub('speed','Speed',clip.speed||1,.25,3,.25,'×'),clip.detached?'':toggle('mute','Mute sound',val('mute',false),'mute'))}${pair}${textAction('clip-menu','more','More actions')}`;
}
function audioPanel(audio){
  return `${audio.missing?`<div class="info-box danger"><b>File not found</b><br>${escapeHtml(audio.name)}${button('relink','Locate file…','secondary wide')}</div>`:''}${well(toggle('mute','Mute',val('mute',false),'mute'),scrub('volume','Volume',val('volume',70),0,200,1,'%'))}${section('Fades',well(scrub('fadeIn','Fade in',val('fadeIn',1),0,Math.min(10,audio.duration),.1,'s'),scrub('fadeOut','Fade out',val('fadeOut',2),0,Math.min(10,audio.duration),.1,'s')))}${well(toggle('repeat','Loop to end of video',val('repeat',false)))}${textAction('delete-audio','trash','Remove audio','danger')}`;
}
