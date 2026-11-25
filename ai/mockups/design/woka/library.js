/* The library (PT-201/202), ported from the app as it is: the folder screen's toolbar, the grid of
   folder cards and media, selection, Quick Look, search, the Info panel. Not redesigned; values are
   the Compose code's (FolderToolbar, FolderGrid*, FolderCard*, FolderSelectionBar, QuickLook,
   SearchResults, FileInfoPanel). */
Object.assign(icons,{
  filter:'<path d="M4 7h16M7 12h10M10 17h4"/>',
  search:'<circle cx="11" cy="11" r="6"/><path d="m20 20-4.5-4.5"/>',
  sort:'<path d="M8 5v14m0 0-3-3m3 3 3-3M16 19V5m0 0-3 3m3-3 3 3"/>',
  check:'<path d="m5 12 4 4 10-10"/>'
});
const EPISODE_SIZES=['7.6 GB','6.8 GB','6.3 GB','6 GB','6.2 GB','5.6 GB','6.8 GB'],EPISODE_WATCHED=[1,1,.3,0,0,0,0];
libraryMedia.push(...EPISODE_SIZES.map((size,index)=>({id:`ep${index+1}`,name:`S01E0${index+1}.mkv`,view:`${350+index*14} ${100+index*8} 1000 562.5`,video:true,folder:'The Sea · Season 1',size,watched:EPISODE_WATCHED[index]})));
const stills=()=>libraryMedia.filter(item=>!item.video&&!item.audioOnly&&!item.folder);
const looseVideos=()=>libraryMedia.filter(item=>item.video&&!item.folder);
const FOLDERS={
  Movies:{children:['The Sea · Season 1'],media:looseVideos},
  'The Sea · Season 1':{parent:'Movies',media:()=>libraryMedia.filter(item=>item.folder==='The Sea · Season 1')},
  Music:{media:()=>libraryMedia.filter(item=>item.audioOnly)},
  Pictures:{children:['Iceland 2025','Phone 2024'],media:stills},
  'Iceland 2025':{parent:'Pictures',media:()=>stills().slice(0,4)},
  'Phone 2024':{parent:'Pictures',media:()=>[]},
  Screenshots:{media:stills},
  Vacation:{media:()=>libraryMedia.filter(item=>!item.audioOnly&&!item.folder)},
  Videos:{media:looseVideos}
};
const ROOTS=['Movies','Music','Pictures','Screenshots','Vacation','Videos'];
const expandedFolders=new Set(['Movies']);
let pinnedFolders=['Vacation','Pictures'],libraryEmpty=false;
const lib={show:'All',size:'M',sort:'Name',descending:false,query:'',scope:'All Folders',focus:null,selection:new Set(),selecting:false,anchor:null,info:false,quickLook:false};
const libraryShown=()=>!$('#library').hidden;
function folderCount(name){const folder=FOLDERS[name];return folder.media().length+(folder.children?.length||0);}
function pinnedLabel(name){return FOLDERS[name].parent||'OldValencia';}
function renderLibrary(){
  if(libraryEmpty){
    $('#library-folders').innerHTML='<div class="library-section">Folders</div><div class="library-nothing">Nothing linked yet</div>';
    $('#link-folder').classList.add('link-first');return;
  }
  $('#link-folder').classList.remove('link-first');
  const row=(name,level)=>{
    const folder=FOLDERS[name],open=expandedFolders.has(name),kids=folder.children||[];
    return `<button class="folder-row ${activeFolder===name&&libraryShown()?'selected':''}" data-folder="${name}" style="padding-left:${8+level*16}px"><span class="chevron ${open?'open':''}" ${kids.length?`data-expand="${name}"`:''}>${kids.length?'›':''}</span>${icon('folder')}<span class="folder-name">${name}</span><span class="count">${folderCount(name)}</span></button>${open?kids.map(kid=>row(kid,level+1)).join(''):''}`;
  };
  $('#library-folders').innerHTML='<div class="library-section">Folders</div>'+ROOTS.map(name=>row(name,0)).join('')+
    (pinnedFolders.length?'<div class="library-section">Pinned</div>'+pinnedFolders.map(name=>`<button class="folder-row pinned" data-folder="${name}">${icon('pin')}<span class="folder-name">${name}<small>${pinnedLabel(name)}</small></span></button>`).join(''):'');
}
function showLibrary(folder=activeFolder){
  if(transitionBusy)return;
  closeChromePopover();if(windowMode!=='window')setWindowMode('window');
  if(currentMedia().audioOnly&&playing)handOverSound();
  playing=false;
  activeFolder=folder;Object.assign(lib,{focus:null,selecting:false,quickLook:false,query:''});lib.selection.clear();
  $('#lib-search').value='';$('#editor').hidden=true;$('#library').hidden=false;
  renderLibrary();renderLibraryScreen();renderNowPlaying();
}
function hideLibrary(){$('#library').hidden=true;$('#editor').hidden=false;}
function shownMedia(){
  const filter={All:()=>true,Photos:item=>!item.video&&!item.audioOnly,Videos:item=>item.video,Music:item=>item.audioOnly}[lib.show];
  const list=FOLDERS[activeFolder].media().filter(filter);
  if(lib.sort==='Name')list.sort((a,b)=>a.name.localeCompare(b.name,undefined,{numeric:true}));
  return lib.descending?list.reverse():list;
}
function middleEllipsis(name){return name.length>26?`${name.slice(0,13)}…${name.slice(-12)}`:name;}
function mediaMeta(item){
  const size=item.size||fileFacts(item).size,kind=item.audioOnly?'Audio':item.video?'Video':'Image';
  return item.frames>1?`${kind} · ${item.frames} frames · ${size}`:`${kind} · ${size}`;
}
function folderMeta(name){const count=folderCount(name);return `Folder · ${count?`${count} item${count>1?'s':''}`:'empty'}`;}
function folderCover(name){
  const own=FOLDERS[name].media().find(item=>!item.audioOnly);
  if(own)return own;
  for(const kid of FOLDERS[name].children||[]){const cover=FOLDERS[kid].media()[0];if(cover)return cover;}
  return null;
}
function caption(title,meta){return `<span class="lib-caption"><b>${escapeHtml(middleEllipsis(title))}</b><span>${escapeHtml(meta)}</span></span>`;}
function mediaCover(item){
  const picture=item.audioOnly&&!item.view?`<span class="lib-no-cover">${icon('music')}</span>`:photoSvg(item.view);
  return `${picture}<i class="lib-calm"></i>${item.video?`<span class="lib-play">${icon('play')}</span>`:''}`;
}
function checkCircle(id){return `<span class="lib-check ${lib.selection.has(id)?'on':''}" data-lib-check="${id}">${icon('check')}</span>`;}
function watched(item){return item.watched?`<span class="lib-watched"><i style="width:${item.watched*100}%"></i></span>`:'';}
function mediaCard(item,mosaic){
  const state=`${lib.selection.has(item.id)?'selected':''} ${lib.focus===item.id?'focused':''}`;
  if(mosaic)return `<div class="lib-tile ${state}" data-lib-item="${item.id}" role="button" tabindex="-1" aria-label="${escapeHtml(item.name)}"><span class="lib-picture">${mediaCover(item)}${caption(item.name,mediaMeta(item))}${watched(item)}</span>${checkCircle(item.id)}</div>`;
  return `<div class="lib-card ${state}" data-lib-item="${item.id}" role="button" tabindex="-1" aria-label="${escapeHtml(item.name)}"><span class="lib-cover">${mediaCover(item)}${caption(item.name,mediaMeta(item))}${watched(item)}${checkCircle(item.id)}</span></div>`;
}
function folderCard(name){
  const cover=folderCover(name),state=lib.focus===`folder:${name}`?'focused':'';
  return `<div class="lib-card folder ${state}" data-lib-folder="${name}" role="button" tabindex="-1" aria-label="${escapeHtml(name)}"><i class="lib-sheet far"></i><i class="lib-sheet near"></i><span class="lib-cover">${cover?`${photoSvg(cover.view)}<i class="lib-calm"></i>`:`<span class="lib-no-cover">${icon('folder')}</span>`}${caption(name,folderMeta(name))}</span></div>`;
}
function renderLibraryScreen(){
  const folder=FOLDERS[activeFolder],root=!folder.parent;
  $('#lib-title').textContent=libraryEmpty?'Woka':activeFolder;
  $('#lib-up').hidden=root||libraryEmpty;$('#lib-up').dataset.tip=root?'':`Back to ${folder.parent}`;
  $('#lib-show-sidebar').hidden=!libraryHidden;
  $('#lib-pin').classList.toggle('active',pinnedFolders.includes(activeFolder));$('#lib-pin').setAttribute('aria-label',pinnedFolders.includes(activeFolder)?'Unpin':'Pin');
  $('#lib-info').classList.toggle('active',lib.info);
  document.querySelectorAll('.lib-folder-only').forEach(node=>node.hidden=libraryEmpty);
  $('#lib-search-hint').textContent=lib.query?'✕':'Ctrl+F';
  const content=$('#lib-content');
  if(libraryEmpty)content.innerHTML=`<div class="lib-welcome">${icon('folder')}<b>Drop a folder here</b><p>Woka shows what is inside and keeps the folder in the sidebar. Nothing is copied or moved.</p><button class="lib-welcome-button" data-lib="link">Link Folder…</button></div>`;
  else if(lib.query)content.innerHTML=searchHtml();
  else{
    const all=folder.media(),media=shownMedia(),kids=folder.children||[];
    const mosaic=all.length>0&&all.filter(item=>item.video).length<all.length/2;
    const heading=(label,count)=>`<h2 class="lib-heading">${label}  <span>${count}</span></h2>`;
    content.innerHTML=`<div class="lib-grid ${mosaic?'mosaic':'cards'} size-${lib.size}">${kids.length?heading('Folders',kids.length)+kids.map(folderCard).join('')+heading('Media',media.length):''}${media.map(item=>mediaCard(item,mosaic)).join('')}</div>`;
  }
  renderSelectionBar();renderInfoPanel();renderQuickLook();
}
/* ---------- selection ---------- */
function toggleSelected(id){lib.selection.has(id)?lib.selection.delete(id):lib.selection.add(id);lib.selecting=lib.selection.size>0;lib.anchor=id;renderLibraryScreen();}
function selectRange(id){
  const ids=shownMedia().map(item=>item.id),from=ids.indexOf(lib.anchor??ids[0]),to=ids.indexOf(id);
  ids.slice(Math.min(from,to),Math.max(from,to)+1).forEach(each=>lib.selection.add(each));lib.selecting=true;renderLibraryScreen();
}
function endSelecting(){lib.selection.clear();lib.selecting=false;renderLibraryScreen();}
function renderSelectionBar(){
  const bar=$('#lib-selection');bar.hidden=!lib.selecting||!!lib.query;if(bar.hidden)return;
  const none=lib.selection.size===0,pill=(action,label,cls='')=>`<button class="lib-pill ${cls}" data-lib="${action}" ${none&&['copy','move','duplicate'].includes(action)?'disabled':''}>${label}</button>`;
  bar.innerHTML=`<span class="lib-count">${lib.selection.size} selected</span><i class="lib-divider"></i>${pill('select-all','Select All')}${pill('copy','Copy…')}${pill('move','Move…')}${pill('duplicate','Duplicate')}<i class="lib-divider"></i>${pill('delete','Delete','danger')}<i class="lib-divider"></i>${pill('done','Done','active')}`;
}
/* ---------- focus, Quick Look, Info ---------- */
function focusedItem(){return libraryMedia.find(item=>item.id===lib.focus);}
function moveFocus(key){
  const nodes=[...document.querySelectorAll('#lib-content [data-lib-item],#lib-content [data-lib-folder]')];if(!nodes.length)return;
  const key_=node=>node.dataset.libItem||`folder:${node.dataset.libFolder}`;
  let index=nodes.findIndex(node=>key_(node)===lib.focus);
  if(index<0)index=0;
  else if(key==='ArrowLeft'||key==='ArrowRight')index=clamp(index+(key==='ArrowRight'?1:-1),0,nodes.length-1);
  else{
    const here=nodes[index].getBoundingClientRect(),down=key==='ArrowDown';
    const row=nodes.filter(node=>{const box=node.getBoundingClientRect();return down?box.top>here.bottom-4:box.bottom<here.top+4;});
    if(row.length){const edge=down?Math.min(...row.map(n=>n.getBoundingClientRect().top)):Math.max(...row.map(n=>n.getBoundingClientRect().top));
      const line=row.filter(n=>Math.abs(n.getBoundingClientRect().top-edge)<4);
      const best=line.reduce((a,b)=>Math.abs(b.getBoundingClientRect().left-here.left)<Math.abs(a.getBoundingClientRect().left-here.left)?b:a);index=nodes.indexOf(best);}
  }
  lib.focus=key_(nodes[index]);renderLibraryScreen();
  document.querySelector('#lib-content .focused')?.scrollIntoView({block:'nearest'});
}
function renderQuickLook(){
  const box=$('#lib-quicklook'),item=focusedItem()||shownMedia()[0];box.hidden=!lib.quickLook||!item;if(box.hidden)return;
  const fit=!item.video&&!item.audioOnly;
  box.innerHTML=`<div class="ql-card" data-ql-card><div class="ql-media">${fit?`<svg viewBox="${item.view}" preserveAspectRatio="xMidYMid meet" aria-hidden="true"><image href="../../../assets/screenshots/photo-viewer.png" width="1440" height="900"/></svg>`:`${photoSvg(item.view)}<span class="lib-play">${icon('play')}</span>${watched(item)}`}</div><div class="ql-caption"><b>${escapeHtml(item.name)}</b><span>← → next · Enter open · Space close</span></div></div>`;
}
function infoRows(item){
  const facts=fileFacts(item),ext=item.name.split('.').pop().toUpperCase(),kind=item.audioOnly?'audio':item.video?'video':'image';
  const general=[['Kind',`${ext} ${kind}`],['Size',item.size||facts.size]];
  facts.sections.flatMap(([,rows])=>rows).forEach(([label,value])=>{if(label==='Resolution')general.push(['Dimensions',value]);if(label==='Duration')general.push(['Duration',value]);});
  general.push(['Modified','14 Aug 2026, 18:32']);
  const sections=[['General',general]];
  if(item.video){const video=Object.fromEntries(facts.sections.find(([t])=>t==='Video')[1]);sections.push(['Video',[['Codec',video.Codec],['Picture',`1920 × 1080 · ${video['Frame rate']}`],['HDR',item.hdr?'PQ':'—']]]);sections.push(['Audio',[['Track 1','AAC · Stereo · English']]]);}
  return sections;
}
function renderInfoPanel(){
  const panel=$('#lib-info-panel');panel.hidden=!lib.info||libraryEmpty;if(panel.hidden)return;
  if(lib.focus?.startsWith('folder:')){const name=lib.focus.slice(7),cover=folderCover(name);
    panel.innerHTML=`<div class="lib-info-body"><span class="lib-info-preview">${cover?photoSvg(cover.view):`<span class="lib-no-cover">${icon('folder')}</span>`}</span><b class="lib-info-name">${name}</b><span class="lib-info-parent">${activeFolder}</span><h3>General</h3><div class="lib-info-row"><span>Kind</span><b>Folder</b></div><div class="lib-info-row"><span>Modified</span><b>14 Aug 2026, 18:32</b></div></div>`;return;}
  const item=focusedItem();
  if(!item){panel.innerHTML='<p class="lib-info-empty">Select an item to see its details</p>';return;}
  panel.innerHTML=`<div class="lib-info-body"><span class="lib-info-preview">${photoSvg(item.view)}</span><b class="lib-info-name">${escapeHtml(item.name)}</b><span class="lib-info-parent">${activeFolder}</span>${infoRows(item).map(([title,rows])=>`<h3>${title}</h3>${rows.map(([label,value])=>`<div class="lib-info-row"><span>${label}</span><b>${escapeHtml(value)}</b></div>`).join('')}`).join('')}</div>`;
}
/* ---------- search ---------- */
function placeOf(item){return item.folder||(item.audioOnly?'Music':item.video?'Videos':'Pictures');}
function inScope(name){if(lib.scope==='All Folders')return true;for(let at=name;at;at=FOLDERS[at]?.parent)if(at===activeFolder)return true;return false;}
function searchHits(){
  const query=lib.query.toLowerCase(),folders=Object.keys(FOLDERS).filter(name=>name.toLowerCase().includes(query)&&inScope(FOLDERS[name].parent||name));
  const media=libraryMedia.filter(item=>item.name.toLowerCase().includes(query)&&inScope(placeOf(item)));
  return [...folders.map(name=>({folder:name})),...media.map(item=>({item}))].slice(0,300);
}
function marked(name){const at=name.toLowerCase().indexOf(lib.query.toLowerCase());return at<0?escapeHtml(name):`${escapeHtml(name.slice(0,at))}<mark>${escapeHtml(name.slice(at,at+lib.query.length))}</mark>${escapeHtml(name.slice(at+lib.query.length))}`;}
function searchHtml(){
  const hits=searchHits(),seg=['This Folder','All Folders'].map(label=>`<button class="${lib.scope===label?'selected':''}" data-lib-scope="${label}">${label}</button>`).join('');
  return `<div class="lib-search-head"><div class="lib-scope">${seg}</div><span>${hits.length} results for “${escapeHtml(lib.query)}”</span></div><div class="lib-results">${hits.map(hit=>hit.folder
    ?`<button class="lib-hit" data-lib-folder="${hit.folder}"><span class="lib-hit-preview">${folderCover(hit.folder)?photoSvg(folderCover(hit.folder).view):`<span class="lib-no-cover">${icon('folder')}</span>`}</span><b>${marked(hit.folder)}</b><span>${FOLDERS[hit.folder].parent||'Linked folder'}</span></button>`
    :`<button class="lib-hit" data-lib-hit="${hit.item.id}"><span class="lib-hit-preview">${photoSvg(hit.item.view)}</span><b>${marked(hit.item.name)}</b><span>${placeOf(hit.item)}</span></button>`).join('')}</div>`;
}
let searchTimer=null;
function setQuery(value){lib.query=value.trim();clearTimeout(searchTimer);searchTimer=setTimeout(renderLibraryScreen,200);}
