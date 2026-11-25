/* The viewer's own dialogs (PT-105): file information as the app draws it — the name, path, size and
   date, then a card per stream (FileInfoDialog, FileInfoStreamCards) — and rename (RenameDialog).
   A dialog over the viewer takes the viewer's palette, one over the editor the dark room's. The
   Dialogs section links to these and to the editor's three: Create a copy, Keep changes?, Revert. */
function fileFacts(item){
  const meta={path:`D:\\${item.audioOnly?'Music':'Vacation'}\\${item.name}`,created:'2026-08-14 18:32'};
  if(item.audioOnly)return {...meta,size:'7.4 MB',sections:[
    ['Tags',[['Title',item.title],['Artist',item.artist],['Album','Shoreline']]],
    ['General',[['Duration',shortTime(32)]]],
    ['Audio',[['Codec','AAC'],['Channels','Stereo'],['Sample rate','44.1 kHz'],['Bitrate','256 kb/s']]]]};
  if(item.video)return {...meta,size:'412 MB',sections:[
    ['General',[['Resolution','1920 × 1080'],['Duration',shortTime(32)]]],
    ['Video',[['Codec','HEVC'],['Frame rate','59.94 fps'],['Bitrate','48.2 Mb/s'],['Colour',item.hdr?'HDR · PQ':'SDR · BT.709']]],
    ['Audio',[['Codec','AAC'],['Channels','Stereo'],['Sample rate','48 kHz'],['Bitrate','256 kb/s']]]]};
  const [,, w,h]=item.view.split(' ').map(Number),portrait=h>w;
  const general=[['Resolution',portrait?'3024 × 4032':'4032 × 3024'],['Colour','SDR · sRGB']];
  if(item.frames>1)general.push(['Frames',String(item.frames)]);
  if(item.gif)general.push(['Duration',clipTime(item.frames/(item.fps||12))]);
  return {...meta,size:item.gif?'3.1 MB':'5.8 MB',sections:[['General',general]]};
}
function infoText(item,facts){
  return [`Name: ${item.name}`,`Path: ${facts.path}`,`Size: ${facts.size}`,`Created: ${facts.created}`,
    ...facts.sections.flatMap(([title,rows])=>['',title,...rows.map(([key,value])=>`${key}: ${value}`)])].join('\n');
}
function infoDialog(){
  const item=currentMedia(),facts=fileFacts(item);
  const edited=editedIds.has(item.id)?'<span class="info-edited">Edited</span>':'';
  const cards=facts.sections.map(([title,rows])=>`<section class="info-section"><h3>${title}</h3><div class="info-card">${rows.map(([key,value])=>`<div class="info-row"><span>${key}</span><b>${escapeHtml(value)}</b></div>`).join('')}</div></section>`).join('');
  modal('File information',`<div class="info-scroll"><div class="info-meta"><b>${escapeHtml(item.name)}${edited}</b><span>Path: ${escapeHtml(facts.path)}</span><span>Size: ${facts.size}</span><span>Created: ${facts.created}</span></div>${cards}</div><div class="dialog-actions">${button('info-copy','Copy','secondary')}${button('close-modal','Close','primary')}</div>`,'dialog-narrow');
}
function renameDialog(){
  const item=currentMedia(),dot=item.name.lastIndexOf('.');
  modal('Rename',`<input id="rename-input" class="text-input" value="${escapeHtml(item.name)}" aria-label="New name" spellcheck="false"><div id="rename-error" class="rename-error" hidden></div><div class="dialog-actions">${button('close-modal','Cancel','quiet')}${button('rename-confirm','Rename','primary')}</div>`,'dialog-narrow');
  const input=$('#rename-input');input.focus();input.setSelectionRange(0,dot>0?dot:item.name.length);
}
function confirmRename(){
  const item=currentMedia(),name=$('#rename-input').value.trim(),error=$('#rename-error');
  const problem=!name?'Give the file a name.':/[\\/:*?"<>|]/.test(name)?'A name cannot contain \\ / : * ? " < > |':libraryMedia.some(other=>other!==item&&other.name===name)?'Another file in this folder has that name.':'';
  error.hidden=!problem;error.textContent=problem;if(problem)return;
  $('#dialog').close();if(name===item.name)return;
  item.name=name;carouselKey='';syncWokaChrome();toast(`Renamed to ${name}.`);
}
/* What each Dialogs scene opens, and over what. */
const DIALOG_SCENES={
  info:()=>{openMedia('video');setWindowMode('window');infoDialog();},
  rename:()=>{openMedia('photo');setWindowMode('window');renameDialog();},
  copy:()=>{setScene('photo');outputDialog();},
  keep:()=>{setScene('photo');commit(()=>Object.assign(settings(),{exposure:12}));requestExit();},
  revert:()=>{setScene('photo');$('#reset').click();}
};
function initDialogs(){
  $('#media-info').onclick=infoDialog;
  document.addEventListener('click',event=>{
    const action=event.target.closest('[data-action]')?.dataset.action;
    if(action==='rename-confirm')confirmRename();
    if(action==='info-copy'){
      const item=currentMedia();
      navigator.clipboard?.writeText(infoText(item,fileFacts(item))).catch(()=>{});
      toast('Information copied.');
    }
  });
  document.addEventListener('keydown',event=>{
    if(event.key==='Enter'&&event.target.id==='rename-input'){event.preventDefault();confirmRename();}
    if(event.key==='F2'&&viewerMode&&!$('#dialog').open){event.preventDefault();renameDialog();}
  });
}
