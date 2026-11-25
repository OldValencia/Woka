/* The Settings overlay as the app has it (SettingsOverlay, SettingsOverlayContent and the panels):
   a 560 × 420 card over a scrim, categories on the left, the panel on the right. The controls answer
   to clicks and remember them for the session; nothing reaches the rest of the prototype. */
const TOOLS=[['Clips','Cut, order, speed and freeze'],['Transform','Rotate and flip'],['Crop','Choose the region to keep'],['Adjust','Exposure, colour and contrast'],['Styles','Undertones, looks and a tone pad'],['Markup','Arrows, boxes, freehand and text'],['Resize','Make the picture smaller'],['HDR','What a save writes'],['Cover','The frame the grid shows'],['Audio','Mute, volume and fades']];
const SETTINGS=[
  {name:'Appearance',icon:'image',sections:[['Appearance',[['seg',['System','Light','Dark'],'Dark']]]]},
  {name:'Library',icon:'folder',sections:[['Library',[['toggle','Show Item Titles','Display file names under thumbnails everywhere',true],['toggle','Show Hidden Files','Display files and folders hidden by the OS',false]]]]},
  {name:'Media Viewer',icon:'carousel',sections:[['Carousel',[['seg',['Auto Hide','Always On','Hidden'],'Always On']]],['Default zoom & size',[['toggle','Remember zoom & size','Restore the last zoom/preset you used; otherwise open at the default below',false],['seg',['Preset','Zoom'],'Preset'],['seg',['Fit','Fill','Actual'],'Fit']]]]},
  {name:'Video Player',icon:'play',sections:[['Resume playback',[['hint','Continue a video where you left off instead of starting over'],['seg',['Off','Long videos','Always'],'Long videos'],['hint','Only for videos longer than'],['stepper','20 min'],['hint','Count a video as watched after'],['seg',['Most of it','The very end'],'Most of it'],['toggle','Dim watched videos','Fade the thumbnail of any video you have already started',false],['danger','Forget watched marks for all videos','Forget them','Every video goes back to looking unwatched']]],
    ['Time',[['hint','What the right-hand number beside the seek bar shows'],['seg',['Total','Remaining'],'Total']]],
    ['System',[['toggle','System media controls','Use the keyboard\'s media keys and show Woka in the system Now Playing panel',true]]],
    ['Ambient fill',[['toggle','Ambient fill','Fill the black bars beside a fullscreen video with a soft glow taken from the picture',false]]],
    ['Tracks',[['toggle','Carry tracks across a folder','Open a video with the audio and subtitle tracks last used in its folder',true],['danger','Forget saved tracks for all files','Forget them','']]]]},
  {name:'Editor',icon:'markup',sections:[['Tools',[['hint','A tool switched off leaves the editor\'s toolbar, and any edits it already wrote into a file stop being applied'],...TOOLS.map(([title,sub])=>['toggle',title,sub,true])]]]},
  {name:'PiP',icon:'pip',sections:[['Picture in picture',[['toggle','Always on Top','Keep the picture-in-picture window above other apps. Not applied while it fills the screen',true]]]]},
  {name:'OS Integration',icon:'settings',sections:[['OS integration',[['hint','Integrate Woka with Windows Explorer and system shortcuts'],['action','Default Media App','Associate Woka with 153 supported media formats in Windows','Associate Formats'],['toggle','Explorer Context Menu','Show "Open with Woka" on folders in Windows Explorer',true],['toggle','Hardware Media Keys','Control playback using system media keys and Now Playing notifications',true]]]]},
  {name:'About',icon:'info',about:true}
];
let settingsPanel='Appearance';
function settingsRow(row,key){
  const [type]=row;
  if(type==='seg')return `<div class="set-seg" data-set="${key}">${row[1].map(option=>`<button class="${option===row[2]?'selected':''}" data-option="${option}">${option}</button>`).join('')}</div>`;
  if(type==='hint')return `<p class="set-hint">${row[1]}</p>`;
  if(type==='toggle')return `<label class="set-row"><span class="set-text"><b>${row[1]}</b><span>${row[2]}</span></span><input type="checkbox" class="set-switch" data-set="${key}" ${row[3]?'checked':''}></label>`;
  if(type==='stepper')return `<div class="set-stepper"><button aria-label="Less">−</button><b>${row[1]}</b><button aria-label="More">+</button></div>`;
  if(type==='action')return `<div class="set-row"><span class="set-text"><b>${row[1]}</b><span>${row[2]}</span></span><button class="set-action" data-set-action>${row[3]}</button></div>`;
  return `<div class="set-danger" data-set-danger><p class="set-hint" hidden>${row[3]}</p><button class="set-danger-button" data-step="ask">${row[1]}</button><span class="set-confirm" hidden><button class="set-confirm-button" data-step="confirm">${row[2]}</button><button class="set-cancel" data-step="cancel">Cancel</button></span></div>`;
}
function settingsPanelHtml(panel){
  if(panel.about)return `<div class="set-about"><div class="set-logo"><img src="../../../logo/woka-logo.svg" alt="Woka"><span>v1.0.9</span></div><p>A minimalist viewer and editor for the media already on your disk.</p><h3>Project &amp; community</h3><div class="set-row set-link"><span class="set-text"><b>GitHub Repository</b><span>Source code, release notes and issue tracker</span></span>${icon('chevronRight')}</div><h3>Support development</h3><p class="set-hint">Woka is free. If it saves you time, you can help keep it going.</p><div class="set-support"><button>GitHub Sponsors</button><button>Ko-fi</button><button>Buy Me a Coffee</button></div><h3>Credits</h3><p class="set-credits">Icons: Phosphor (MIT). Video: FFmpeg (LGPL) through JavaCV.</p></div>`;
  return panel.sections.map(([label,rows],index)=>`<section class="set-section"><h3>${label}</h3>${rows.map((row,at)=>settingsRow(row,`${panel.name}:${index}:${at}`)).join('')}</section>`).join('');
}
function renderSettings(){
  const panel=SETTINGS.find(each=>each.name===settingsPanel);
  $('#settings-overlay').innerHTML=`<div class="set-card" role="dialog" aria-label="Settings"><div class="set-head"><h2>Settings</h2><button class="set-close" data-set-close aria-label="Close">${icon('close')}</button></div><div class="set-body"><nav class="set-categories">${SETTINGS.map(each=>`<button class="${each.name===settingsPanel?'selected':''}" data-set-panel="${each.name}">${icon(each.icon)}<span>${each.name}</span></button>`).join('')}</nav><div class="set-panel">${settingsPanelHtml(panel)}</div></div></div>`;
}
function openSettings(){renderSettings();$('#settings-overlay').hidden=false;}
function closeSettings(){$('#settings-overlay').hidden=true;}
function initSettings(){
  const overlay=$('#settings-overlay');
  overlay.addEventListener('click',event=>{
    const t=event.target;
    if(t===overlay||t.closest('[data-set-close]')){closeSettings();return;}
    const panel=t.closest('[data-set-panel]');if(panel){settingsPanel=panel.dataset.setPanel;renderSettings();return;}
    const option=t.closest('[data-option]');
    if(option){const [name,section,row]=option.parentElement.dataset.set.split(':');SETTINGS.find(each=>each.name===name).sections[section][1][row][2]=option.dataset.option;renderSettings();return;}
    const step=t.closest('[data-step]');
    if(step){const box=step.closest('[data-set-danger]'),asking=step.dataset.step==='ask';
      box.querySelector('.set-hint').hidden=!asking||!box.querySelector('.set-hint').textContent;box.querySelector('.set-danger-button').hidden=asking;box.querySelector('.set-confirm').hidden=!asking;
      if(step.dataset.step==='confirm')toast('Done.');}
  });
  overlay.addEventListener('change',event=>{
    const key=event.target.dataset.set;if(!key)return;const [name,section,row]=key.split(':');
    SETTINGS.find(each=>each.name===name).sections[section][1][row][3]=event.target.checked;
  });
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!overlay.hidden){event.stopImmediatePropagation();closeSettings();}},true);
}
