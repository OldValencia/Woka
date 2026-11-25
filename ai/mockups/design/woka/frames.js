/* The frame strip: the pictures inside one file — a burst, a GIF's frames, later a PDF's pages.
   It stands on the left of the canvas, in the same place in the viewer and the editor, so entering
   the editor does not move it; the bottom of the window is the folder's, not the file's. */
function renderFrameStrip(){
  const column=$('#frame-column'),count=state.frames?.length||0,show=count>1;
  column.hidden=!show;$('.work-area').classList.toggle('has-frames',show);
  if(!show){column.replaceChildren();return;}
  const item=currentMedia();
  column.innerHTML=state.frames.map((frame,index)=>`<button class="frame-thumb ${index===state.frameIndex?'selected':''}" data-action="frame" data-frame="${index}" aria-label="Frame ${index+1}${Object.keys(frame.settings).length?', edited':''}" aria-current="${index===state.frameIndex}">${photoSvg(stillFrameView(item,index))}${Object.keys(frame.settings).length?'<i class="frame-dot" aria-hidden="true"></i>':''}</button>`).join('');
  syncFrameStrip();
}
function syncFrameStrip(){
  const column=$('#frame-column');if(column.hidden)return;
  let selected=null;
  column.querySelectorAll('.frame-thumb').forEach(thumb=>{const on=+thumb.dataset.frame===state.frameIndex;thumb.classList.toggle('selected',on);thumb.setAttribute('aria-current',on);if(on)selected=thumb;});
  if(!selected)return;
  // Scrolled by hand: scrollIntoView would also scroll every ancestor, the page included.
  const top=selected.offsetTop-column.clientHeight/2+selected.offsetHeight/2;
  column.scrollTop=clamp(top,0,column.scrollHeight-column.clientHeight);
}
