let skimTime=null,timelineDragging=false;
const EASE='cubic-bezier(.22,1,.36,1)';
function duration(){return state.clips.reduce((sum,clip)=>sum+clip.duration,0);}
function formatTime(seconds){return `${String(Math.floor(seconds/60)).padStart(2,'0')}:${(seconds%60).toFixed(1).padStart(4,'0')}`;}
function waveform(seed=1,count=160){
  let bars='';
  for(let i=0;i<count;i++){const h=3+Math.abs(Math.sin(i*2.13+seed)*Math.cos(i*.17+seed))*24;bars+=`<path d="M${i*4} ${15-h/2}v${h}"/>`;}
  return `<svg class="waveform" viewBox="0 0 ${count*4} 30" preserveAspectRatio="none" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">${bars}</svg>`;
}
function clipLabel(clip){return clip.freeze?'Still frame':`Clip ${state.clips.indexOf(clip)+1}`;}
function clipAt(time){let at=0;for(const clip of state.clips){if(time<at+clip.duration)return {clip,start:at};at+=clip.duration;}const last=state.clips[state.clips.length-1];return {clip:last,start:duration()-last.duration};}
function lookAt(time){const clip=clipAt(time).clip;return {...state.settings,...(clip?clip.settings:{})};}
function splittable(){let at=0;for(const clip of state.clips){if(state.time>at+.3&&state.time<at+clip.duration-.3)return true;at+=clip.duration;}return false;}
function trackLabel(name,tip){return `<div class="track-label" data-tip="${tip}">${icon(name)}</div>`;}
function captionTiming(){const s=state.settings;return {start:s.captionStart??3,length:s.captionDuration??6,fade:s.captionFade??true};}
function renderTimeline(){
  if(state.mode!=='video')return;
  const total=duration(),tracks=document.querySelector('#tracks'),marks=Math.round(8*state.timelineZoom);
  tracks.style.width=`${state.timelineZoom*100}%`;
  let ruler='';
  for(let i=0;i<marks;i++)ruler+=`<span style="left:${i/marks*100}%">${formatTime(total*i/marks)}</span>`;
  let html=`<div class="track-line ruler-row"><div class="track-label"></div><div class="ruler" id="ruler" aria-label="Playback position" role="slider" tabindex="0" aria-valuemin="0" aria-valuemax="${total}" aria-valuenow="${state.time}">${ruler}</div></div>`;
  let at=0;
  const clips=state.clips.map((clip,index)=>{
    const silent=clip.freeze||clip.detached,muted=silent||clip.settings.mute||state.settings.mute;
    const badge=clip.freeze?' · still':clip.speed!==1?` · ${clip.speed}×`:'';
    const markup=`<button class="clip ${state.selection===clip.id?'selected':''}" data-clip="${clip.id}" style="left:${at/total*100}%;width:calc(${clip.duration/total*100}% - 3px)" aria-label="${clipLabel(clip)}, ${clip.duration.toFixed(1)} seconds"><span class="thumb-strip" style="filter:hue-rotate(${index*6}deg)">${photoSvg(VIDEO_VIEW)}${photoSvg(VIDEO_VIEW)}${photoSvg(VIDEO_VIEW)}</span><span class="clip-name">${clip.duration.toFixed(1)} s${badge}</span><span class="clip-wave ${muted?'muted':''}" data-wave>${silent?'':waveform(index,90)}</span><span class="clip-handle left" data-trim="left"></span><span class="clip-handle right" data-trim="right"></span></button>`;
    at+=clip.duration;return markup;
  }).join('');
  html+=`<div class="track-line video-row">${trackLabel('clip','Video and its sound')}<div class="lane video-lane">${clips}</div></div>`;
  state.audio.forEach((audio,index)=>{
    html+=`<div class="track-line audio-row">${trackLabel('music',audio.detached?'Detached sound':'Music')}<div class="lane"><button class="clip audio ${state.selection===audio.id?'selected':''} ${audio.missing?'audio-missing':''}" data-audio="${audio.id}" style="left:${audio.start/total*100}%;width:${Math.min(audio.duration,total-audio.start)/total*100}%;opacity:${audio.settings.mute?.45:1}" aria-label="${escapeHtml(audio.name)}"><span class="fade"></span>${audio.missing?'':waveform(index+4)}<span class="clip-name">${audio.missing?'File not found · ':''}${escapeHtml(audio.name)}</span><span class="clip-handle left" data-trim="left"></span><span class="clip-handle right" data-trim="right"></span></button></div></div>`;
  });
  if(state.captionVisible){
    const {start,length,fade}=captionTiming();
    html+=`<div class="track-line text-row">${trackLabel('text','Text')}<div class="lane"><button class="clip text-bar ${state.selection==='caption'?'selected':''} ${fade?'faded':''}" data-caption style="left:${start/total*100}%;width:${Math.min(length,total-start)/total*100}%" aria-label="Text, ${length.toFixed(1)} seconds"><span class="clip-name">${escapeHtml(state.settings.caption||'Nowhere else to be.')}</span><span class="clip-handle left" data-trim="left"></span><span class="clip-handle right" data-trim="right"></span></button></div></div>`;
  }
  if(!state.audio.length)html+=`<div class="track-line ghost-row">${trackLabel('music','Music')}<div class="lane"><button class="ghost-add" data-action="add-audio">${icon('music')} Add music or drop a file here</button></div></div>`;
  tracks.innerHTML=html+`<div id="playhead" class="playhead"><button id="playhead-split" class="playhead-split" data-action="split" aria-label="Split here (S)">${icon('clips')}</button></div><div id="skim" class="skim" hidden></div><div id="snap-guide" class="snap-guide" hidden></div>`;
  updatePlayhead();
}
function rulerX(time){const ruler=document.querySelector('#ruler');return ruler.offsetLeft+time/duration()*ruler.clientWidth;}
function updatePlayhead(){
  const time=document.querySelector('#time');if(time)time.innerHTML=`${formatTime(state.time)} <span>/ ${formatTime(duration())}</span>`;
  const ruler=document.querySelector('#ruler'),head=document.querySelector('#playhead');
  if(ruler&&head){head.style.left=`${rulerX(state.time)}px`;ruler.setAttribute('aria-valuenow',state.time.toFixed(1));}
  const scissors=document.querySelector('#playhead-split');if(scissors)scissors.disabled=playing||!splittable();
  document.querySelector('#play').innerHTML=icon(playing?'pause':'play');
  document.querySelector('#play').setAttribute('aria-label',playing?'Pause (Space)':'Play (Space)');
  syncViewerPlayback();syncFrame();
}
function timeAtX(x){const rect=document.querySelector('#ruler').getBoundingClientRect();return clamp((x-rect.left)/rect.width*duration(),0,duration());}
function seek(event){state.time=timeAtX(event.clientX);updatePlayhead();}
function snapTime(value,free){
  if(free)return {value,guide:null};
  const total=duration(),tolerance=total/document.querySelector('#ruler').clientWidth*8,points=[0,state.time];
  let boundary=0;state.clips.forEach(clip=>{boundary+=clip.duration;points.push(boundary);});
  const hit=points.find(point=>Math.abs(value-point)<tolerance);
  return hit===undefined?{value,guide:null}:{value:hit,guide:hit};
}
function showGuide(time){const guide=document.querySelector('#snap-guide');if(!guide)return;guide.hidden=time===null||time===undefined;if(!guide.hidden)guide.style.left=`${rulerX(time)}px`;}
function showBadge(event,text){const badge=document.querySelector('#drag-badge');badge.textContent=text;badge.hidden=false;badge.style.left=`${event.clientX}px`;badge.style.top=`${event.clientY}px`;}
function hideBadge(){document.querySelector('#drag-badge').hidden=true;}
function flipLane(change){
  const before=new Map([...document.querySelectorAll('[data-clip]')].map(node=>[node.dataset.clip,node.getBoundingClientRect()]));
  change();
  if(reducedMotion())return;
  document.querySelectorAll('[data-clip]').forEach(node=>{
    const old=before.get(node.dataset.clip),now=node.getBoundingClientRect();
    if(!old){node.animate([{opacity:0,transform:'scale(.9)'},{opacity:1,transform:'none'}],{duration:240,easing:EASE});return;}
    const dx=old.left-now.left,sx=old.width/Math.max(1,now.width);
    if(Math.abs(dx)>1||Math.abs(sx-1)>.01)node.animate([{transformOrigin:'0 0',transform:`translateX(${dx}px) scaleX(${sx})`},{transformOrigin:'0 0',transform:'none'}],{duration:260,easing:EASE});
  });
}
function splitClip(){
  let at=0;const index=state.clips.findIndex(clip=>{const inside=state.time>at+.3&&state.time<at+clip.duration-.3;if(!inside)at+=clip.duration;return inside;});
  if(index<0){toast('Move the playhead inside a clip, away from its edges.');return;}
  flipLane(()=>commit(()=>{const clip=state.clips[index],left=state.time-at,right={...structuredClone(clip),id:uid('clip'),duration:clip.duration-left,sourceStart:(clip.sourceStart||0)+left*(clip.speed||1)};clip.duration=left;state.clips.splice(index+1,0,right);state.selection=right.id;state.tool='clip';}));
}
function addAudio(name='Sea breeze.m4a',length=24,detached=false,at=state.time){
  let id;
  commit(()=>{id=uid('audio');const start=Math.min(at,duration()-.5);state.audio.push({id,name,start,duration:Math.min(length,duration()-start)||duration(),detached,settings:{volume:70,fadeIn:1,fadeOut:2}});state.selection=id;});
  document.documentElement.style.setProperty('--timeline-height','320px');
  const node=document.querySelector(`[data-audio="${id}"]`);
  node?.scrollIntoView({block:'nearest',inline:'nearest'});
  if(node&&!reducedMotion())node.closest('.track-line').animate([{opacity:0,transform:'translateY(10px)'},{opacity:1,transform:'none'}],{duration:260,easing:EASE});
}
function readAudioFile(file,at=state.time){
  const source=URL.createObjectURL(file),audio=new Audio();let settled=false;
  const finish=()=>{if(settled)return;settled=true;addAudio(file.name,Number.isFinite(audio.duration)?audio.duration:24,false,at);URL.revokeObjectURL(source);audio.removeAttribute('src');};
  audio.onloadedmetadata=finish;audio.onerror=finish;audio.src=source;setTimeout(finish,1800);
}
function detachAudio(){
  const clip=selectedClip();if(!clip){toast('Select a video clip first.');return;}
  if(clip.detached){toast('This clip’s sound is already detached.');return;}
  commit(()=>{let at=0;for(const item of state.clips){if(item===clip)break;at+=item.duration;}const id=uid('audio');state.audio.push({id,name:`${clipLabel(clip)} sound`,start:at,duration:clip.duration,detached:true,settings:{...clip.settings,volume:clip.settings.volume??100}});clip.detached=true;state.selection=id;});
  document.documentElement.style.setProperty('--timeline-height','320px');
}
function zoomTimeline(step,anchorX){
  const scroll=document.querySelector('#timeline-scroll'),old=state.timelineZoom,next=clamp(old+step,1,4);if(next===old)return;
  const rect=scroll.getBoundingClientRect(),offset=(anchorX??rect.left+rect.width/2)-rect.left,content=scroll.scrollLeft+offset;
  state.timelineZoom=next;renderTimeline();scroll.scrollLeft=content*next/old-offset;
}
function selectFromTimeline(node,onWave){
  const wasAudio=!!selectedAudio();
  state.selection=node.dataset.clip||node.dataset.audio||'caption';
  if(node.dataset.caption!==undefined)state.tool='markup';
  else if(node.dataset.clip){if(onWave)state.tool='sound';else if(wasAudio||['crop','markup'].includes(state.tool))state.tool='clip';}
}
function hideSkim(){if(skimTime===null)return;skimTime=null;const line=document.querySelector('#skim');if(line)line.hidden=true;document.querySelector('#skim-badge').hidden=true;updatePicture();}
function skim(event){
  if(timelineDragging||playing||!document.querySelector('#ruler'))return;
  skimTime=timeAtX(event.clientX);
  const line=document.querySelector('#skim');line.hidden=false;line.style.left=`${rulerX(skimTime)}px`;
  const badge=document.querySelector('#skim-badge');badge.hidden=false;badge.textContent=formatTime(skimTime);
  updatePicture();
}
function dragItem(event,node){
  const kind=node.dataset.clip?'clip':node.dataset.audio?'audio':'caption',edge=event.target.dataset.trim;
  const id=node.dataset.clip||node.dataset.audio||'caption',before=JSON.stringify(state),startX=event.clientX,total=duration(),width=node.parentElement.clientWidth;
  selectFromTimeline(node,!!event.target.closest('[data-wave]')&&!edge);hideSkim();
  const item=kind==='clip'?selectedClip():kind==='audio'?selectedAudio():null,caption=captionTiming();
  const original=kind==='caption'?{start:caption.start,duration:caption.length}:{...item};
  const siblings=kind==='clip'?[...node.parentElement.querySelectorAll('[data-clip]')]:[];
  const rects=new Map(siblings.map(sibling=>[sibling.dataset.clip,sibling.getBoundingClientRect()]));
  const from=state.clips.findIndex(clip=>clip.id===id);let target=from,moved=false;
  renderInspector();document.querySelectorAll('#tracks .clip.selected').forEach(other=>other.classList.remove('selected'));node.classList.add('selected');
  timelineDragging=true;
  const move=e=>{
    const dx=e.clientX-startX;if(Math.abs(dx)<4&&!moved)return;moved=true;
    const delta=dx/width*total;let guide=null;
    if(kind==='clip'&&!edge){
      node.classList.add('dragging');node.style.transform=`translateX(${dx}px)`;
      const own=rects.get(id),center=own.left+own.width/2+dx,gap=own.width+3;
      target=state.clips.filter(clip=>clip.id!==id&&center>rects.get(clip.id).left+rects.get(clip.id).width/2).length;
      siblings.forEach(sibling=>{if(sibling===node)return;const index=state.clips.findIndex(clip=>clip.id===sibling.dataset.clip);const shift=index>from&&index<=target?-gap:index<from&&index>=target?gap:0;sibling.style.transform=shift?`translateX(${shift}px)`:'';});
      return;
    }
    if(kind==='clip'){item.duration=clamp(original.duration+(edge==='left'?-delta:delta),1,120);if(edge==='left')item.sourceStart=Math.max(0,(original.sourceStart||0)+original.duration-item.duration);showBadge(e,`${item.duration.toFixed(1)} s`);}
    else{
      let start=original.start,length=original.duration;
      if(edge==='left'){const hit=snapTime(clamp(original.start+delta,0,original.start+original.duration-.5),e.altKey);start=hit.value;guide=hit.guide;length=original.duration-(start-original.start);}
      else if(edge==='right'){const hit=snapTime(original.start+original.duration+delta,e.altKey);length=clamp(hit.value-original.start,.5,total-original.start);guide=hit.guide;}
      else{let next=clamp(original.start+delta,0,Math.max(0,total-original.duration));const head=snapTime(next,e.altKey),tail=snapTime(next+original.duration,e.altKey);if(head.guide!==null){next=head.value;guide=head.guide;}else if(tail.guide!==null){next=tail.value-original.duration;guide=tail.guide;}start=clamp(next,0,Math.max(0,total-original.duration));}
      if(kind==='audio'){item.start=start;item.duration=length;}else{state.settings.captionStart=start;state.settings.captionDuration=length;}
      showBadge(e,edge?`${length.toFixed(1)} s`:formatTime(start));
    }
    renderTimeline();showGuide(guide);renderInspector();if(kind==='caption')updatePicture();
  };
  const up=()=>{
    document.removeEventListener('pointermove',move);timelineDragging=false;hideBadge();showGuide(null);
    if(moved&&kind==='clip'&&!edge){flipLane(()=>{if(target!==from){state.clips.splice(target,0,state.clips.splice(from,1)[0]);remember(before);state.dirty=true;}render();});return;}
    if(moved){remember(before);state.dirty=true;normalizeTimeline();}render();
  };
  document.addEventListener('pointermove',move);document.addEventListener('pointerup',up,{once:true});
}
function initDrop(){
  const timeline=document.querySelector('#timeline'),hasFiles=e=>[...(e.dataTransfer?.types||[])].includes('Files');let depth=0;
  const reset=()=>{depth=0;timeline.classList.remove('drop-ready');showGuide(null);};
  timeline.addEventListener('dragenter',e=>{if(!hasFiles(e))return;e.preventDefault();depth++;timeline.classList.add('drop-ready');});
  timeline.addEventListener('dragover',e=>{if(!hasFiles(e))return;e.preventDefault();e.dataTransfer.dropEffect='copy';const at=timeAtX(e.clientX);showGuide(at);document.querySelector('#drop-time').textContent=formatTime(at);});
  timeline.addEventListener('dragleave',()=>{if(--depth<=0)reset();});
  timeline.addEventListener('drop',e=>{
    e.preventDefault();const at=timeAtX(e.clientX);reset();
    const file=[...e.dataTransfer.files].find(f=>f.type.startsWith('audio/')||/\.(m4a|mp3|wav|aac|flac|ogg|opus)$/i.test(f.name));
    if(!file){toast('That isn’t an audio file. Try MP3, M4A or WAV.');return;}
    readAudioFile(file,at);
  });
}
function initTimeline(){
  const tracks=document.querySelector('#tracks');
  tracks.addEventListener('pointerdown',event=>{
    if(event.button!==0||event.target.closest('#playhead-split'))return;
    if(event.target.closest('#ruler')){event.preventDefault();hideSkim();timelineDragging=true;seek(event);const move=e=>seek(e);document.addEventListener('pointermove',move);document.addEventListener('pointerup',()=>{timelineDragging=false;document.removeEventListener('pointermove',move);},{once:true});return;}
    const node=event.target.closest('[data-clip],[data-audio],[data-caption]');if(node)dragItem(event,node);
  });
  tracks.addEventListener('pointermove',skim);
  tracks.addEventListener('pointerleave',hideSkim);
  tracks.addEventListener('click',event=>{
    if(event.detail!==0)return;const node=event.target.closest('[data-clip],[data-audio],[data-caption]');if(node){selectFromTimeline(node,false);render();}
  });
  tracks.addEventListener('keydown',event=>{if(event.target.id==='ruler'&&['ArrowLeft','ArrowRight','Home','End'].includes(event.key)){event.preventDefault();state.time=event.key==='Home'?0:event.key==='End'?duration():clamp(state.time+(event.key==='ArrowRight'?.1:-.1),0,duration());updatePlayhead();}});
  document.querySelector('#timeline-scroll').addEventListener('wheel',event=>{if(!event.ctrlKey&&!event.metaKey)return;event.preventDefault();zoomTimeline(event.deltaY<0?.25:-.25,event.clientX);},{passive:false});
  document.querySelector('#tl-zoom-in').onclick=()=>zoomTimeline(.5);
  document.querySelector('#tl-zoom-out').onclick=()=>zoomTimeline(-.5);
  const resize=document.querySelector('#timeline-resizer');
  const setHeight=h=>document.documentElement.style.setProperty('--timeline-height',`${clamp(h,200,Math.max(200,innerHeight-380))}px`);
  resize.addEventListener('pointerdown',event=>{event.preventDefault();const y=event.clientY,height=document.querySelector('#timeline').clientHeight;const move=e=>setHeight(height+y-e.clientY);document.addEventListener('pointermove',move);document.addEventListener('pointerup',()=>document.removeEventListener('pointermove',move),{once:true});});
  resize.addEventListener('keydown',event=>{if(['ArrowUp','ArrowDown'].includes(event.key)){event.preventDefault();setHeight(document.querySelector('#timeline').clientHeight+(event.key==='ArrowUp'?20:-20));}});
  initDrop();
}
