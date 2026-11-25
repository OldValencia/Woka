/* What the library does when touched: the View panel, size steps and their HUD, the context menus,
   the keyboard, and the Library section's scenes. */
const SIZE_NAMES={S:'Small',M:'Medium',L:'Large'},SIZES=['S','M','L'];
function segmentHtml(key,options,value){return `<div class="lib-seg" data-lib-seg="${key}">${options.map(option=>`<button class="${option===value?'selected':''}" data-value="${option}">${option}</button>`).join('')}</div>`;}
function renderViewMenu(){
  const menu=$('#lib-view-menu'),row=(label,control)=>`<div class="lib-menu-row"><span>${label}</span>${control}</div>`;
  menu.innerHTML=row('Show',segmentHtml('show',['All','Photos','Videos','Music'],lib.show))+row('Size',segmentHtml('size',SIZES,lib.size))+
    row('Sort',`<div class="lib-sort">${segmentHtml('sort',['Name','Date'],lib.sort)}<button class="ib ${lib.descending?'active':''}" data-lib="direction" aria-label="${lib.descending?'Descending':'Ascending'}">${icon('sort')}</button></div>`);
}
function toggleViewMenu(force){
  const menu=$('#lib-view-menu'),open=force??menu.hidden;menu.hidden=!open;$('#lib-view').classList.toggle('active',open);if(!open)return;
  renderViewMenu();const anchor=$('#lib-view').getBoundingClientRect();
  menu.style.top=`${anchor.bottom+6}px`;menu.style.left=`${Math.max(8,anchor.right-menu.offsetWidth)}px`;
}
let hudTimer=null;
function setSize(size){
  if(size===lib.size)return;const old=lib.size;lib.size=size;renderLibraryScreen();if(!$('#lib-view-menu').hidden)renderViewMenu();
  const hud=$('#lib-hud');hud.innerHTML=`<div><span class="old">${old}</span><span class="arrow">→</span><b>${size}</b></div><small>${SIZE_NAMES[size]}</small>`;hud.hidden=false;
  clearTimeout(hudTimer);hudTimer=setTimeout(()=>{hud.hidden=true;},900);
}
function stepSize(delta){setSize(SIZES[clamp(SIZES.indexOf(lib.size)+delta,0,2)]);}
function itemMenu(x,y,node){
  const id=node.dataset.libItem,folder=node.dataset.libFolder,item=libraryMedia.find(each=>each.id===id);lib.menuTarget=id||`folder:${folder}`;
  const entries=[{label:'Open',action:'lib-open'}];
  if(folder)entries.push({label:'Pin to Library',action:'lib-pin-item'});
  if(item?.video)entries.push({label:item.watched>=1?'Mark as not watched':'Mark as watched',action:'lib-watched'});
  if(item?.frames>1)entries.push({label:'Export Frames...',action:'lib-todo'});
  if(item&&!item.video&&!item.audioOnly)entries.push({label:'Edit',action:'lib-edit'});
  entries.push({label:'Rename',action:'lib-todo'},{label:'Copy',action:'lib-todo'},{label:'Duplicate',action:'lib-todo'},{label:'Move',action:'lib-todo'});
  if(item&&!item.video&&!item.audioOnly)entries.push({label:'Revert to Original',action:'lib-revert',danger:true,disabled:!editedIds.has(id)});
  entries.push({label:'Delete',action:'lib-todo',danger:true});
  openMenu(x+2,y+2,entries);
}
function backgroundMenu(x,y){
  openMenu(x+2,y+2,[{label:'Create folder',action:'lib-todo'},{label:'Pin to Library',action:'lib-pin'},{label:'Open in Explorer',action:'lib-todo'},{label:'Copy Path',action:'lib-todo'},'-',
    {label:'View: Bigger',action:'lib-bigger',disabled:lib.size==='L'},{label:'View: Smaller',action:'lib-smaller',disabled:lib.size==='S'}]);
}
function togglePin(name=activeFolder){pinnedFolders=pinnedFolders.includes(name)?pinnedFolders.filter(each=>each!==name):[...pinnedFolders,name];renderLibrary();renderLibraryScreen();}
function openLibraryItem(id){lib.focus=id;lib.quickLook=false;openMedia(id);}
const LIBRARY_FOLDER={pictures:'Pictures',movies:'Movies',series:'The Sea · Season 1',empty:'Phone 2024',select:'Vacation',search:'Pictures',info:'Vacation',quicklook:'Vacation',settings:'Vacation',welcome:'Vacation'};
const LIBRARY_SCENES={
  pictures:()=>showLibrary('Pictures'),movies:()=>showLibrary('Movies'),series:()=>showLibrary('The Sea · Season 1'),empty:()=>showLibrary('Phone 2024'),
  select:()=>{showLibrary('Vacation');['palm','crop','frames'].forEach(id=>lib.selection.add(id));lib.selecting=true;lib.anchor='frames';renderLibraryScreen();},
  search:()=>{showLibrary('Pictures');$('#lib-search').value='sea';lib.query='sea';renderLibraryScreen();},
  info:()=>{showLibrary('Vacation');lib.info=true;lib.focus='video';renderLibraryScreen();},
  quicklook:()=>{showLibrary('Vacation');lib.focus='photo';lib.quickLook=true;renderLibraryScreen();},
  settings:()=>{showLibrary('Vacation');openSettings();},
  welcome:()=>showLibrary('Vacation')
};
function libraryKeys(event){
  if(!libraryShown()||$('#dialog').open||!$('#settings-overlay').hidden)return;
  const typing=['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName),ctrl=event.ctrlKey||event.metaKey,key=event.key;
  if(ctrl&&key.toLowerCase()==='f'){event.preventDefault();$('#lib-search').focus();}
  else if(ctrl&&key.toLowerCase()==='i'){event.preventDefault();lib.info=!lib.info;renderLibraryScreen();}
  else if(typing){if(key==='Escape'){$('#lib-search').value='';setQuery('');document.activeElement.blur();}return;}
  else if(ctrl&&(key==='='||key==='+'||key==='-')){event.preventDefault();stepSize(key==='-'?-1:1);}
  else if(ctrl&&key.toLowerCase()==='a'){event.preventDefault();shownMedia().forEach(item=>lib.selection.add(item.id));lib.selecting=true;renderLibraryScreen();}
  else if(key.startsWith('Arrow')){event.preventDefault();moveFocus(key);}
  else if(key===' '){event.preventDefault();lib.quickLook=!lib.quickLook;renderLibraryScreen();}
  else if(key==='Enter'){const item=focusedItem();if(item)openLibraryItem(item.id);else if(lib.focus?.startsWith('folder:'))showLibrary(lib.focus.slice(7));}
  else if(key==='Escape'){if(lib.quickLook){lib.quickLook=false;renderLibraryScreen();}else if(lib.selecting)endSelecting();}
  else if(key==='Delete'&&lib.selecting){toast(`Delete ${lib.selection.size} items is not part of the prototype.`);endSelecting();}
  else return;
  event.stopImmediatePropagation();
}
function initLibrary(){
  [['lib-show-sidebar','focus'],['lib-up','chevronLeft'],['lib-pin','pin'],['lib-info','info'],['lib-settings','settings']].forEach(([id,name])=>$('#'+id).innerHTML=icon(name));
  $('#lib-view').innerHTML=icon('filter');$('#lib-search-icon').innerHTML=icon('search');
  $('#lib-view').onclick=()=>toggleViewMenu();$('#lib-pin').onclick=()=>togglePin();$('#lib-settings').onclick=openSettings;
  $('#lib-info').onclick=()=>{lib.info=!lib.info;renderLibraryScreen();};
  $('#lib-up').onclick=()=>showLibrary(FOLDERS[activeFolder].parent);
  $('#lib-show-sidebar').onclick=()=>{libraryHidden=false;syncWokaChrome();renderLibraryScreen();};
  $('#lib-search').addEventListener('input',event=>{setQuery(event.target.value);$('#lib-search-hint').textContent=event.target.value?'✕':'Ctrl+F';});
  $('#lib-search-hint').onclick=()=>{$('#lib-search').value='';setQuery('');$('#lib-search-hint').textContent='Ctrl+F';};
  $('#library').addEventListener('click',event=>{
    const t=event.target,check=t.closest('[data-lib-check]');
    if(check){event.stopPropagation();toggleSelected(check.dataset.libCheck);return;}
    if(t.closest('#lib-quicklook')&&!t.closest('[data-ql-card]')){lib.quickLook=false;renderLibraryScreen();return;}
    const hit=t.closest('[data-lib-hit]');
    if(hit){const item=libraryMedia.find(each=>each.id===hit.dataset.libHit);activeFolder=placeOf(item);$('#lib-search').value='';lib.query='';openLibraryItem(item.id);return;}
    const card=t.closest('[data-lib-item]');
    if(card){const id=card.dataset.libItem;if(event.altKey||(lib.selecting&&!event.shiftKey))toggleSelected(id);else if(event.shiftKey)selectRange(id);else openLibraryItem(id);return;}
    const folder=t.closest('#lib-content [data-lib-folder]');if(folder){showLibrary(folder.dataset.libFolder);return;}
    const scope=t.closest('[data-lib-scope]');if(scope){lib.scope=scope.dataset.libScope;renderLibraryScreen();return;}
    const action=t.closest('[data-lib]')?.dataset.lib;
    if(action==='select-all'){shownMedia().forEach(item=>lib.selection.add(item.id));renderLibraryScreen();}
    else if(action==='done')endSelecting();
    else if(['copy','move','duplicate','delete'].includes(action)){toast(`${t.textContent.replace('…','')} is not part of the prototype.`);endSelecting();}
    else if(action==='link'){libraryEmpty=false;showLibrary('Vacation');}
  });
  $('#lib-view-menu').addEventListener('click',event=>{
    const seg=event.target.closest('[data-lib-seg] button');
    if(seg){const key=seg.parentElement.dataset.libSeg,value=seg.dataset.value;if(key==='size')setSize(value);else{lib[key]=value;renderLibraryScreen();renderViewMenu();}}
    if(event.target.closest('[data-lib="direction"]')){lib.descending=!lib.descending;renderLibraryScreen();renderViewMenu();}
  });
  document.addEventListener('pointerdown',event=>{if(!$('#lib-view-menu').hidden&&!event.target.closest('#lib-view-menu,#lib-view'))toggleViewMenu(false);});
  $('#lib-content').addEventListener('contextmenu',event=>{event.preventDefault();const node=event.target.closest('[data-lib-item],[data-lib-folder]');if(node)itemMenu(event.clientX,event.clientY,node);else backgroundMenu(event.clientX,event.clientY);});
  $('#lib-scroll').addEventListener('wheel',event=>{if(!event.ctrlKey)return;event.preventDefault();stepSize(event.deltaY<0?1:-1);},{passive:false});
  document.addEventListener('click',event=>{
    const action=event.target.closest('[data-action^="lib-"]')?.dataset.action;if(!action)return;
    const target=lib.menuTarget||'',id=target.startsWith('folder:')?null:target;
    ({'lib-open':()=>id?openLibraryItem(id):showLibrary(target.slice(7)),'lib-pin-item':()=>togglePin(target.slice(7)),'lib-pin':()=>togglePin(),
      'lib-watched':()=>{const item=libraryMedia.find(each=>each.id===id);item.watched=item.watched>=1?0:1;renderLibraryScreen();},
      'lib-edit':()=>{lib.focus=id;hideLibrary();setScene(id);},'lib-revert':()=>{editedIds.delete(id);sceneSessions.delete(id);renderLibraryScreen();toast('Reverted to the original.');},
      'lib-bigger':()=>stepSize(1),'lib-smaller':()=>stepSize(-1),'lib-todo':()=>toast(`${event.target.textContent.trim()} is not part of the prototype.`)})[action]?.();
  });
  document.addEventListener('keydown',libraryKeys,true);
}
