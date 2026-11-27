/* The viewer's own dialogs (PT-105): file information as the app draws it — the name, path, size and
   date, then a card per stream (FileInfoDialog, FileInfoStreamCards) — and rename (RenameDialog).
   A dialog over the viewer takes the viewer's palette, one over the editor the dark room's. The
   Dialogs section links to these and to the editor's dialogs. */
function fileFacts(item){
  const meta={path:`D:\\${item.audioOnly?'Music':'Vacation'}\\${item.name}`,created:'2026-08-14 18:32'};
  if(item.audioOnly)return {...meta,size:'7.4 MB',sections:[
    ['Tags',[['Title',item.title],['Artist',item.artist],['Album','Shoreline']]],
    ['General',[['Duration',shortTime(32)]]],
    ['Audio',[['Codec','AAC'],['Channels','Stereo'],['Sample rate','44.1 kHz'],['Bitrate','256 kb/s']]]]};
  if(item.video)return {...meta,size:'412 MB',sections:[
    ['General',[['Resolution',item.dimensions.join(' × ')],['Duration',shortTime(32)]]],
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
  const cards=facts.sections.map(([title,rows])=>`<section class="dialog-group info-section"><h3>${title}</h3><dl>${rows.map(([key,value])=>`<div class="info-row"><dt>${key}</dt><dd>${escapeHtml(value)}</dd></div>`).join('')}</dl></section>`).join('');
  modal('File information',`<div class="info-meta"><b>${escapeHtml(item.name)}${edited}</b><span>${escapeHtml(facts.path)}</span><span>${facts.size} · Created ${facts.created}</span></div><div class="info-sections">${cards}</div><div class="dialog-actions">${button('info-copy','Copy details','secondary')}${button('close-modal','Close','primary')}</div>`,'dialog-compact');
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
function saveChangesDialog(){
  if(state.mode==='photo'){sizedOutputDialog({copy:false});return;}
  modal('Save your changes?',`<p>Keep these edits so you can come back to them in Woka.</p><div class="dialog-actions">${button('close-modal','Cancel','quiet')}${button('finish-editor','Save','primary')}</div>`,'dialog-compact');
}
function videoSizePresets(frame){
  const presets=[['100','Original',frame]],portrait=frame[1]>frame[0];
  for(const height of [2160,1440,1080,720,480]){
    const bounds=[2*Math.round(height*16/18),height];
    if(portrait)bounds.reverse();
    const scale=Math.min(...bounds.map((limit,index)=>limit/frame[index]));
    if(scale>=1)continue;
    const size=frame.map((side,index)=>Math.min(2*Math.round(side*scale/2),2*Math.floor(side/2),bounds[index]));
    if(size.some(side=>side<2)||presets.some(([, , previous])=>size.every((side,index)=>side===previous[index])))continue;
    presets.push([String(height),size.every((side,index)=>side===bounds[index])?`${height}p`:size.join(' × '),size]);
    if(presets.length===3)break;
  }
  return presets;
}
function sizedOutputDialog({copy,intro='',options=''}){
  const video=state.mode==='video',frame=cropPixelSize(video?currentMedia().dimensions:undefined);
  if(state.rotation%180)frame.reverse();
  const saved=copy?undefined:state.photoSize;
  let selected=saved?.preset??'100';
  const presets=video?videoSizePresets(frame)
    :[['100','Original'],['75','75%'],['50','50%'],['25','25%']];
  const presetSize=value=>presets.find(([key])=>key===value)?.[2]
    ??frame.map(side=>Math.max(1,Math.round(side*Number(value)/100)));
  const start=selected==='custom'?[saved.width,saved.height]:presetSize(selected);
  const fields=['Width','Height'].map((label,index)=>`<label>${label} (px)<input class="text-input" type="number" min="1" step="1" required value="${start[index]}" aria-errormessage="output-size-error"></label>`).join('');
  const sizes=[...presets,['custom','Custom']].map(([value,label])=>`<button type="button" value="${value}" aria-pressed="${value===selected}">${label}</button>`).join('');
  const extra=video?button('extract','Audio only…','quiet dialog-leading-action','type="button" aria-disabled="true" data-tip="Coming in 1.1.0"'):'';
  modal(copy?'Create a finished copy':'Save your changes?',`${intro}<form id="output-size-form" novalidate>
    <fieldset class="dialog-group output-size-group"><legend>Size</legend>
      <div class="segmented output-size" role="group" aria-label="Size preset">${sizes}</div>
      <div class="output-size-fields">${fields}</div>
      <label class="output-size-lock"><input type="checkbox" ${saved?.keepProportions===false?'':'checked'}>Keep proportions</label>
      <p id="output-size-error" role="alert" hidden>${video?'Use even sizes from 2 up to 4096 × 2304.':'Use whole pixels from 1 to 16384.'}</p></fieldset>${options}
    <div class="dialog-actions">${extra}${button('close-modal','Cancel','quiet','type="button"')}<button type="submit" class="primary">${copy?'Create copy':'Save'}</button></div></form>`,'dialog-compact');
  const form=$('#output-size-form');
  const inputs=[...form.querySelectorAll('.output-size-fields input')],lock=form.querySelector('.output-size-lock input');
  const choices=[...form.querySelectorAll('.output-size button')];
  const error=form.querySelector('#output-size-error'),submit=form.querySelector('button[type=submit]');
  // The renderer's limits (2026-11-27): a photo up to 16384 px a side; a video even, within 4096 × 2304.
  const valid=value=>Number.isSafeInteger(value)&&value>=(video?2:1)&&value<=(video?4096:16384)&&(!video||value%2===0);
  const fits=size=>!video||Math.min(...size)<=2304;
  let lastEdited=0;
  function update(){
    const size=inputs.map(input=>input.valueAsNumber);
    const invalid=!size.every(valid)||!fits(size);
    choices.forEach(choice=>{
      const active=choice.value===selected;
      choice.classList.toggle('selected',active);
      choice.setAttribute('aria-pressed',active);
    });
    inputs.forEach(input=>input.setAttribute('aria-invalid',!valid(input.valueAsNumber)));
    error.hidden=!invalid;submit.disabled=invalid;
    return invalid?null:size;
  }
  function matchProportions(){
    const value=inputs[lastEdited].valueAsNumber,other=1-lastEdited;
    if(!lock.checked||!Number.isSafeInteger(value)||value<1)return;
    const exact=value*frame[other]/frame[lastEdited];
    inputs[other].value=video?Math.max(2,2*Math.round(exact/2)):Math.max(1,Math.round(exact));
  }
  choices.forEach(choice=>choice.onclick=()=>{
    selected=choice.value;
    if(selected==='custom'){
      inputs[0].focus();inputs[0].select();
    }else{
      const size=presetSize(selected);
      inputs.forEach((input,index)=>input.value=size[index]);
      lastEdited=0;
    }
    update();
  });
  inputs.forEach((input,index)=>input.oninput=()=>{selected='custom';lastEdited=index;matchProportions();update();});
  lock.onchange=()=>{if(selected==='custom')matchProportions();update();};
  form.onsubmit=event=>{
    event.preventDefault();
    const size=update();if(!size)return;
    $('#dialog').close();
    if(copy){startJob('Creating copy');return;}
    let photoSize;
    if(selected==='custom')photoSize={preset:selected,width:size[0],height:size[1],keepProportions:lock.checked};
    else if(selected!=='100')photoSize={preset:selected};
    if(JSON.stringify(photoSize)!==JSON.stringify(saved))commit(()=>{
      if(photoSize)state.photoSize=photoSize;else delete state.photoSize;
    });
    finishEditing();
  };
  update();
}
/* What each Dialogs scene opens, and over what. */
const DIALOG_SCENES={
  info:()=>{openMedia('video');setWindowMode('window');infoDialog();},
  rename:()=>{openMedia('photo');setWindowMode('window');renameDialog();},
  copy:()=>{setScene('photo');outputDialog();},
  done:()=>{setScene('photo');saveChangesDialog();},
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
