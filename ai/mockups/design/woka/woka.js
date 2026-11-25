const libraryMedia=[
  {id:'photo',name:'Seaside.jpg',view:'350 55 960 720'},
  {id:'palm',name:'Palms.jpg',view:'600 55 700 525'},
  {id:'crop',name:'Seaside-portrait.jpg',view:'650 60 520 693.3'},
  {id:'shade',name:'Under the palms.jpg',view:'500 200 800 600'},
  {id:'frames',name:'Seaside-burst.heic',view:'350 55 960 720',frames:4},
  {id:'gif',name:'Waves.gif',view:'350 55 960 720',frames:24,fps:12,gif:true},
  {id:'video',name:'A day by the sea.mp4',view:'350 100 1000 562.5',video:true,hdr:true},
  {id:'audio',name:'Sea breeze edit.mp4',view:'350 100 1000 562.5',video:true},
  {id:'track',name:'Sea breeze.m4a',title:'Sea breeze',artist:'Coastline',view:'760 120 520 520',audioOnly:true}
];
/* Files shown with an edit kept inside them (PT-102). finishEditing keeps it current. */
const editedIds=new Set(['palm']);
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
function folderItems(folder=activeFolder){return FOLDERS[folder]?.media()??[];}
function carouselBadge(item){
  const chip=item.audioOnly?icon('music'):item.video?`${icon('play')}<span>${shortTime(32)}</span>`:item.gif?'<span>GIF</span>':item.frames>1?`<span>${item.frames}</span>`:'';
  return (chip?`<span class="media-chip">${chip}</span>`:'')+(editedIds.has(item.id)?'<i class="media-edited" aria-hidden="true"></i>':'');
}
function syncWokaChrome(){
  const item=currentMedia();$('#app-shell').classList.toggle('editing',!viewerMode);$('#app-shell').classList.toggle('sidebar-hidden',libraryHidden);
  keepWindowMode();
  $('#viewer-layer').inert=!viewerMode;$('#library-sidebar').inert=!viewerMode||libraryHidden;
  $('#carousel-well').hidden=!carouselShown;
  $('#viewer-status').textContent=item.name;
  $('#photo').setAttribute('viewBox',state.mode==='video'?frameView(skimTime??state.time):stillFrameView(item,state.frameIndex));
  document.querySelectorAll('.hdr-part').forEach(node=>node.hidden=!item.hdr);
  // A track has no picture to size and no editor (audio-player §3.4); its stage is the cover.
  const audio=!!item.audioOnly;
  document.querySelectorAll('.size-part').forEach(node=>node.hidden=audio);
  document.querySelectorAll('.edit-part').forEach(node=>node.hidden=audio);
  $('#picture').hidden=audio;$('#audio-stage').hidden=!audio;
  if(audio)$('#audio-stage').innerHTML=`<div class="audio-cover">${photoSvg(item.view)}</div><b class="audio-title">${item.title}</b><span class="audio-artist">${item.artist}</span>`;
  $('#viewer-original').hidden=!editedIds.has(item.id);
  const media=folderItems(),index=media.findIndex(entry=>entry.id===item.id);
  $('#viewer-index').textContent=`${Math.max(0,index)+1} of ${media.length}`;
  $('#previous-media').disabled=index<=0;$('#next-media').disabled=index===media.length-1;
  $('#toggle-carousel').setAttribute('aria-pressed',carouselShown);$('#toggle-carousel').setAttribute('aria-label',carouselShown?'Hide filmstrip':'Show filmstrip');
  const key=activeFolder+':'+currentScene+':'+[...editedIds].join();
  if(carouselKey!==key){carouselKey=key;$('#media-carousel').innerHTML=media.map(entry=>`<button class="carousel-item ${entry.id===item.id?'selected':''}" data-media="${entry.id}" aria-label="${entry.name}" aria-current="${entry.id===item.id}">${photoSvg(entry.view)}${carouselBadge(entry)}</button>`).join('');}
  measurePicture();renderTransport();renderNowPlaying();
  if(libraryShown())renderLibraryScreen();
}
function measurePicture(){
  const stage=$('#stage');if(!stage)return;const style=getComputedStyle(stage),view=currentMedia().view.split(' ').map(Number),ratio=view[2]/view[3];
  const width=Math.max(1,stage.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight)),height=Math.max(1,stage.clientHeight-parseFloat(style.paddingTop)-parseFloat(style.paddingBottom));
  const fitWidth=Math.min(width,height*ratio);Object.assign($('#picture').style,{width:`${fitWidth}px`,height:`${fitWidth/ratio}px`,flexShrink:0,maxWidth:'none'});
  $('#size-percent').textContent=`${Math.round(fitWidth/(state.mode==='video'?1920:state.portrait?3024:4032)*state.zoom*100)}%`;
}
function openMedia(id){
  if(transitionBusy)return;closeChromePopover();
  const target=libraryMedia.find(item=>item.id===id);
  if(!folderItems().some(item=>item.id===id))activeFolder=target?.folder||(target?.audioOnly?'Music':'Vacation');
  hideLibrary();renderLibrary();
  setScene(id);setViewer(true,false);takeBackSound();syncWokaChrome();
}
function closeChromePopover(){const popover=document.querySelector('#chrome-popover');if(!popover)return;popover.hidden=true;if(popoverAnchor)popoverAnchor.setAttribute('aria-expanded','false');popoverAnchor=null;}
function chromePopover(anchor,title,content){
  if(popoverAnchor===anchor){closeChromePopover();return;}closeChromePopover();hideTooltip();popoverAnchor=anchor;anchor.setAttribute('aria-expanded','true');
  const popover=$('#chrome-popover');popover.innerHTML=`<h2>${title}</h2>${content}`;popover.hidden=false;popover.setAttribute('aria-label',title);
  const rect=anchor.getBoundingClientRect(),box=popover.getBoundingClientRect();popover.style.left=`${clamp(rect.left+rect.width/2-box.width/2,8,innerWidth-box.width-8)}px`;popover.style.top=`${rect.bottom+box.height+10<innerHeight?rect.bottom+8:Math.max(8,rect.top-box.height-8)}px`;popover.querySelector('button,input')?.focus({preventScroll:true});
}
function zoomPopover(){chromePopover($('#size-menu'),'Image size',`<div class="segmented">${button('view-fit','Fit','selected')}${button('view-fill','Fill')}${button('view-actual','100%')}</div><label>Zoom relative to Fit<input id="chrome-zoom" class="media-range" type="range" min="100" max="300" value="${state.zoom*100}" step="5" aria-label="Zoom relative to Fit"></label>`);}
function initWoka(){
  [['hide-library','focus'],['show-library','focus'],['folder-back','chevronLeft'],['media-info','info'],['toggle-carousel','carousel'],['previous-media','chevronLeft'],['next-media','chevronRight']].forEach(([id,name])=>$('#'+id).innerHTML=icon(name));
  $('#enter-editor').innerHTML=icon('markup')+' Edit';$('#viewer-original').innerHTML=icon('compare')+'<span>Original</span>';renderLibrary();
  $('#hide-library').onclick=()=>{libraryHidden=true;syncWokaChrome();};$('#show-library').onclick=()=>{libraryHidden=false;syncWokaChrome();};$('#folder-back').onclick=()=>showLibrary();
  $('#toggle-carousel').onclick=()=>{carouselShown=!carouselShown;syncWokaChrome();};
  $('#previous-media').onclick=()=>{const items=folderItems(),index=items.findIndex(item=>item.id===currentScene);if(index>0)openMedia(items[index-1].id);};
  $('#next-media').onclick=()=>{const items=folderItems(),index=items.findIndex(item=>item.id===currentScene);if(index<items.length-1)openMedia(items[index+1].id);};
  $('#pill-minus').onclick=()=>{state.zoom=clamp(state.zoom-.15,1,4);updatePicture();};$('#pill-plus').onclick=()=>{state.zoom=clamp(state.zoom+.15,1,4);updatePicture();};$('#size-menu').onclick=zoomPopover;
  document.addEventListener('click',event=>{
    const media=event.target.closest('[data-media]');if(media){if($('#dialog').open)$('#dialog').close();openMedia(media.dataset.media);return;}
    const expand=event.target.closest('[data-expand]');if(expand){const name=expand.dataset.expand;expandedFolders.has(name)?expandedFolders.delete(name):expandedFolders.add(name);renderLibrary();return;}
    const folder=event.target.closest('#library-sidebar [data-folder]');if(folder){showLibrary(folder.dataset.folder);return;}
    const action=event.target.closest('[data-action]')?.dataset.action;
    if(['view-fit','view-fill','view-actual'].includes(action)){const stage=$('#stage'),picture=$('#picture');pan={x:0,y:0};state.zoom=action==='view-fit'?1:action==='view-fill'?Math.max(stage.clientWidth/parseFloat(picture.style.width),stage.clientHeight/parseFloat(picture.style.height)):(state.mode==='video'?1920:4032)/parseFloat(picture.style.width);closeChromePopover();updatePicture();}
    if(action==='view-mute'){listenVolume=listenVolume?0:70;closeChromePopover();syncViewerPlayback();}
  });
  document.addEventListener('input',event=>{if(event.target.id==='chrome-zoom'){state.zoom=+event.target.value/100;updatePicture();}if(event.target.id==='chrome-volume'){listenVolume=+event.target.value;if($('#volume-value'))$('#volume-value').textContent=listenVolume+'%';syncViewerPlayback();}if(event.target.id==='chrome-speed')viewerSpeed=+event.target.value;});
  document.addEventListener('pointerdown',event=>{if(!event.target.closest('#chrome-popover')&&event.target!==popoverAnchor&&!popoverAnchor?.contains(event.target))closeChromePopover();});
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!$('#chrome-popover').hidden){event.stopImmediatePropagation();const anchor=popoverAnchor;closeChromePopover();anchor?.focus();}},true);
  new ResizeObserver(measurePicture).observe($('#stage'));
}
