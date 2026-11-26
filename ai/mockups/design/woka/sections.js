/* The prototype bar: which part of the app is on screen, and a link to every scene, #section/scene.
   The bar names a few scenes per section; a link may name any scene the section can show. */
const SECTIONS=[
  {id:'library',label:'Library',scenes:[['pictures','Photo folder'],['movies','Video folder'],['series','Series'],['empty','Empty folder'],['select','Selecting'],['search','Search'],['info','Info'],['quicklook','Quick Look'],['settings','Settings'],['welcome','First run']]},
  {id:'viewer',label:'Viewer',scenes:[['photo','Photo'],['palm','Edited photo'],['frames','Frame set'],['gif','GIF'],['video','Video'],['fullscreen','Fullscreen'],['pip','PiP'],['track','Track']]},
  {id:'editor',label:'Editor',scenes:[['photo','Photo'],['crop','Portrait crop'],['frames','Frame set'],['gif','GIF'],['video','Video'],['audio','Video + music · 1.1.0']]},
  {id:'dialogs',label:'Dialogs',scenes:[['info','File information'],['rename','Rename'],['copy','Create a copy'],['done','Save changes'],['keep','Keep changes'],['revert','Revert']]}
];
let route={section:'viewer',scene:'photo'};
/* Fullscreen and PiP are the video in another window, not another file. */
const WINDOW_SCENES=['fullscreen','pip'];
function canShow(section,scene){
  if(section==='library'&&FOLDERS[scene])return true;
  if(section==='viewer')return WINDOW_SCENES.includes(scene)||libraryMedia.some(item=>item.id===scene);
  return SECTIONS.find(entry=>entry.id===section)?.scenes.some(([id])=>id===scene)??false;
}
function renderPrototypeNav(){
  const current=SECTIONS.find(entry=>entry.id===route.section);
  const sections=SECTIONS.map(entry=>entry.later
    ?`<button data-section="${entry.id}" aria-disabled="true" data-tip="Comes with ${entry.later}">${entry.label}</button>`
    :`<button data-section="${entry.id}" class="${entry.id===route.section?'selected':''}" aria-pressed="${entry.id===route.section}">${entry.label}</button>`).join('');
  const scenes=current.scenes.map(([id,label])=>`<button data-route="${current.id}/${id}" class="${id===route.scene?'selected':''}" aria-pressed="${id===route.scene}">${label}</button>`).join('');
  $('#prototype-nav').innerHTML=`${sections}<span class="divider"></span>${scenes}`;
}
/* history.replaceState throws on a file:// page (its origin is opaque), so the hash is replaced as a
   fragment navigation; the hashchange it raises finds the route already there and stops. */
const routeHash=()=>`#${route.section}/${encodeURIComponent(route.scene)}`;
function writeRoute(){renderPrototypeNav();if(location.hash!==routeHash())location.replace(routeHash());}
function go(section,scene){
  const entry=SECTIONS.find(item=>item.id===section&&!item.later);
  if(!entry)return false;
  if(!canShow(section,scene))scene=entry.scenes[0][0];
  if($('#dialog').open)$('#dialog').close();
  closeSettings();libraryEmpty=section==='library'&&scene==='welcome';
  if(section==='library')lib.info=scene==='info';
  if(section==='library')(LIBRARY_SCENES[scene]??(()=>showLibrary(scene)))();
  else if(section==='dialogs')DIALOG_SCENES[scene]();
  else if(section==='viewer'&&WINDOW_SCENES.includes(scene)){openMedia('video');setWindowMode(scene);}
  else if(section==='viewer'){openMedia(scene);setWindowMode('window');}
  else setScene(scene);
  route={section,scene};writeRoute();
  return true;
}
function readHash(){
  if(location.hash===routeHash()&&$('#prototype-nav').childElementCount)return true;
  const [section,scene]=location.hash.slice(1).split('/');return go(section,decodeURIComponent(scene||''));
}
/* The prototype's own controls move between scenes too — Edit, Cancel, Done, the carousel — and so
   can a script. Every one of them goes through setScene or setViewer, so the bar follows those two.
   It looks once the call stack has settled: openMedia is setScene (editor) and then setViewer
   (viewer), and the step in between is not a place the user ever was. */
let followQueued=false;
function followApp(){
  followQueued=false;
  // A Dialogs scene is the dialog over its screen; the bar follows the screen once it closes.
  if(route.section==='dialogs'&&$('#dialog').open)return;
  // The library: the bar names the folder on screen, or keeps the Library scene that shows it.
  if(libraryShown()){
    if(route.section==='library'&&(LIBRARY_FOLDER[route.scene]??route.scene)===activeFolder)return;
    route={section:'library',scene:Object.keys(LIBRARY_FOLDER).find(key=>LIBRARY_FOLDER[key]===activeFolder)??activeFolder};writeRoute();return;
  }
  const section=viewerMode?'viewer':'editor',scene=viewerMode&&windowMode!=='window'?windowMode:currentScene;
  if(!canShow(section,scene)||(route.section===section&&route.scene===scene))return;
  route={section,scene};writeRoute();
}
function initSections(){
  for(const name of ['setScene','setViewer','setWindowMode','showLibrary']){
    const original=window[name];
    window[name]=function(...args){const result=original.apply(this,args);if(!followQueued){followQueued=true;setTimeout(followApp,0);}return result;};
  }
  document.addEventListener('click',event=>{
    const section=event.target.closest('[data-section]');
    if(section){
      if(section.getAttribute('aria-disabled')==='true')return;
      const entry=SECTIONS.find(item=>item.id===section.dataset.section);
      go(entry.id,canShow(entry.id,route.scene)?route.scene:entry.scenes[0][0]);return;
    }
    const link=event.target.closest('[data-route]');
    if(link){const [target,scene]=link.dataset.route.split('/');go(target,scene);}
  });
  $('#dialog').addEventListener('close',()=>setTimeout(followApp,0));
  const followHash=()=>{if(!readHash())writeRoute();};
  addEventListener('hashchange',followHash);
  followHash();
}
initSections();
