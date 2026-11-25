/* Direct manipulation and feedback: canvas text, zoom and pan, menus, style preview, background jobs. */
let pan={x:0,y:0},stylePreview=null,captionBefore=null,straightenTimer=null,jobAnimation=null;
function frameView(time){const k=time/(duration()||1),w=1000-k*100,h=w*.5625;return `${850-w/2} ${381.25-h/2} ${w} ${h}`;}
function syncFrame(){if(state.mode!=='video')return;document.querySelector('#photo').setAttribute('viewBox',frameView(skimTime??state.time));syncCaption();}
function captionSettings(){return state.mode==='video'?state.settings:settings();}
function showDirty(){
  const done=document.querySelector('#done');done.classList.toggle('dirty',!!state.dirty);
  done.dataset.tip=state.dirty?'You have unsaved edits. Done keeps them for Woka and returns to the viewer.':'Return to the viewer.';
}
function syncCaption(){
  const caption=document.querySelector('#caption'),s=captionSettings();
  let show=state.captionVisible&&!compareOriginal,alpha=(s.opacity??100)/100;
  if(show&&state.mode==='video'){
    const time=skimTime??state.time,{start,length,fade}=captionTiming();
    if(time<start||time>start+length)show=false;
    else if(fade&&(playing||state.tool!=='markup'))alpha*=Math.min(1,(time-start)/.4,(start+length-time)/.4);
  }
  caption.hidden=!show;if(!show)return;
  if(!caption.isContentEditable)caption.textContent=s.caption||'Nowhere else to be.';
  Object.assign(caption.style,{left:`${s.captionX??50}%`,top:`${s.captionY??87}%`,fontSize:`${s.textSize||24}px`,color:s.textColor||'#fff',opacity:alpha,background:s.plate&&s.plate!=='none'?s.plate:'none',borderRadius:`${(s.corner??25)/100*0.6}em`,fontWeight:(s.faces||[]).includes('bold')?'700':'',fontStyle:(s.faces||[]).includes('italic')?'italic':'',textDecoration:[(s.faces||[]).includes('underline')?'underline':'',(s.faces||[]).includes('strike')?'line-through':''].join(' ').trim()||'none',fontFamily:({Serif:'serif',Mono:'monospace'})[s.font]||'sans-serif'});
  caption.classList.toggle('selected',!viewerMode&&state.tool==='markup');
}
function selectCaption(){
  if(state.tool==='markup'&&(state.mode!=='video'||state.selection==='caption'))return;
  state.tool='markup';if(state.mode==='video')state.selection='caption';render();
}
function editCaption(){
  const caption=document.querySelector('#caption');if(viewerMode||caption.hidden)return;
  selectCaption();captionBefore=JSON.stringify(state);caption.contentEditable='true';caption.focus();
  const range=document.createRange();range.selectNodeContents(caption);const selection=getSelection();selection.removeAllRanges();selection.addRange(range);
}
function easeCrop(){
  if(reducedMotion())return;
  const items=[document.querySelector('#crop-box'),document.querySelector('#photo')];
  items.forEach(item=>item.classList.add('easing'));setTimeout(()=>items.forEach(item=>item.classList.remove('easing')),340);
}
function easePhoto(){if(reducedMotion())return;const photo=document.querySelector('#photo');photo.classList.add('easing');clearTimeout(photo.easeTimer);photo.easeTimer=setTimeout(()=>photo.classList.remove('easing'),220);}
function openMenu(x,y,items){
  const menu=document.querySelector('#context-menu');
  menu.innerHTML=items.map(item=>item==='-'?'<hr>':`<button role="menuitem" data-action="${item.action}" ${item.kind?`data-kind="${item.kind}"`:''} class="${item.danger?'danger':''}" ${item.disabled?'disabled':''}><span>${item.label}</span>${item.key?`<kbd>${item.key}</kbd>`:''}</button>`).join('');
  menu.hidden=false;hideTooltip();
  const box=menu.getBoundingClientRect();
  menu.style.left=`${clamp(x,8,innerWidth-box.width-8)}px`;menu.style.top=`${y+box.height+8<innerHeight?y:Math.max(8,y-box.height)}px`;
  menu.querySelector('button:not(:disabled)')?.focus({preventScroll:true});
}
function closeMenu(){document.querySelector('#context-menu').hidden=true;}
function clipMenu(){
  const clip=selectedClip();
  return [{label:'Split at playhead',action:'split',key:'S',disabled:!splittable()},'-',{label:'Copy look',action:'copy-look'},{label:'Paste look',action:'paste-look',kind:'look',disabled:!clipboard},{label:'Paste sound settings',action:'paste-look',kind:'sound',disabled:!clipboard},'-',{label:'Move earlier',action:'move-left',disabled:state.clips[0]===clip},{label:'Move later',action:'move-right',disabled:state.clips[state.clips.length-1]===clip},{label:'Hold this frame for 2 s',action:'freeze',disabled:clip.freeze},{label:'Detach sound',action:'detach',disabled:clip.detached||clip.freeze},'-',{label:'Save frame as photo…',action:'frame-photo'},{label:'Use frame as cover',action:'cover'},'-',{label:'Delete clip',action:'delete-clip',key:'Del',danger:true}];
}
function menuFor(node){
  if(node?.dataset.clip)return clipMenu();
  if(node?.dataset.audio){const audio=selectedAudio();return [{label:audio.settings.mute?'Unmute':'Mute',action:'mute-selected'},'-',{label:'Remove audio',action:'delete-audio',key:'Del',danger:true}];}
  if(node&&node.dataset.caption!==undefined)return [{label:'Edit text',action:'edit-text'},'-',{label:'Remove text',action:'remove-text',key:'Del',danger:true}];
  return [{label:'Split at playhead',action:'split',key:'S',disabled:!splittable()},{label:'Add music…',action:'add-audio'},'-',{label:'Save frame as photo…',action:'frame-photo'},{label:'Use frame as cover',action:'cover'}];
}
function startJob(label){
  clearTimeout(toastTimer);jobAnimation?.cancel();
  const toastNode=document.querySelector('#toast');
  toastNode.innerHTML=`<span>${escapeHtml(label)}…</span><span class="toast-progress"><i></i></span><button data-action="job-cancel">Cancel</button>`;
  toastNode.classList.add('visible');
  jobAnimation=toastNode.querySelector('.toast-progress i').animate([{width:'0%'},{width:'100%'}],{duration:2800,easing:'cubic-bezier(.4,.1,.6,1)',fill:'forwards'});
  jobAnimation.finished.then(()=>{jobAnimation=null;toastNode.innerHTML=`<span>Saved next to the original.</span><button data-action="toast-dismiss">Show in folder</button>`;toastNode.classList.add('visible');toastTimer=setTimeout(hideToast,6000);}).catch(()=>{});
}
function initCaption(){
  const caption=document.querySelector('#caption');
  caption.addEventListener('pointerdown',event=>{
    if(viewerMode||caption.isContentEditable||event.button!==0)return;
    event.preventDefault();event.stopPropagation();selectCaption();
    const picture=document.querySelector('#picture').getBoundingClientRect(),s=captionSettings(),ox=s.captionX??50,oy=s.captionY??87,x=event.clientX,y=event.clientY,before=JSON.stringify(state);let moved=false;
    const move=e=>{moved=true;s.captionX=clamp(ox+(e.clientX-x)/picture.width*100,5,95);s.captionY=clamp(oy+(e.clientY-y)/picture.height*100,5,95);document.querySelector('#caption').classList.add('moving');syncCaption();};
    document.addEventListener('pointermove',move);
    document.addEventListener('pointerup',()=>{document.removeEventListener('pointermove',move);document.querySelector('#caption').classList.remove('moving');if(moved){remember(before);state.dirty=true;render();}},{once:true});
  });
  caption.addEventListener('dblclick',editCaption);
  caption.addEventListener('input',()=>{const text=caption.textContent;captionSettings().caption=text;document.querySelectorAll('[data-value="caption"]').forEach(field=>field.value=text);state.dirty=true;showDirty();});
  caption.addEventListener('keydown',event=>{if((event.key==='Enter'&&!event.shiftKey)||event.key==='Escape'){event.preventDefault();event.stopPropagation();caption.blur();}});
  caption.addEventListener('blur',()=>{caption.contentEditable='false';if(captionBefore!==null&&captionBefore!==JSON.stringify(state))remember(captionBefore);captionBefore=null;render();});
}
function initCanvasNavigation(){
  const stage=document.querySelector('#stage');
  stage.addEventListener('wheel',event=>{
    if(viewerMode)return;event.preventDefault();
    const rect=stage.getBoundingClientRect(),mx=event.clientX-(rect.left+rect.width/2),my=event.clientY-(rect.top+rect.height/2);
    const zoom=state.zoom,next=clamp(zoom*(event.deltaY<0?1.12:1/1.12),1,4);if(next===zoom)return;
    const px=(mx-pan.x)/zoom,py=(my-pan.y)/zoom;pan={x:mx-next*px,y:my-next*py};state.zoom=next;updatePicture();
  },{passive:false});
  stage.addEventListener('pointerdown',event=>{
    if(viewerMode||state.zoom<=1||event.button!==0||event.target.closest('#crop-box,#caption,button'))return;
    event.preventDefault();const x=event.clientX,y=event.clientY,start={...pan};stage.classList.add('panning');
    const move=e=>{pan={x:start.x+e.clientX-x,y:start.y+e.clientY-y};updatePicture();};
    document.addEventListener('pointermove',move);
    document.addEventListener('pointerup',()=>{document.removeEventListener('pointermove',move);stage.classList.remove('panning');},{once:true});
  });
}
function initMenus(){
  const menu=document.querySelector('#context-menu');
  menu.addEventListener('click',event=>{if(event.target.closest('button'))closeMenu();});
  document.querySelector('#tracks').addEventListener('contextmenu',event=>{
    event.preventDefault();const node=event.target.closest('[data-clip],[data-audio],[data-caption]');
    if(node){selectFromTimeline(node,false);render();}
    openMenu(event.clientX,event.clientY,menuFor(node));
  });
  document.querySelector('#tracks').addEventListener('keydown',event=>{
    const node=event.target.closest('[data-clip],[data-audio],[data-caption]');
    if(!node||!(event.key==='ContextMenu'||(event.shiftKey&&event.key==='F10')))return;
    event.preventDefault();selectFromTimeline(node,false);render();const rect=node.getBoundingClientRect();openMenu(rect.left+12,rect.bottom+4,menuFor(node));
  });
  document.addEventListener('pointerdown',event=>{if(!menu.hidden&&!menu.contains(event.target))closeMenu();});
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!menu.hidden){event.stopImmediatePropagation();closeMenu();}},true);
}
function initPreviews(){
  document.addEventListener('pointerover',event=>{const card=event.target.closest('.style-tile');if(!card||stylePreview?.key===card.dataset.style)return;stylePreview={group:card.dataset.group,key:card.dataset.style};easePhoto();updatePicture();});
  document.addEventListener('pointerout',event=>{const card=event.target.closest('.style-tile');if(!card||card.contains(event.relatedTarget))return;stylePreview=null;easePhoto();updatePicture();});
  document.addEventListener('input',event=>{
    if(event.target.dataset?.value!=='straighten')return;
    const picture=document.querySelector('#picture');picture.classList.add('straightening');clearTimeout(straightenTimer);
    straightenTimer=setTimeout(()=>picture.classList.remove('straightening'),700);
  });
  document.addEventListener('keydown',event=>{
    if(event.key!=='\\'||event.repeat||(viewerMode&&!editedIds.has(currentScene))||document.activeElement.isContentEditable||['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName))return;
    toggleComparison(true);
    const release=up=>{if(up.key!=='\\')return;document.removeEventListener('keyup',release);toggleComparison(false);};
    document.addEventListener('keyup',release);
  });
}
function initRefine(){initCaption();initCanvasNavigation();initMenus();initPreviews();showDirty();}
