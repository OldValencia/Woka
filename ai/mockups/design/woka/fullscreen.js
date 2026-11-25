/* The video's two other windows: fullscreen, and the same window shrunk to picture-in-picture
   (MediaViewerFullscreenWindow, PipWindowMode). Both keep the picture and the transport and lose
   the library around them; the pill is size | PiP, and in PiP the size control goes too (1.0.9).
   Their chrome leaves after 2.5 s of stillness while the film plays, and stays while it is paused or
   a popover is open (FullscreenChromeVisibility). */
Object.assign(icons,{
  pip:'<rect x="3" y="5" width="18" height="14" rx="2"/><rect x="12" y="11" width="7" height="6" rx="1" fill="currentColor" stroke="none"/>',
  collapse:'<path d="M4 9h5V4m6 0v5h5M4 15h5v5m6 0v-5h5"/>'
});
const CHROME_IDLE_MS=2500,PIP_WIDTH=480,PIP_MIN_WIDTH=320;
let windowMode='window',chromeTimer=null;
function setWindowMode(mode){
  if(mode!=='window'&&!currentMedia().video)mode='window';
  windowMode=mode;
  const shell=$('#app-shell');
  shell.classList.toggle('focus-view',mode!=='window');
  shell.classList.toggle('pip-view',mode==='pip');
  if(mode==='pip')sizePip(PIP_WIDTH,PIP_WIDTH*9/16);
  closeChromePopover();syncWindowControls();pokeChrome();measurePicture();
}
/* Called from syncWokaChrome: a window mode belongs to a video in the viewer and nothing else. */
function keepWindowMode(){if(windowMode!=='window'&&(!viewerMode||!currentMedia().video))setWindowMode('window');}
function syncWindowControls(){
  const pip=windowMode==='pip',full=windowMode!=='window';
  const toggle=$('#pip-toggle');
  toggle.innerHTML=icon('pip');toggle.classList.toggle('selected',pip);toggle.setAttribute('aria-pressed',pip);
  toggle.setAttribute('aria-label',pip?'Fill the screen':'Shrink to picture-in-picture');
  const exit=$('#viewer-fullscreen');
  if(exit){exit.innerHTML=icon(full?'collapse':'expand');exit.setAttribute('aria-label',full?'Exit fullscreen (Esc)':'Fullscreen');}
  if(!full)$('#app-shell').classList.remove('chrome-idle');
}
function sizePip(width,height){
  const shell=$('#app-shell').getBoundingClientRect();
  width=clamp(width,PIP_MIN_WIDTH,shell.width-48);height=clamp(height,PIP_MIN_WIDTH*9/16,shell.height-48);
  const root=document.documentElement.style;root.setProperty('--pip-width',`${width}px`);root.setProperty('--pip-height',`${height}px`);
}
/* Movement brings the chrome back; stillness takes it away again, unless something is being read. */
function pokeChrome(){
  $('#app-shell').classList.remove('chrome-idle');
  clearTimeout(chromeTimer);
  if(windowMode!=='window')chromeTimer=setTimeout(chromeCheck,CHROME_IDLE_MS);
}
function chromeCheck(){
  if(windowMode==='window')return;
  if(!playing||!$('#chrome-popover').hidden){chromeTimer=setTimeout(chromeCheck,CHROME_IDLE_MS);return;}
  $('#app-shell').classList.add('chrome-idle');
}
function initFullscreen(){
  $('#pip-desktop').innerHTML=photoSvg('350 55 1000 720');
  $('#pip-toggle').onclick=()=>setWindowMode(windowMode==='pip'?'fullscreen':'pip');
  $('#app-shell').addEventListener('pointermove',pokeChrome);
  document.addEventListener('keydown',event=>{
    if(windowMode==='window')return;
    pokeChrome();
    if(event.key==='Escape'&&$('#chrome-popover').hidden&&!$('#dialog').open){event.stopImmediatePropagation();setWindowMode('window');}
  },true);
  // The window grows from its top-left corner, away from the corner it is anchored to. The
  // frame keeps the film's proportions; Alt frees them (1.0.9).
  $('#pip-resize').addEventListener('pointerdown',event=>{
    if(event.button!==0)return;event.preventDefault();
    const box=$('#editor').getBoundingClientRect(),x=event.clientX,y=event.clientY;
    const move=e=>{const width=box.width+x-e.clientX;sizePip(width,e.altKey?box.height+y-e.clientY:width*9/16);measurePicture();};
    document.addEventListener('pointermove',move);
    document.addEventListener('pointerup',()=>document.removeEventListener('pointermove',move),{once:true});
  });
  syncWindowControls();
}
