/* The viewer's one transport: the same bar, the same left group and seek for a film, a GIF and a
   track; only the right-hand group changes with the kind (feature doc, "One transport, three slot
   sets"). A GIF plays on its own clock, which moves state.frameIndex — the frame strip and the
   editor read the same number. */
Object.assign(icons,{
  subtitles:'<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M7 11h3m3 0h4M7 15h6m3 0h1"/>',
  repeat:'<path d="m17 3 3 3-3 3"/><path d="M4 12v-2a4 4 0 0 1 4-4h12"/><path d="m7 21-3-3 3-3"/><path d="M20 12v2a4 4 0 0 1-4 4H4"/>',
  frameBack:'<path d="M6 5v14"/><path d="m18 6-8 6 8 6Z"/>',
  frameForward:'<path d="M18 5v14"/><path d="m6 6 8 6-8 6Z"/>'
});
const TRANSPORT={
  video:{left:['play','time','back5'],right:['forward5','duration','subtitles','volume','settings','fullscreen']},
  gif:{left:['play','frameTime','frameBack'],right:['frameForward','frameDuration','loop','speed']},
  audio:{left:['play','time','back5'],right:['forward5','duration','volume','settings']}
};
const SLOTS={
  play:()=>`<button id="viewer-play" class="icon-button transport-play" data-transport="play" aria-label="Play (Space)"></button>`,
  time:()=>`<span id="viewer-time" class="playback-time"></span>`,
  frameTime:()=>`<button id="viewer-time" class="playback-time quiet" data-transport="frame-count" data-tip="Click to switch between time and frame number."></button>`,
  back5:()=>`<button id="skip-back" class="icon-button skip" data-transport="back5" aria-label="Back 5 seconds">↶<small>5</small></button>`,
  forward5:()=>`<button id="skip-forward" class="icon-button skip" data-transport="forward5" aria-label="Forward 5 seconds">↷<small>5</small></button>`,
  frameBack:()=>`<button id="frame-back" class="icon-button" data-transport="frame-back" aria-label="Previous frame (,)">${icon('frameBack')}</button>`,
  frameForward:()=>`<button id="frame-forward" class="icon-button" data-transport="frame-forward" aria-label="Next frame (.)">${icon('frameForward')}</button>`,
  duration:()=>`<button id="viewer-duration" class="playback-time quiet" data-transport="remaining" data-tip="Click to switch between duration and time remaining."></button>`,
  frameDuration:()=>`<span id="viewer-duration" class="playback-time"></span>`,
  subtitles:()=>`<button id="viewer-subtitles" class="icon-button" data-transport="subtitles" aria-label="Subtitles" aria-haspopup="dialog" aria-expanded="false">${icon('subtitles')}</button>`,
  volume:()=>`<button id="viewer-volume-button" class="icon-button" data-transport="volume" aria-label="Listening volume" aria-haspopup="dialog" aria-expanded="false">${icon('sound')}</button>`,
  settings:()=>`<button id="viewer-settings" class="icon-button" data-transport="settings" aria-label="Playback settings" aria-haspopup="dialog" aria-expanded="false">${icon('settings')}</button>`,
  speed:()=>`<button id="viewer-settings" class="icon-button" data-transport="speed" aria-label="Playback speed" aria-haspopup="dialog" aria-expanded="false">${icon('speed')}</button>`,
  fullscreen:()=>`<button id="viewer-fullscreen" class="icon-button" data-transport="fullscreen" aria-label="Focus view">${icon('expand')}</button>`,
  loop:()=>`<button id="viewer-loop" class="icon-button" data-transport="loop" aria-label="Repeat" aria-pressed="true">${icon('repeat')}</button>`
};
const SEEK=`<div class="seek-wrap"><input id="viewer-seek" class="media-range" type="range" data-transport="seek" aria-label="Playback position"><div id="seek-preview" class="seek-preview" hidden><div id="seek-image"></div><span id="seek-time"></span></div></div>`;
// A GIF's speed is its own and only for looking: 1× for every animation shown and on entering or
// leaving the editor (RD-501). Loop opens On for every animation, never remembered.
let transportKind=null,transportMedia=null,showFrameNumbers=false,gifSpeed=1;
// The app's FrameSpeedScale: 0.25–5×, two log halves with 1× at the centre, pulled onto detents.
const GIF_SPEED_DETENTS=[.5,.75,1,1.25,1.5,2,3,4,5];
function gifSpeedFraction(speed){const s=clamp(speed,.25,5);return s<=1?.5*(Math.log2(s)+2)/2:.5+.5*Math.log2(s)/Math.log2(5);}
function gifSpeedAt(fraction){
  const f=clamp(fraction,0,1),raw=f<=.5?.25*4**(f*2):5**(f*2-1);
  return GIF_SPEED_DETENTS.find(d=>Math.abs(Math.log2(d)-Math.log2(raw))<.06)??clamp(Math.round(raw*20)/20,.25,5);
}
const frameClock={playing:true,loop:true,running:false,last:0,carry:0};
function transportHtml(kind){const slots=TRANSPORT[kind];return slots.left.map(slot=>SLOTS[slot]()).join('')+SEEK+slots.right.map(slot=>SLOTS[slot]()).join('');}
function mediaKind(){const item=currentMedia();return item.gif?'gif':item.audioOnly?'audio':item.video?'video':null;}
function frameCount(){return state.frames?.length||1;}
function frameRate(){return currentMedia().fps||12;}
function clipTime(seconds){return `${Math.floor(seconds/60)}:${(seconds%60).toFixed(1).padStart(4,'0')}`;}
function stillFrameView(item,index=0){
  if(!(item.frames>1))return item.view;
  const [x,y,w,h]=item.view.split(' ').map(Number);
  if(item.gif){const turn=index/item.frames*Math.PI*2;return `${(x+Math.sin(turn)*36).toFixed(1)} ${(y+Math.cos(turn)*14).toFixed(1)} ${w} ${h}`;}
  return `${x+index*22} ${y} ${w} ${h}`;
}
function renderTransport(){
  const kind=mediaKind(),bar=$('#viewer-playback');
  bar.hidden=!kind;
  if(kind!==transportKind){
    transportKind=kind;
    bar.innerHTML=kind?transportHtml(kind):'';
    if(kind)$('#seek-image').innerHTML=photoSvg(currentMedia().view);
    if(kind==='gif')showFrameNumbers=false;
    if(kind==='video')syncWindowControls();
  }
  const id=currentMedia().id;
  if(kind==='gif'&&id!==transportMedia){frameClock.playing=true;frameClock.loop=true;gifSpeed=1;}
  transportMedia=id;
  if(kind==='gif'&&frameClock.playing)startFrameClock();
  syncViewerPlayback();
}
function syncViewerPlayback(){
  if(!transportKind||!$('#viewer-seek'))return;
  const seek=$('#viewer-seek'),gif=transportKind==='gif',on=gif?frameClock.playing:playing;
  if(gif){
    const count=frameCount(),index=state.frameIndex||0;
    $('#viewer-time').textContent=showFrameNumbers?`${index+1} / ${count}`:clipTime(index/frameRate());
    $('#viewer-duration').textContent=clipTime(count/frameRate());
    Object.assign(seek,{min:0,max:count-1,step:1,value:index});seek.style.setProperty('--range-fill',`${index/Math.max(1,count-1)*100}%`);
    $('#viewer-loop').setAttribute('aria-pressed',frameClock.loop);$('#viewer-loop').classList.toggle('selected',frameClock.loop);
  }else{
    $('#viewer-time').textContent=shortTime(state.time);
    $('#viewer-duration').textContent=(remainingTime?'−':'')+shortTime(remainingTime?duration()-state.time:duration());
    Object.assign(seek,{min:0,max:duration(),step:.1,value:state.time});seek.style.setProperty('--range-fill',`${state.time/duration()*100}%`);
    $('#viewer-volume-button').style.opacity=listenVolume===0?.45:1;
  }
  $('#viewer-play').innerHTML=icon(on?'pause':'play');$('#viewer-play').setAttribute('aria-label',on?'Pause (Space)':'Play (Space)');
  if(transportKind==='audio')mirrorSound();
  if(!on)$('#app-shell').classList.remove('chrome-idle');
}
function showFrame(index){
  state.frameIndex=clamp(index,0,frameCount()-1);
  $('#photo').setAttribute('viewBox',stillFrameView(currentMedia(),state.frameIndex));
  syncFrameStrip();syncViewerPlayback();
}
function startFrameClock(){
  if(frameClock.running)return;
  frameClock.running=true;frameClock.last=performance.now();frameClock.carry=0;requestAnimationFrame(frameTick);
}
function frameTick(now){
  if(!frameClock.playing||!currentMedia().gif){frameClock.running=false;return;}
  frameClock.carry+=(now-frameClock.last)/1000*frameRate()*gifSpeed;frameClock.last=now;
  if(frameClock.carry>=1){
    const steps=Math.floor(frameClock.carry),count=frameCount();frameClock.carry-=steps;
    let next=(state.frameIndex||0)+steps;
    if(next>=count){if(frameClock.loop)next%=count;else{next=count-1;frameClock.playing=false;}}
    showFrame(next);
  }
  requestAnimationFrame(frameTick);
}
function setFramePlaying(on){
  frameClock.playing=on;
  if(on&&!frameClock.loop&&state.frameIndex===frameCount()-1)showFrame(0);
  if(on)startFrameClock();
  syncViewerPlayback();
}
function stepFrame(delta){
  if(currentMedia().gif){
    setFramePlaying(false);showFrame((state.frameIndex||0)+delta);
    return true;
  }
  if(state.mode==='video'&&!playing){state.time=clamp(state.time+delta/30,0,duration());updatePlayhead();return true;}
  return false;
}
function hdrPopover(){
  const current=$('#hdr-mode').firstChild.textContent;
  const rows=[['Auto','Follow the file’s own tags'],['HDR','Read as HDR even if untagged'],['SDR','Never tone map']];
  chromePopover($('#hdr-mode'),'Colour',rows.map(([mode,caption])=>`<button class="hdr-option ${mode===current?'selected':''}" data-action="hdr-mode" data-mode="${mode}" aria-pressed="${mode===current}"><b>${mode}</b><small>${caption}</small></button>`).join(''));
}
function transportAction(name,node){
  const commands={
    play,
    back5:()=>{state.time=clamp(state.time-5,0,duration());updatePlayhead();},
    forward5:()=>{state.time=clamp(state.time+5,0,duration());updatePlayhead();},
    'frame-back':()=>stepFrame(-1),'frame-forward':()=>stepFrame(1),
    'frame-count':()=>{showFrameNumbers=!showFrameNumbers;syncViewerPlayback();},
    remaining:()=>{remainingTime=!remainingTime;syncViewerPlayback();},
    loop:()=>{frameClock.loop=!frameClock.loop;syncViewerPlayback();},
    subtitles:()=>chromePopover(node,'Subtitles',`<div class="segmented">${button('view-subtitles','Off','selected')}${button('view-subtitles','English')}</div>`),
    volume:()=>chromePopover(node,'Listening volume',`<label>Only on this device<input id="chrome-volume" class="media-range" type="range" min="0" max="100" value="${listenVolume}" aria-label="Listening volume"></label><div class="popover-value"><span id="volume-value">${listenVolume}%</span>${button('view-mute',listenVolume?'Mute':'Unmute','secondary')}</div>`),
    settings:()=>chromePopover(node,'Playback',`<label>Speed<select id="chrome-speed" aria-label="Playback speed">${[.5,1,1.5,2].map(speed=>`<option ${speed===viewerSpeed?'selected':''}>${speed}</option>`).join('')}</select></label>`),
    speed:()=>chromePopover(node,'Playback',`<label>Speed<input id="chrome-gif-speed" class="media-range" type="range" min="0" max="1" step="0.001" value="${gifSpeedFraction(gifSpeed)}" aria-label="Playback speed"></label><div class="popover-value"><span id="gif-speed-value">${gifSpeed}×</span></div>`),
    fullscreen:()=>setWindowMode(windowMode==='window'?'fullscreen':'window')
  };
  commands[name]?.();
}
function initTransport(){
  const bar=$('#viewer-playback');
  bar.addEventListener('click',event=>{const node=event.target.closest('[data-transport]');if(node&&node.dataset.transport!=='seek')transportAction(node.dataset.transport,node);});
  bar.addEventListener('input',event=>{
    if(event.target.id!=='viewer-seek')return;
    if(transportKind==='gif'){setFramePlaying(false);showFrame(+event.target.value);}else{state.time=+event.target.value;updatePlayhead();}
  });
  bar.addEventListener('pointermove',event=>{
    if(event.target.id!=='viewer-seek')return;
    const rect=event.target.getBoundingClientRect(),fraction=clamp((event.clientX-rect.left)/rect.width,0,1),preview=$('#seek-preview');
    preview.hidden=false;preview.style.left=`${clamp(fraction*rect.width,72,Math.max(72,rect.width-72))}px`;
    if(transportKind==='gif'){const index=Math.round(fraction*(frameCount()-1));$('#seek-time').textContent=showFrameNumbers?`${index+1} / ${frameCount()}`:clipTime(index/frameRate());$('#seek-image').innerHTML=photoSvg(stillFrameView(currentMedia(),index));}
    else $('#seek-time').textContent=shortTime(fraction*duration());
  });
  bar.addEventListener('pointerout',event=>{if(event.target.id==='viewer-seek')$('#seek-preview').hidden=true;});
  $('#stage').addEventListener('click',()=>{if((viewerMode||currentMedia().gif)&&mediaKind())play();});
  $('#hdr-mode').onclick=hdrPopover;
  document.addEventListener('click',event=>{
    const node=event.target.closest('[data-action="hdr-mode"]');
    if(node){$('#hdr-mode').firstChild.textContent=node.dataset.mode;closeChromePopover();}
    const subtitles=event.target.closest('[data-action="view-subtitles"]');
    if(subtitles)subtitles.parentElement.querySelectorAll('button').forEach(item=>item.classList.toggle('selected',item===subtitles));
  });
  document.addEventListener('input',event=>{
    if(event.target.id!=='chrome-gif-speed')return;
    gifSpeed=gifSpeedAt(+event.target.value);
    $('#gif-speed-value').textContent=`${gifSpeed}×`;
  });
  document.addEventListener('keydown',event=>{
    if(event.key!==','&&event.key!=='.')return;
    if($('#dialog').open||['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName)||document.activeElement.isContentEditable)return;
    if(stepFrame(event.key==='.'?1:-1))event.preventDefault();
  });
}
