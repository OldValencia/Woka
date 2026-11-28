/* Motion follows a change of context; dragging and typing remain immediate. */
let viewerMode=false,transitionBusy=false,savedSnapshot=null,compareOriginal=false;
let currentScene=null,sceneSessions=new Map(),tooltipTarget=null,tooltipTimer=null;
const panelMemory=new Map();
const reducedMotion=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
const parameterHints={
  exposure:'Make the whole image lighter or darker.',brightness:'Adjust overall brightness.',
  contrast:'Increase or soften the difference between light and dark.',
  highlights:'Recover detail in the brightest areas.',shadows:'Reveal or deepen the darker areas.',
  blackPoint:'Set how deep the darkest tones appear.',warmth:'Move between cooler and warmer colour.',
  tint:'Balance green and magenta tones.',saturation:'Change the strength of all colours.',
  vibrance:'Adjust the less saturated colours more gently.',sharpness:'Bring out fine edges and detail.',
  noise:'Adjust the texture of the image.',volume:'Changes the sound in your finished video. Listening volume is separate.',
  fadeIn:'How long the sound takes to reach its full volume.',fadeOut:'How long the sound takes to fade to silence.',
  speed:'Changing speed also changes the length of this clip.',audioStart:'Where this sound begins in the edited video.',
  audioDuration:'Trim the sound without changing the length of the video.',audioSpeed:'A future control for the speed of an individual sound.',
  straighten:'Level a tilted horizon. The picture zooms in slightly so the corners stay filled.',
  intensity:'Blend the selected style with the original image.',undertone:'Fine-tune the colour of the selected style.',
  styleDepth:'Fine-tune the strength of the selected look.',textSize:'Size of the selected caption.',opacity:'How visible the selected caption is.',
  captionStart:'When the caption first appears in the video.',captionDuration:'How long the caption stays on screen.',
  captionFadeIn:'How quickly the caption appears.',captionFadeOut:'How quickly the caption disappears.',outputScale:'Size of the finished copy relative to the original.'
};
function defaultValue(key){return ({volume:100,speed:1,audioSpeed:1,intensity:100,palette:100,textSize:24,strokeWidth:6,holdsFor:6,corner:25,opacity:100,outputScale:100,cropHeight:100,cropWidth:100,captionDuration:6})[key]??0;}
function rememberPanel(){
  const panel=document.querySelector('#panel'),active=document.activeElement;
  const memory={scroll:panel.scrollTop,open:[...panel.querySelectorAll('details')].map(d=>d.open)};
  if(panel.dataset.view)panelMemory.set(panel.dataset.view,memory);
  return {key:panel.dataset.view,focus:active?.dataset.value?{key:active.dataset.value,type:active.type,start:active.selectionStart}:null};
}
function restorePanel(previous){
  const panel=$('#panel'),key=[currentScene,state.tool,state.group,state.selection,state.frameScope,state.frameIndex].join(':');
  const memory=panelMemory.get(key);panel.dataset.view=key;
  panel.scrollTop=memory?.scroll||0;
  panel.querySelectorAll('details').forEach((details,index)=>{if(memory?.open[index]!==undefined)details.open=memory.open[index];});
  panel.querySelectorAll('select[data-value]').forEach(select=>{if(settings()[select.dataset.value]!==undefined)select.value=settings()[select.dataset.value];});
  if(previous.key===key&&previous.focus){
    const field=panel.querySelector(`[data-value="${previous.focus.key}"][type="${previous.focus.type}"]`);
    if(field){field.focus({preventScroll:true});if(field.type==='text'&&previous.focus.start!==null)field.setSelectionRange(previous.focus.start,previous.focus.start);}
  }
  if(previous.key&&previous.key!==key&&!reducedMotion()&&!transitionBusy)panel.animate([{opacity:.45,transform:'translateY(5px)'},{opacity:1,transform:'none'}],{duration:160,easing:'ease-out'});
  refreshControlStates();
}
/* The clip speed's stops (RD-302, the app's `snapClipSpeed`): a detent within 0.06 octaves, else steps of 0.05. */
const SPEED_DETENTS=[.25,.5,.75,1,1.25,1.5,2,3,4];
function snapSpeed(speed){const v=clamp(speed,.25,4),hit=SPEED_DETENTS.find(d=>Math.abs(Math.log2(d)-Math.log2(v))<.06);return hit??clamp(Math.round(v*20)/20,.25,4);}
function refreshControlStates(){
  document.querySelectorAll('.control[data-control]').forEach(control=>{
    const input=control.querySelector('input[data-value]');if(!input)return;
    const def=defaultValue(input.dataset.value);
    control.classList.toggle('changed',Number(input.value)!==def);
    const slider=control.querySelector('input[type=range]');if(!slider)return;
    const min=+slider.min,max=+slider.max,at=value=>(clamp(value,min,max)-min)/(max-min)*100,home=slider.dataset.scale==='log'?Math.log2(def):def,a=at(+slider.value),b=at(min<0&&home>min&&home<max?home:min);
    [slider,control].forEach(node=>{node.style.setProperty('--from',`${Math.min(a,b)}%`);node.style.setProperty('--to',`${Math.max(a,b)}%`);node.style.setProperty('--val',`${a}%`);});
  });
}
function setViewer(viewing,animate=true){
  if(transitionBusy||viewing===viewerMode)return;
  closeChromePopover();gifSpeed=1;
  hideTooltip();playing=false;const picture=$('#picture');
  picture.getAnimations().forEach(animation=>animation.cancel());
  state.zoom=1;pan={x:0,y:0};updatePicture();const before=picture.getBoundingClientRect();
  const panels=[$('.inspector'),...state.mode==='video'?[$('#timeline')]:[]];
  const ghosts=[];
  if(viewing&&animate&&!reducedMotion())panels.forEach(panel=>{
    if(!panel.getBoundingClientRect().width)return;
    const rect=panel.getBoundingClientRect(),ghost=panel.cloneNode(true);
    ghost.removeAttribute('id');ghost.querySelectorAll('[id]').forEach(node=>node.removeAttribute('id'));
    ghost.inert=true;ghost.setAttribute('aria-hidden','true');ghost.classList.add('motion-ghost');
    Object.assign(ghost.style,{left:`${rect.left}px`,top:`${rect.top}px`,width:`${rect.width}px`,height:`${rect.height}px`});
    document.body.append(ghost);ghosts.push(ghost);
  });
  viewerMode=viewing;$('#editor').classList.toggle('viewing',viewing);$('#editor').classList.remove('focus');
  $('.header-actions').hidden=viewing;$('.viewer-actions').hidden=!viewing;
  $('#viewer-status').textContent=history.length?'Edited in this preview':'Viewer';
  compareOriginal=false;updatePicture();applyComparison();
  syncWokaChrome();syncIndicators();
  const after=picture.getBoundingClientRect();
  if(animate&&!reducedMotion()){
    transitionBusy=true;$('#editor').classList.add('transitioning');
    const options={duration:380,easing:'cubic-bezier(.22,1,.36,1)'};
    const animation=picture.animate([
      {transformOrigin:'0 0',transform:`translate(${before.left-after.left}px,${before.top-after.top}px) scale(${before.width/after.width},${before.height/after.height})`},
      {transformOrigin:'0 0',transform:'none'}
    ],options);
    if(!viewing)panels.forEach((panel,index)=>panel.animate([{opacity:0,transform:index?'translateY(24px)':'translateX(30px)'},{opacity:1,transform:'none'}],options));
    ghosts.forEach(ghost=>ghost.animate([{opacity:1,transform:'none'},{opacity:0,transform:ghost.classList.contains('timeline')?'translateY(24px)':'translateX(30px)'}],{...options,duration:250}));
    animation.finished.catch(()=>{}).then(()=>{ghosts.forEach(g=>g.remove());transitionBusy=false;$('#editor').classList.remove('transitioning');updatePlayhead();});
  }else ghosts.forEach(ghost=>ghost.remove());
  if(animate)(viewing?$('#enter-editor'):$('#back-viewer')).focus({preventScroll:true});updatePlayhead();
}
function finishEditing(){
  flushInput();state.dirty=false;savedSnapshot=JSON.stringify(state);
  editedIds[documentSignature(state)!==documentSignature(sceneState(currentScene,false))?'add':'delete'](currentScene);
  render();setViewer(true);
  toast('Edits kept. The original file is untouched.');
}
function requestExit(){
  if(viewerMode||transitionBusy)return;flushInput();
  if(!state.dirty){setViewer(true);return;}
  modal('Keep your changes?',`<p>You can come back and edit again at any time.</p><div class="dialog-actions">${button('stay-editor','Keep editing','secondary')}${button('discard-session','Discard changes','quiet')}${button('finish-editor','Keep changes','primary')}</div>`);
}
function flushInput(){if(inputBefore!==null){remember(inputBefore);inputBefore=null;}}
function documentSignature(document){
  const {settings,crop,portrait,ratio,rotation,straighten,flipped,flippedV,captionVisible,clips,audio,frames,photoSize}=document;
  return JSON.stringify({settings,crop,portrait,ratio,rotation,straighten,flipped,flippedV,captionVisible,clips,audio,frames,photoSize});
}
function syncDirty(){if(savedSnapshot)state.dirty=documentSignature(state)!==documentSignature(JSON.parse(savedSnapshot));}
function applyComparison(){
  $('.original-label').hidden=!compareOriginal;
  ['#compare','#viewer-original'].forEach(id=>$(id).setAttribute('aria-pressed',compareOriginal));
  if(compareOriginal){$('#photo').style.filter='none';$('#photo').style.transform='none';$('#crop-box').hidden=true;$('#caption').hidden=true;}
  if(viewerMode)$('#crop-box').hidden=true;
}
function toggleComparison(on){if(compareOriginal===on)return;compareOriginal=on;easePhoto();updatePicture();applyComparison();}
function initComparison(){
  let timer,held=false,suppress=false,previous=false;
  // The editor's Original and the viewer's are one control in two places.
  for(const id of ['#compare','#viewer-original']){
    $(id).addEventListener('pointerdown',event=>{if(event.button!==0)return;held=false;previous=compareOriginal;timer=setTimeout(()=>{held=true;toggleComparison(true);},200);});
    $(id).addEventListener('click',()=>{if(suppress){suppress=false;return;}toggleComparison(!compareOriginal);});
  }
  const release=()=>{clearTimeout(timer);if(held){held=false;suppress=true;toggleComparison(previous);}};
  document.addEventListener('pointerup',release);document.addEventListener('pointercancel',release);window.addEventListener('blur',release);
}
function hideTooltip(){clearTimeout(tooltipTimer);$('#tooltip').hidden=true;if(tooltipTarget)tooltipTarget.removeAttribute('aria-describedby');tooltipTarget=null;}
function queueTooltip(target,immediate=false){
  if(!target||target===tooltipTarget)return;hideTooltip();tooltipTarget=target;
  tooltipTimer=setTimeout(()=>{
    if(!target.isConnected||transitionBusy)return;
    const text=target.dataset.tip||target.getAttribute('aria-label');if(!text)return;
    const tooltip=$('#tooltip');tooltip.textContent=text;tooltip.hidden=false;target.setAttribute('aria-describedby','tooltip');
    const rect=target.getBoundingClientRect(),tip=tooltip.getBoundingClientRect();
    tooltip.style.left=`${Math.max(8,Math.min(innerWidth-tip.width-8,rect.left+rect.width/2-tip.width/2))}px`;
    tooltip.style.top=`${rect.bottom+tip.height+14<innerHeight?rect.bottom+9:Math.max(8,rect.top-tip.height-9)}px`;
  },immediate?100:500);
}
function helpDialog(){
  const video=state.mode==='video';
  modal(video?'A short edit, step by step':'A quick photo edit',video?`
    <button class="help-option" data-action="help-clip"><strong>1. Keep the moments you want</strong><span>Point at the timeline to preview a moment. Click the scissors on the playhead or press S to split. Select a part you don't need and press Delete. Drag clips to change their order.</span></button>
    <button class="help-option" data-action="help-output"><strong>2. Keep the edit or make a finished copy</strong><span>Done keeps your edit in Woka. Create a copy makes a file other apps can open.</span></button>
    <p>Right-click a clip for more actions. The line above the tabs shows whether you're changing one clip or the whole video.</p>`:`
    <button class="help-option" data-action="help-styles"><strong>1. Try a style</strong><span>Point at a style to preview it on your photo, click to keep it.</span></button>
    <button class="help-option" data-action="help-adjust"><strong>2. Fine-tune light and colour</strong><span>Try Auto first. Sliders stick at zero, and a changed value can be reset with one click.</span></button>
    <button class="help-option" data-action="help-crop"><strong>3. Frame the picture</strong><span>Choose a shape, drag the edges, level the horizon with Straighten.</span></button>
    <button class="help-option" data-action="help-output"><strong>4. Compare, then finish</strong><span>Click Original, or hold it or \\, to see the photo before your edits. Done keeps your edit; Create a copy makes a separate file.</span></button>`);
}
function initExperience(){
  savedSnapshot=JSON.stringify(state);
  $('#back-viewer').onclick=requestExit;$('#enter-editor').onclick=()=>setViewer(false);$('#done').onclick=saveChangesDialog;$('#help').onclick=helpDialog;
  document.addEventListener('click',event=>{
    const node=event.target.closest('[data-action]');if(!node)return;const action=node.dataset.action;
    if(action==='reset-param')commit(()=>setValue(node.dataset.param,defaultValue(node.dataset.param)));
    if(action==='toast-undo'){undo();hideToast();}
    if(action==='stay-editor')$('#dialog').close();
    if(action==='finish-editor'){$('#dialog').close();finishEditing();}
    if(action==='discard-session'){$('#dialog').close();state=JSON.parse(savedSnapshot);history=[];future=[];render();setViewer(true);}
    if(action.startsWith('help-')){$('#dialog').close();if(viewerMode)setViewer(false);const step=action.slice(5);if(step==='clip'){if(!selectedClip()){state.selection=state.clips[0].id;}setTool('clip');}else if(step==='output')outputDialog();else if(step==='audio')window.setTimeout(()=>window.action?.('add-audio'),0);else setTool(step);}
  });
  document.addEventListener('pointerover',event=>{const target=event.target.closest('[data-tip],button[aria-label]');if(target)queueTooltip(target);});
  document.addEventListener('pointerout',event=>{if(!tooltipTarget)return;if(event.relatedTarget?.closest?.('#tooltip')||tooltipTarget.contains(event.relatedTarget))return;if(tooltipTarget.contains(event.target))hideTooltip();});
  $('#tooltip').addEventListener('pointerleave',hideTooltip);
  document.addEventListener('focusin',event=>{if(event.target.matches('[data-tip],button[aria-label]')&&event.target.matches(':focus-visible'))queueTooltip(event.target,true);});
  document.addEventListener('focusout',hideTooltip);document.addEventListener('pointerdown',hideTooltip);
  document.addEventListener('scroll',hideTooltip,true);
  $('#toast').addEventListener('pointerenter',()=>clearTimeout(toastTimer));$('#toast').addEventListener('focusin',()=>clearTimeout(toastTimer));
  $('#toast').addEventListener('pointerleave',()=>{if(!$('#toast').contains(document.activeElement))toastTimer=setTimeout(hideToast,3000);});
  initComparison();setViewer(true,false);
}
function hideToast(){$('#toast').classList.remove('visible');$('#toast').replaceChildren();}
