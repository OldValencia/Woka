const $=selector=>document.querySelector(selector);
const clamp=(value,min,max)=>Math.min(max,Math.max(min,value));
let counter=10,playing=false,lastTick=0,history=[],future=[],clipboard=null,inputBefore=null,toastTimer,relinkId=null;
const uid=prefix=>`${prefix}-${++counter}`;
function initialState(){return {mode:'photo',tool:'styles',group:'light',portrait:false,ratio:'Free',crop:{x:8,y:8,w:84,h:84},rotation:0,straighten:0,flipped:false,zoom:1,dirty:false,selection:null,time:8,timelineZoom:1,snap:true,showWave:false,captionVisible:false,settings:{},clips:[{id:'c1',name:'By the sea',duration:12,sourceStart:0,speed:1,settings:{}},{id:'c2',name:'A sunny afternoon',duration:8,sourceStart:15,speed:1,settings:{}},{id:'c3',name:'Evening light',duration:12,sourceStart:28,speed:1,settings:{}}],audio:[]};}
let state=initialState();
function selectedClip(){return state.clips.find(clip=>clip.id===state.selection);}
function selectedAudio(){return state.audio.find(audio=>audio.id===state.selection);}
function settings(){if(state.mode==='photo'&&state.frames&&state.frameScope==='single')return state.frames[state.frameIndex].settings;return (selectedAudio()||selectedClip()||state).settings;}
function remember(before){history.push(before);if(history.length>70)history.shift();future=[];}
function normalizeTimeline(){const end=duration();state.time=clamp(state.time,0,end);state.audio.forEach(audio=>{audio.start=Math.min(audio.start,Math.max(0,end-.5));audio.duration=clamp(audio.duration,.5,end-audio.start);});}
function commit(change){const before=JSON.stringify(state);change();state.dirty=true;normalizeTimeline();remember(before);render();}
function undo(){if(!history.length)return;future.push(JSON.stringify(state));state=JSON.parse(history.pop());render();}
function redo(){if(!future.length)return;history.push(JSON.stringify(state));state=JSON.parse(future.pop());render();}
function toast(message,canUndo=false){$('#toast').innerHTML=`<span>${escapeHtml(message)}</span>${canUndo?button('toast-undo','Undo','quiet'):''}`;$('#toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(hideToast,canUndo?7000:4500);}
function modal(title,html,cls=''){$('#dialog').className=cls;$('#dialog-title').textContent=title;$('#dialog-content').innerHTML=html;$('#dialog').showModal();}
/* A scene's document as it opens. withKeptEdits=false is the file with nothing kept on it, which is
   what "is this file edited" compares against; Palms.jpg opens with the Warm style already kept, so
   the prototype has an edited file without a round trip through the editor. */
function sceneState(scene,withKeptEdits=true){
  const s=initialState(),media=libraryMedia.find(item=>item.id===scene);
  s.mode=['video','audio'].includes(scene)||media?.audioOnly?'video':'photo';
  if(scene==='crop'){s.portrait=true;s.tool='crop';}
  if(media?.frames>1){s.frames=Array.from({length:media.frames},()=>({settings:{}}));s.frameIndex=0;s.frameScope='all';}
  if(scene==='video'){s.tool='clip';s.selection='c2';}
  if(scene==='audio'){s.tool='sound';s.audio=[{id:'a1',name:'Sea breeze.m4a',start:4,duration:26,settings:{volume:55,fadeIn:1.5,fadeOut:3}}];s.selection='a1';}
  if(withKeptEdits&&scene==='palm')s.settings={style:'warm'};
  return s;
}
function setScene(scene){
  if(transitionBusy)return;
  handOverSound();
  if(currentScene){flushInput();sceneSessions.set(currentScene,{state:JSON.stringify(state),history:[...history],future:[...future],saved:savedSnapshot});}
  currentScene=scene;compareOriginal=false;hideTooltip();
  playing=false;state=sceneState(scene);history=[];future=[];inputBefore=null;
  const remembered=sceneSessions.get(scene);
  if(remembered){state=JSON.parse(remembered.state);history=remembered.history;future=remembered.future;savedSnapshot=remembered.saved;}else savedSnapshot=JSON.stringify(state);
  viewerMode=false;$('#editor').classList.remove('viewing','focus');$('.header-actions').hidden=false;$('.viewer-actions').hidden=true;
  document.documentElement.style.setProperty('--timeline-height',scene==='audio'?'320px':'220px');
  pan={x:0,y:0};render();
}
function cropDimensions(){const w=state.portrait?3024:4032,h=state.portrait?4032:3024;return `${Math.round(w*state.crop.w/100)} × ${Math.round(h*state.crop.h/100)} px`;}
function updatePicture(){
  const picture=$('#picture'),video=state.mode==='video',crop=state.tool==='crop';
  picture.classList.toggle('portrait',state.portrait&&!video);
  $('#photo').setAttribute('viewBox',state.portrait&&!video?'650 60 520 715':video?VIDEO_VIEW:'350 55 1000 720');
  const w=parseFloat(picture.style.width)||picture.clientWidth||1,h=parseFloat(picture.style.height)||picture.clientHeight||1;
  pan.x=clamp(pan.x,-(state.zoom-1)*w/2,(state.zoom-1)*w/2);pan.y=clamp(pan.y,-(state.zoom-1)*h/2,(state.zoom-1)*h/2);
  picture.style.transform=`translate(${pan.x}px,${pan.y}px) scale(${state.zoom})`;
  $('#stage').classList.toggle('pannable',state.zoom>1&&!viewerMode);
  const clip=selectedClip(),s=video?(skimTime===null&&clip?{...state.settings,...clip.settings}:lookAt(skimTime??state.time)):settings();
  const filters={original:'',warm:'sepia(.25) saturate(.85)',cool:'hue-rotate(15deg) saturate(.7)',mono:'grayscale(1)',soft:'contrast(.8)',film:'sepia(.4) contrast(1.1)'};
  $('#photo').style.filter=`brightness(${1+(s.exposure||0)/200+(s.brightness||0)/250}) contrast(${1+(s.contrast||0)/150}) saturate(${1+(s.saturation||0)/100}) sepia(${Math.max(0,s.warmth||0)/250}) ${filters[stylePreview??s.style]||''}`;
  const angle=state.straighten||0,rad=Math.abs(angle)*Math.PI/180,ratio=w/h,cover=Math.cos(rad)+Math.max(ratio,1/ratio)*Math.sin(rad);
  $('#photo').style.transform=`rotate(${state.rotation+angle}deg) scale(${cover}) scaleX(${state.flipped?-1:1})`;
  $('#crop-box').hidden=!crop;
  const c=state.crop;Object.assign($('#crop-box').style,{left:`${c.x}%`,top:`${c.y}%`,width:`${c.w}%`,height:`${c.h}%`});
  if($('#crop-dimensions'))$('#crop-dimensions').textContent=cropDimensions();
  applyComparison();
  syncWokaChrome();
  syncCaption();
}
function render(){
  syncDirty();
  const video=state.mode==='video';$('#editor').classList.toggle('video',video);$('#timeline').hidden=!video;
  showDirty();
  $('#undo').disabled=!history.length;$('#redo').disabled=!future.length;
  renderFrameStrip();renderInspector();renderTimeline();updatePicture();
}
function setTool(tool){
  flushInput();
  if(state.mode==='video'){
    if(tool==='crop'){if(selectedClip()||selectedAudio())toast('Crop applies to the whole video.');state.selection=null;}
    if(tool==='markup')state.selection=state.captionVisible?'caption':null;
    else if(state.selection==='caption')state.selection=null;
  }
  state.tool=tool;render();
}
function applyRatio(){
  if(['Free','Original'].includes(state.ratio)){state.crop={x:8,y:8,w:84,h:84};return;}
  let [w,h]=state.ratio.split(':').map(Number);if(state.portrait)[w,h]=[h,w];
  const base=state.mode==='video'?16/9:state.portrait?520/715:1000/720;
  const relative=w/h/base;const cw=relative>1?84:84*relative,ch=relative>1?84/relative:84;
  state.crop={x:(100-cw)/2,y:(100-ch)/2,w:cw,h:ch};
}
function setValue(key,value){
  const audio=selectedAudio(),clip=selectedClip();
  if(key==='straighten')state.straighten=value;
  else if(key==='speed'&&clip){clip.duration=clip.duration*(clip.speed||1)/value;clip.speed=value;}
  else if(key==='clipDuration'&&clip)clip.duration=value;
  else if(key==='sourceStart'&&clip)clip.sourceStart=value;
  else if(key==='audioStart'&&audio)audio.start=value;
  else if(key==='audioDuration'&&audio)audio.duration=value;
  else{settings()[key]=value;if(key==='caption')state.captionVisible=true;}
  normalizeTimeline();state.dirty=true;updatePicture();if(state.mode==='video')renderTimeline();showDirty();refreshControlStates();
}
function outputDialog(kind='copy'){
  const video=state.mode==='video';
  if(kind!=='copy'){
    const frame=kind==='frame';
    modal(frame?'Save frame as photo':'Save audio',`<div class="output-option"><div><strong>${frame?'Current frame · PNG':'Audio · M4A'}</strong><p>${frame?formatTime(state.time):'With your cuts, volume and fades.'}</p></div>${button('demo-export','Save','primary',`data-label="${frame?'Saving frame':'Saving audio'}"`)}</div>`);
    return;
  }
  const sizes=video?[['Original','1920 × 1080'],['720p','1280 × 720'],['Small','854 × 480']]:[['Original',state.portrait?'3024 × 4032':'4032 × 3024'],['Large','2048 px'],['Small','1080 px']];
  modal('Create a finished copy',`<p>A new file with your edits. The original stays as it is.</p><h3>Size</h3><div class="segmented output-size">${sizes.map(([name,detail],index)=>button('out-size',`<b>${name}</b><small>${detail}</small>`,index===0?'selected':'')).join('')}</div>${video?toggle('outSdr','<span data-tip="Use this when the video looks too bright or washed out in other apps.">Standard colours (SDR)</span>',false):''}<div class="dialog-actions">${video?button('extract','Audio only…','quiet'):''}${button('demo-export','Create copy','primary','data-label="Creating copy"')}</div>`);
}

function action(name,node){
  if(name.startsWith('tool-')){setTool(name.slice(5));return;}
  if(name.startsWith('mute-track:')){const audio=state.audio.find(a=>a.id===name.slice(11));commit(()=>audio.settings.mute=!audio.settings.mute);return;}
  const commands={
    'scope-all':()=>{const wasAudio=!!selectedAudio();state.selection=null;if(wasAudio)state.tool='sound';if(['clip','markup'].includes(state.tool))state.tool='styles';render();},
    'frame':()=>{frameClock.playing=false;state.frameIndex=+node.dataset.frame;if(!viewerMode)state.frameScope='single';render();},
    'frame-scope':()=>{state.frameScope=node.dataset.scope;render();},
    'adjust-group':()=>{state.group=node.dataset.group;renderInspector();},
    auto:()=>{easePhoto();commit(()=>Object.assign(settings(),{exposure:12,contrast:8,shadows:18,highlights:-16}));},
    'reset-adjust':()=>commit(()=>['exposure','brightness','contrast','blackPoint','highlights','shadows','saturation','vibrance','warmth','tint','noise','sharpness'].forEach(key=>delete settings()[key])),
    ratio:()=>{easeCrop();commit(()=>{state.ratio=node.dataset.ratio;applyRatio();});},
    orientation:()=>{easeCrop();commit(()=>{state.portrait=node.dataset.portrait==='true';applyRatio();});},
    rotate:()=>{easeCrop();commit(()=>state.rotation+=90);},
    flip:()=>{easeCrop();commit(()=>state.flipped=!state.flipped);},
    'reset-crop':()=>{easeCrop();commit(()=>{state.crop={x:8,y:8,w:84,h:84};state.ratio='Free';state.rotation=0;state.straighten=0;state.flipped=false;});},
    style:()=>{stylePreview=null;commit(()=>settings().style=node.dataset.style);},
    'mark-type':()=>commit(()=>settings().markType=node.dataset.type),
    font:()=>commit(()=>settings().font=node.dataset.font),
    caption:()=>{commit(()=>{state.captionVisible=true;if(state.mode==='video'){const s=state.settings,total=duration();state.selection='caption';s.captionStart=Math.min(state.time,Math.max(0,total-1));s.captionDuration=Math.min(4,total-s.captionStart);}});if(state.mode==='video'){if(document.querySelector('#timeline').clientHeight<280)document.documentElement.style.setProperty('--timeline-height','280px');document.querySelector('[data-caption]')?.scrollIntoView({block:'nearest'});}},
    'remove-text':()=>{commit(()=>{state.captionVisible=false;if(state.selection==='caption')state.selection=null;});toast('Text removed.',true);},
    'edit-text':()=>setTimeout(editCaption,0),
    'text-color':()=>commit(()=>settings().textColor=node.dataset.color),
    split:splitClip,
    'move-left':()=>moveClip(-1),'move-right':()=>moveClip(1),
    freeze:()=>{const clip=selectedClip();if(clip)flipLane(()=>commit(()=>{const freeze={id:uid('clip'),name:'Freeze frame',duration:2,sourceStart:clip.sourceStart,speed:1,freeze:true,settings:{...clip.settings}};state.clips.splice(state.clips.indexOf(clip)+1,0,freeze);state.selection=freeze.id;}));},
    'delete-clip':()=>{if(state.clips.length===1){toast('Keep at least one clip on the timeline.');return;}flipLane(()=>commit(()=>{state.clips=state.clips.filter(c=>c.id!==state.selection);state.selection=null;}));toast('Clip removed. The remaining clips close the gap.',true);},
    'copy-look':()=>{clipboard=structuredClone(settings());toast('Clip settings copied.');},
    'paste-look':()=>{if(!clipboard){toast('Copy the settings from another clip first.');return;}const kind=node.dataset.kind||'both',soundKeys=['volume','mute','fadeIn','fadeOut','even'];commit(()=>{const target=settings();const accepts=key=>kind==='both'||(kind==='sound')===soundKeys.includes(key);Object.keys(target).filter(accepts).forEach(key=>delete target[key]);Object.keys(clipboard).filter(accepts).forEach(key=>target[key]=clipboard[key]);});},
    detach:detachAudio,
    'mute-original':()=>commit(()=>state.settings.mute=!state.settings.mute),
    'add-audio':()=>modal('Add music',`<p>Starts at the playhead. You can also drop a file onto the timeline.</p><h3>Recent</h3><div class="output-option"><div><strong>Sea breeze.m4a</strong><p>00:24</p></div>${button('demo-audio','Add','primary')}</div>${button('pick-audio','Choose an audio file…','secondary wide')}`),
    'demo-audio':()=>{$('#dialog').close();addAudio();},
    'pick-audio':()=>{$('#dialog').close();relinkId=null;$('#audio-file').click();},
    'delete-audio':()=>{commit(()=>{state.audio=state.audio.filter(a=>a.id!==state.selection);state.selection=null;state.tool='sound';});toast('Audio removed from this edit.',true);},
    'mute-selected':()=>{const audio=selectedAudio();if(audio)commit(()=>audio.settings.mute=!audio.settings.mute);},
    relink:()=>{relinkId=state.selection;$('#audio-file').click();},
    cover:()=>{commit(()=>state.settings.coverTime=state.time);toast(`Frame ${formatTime(state.time)} is now the cover in this preview.`);},
    'frame-photo':()=>outputDialog('frame'),
    extract:()=>{$('#dialog').close();outputDialog('audio');},
    'demo-export':()=>{$('#dialog').close();startJob(node.dataset.label||'Creating copy');},
    'job-cancel':()=>{jobAnimation?.cancel();jobAnimation=null;toast('Cancelled. Nothing was saved.');},
    'toast-dismiss':hideToast,
    'out-size':()=>node.parentElement.querySelectorAll('button').forEach(item=>item.classList.toggle('selected',item===node)),
    'clip-menu':()=>{const rect=node.getBoundingClientRect();openMenu(rect.left,rect.bottom+4,clipMenu());},
    'close-modal':()=>$('#dialog').close(),
    'confirm-reset':()=>{$('#dialog').close();commit(()=>{const mode=state.mode;state=initialState();state.mode=mode;state.tool='styles';state.audio=[];if(mode==='video')state.clips=[{id:'original',name:'Original video',duration:40,sourceStart:0,speed:1,settings:{}}];});}
  };
  commands[name]?.();
}
function moveClip(delta){const index=state.clips.findIndex(c=>c.id===state.selection),next=index+delta;if(index<0||next<0||next>=state.clips.length)return;flipLane(()=>commit(()=>state.clips.splice(next,0,state.clips.splice(index,1)[0])));}
function initCrop(){
  const box=$('#crop-box');
  const change=(original,edge,dx,dy)=>{
    const c={...original};if(edge==='top'){c.y=clamp(original.y+dy,0,original.y+original.h-20);c.h=original.h+original.y-c.y;}
    else if(edge==='bottom')c.h=clamp(original.h+dy,20,100-original.y);
    else if(edge==='left'){c.x=clamp(original.x+dx,0,original.x+original.w-20);c.w=original.w+original.x-c.x;}
    else if(edge==='right')c.w=clamp(original.w+dx,20,100-original.x);
    else{c.x=clamp(original.x+dx,0,100-original.w);c.y=clamp(original.y+dy,0,100-original.h);}
    state.crop=c;state.ratio='Free';updatePicture();
  };
  box.addEventListener('pointerdown',event=>{event.preventDefault();const before=JSON.stringify(state),original={...state.crop},rect=$('#picture').getBoundingClientRect(),x=event.clientX,y=event.clientY,edge=event.target.dataset.edge;let changed=false;
    const move=e=>{changed=true;change(original,edge,(e.clientX-x)/rect.width*100,(e.clientY-y)/rect.height*100);};
    document.addEventListener('pointermove',move);document.addEventListener('pointerup',()=>{document.removeEventListener('pointermove',move);if(changed){remember(before);state.dirty=true;}render();},{once:true});
  });
  box.addEventListener('keydown',event=>{if(!event.key.startsWith('Arrow'))return;event.preventDefault();const step=event.shiftKey?5:1;commit(()=>change(state.crop,event.target.dataset.edge,event.key==='ArrowRight'?step:event.key==='ArrowLeft'?-step:0,event.key==='ArrowDown'?step:event.key==='ArrowUp'?-step:0));$('.crop-handle.'+event.target.dataset.edge)?.focus();});
}
function play(){if(currentMedia().gif&&viewerMode){setFramePlaying(!frameClock.playing);return;}if(state.mode!=='video')return;playing=!playing;if(playing&&state.time>=duration())state.time=0;lastTick=performance.now();updatePlayhead();if(playing)requestAnimationFrame(tick);}
function tick(now){if(!playing)return;state.time=Math.min(duration(),state.time+(now-lastTick)/1000*(viewerMode?viewerSpeed:1));lastTick=now;if(state.time>=duration())playing=false;updatePlayhead();if(playing)requestAnimationFrame(tick);}
document.addEventListener('click',event=>{
  const tool=event.target.closest('[data-tool]');if(tool){setTool(tool.dataset.tool);return;}
  const node=event.target.closest('[data-action]');if(node)action(node.dataset.action,node);
});
document.addEventListener('input',event=>{
  const node=event.target,key=node.dataset.value;if(!key)return;if(inputBefore===null)inputBefore=JSON.stringify(state);
  let value=node.type==='checkbox'?node.checked:['range','number'].includes(node.type)?+node.value:node.value;
  if(node.type==='number'||node.type==='range'){if(!Number.isFinite(value)||node.value==='')return;value=clamp(value,+node.min,+node.max);}
  if(node.type==='range'){
    const def=defaultValue(key),min=+node.min,max=+node.max,control=node.closest('.control');
    if(def>min&&def<max&&Math.abs(value-def)<=(max-min)*.025){value=def;node.value=def;}
    if(control){const snapped=value===def&&def>min&&def<max;if(snapped&&!control.classList.contains('snapped')&&!reducedMotion())control.querySelector('.detent')?.animate([{transform:'none'},{transform:'scale(1.8,1.6)'},{transform:'none'}],{duration:220,easing:'ease-out'});control.classList.toggle('snapped',snapped);}
  }
  setValue(key,value);document.querySelectorAll(`[data-value="${key}"]`).forEach(other=>{if(other!==node)other.value=value;});
});
document.addEventListener('change',event=>{if(!event.target.dataset.value)return;flushInput();render();});
document.addEventListener('dblclick',event=>{const node=event.target;if(node.type==='range'&&node.dataset.value){commit(()=>setValue(node.dataset.value,clamp(defaultValue(node.dataset.value),+node.min,+node.max)));}});
$('#undo').innerHTML=icon('undo');$('#redo').innerHTML=icon('redo');$('#focus').innerHTML=icon('focus');$('#theme').innerHTML=icon('sun');$('#play').innerHTML=icon('play');$('#split').innerHTML=icon('clips')+' Split';$('#add-audio').innerHTML=icon('music')+' Audio';$('#compare').innerHTML=icon('compare')+'<span>Original</span>';
$('#undo').onclick=undo;$('#redo').onclick=redo;$('#play').onclick=play;$('#split').onclick=splitClip;$('#add-audio').onclick=()=>action('add-audio');;
$('#theme').onclick=()=>{document.documentElement.dataset.theme=document.documentElement.dataset.theme==='dark'?'light':'dark';};
$('#focus').onclick=()=>{$('#editor').classList.toggle('focus');updatePlayhead();};
$('#output').onclick=()=>outputDialog();
$('#reset').onclick=()=>modal('Revert to original?',`<p>All edits to ${state.mode==='video'?'the video, including cuts and added audio':'the photo'} will be reset. You can undo this action.</p>${button('confirm-reset','Revert to original','primary wide')}${button('close-modal','Cancel','quiet wide')}`);
$('#close-dialog').onclick=()=>$('#dialog').close();
$('#audio-file').onchange=event=>{
  const file=event.target.files[0];if(!file)return;
  if(relinkId){commit(()=>{const audio=state.audio.find(a=>a.id===relinkId);if(audio){audio.name=file.name;audio.missing=false;}});relinkId=null;event.target.value='';return;}
  readAudioFile(file);event.target.value='';
};
document.addEventListener('keydown',event=>{
  if($('#dialog').open||document.activeElement.isContentEditable||['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName))return;
  if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='z'){event.preventDefault();event.shiftKey?redo():undo();}
  if(event.code==='Space'&&document.activeElement.tagName!=='BUTTON'){event.preventDefault();play();}
  if(event.key.toLowerCase()==='s'&&!event.ctrlKey&&!event.metaKey&&!event.altKey&&state.mode==='video'&&!viewerMode){event.preventDefault();splitClip();}
  if((event.key==='Home'||event.key==='End')&&state.mode==='video'&&!viewerMode){event.preventDefault();state.time=event.key==='Home'?0:duration();updatePlayhead();}
  if(event.key==='F6'){event.preventDefault();$('#focus').click();}
  if(event.key==='Escape'){if(!$('#tooltip').hidden){hideTooltip();return;}if(compareOriginal)toggleComparison(false);else if($('#editor').classList.contains('focus'))$('#focus').click();else if(state.selection){state.selection=null;render();}else requestExit();}
  if(event.key==='Delete'&&!viewerMode){if(selectedAudio())action('delete-audio');else if(selectedClip())action('delete-clip');else if(state.selection==='caption')action('remove-text');}
});
window.addEventListener('resize',updatePlayhead);
initTimeline();initCrop();initWoka();initTransport();initNowPlaying();initFullscreen();initDialogs();setScene('photo');initExperience();initRefine();initInstruments();
