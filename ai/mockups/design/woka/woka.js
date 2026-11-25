const libraryMedia=[
  {id:'photo',name:'Seaside.jpg',view:'350 55 960 720'},
  {id:'palm',name:'Palms.jpg',view:'600 55 700 525'},
  {id:'crop',name:'Seaside-portrait.jpg',view:'650 60 520 693.3'},
  {id:'shade',name:'Under the palms.jpg',view:'500 200 800 600'},
  {id:'frames',name:'Seaside-burst.heic',view:'350 55 960 720'},
  {id:'video',name:'A day by the sea.mp4',view:'350 100 1000 562.5',video:true},
  {id:'audio',name:'Sea breeze edit.mp4',view:'350 100 1000 562.5',video:true}
];
let libraryHidden=false,carouselShown=true,activeFolder='Vacation',popoverAnchor=null,remainingTime=false,listenVolume=70,viewerSpeed=1,carouselKey='';
Object.assign(icons,{
  folder:'<path d="M3 6h7l2 2h9v12H3Z"/><path d="M3 6V4h7l2 2h9v2"/>',
  pin:'<path d="m9 3 6 0-1 6 4 4v2H6v-2l4-4ZM12 15v7"/>',
  chevronLeft:'<path d="m15 5-7 7 7 7"/>',chevronRight:'<path d="m9 5 7 7-7 7"/>',
  info:'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',
  carousel:'<rect x="5" y="6" width="14" height="12" rx="2"/><path d="M2 8v8m20-8v8m-16-1 4-4 3 3 2-2 3 3"/>',
  settings:'<path d="m9 3 6 0 1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1Z"/><circle cx="12" cy="12" r="3"/>',
  expand:'<path d="M9 3H3v6m12-6h6v6M3 15v6h6m12-6v6h-6"/>'
});
function currentMedia(){return libraryMedia.find(item=>item.id===currentScene)||libraryMedia[0];}
function shortTime(value){return `${String(Math.floor(value/60)).padStart(2,'0')}:${String(Math.floor(value%60)).padStart(2,'0')}`;}
function folderItems(){return activeFolder==='Videos'||activeFolder==='Movies'?libraryMedia.filter(item=>item.video):activeFolder==='Pictures'||activeFolder==='Screenshots'?libraryMedia.filter(item=>!item.video):libraryMedia;}
function renderLibrary(){
  $('#library-folders').innerHTML='<div class="library-section">Folders</div>'+['Movies','Pictures','Screenshots','Vacation','Videos'].map(folder=>`<button class="folder-row ${activeFolder===folder?'selected':''}" data-folder="${folder}"><span class="chevron">›</span>${icon('folder')}<span class="folder-name">${folder}</span><span class="count">${folder==='Vacation'?7:folder==='Videos'||folder==='Movies'?2:5}</span></button>`).join('')+'<div class="library-section">Pinned</div><button class="folder-row pinned" data-folder="Vacation">'+icon('pin')+'<span class="folder-name">Vacation<small>Pictures</small></span></button><button class="folder-row pinned" data-folder="Pictures">'+icon('pin')+'<span class="folder-name">Pictures<small>OldValencia</small></span></button>';
}
function syncWokaChrome(){
  const item=currentMedia();$('#app-shell').classList.toggle('editing',!viewerMode);$('#app-shell').classList.toggle('sidebar-hidden',libraryHidden);
  if(!viewerMode)$('#app-shell').classList.remove('focus-view');
  $('#viewer-layer').inert=!viewerMode;$('#library-sidebar').inert=!viewerMode||libraryHidden;
  $('#viewer-playback').hidden=state.mode!=='video';$('#carousel-well').hidden=!carouselShown;
  $('#viewer-status').textContent=item.name;
  $('#photo').setAttribute('viewBox',state.mode==='video'?frameView(skimTime??state.time):item.view);
  const media=folderItems(),index=media.findIndex(entry=>entry.id===item.id);
  $('#viewer-index').textContent=`${Math.max(0,index)+1} of ${media.length}`;
  $('#previous-media').disabled=index<=0;$('#next-media').disabled=index===media.length-1;
  $('#toggle-carousel').setAttribute('aria-pressed',carouselShown);$('#toggle-carousel').setAttribute('aria-label',carouselShown?'Hide filmstrip':'Show filmstrip');
  const key=activeFolder+':'+currentScene;
  if(carouselKey!==key){carouselKey=key;$('#media-carousel').innerHTML=media.map(entry=>`<button class="carousel-item ${entry.id===item.id?'selected':''}" data-media="${entry.id}" aria-label="${entry.name}" aria-current="${entry.id===item.id}">${photoSvg(entry.view)}${entry.video?`<span class="media-badge">${icon('play')}</span>`:''}</button>`).join('');}
  measurePicture();syncViewerPlayback();
}
function measurePicture(){
  const stage=$('#stage');if(!stage)return;const style=getComputedStyle(stage),view=currentMedia().view.split(' ').map(Number),ratio=view[2]/view[3];
  const width=Math.max(1,stage.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight)),height=Math.max(1,stage.clientHeight-parseFloat(style.paddingTop)-parseFloat(style.paddingBottom));
  const fitWidth=Math.min(width,height*ratio);Object.assign($('#picture').style,{width:`${fitWidth}px`,height:`${fitWidth/ratio}px`,flexShrink:0,maxWidth:'none'});
  $('#size-percent').textContent=`${Math.round(fitWidth/(state.mode==='video'?1920:state.portrait?3024:4032)*state.zoom*100)}%`;
}
function syncViewerPlayback(){
  if(!document.querySelector('#viewer-seek'))return;
  $('#viewer-time').textContent=shortTime(state.time);$('#viewer-duration').textContent=(remainingTime?'−':'')+shortTime(remainingTime?duration()-state.time:duration());
  const seek=$('#viewer-seek');seek.max=duration();seek.value=state.time;seek.style.setProperty('--range-fill',`${state.time/duration()*100}%`);
  $('#viewer-play').innerHTML=icon(playing?'pause':'play');$('#viewer-play').setAttribute('aria-label',playing?'Pause (Space)':'Play (Space)');
  $('#viewer-volume-button').style.opacity=listenVolume===0?.45:1;
}
function openMedia(id){
  if(transitionBusy)return;closeChromePopover();setScene(id);setViewer(true,false);syncWokaChrome();
}
function closeChromePopover(){const popover=document.querySelector('#chrome-popover');if(!popover)return;popover.hidden=true;if(popoverAnchor)popoverAnchor.setAttribute('aria-expanded','false');popoverAnchor=null;}
function chromePopover(anchor,title,content){
  if(popoverAnchor===anchor){closeChromePopover();return;}closeChromePopover();hideTooltip();popoverAnchor=anchor;anchor.setAttribute('aria-expanded','true');
  const popover=$('#chrome-popover');popover.innerHTML=`<h2>${title}</h2>${content}`;popover.hidden=false;popover.setAttribute('aria-label',title);
  const rect=anchor.getBoundingClientRect(),box=popover.getBoundingClientRect();popover.style.left=`${clamp(rect.left+rect.width/2-box.width/2,8,innerWidth-box.width-8)}px`;popover.style.top=`${rect.bottom+box.height+10<innerHeight?rect.bottom+8:Math.max(8,rect.top-box.height-8)}px`;popover.querySelector('button,input')?.focus({preventScroll:true});
}
function zoomPopover(){chromePopover($('#size-menu'),'Image size',`<div class="segmented">${button('view-fit','Fit','selected')}${button('view-fill','Fill')}${button('view-actual','100%')}</div><label>Zoom relative to Fit<input id="chrome-zoom" class="media-range" type="range" min="100" max="300" value="${state.zoom*100}" step="5" aria-label="Zoom relative to Fit"></label>`);}
function showFolder(){modal(activeFolder,`<div class="folder-grid">${folderItems().map(item=>`<button data-media="${item.id}">${photoSvg(item.view)}<span>${item.name}</span></button>`).join('')}</div>`);}
function initWoka(){
  [['hide-library','focus'],['show-library','focus'],['folder-back','chevronLeft'],['media-info','info'],['toggle-carousel','carousel'],['previous-media','chevronLeft'],['next-media','chevronRight'],['viewer-volume-button','sound'],['viewer-settings','settings'],['viewer-fullscreen','expand']].forEach(([id,name])=>$('#'+id).innerHTML=icon(name));
  $('#enter-editor').innerHTML=icon('markup')+' Edit';renderLibrary();
  $('#hide-library').onclick=()=>{libraryHidden=true;syncWokaChrome();};$('#show-library').onclick=()=>{libraryHidden=false;syncWokaChrome();};$('#folder-back').onclick=showFolder;
  $('#toggle-carousel').onclick=()=>{carouselShown=!carouselShown;syncWokaChrome();};
  $('#previous-media').onclick=()=>{const items=folderItems(),index=items.findIndex(item=>item.id===currentScene);if(index>0)openMedia(items[index-1].id);};
  $('#next-media').onclick=()=>{const items=folderItems(),index=items.findIndex(item=>item.id===currentScene);if(index<items.length-1)openMedia(items[index+1].id);};
  $('#pill-minus').onclick=()=>{state.zoom=clamp(state.zoom-.15,1,4);updatePicture();};$('#pill-plus').onclick=()=>{state.zoom=clamp(state.zoom+.15,1,4);updatePicture();};$('#size-menu').onclick=zoomPopover;
  $('#media-info').onclick=()=>chromePopover($('#media-info'),'File information',`<div class="info-line"><span>Name</span><span>${currentMedia().name}</span></div><div class="info-line"><span>Dimensions</span><span>${state.mode==='video'?'1920 × 1080':'4032 × 3024'}</span></div><div class="info-line"><span>Colour</span><span>${state.mode==='video'?'HDR · PQ':'SDR · sRGB'}</span></div>`);
  $('#viewer-play').onclick=play;$('#skip-back').onclick=()=>{state.time=clamp(state.time-5,0,duration());updatePlayhead();};$('#skip-forward').onclick=()=>{state.time=clamp(state.time+5,0,duration());updatePlayhead();};$('#viewer-duration').onclick=()=>{remainingTime=!remainingTime;syncViewerPlayback();};
  $('#viewer-seek').oninput=event=>{state.time=+event.target.value;updatePlayhead();};
  $('#viewer-seek').onpointermove=event=>{const rect=event.target.getBoundingClientRect(),fraction=clamp((event.clientX-rect.left)/rect.width,0,1);$('#seek-preview').hidden=false;$('#seek-preview').style.left=`${clamp(fraction*rect.width,72,Math.max(72,rect.width-72))}px`;$('#seek-time').textContent=shortTime(fraction*duration());};$('#viewer-seek').onpointerleave=()=>$('#seek-preview').hidden=true;$('#seek-image').innerHTML=photoSvg();
  $('#viewer-volume-button').onclick=()=>chromePopover($('#viewer-volume-button'),'Listening volume',`<label>Only on this device<input id="chrome-volume" class="media-range" type="range" min="0" max="100" value="${listenVolume}" aria-label="Listening volume"></label><div class="popover-value"><span id="volume-value">${listenVolume}%</span>${button('view-mute',listenVolume?'Mute':'Unmute','secondary')}</div>`);
  $('#viewer-settings').onclick=()=>chromePopover($('#viewer-settings'),'Playback',`<label>Speed<select id="chrome-speed" aria-label="Playback speed">${[.5,1,1.5,2].map(speed=>`<option ${speed===viewerSpeed?'selected':''}>${speed}</option>`).join('')}</select></label>`);
  $('#viewer-fullscreen').onclick=()=>{$('#app-shell').classList.toggle('focus-view');measurePicture();};
  document.addEventListener('click',event=>{
    const media=event.target.closest('[data-media]');if(media){if($('#dialog').open)$('#dialog').close();openMedia(media.dataset.media);return;}
    const folder=event.target.closest('[data-folder]');if(folder){activeFolder=folder.dataset.folder;renderLibrary();openMedia(folderItems()[0].id);return;}
    const action=event.target.closest('[data-action]')?.dataset.action;
    if(['view-fit','view-fill','view-actual'].includes(action)){const stage=$('#stage'),picture=$('#picture');pan={x:0,y:0};state.zoom=action==='view-fit'?1:action==='view-fill'?Math.max(stage.clientWidth/parseFloat(picture.style.width),stage.clientHeight/parseFloat(picture.style.height)):(state.mode==='video'?1920:4032)/parseFloat(picture.style.width);closeChromePopover();updatePicture();}
    if(action==='view-mute'){listenVolume=listenVolume?0:70;closeChromePopover();syncViewerPlayback();}
  });
  document.addEventListener('input',event=>{if(event.target.id==='chrome-zoom'){state.zoom=+event.target.value/100;updatePicture();}if(event.target.id==='chrome-volume'){listenVolume=+event.target.value;if($('#volume-value'))$('#volume-value').textContent=listenVolume+'%';syncViewerPlayback();}if(event.target.id==='chrome-speed')viewerSpeed=+event.target.value;});
  document.addEventListener('pointerdown',event=>{if(!event.target.closest('#chrome-popover')&&event.target!==popoverAnchor&&!popoverAnchor?.contains(event.target))closeChromePopover();});
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!$('#chrome-popover').hidden){event.stopImmediatePropagation();const anchor=popoverAnchor;closeChromePopover();anchor?.focus();}},true);
  new ResizeObserver(measurePicture).observe($('#stage'));
}
