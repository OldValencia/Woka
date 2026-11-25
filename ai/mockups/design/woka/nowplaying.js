/* Now Playing (audio-player §3.5): one sound source for the whole app. A track keeps playing after
   its viewer closes; the slot shows it as a row in the sidebar, or as a bubble in the corner while
   the sidebar is hidden. While the viewer is on the slot's own track the scene's clock is the one
   that runs and the slot mirrors it; the slot's own clock runs only once the viewer has left. */
// owner: who runs the sound — 'scene' while the viewer is on the track, 'slot' once it has left.
const nowPlaying={id:null,time:0,length:32,playing:false,running:false,last:0,owner:'scene'};
const RING=2*Math.PI*22;
function slotItem(){return libraryMedia.find(item=>item.id===nowPlaying.id);}
function slotOnScreen(){return viewerMode&&currentScene===nowPlaying.id;}
/* From the viewer's sync: a track that plays fills the slot, and the slot follows its time. */
function mirrorSound(){
  if(!currentMedia().audioOnly||!viewerMode||nowPlaying.owner==='slot')return;
  if(playing&&nowPlaying.id!==currentScene){nowPlaying.id=currentScene;renderNowPlaying(true);}
  if(nowPlaying.id!==currentScene)return;
  const changed=nowPlaying.playing!==playing;
  Object.assign(nowPlaying,{time:state.time,length:duration(),playing});
  if(changed)renderNowPlaying();else syncNowPlaying();
}
/* Called before setScene replaces the scene: a playing track goes on in the slot. */
function handOverSound(){
  if(!currentScene||currentScene!==nowPlaying.id)return;
  Object.assign(nowPlaying,{time:state.time,length:duration(),playing,owner:'slot'});
  if(playing)startSlotClock();
}
/* Called once openMedia has the viewer up: the track takes its time back and carries on. */
function takeBackSound(){
  if(currentScene!==nowPlaying.id)return;
  nowPlaying.owner='scene';state.time=nowPlaying.time;
  if(nowPlaying.playing){playing=false;play();}
  updatePlayhead();
}
function startSlotClock(){if(nowPlaying.running)return;nowPlaying.running=true;nowPlaying.last=performance.now();requestAnimationFrame(slotTick);}
function slotTick(now){
  if(!nowPlaying.playing||nowPlaying.owner!=='slot'||!nowPlaying.id){nowPlaying.running=false;return;}
  nowPlaying.time=Math.min(nowPlaying.length,nowPlaying.time+(now-nowPlaying.last)/1000);nowPlaying.last=now;
  if(nowPlaying.time>=nowPlaying.length){nowPlaying.playing=false;nowPlaying.running=false;renderNowPlaying();return;}
  syncNowPlaying();requestAnimationFrame(slotTick);
}
function toggleSlot(){
  if(nowPlaying.owner==='scene'){play();return;}
  nowPlaying.playing=!nowPlaying.playing;if(nowPlaying.playing)startSlotClock();renderNowPlaying();
}
function emptySlot(){if(nowPlaying.owner==='scene'&&playing)play();Object.assign(nowPlaying,{id:null,playing:false,owner:'scene'});renderNowPlaying(true);}
function slotText(item){return `<span class="np-text"><b>${item.title}</b><span>${item.artist}</span></span>`;}
function slotButton(){return `<button class="np-button" data-np="toggle" aria-label="${nowPlaying.playing?'Pause':'Play'}">${icon(nowPlaying.playing?'pause':'play')}</button>`;}
function renderNowPlaying(){
  const row=$('#now-playing-row'),bubble=$('#now-playing-bubble'),item=slotItem();
  row.hidden=!item;
  // The bubble is for a hidden sidebar; it stays out of the editor and off its own track's viewer.
  bubble.hidden=!item||!libraryHidden||!viewerMode||slotOnScreen();
  if(!item){row.replaceChildren();bubble.replaceChildren();return;}
  row.innerHTML=`<div class="np-row" data-np="open" role="button" tabindex="0" aria-label="Open ${item.name}"><span class="np-cover">${photoSvg(item.view)}</span>${slotText(item)}${slotButton()}</div>`;
  bubble.innerHTML=`<span class="np-cover">${photoSvg(item.view)}<span class="np-state">${icon(nowPlaying.playing?'pause':'play')}</span></span><svg class="np-ring" viewBox="0 0 46 46" aria-hidden="true"><circle class="bg" cx="23" cy="23" r="22"/><circle class="fg" cx="23" cy="23" r="22" stroke-dasharray="${RING}"/></svg><span class="np-more"><span data-np="open">${slotText(item)}</span>${slotButton()}<button class="np-button np-close" data-np="close" aria-label="Stop and close">${icon('close')}</button></span>`;
  syncNowPlaying();
}
function syncNowPlaying(){
  const ring=document.querySelector('#now-playing-bubble .fg');
  if(ring)ring.setAttribute('stroke-dashoffset',RING*(1-nowPlaying.time/nowPlaying.length));
}
function initNowPlaying(){
  document.addEventListener('click',event=>{
    const node=event.target.closest('[data-np]');if(!node)return;
    event.stopPropagation();
    ({open:()=>openMedia(nowPlaying.id),toggle:toggleSlot,close:emptySlot})[node.dataset.np]?.();
  },true);
}
