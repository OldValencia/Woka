/* Tactile instruments: a selection pill that slides between options, and the straighten dial. */
const indicatorMemory=new Map();
const DIAL_STEP=8;
function syncIndicators(){
  document.querySelectorAll('[data-indicator]').forEach(group=>{
    let pill=group.querySelector(':scope > .indicator');
    if(!pill){pill=document.createElement('span');pill.className='indicator';pill.setAttribute('aria-hidden','true');group.prepend(pill);}
    const selected=group.querySelector(':scope > button.selected');
    if(!selected||!group.offsetWidth){pill.style.opacity='0';return;}
    const key=group.dataset.indicator,next={x:selected.offsetLeft,y:selected.offsetTop,w:selected.offsetWidth,h:selected.offsetHeight},previous=indicatorMemory.get(key);
    indicatorMemory.set(key,next);
    const place=box=>Object.assign(pill.style,{transform:`translate(${box.x}px,${box.y}px)`,width:`${box.w}px`,height:`${box.h}px`,opacity:'1'});
    pill.style.transition='none';place(previous&&!reducedMotion()?previous:next);pill.getBoundingClientRect();pill.style.transition='';place(next);
  });
}
function paintDial(dial,value){
  dial.querySelector('.dial-scale').style.setProperty('--v',value);
  dial.querySelector('.dial-readout').textContent=`${value.toFixed(1)}°`;
  dial.setAttribute('aria-valuenow',value);
  const zero=value===0;
  if(zero&&!dial.classList.contains('at-zero')&&!reducedMotion())dial.querySelector('.dial-needle').animate([{transform:'scaleY(1)'},{transform:'scaleY(1.35)'},{transform:'scaleY(1)'}],{duration:240,easing:'ease-out'});
  dial.classList.toggle('at-zero',zero);
}
function dialValue(raw){const value=Math.round(clamp(raw,-45,45)*10)/10;return Math.abs(value)<.9?0:value;}
function straightening(on){document.querySelector('#picture').classList.toggle('straightening',on);}
function initInstruments(){
  const panel=document.querySelector('#panel');
  panel.addEventListener('pointerdown',event=>{
    const dial=event.target.closest('[data-dial]');if(!dial||event.button!==0)return;
    event.preventDefault();dial.focus({preventScroll:true});dial.setPointerCapture(event.pointerId);
    const before=JSON.stringify(state),startX=event.clientX,start=state.straighten||0;let changed=false;
    dial.classList.add('dragging');straightening(true);
    const move=e=>{const value=dialValue(start-(e.clientX-startX)/DIAL_STEP);if(value===(state.straighten||0))return;changed=true;setValue('straighten',value);paintDial(dial,value);};
    const up=()=>{dial.removeEventListener('pointermove',move);dial.classList.remove('dragging');straightening(false);if(changed){remember(before);state.dirty=true;}render();};
    dial.addEventListener('pointermove',move);dial.addEventListener('pointerup',up,{once:true});dial.addEventListener('pointercancel',up,{once:true});
  });
  panel.addEventListener('dblclick',event=>{const dial=event.target.closest('[data-dial]');if(dial){easeCrop();commit(()=>setValue('straighten',0));}});
  panel.addEventListener('keydown',event=>{
    const dial=event.target.closest('[data-dial]');if(!dial)return;
    const step=event.shiftKey?5:.5,current=state.straighten||0;
    const next=event.key==='ArrowRight'?current+step:event.key==='ArrowLeft'?current-step:event.key==='Home'?0:null;
    if(next===null)return;event.preventDefault();event.stopPropagation();commit(()=>setValue('straighten',Math.round(clamp(next,-45,45)*10)/10));
    document.querySelector('[data-dial]')?.focus({preventScroll:true});
  });
  addEventListener('resize',()=>{indicatorMemory.clear();syncIndicators();});
}
