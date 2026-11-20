(function(){
'use strict';
if(window.PLHost && window.PLHost.version==='0.3.0')return;
var state={open:false,query:'',category:'',sort:'seeders',data:[]};
var categories=[['','Все видео'],['1670','Эротика'],['1672','Фильмы'],['1717','DVD/HD'],['1674','Русское видео'],['1753','На русском'],['1677','Ролики'],['1800','Японское/китайское'],['1745','Анимация'],['11','Тематики'],['1688','Гей-видео']];
var css=document.createElement('style');css.id='pl-lampa-style';
css.textContent=[
'#pl-screen{display:none;position:fixed;inset:0;z-index:2147483600;background:#11121b;color:#fff;font-family:Arial,sans-serif;overflow:hidden;box-sizing:border-box}',
'#pl-screen *{box-sizing:border-box}',
'#pl-top{height:82px;display:flex;justify-content:space-between;align-items:center;padding:15px 32px;border-bottom:1px solid #303042}',
'.pl-logo{font-size:33px;font-weight:800;color:#d4b6ff}.pl-sub{font-size:14px;color:#a7a0bf;margin-left:18px}',
'.pl-action,.pl-filter,.pl-result{cursor:pointer;border:2px solid transparent;background:#262537;color:#fff;border-radius:14px}',
'.pl-action{font-size:18px;padding:14px 20px;white-space:nowrap}',
'#pl-searchbar{display:flex;gap:13px;align-items:center;padding:18px 32px}',
'#pl-query{font-size:20px;padding:15px 20px;border-radius:12px;border:2px solid #464159;outline:none;color:#fff;background:#232330;flex:1;min-width:100px}',
'#pl-cats{padding:0 32px 17px;display:flex;gap:10px;overflow-x:auto}',
'.pl-filter{white-space:nowrap;font-size:17px;padding:12px 16px}.pl-filter.pl-active{background:#6e43ae}',
'.pl-action:focus,.pl-filter:focus,.pl-result:focus,#pl-query:focus{outline:none!important;border-color:#edd6ff!important;box-shadow:0 0 0 3px #8f5bc5}',
'#pl-status{height:42px;padding:6px 32px;color:#b4aeca;font-size:17px}',
'#pl-scroll{overflow:auto;height:calc(100vh - 260px);padding:0 32px 40px}',
'#pl-results{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:17px}',
'.pl-result{min-height:160px;padding:17px 18px;text-align:left;display:flex;flex-direction:column;justify-content:space-between;word-break:break-word}',
'.pl-result-title{font-size:17px;line-height:1.38;max-height:100px;overflow:hidden}',
'.pl-result-meta{font-size:14px;color:#b0a8c8;margin-top:12px}',
'#pl-foot{position:absolute;bottom:0;left:0;right:0;height:26px;background:#11121b;color:#817891;font-size:12px;padding-left:32px}',
'@media(max-width:1150px){#pl-results{grid-template-columns:repeat(3,minmax(0,1fr));}}'
].join('');
document.head.appendChild(css);
var overlay=document.createElement('div');overlay.id='pl-screen';
overlay.innerHTML='<div id="pl-top"><div><span class="pl-logo">▶ PL</span><span class="pl-sub">Поиск по PornoLab через Jackett</span></div>'+
'<div style="display:flex;gap:12px"><button class="pl-action" id="pl-config">⚙ Настройки</button><button class="pl-action" id="pl-close">✕ Закрыть</button></div></div>'+
'<div id="pl-searchbar"><input id="pl-query" type="search" placeholder="Название раздачи"/>'+
'<button class="pl-action" id="pl-find">Найти</button><button class="pl-action" id="pl-order">Сортировка: сиды</button></div>'+
'<div id="pl-cats"></div><div id="pl-status">Выбери категорию или введи запрос.</div>'+
'<div id="pl-scroll"><div id="pl-results"></div></div>'+
'<div id="pl-foot">PL 0.3.0 · Android TV · Lampa · Jackett · TorrServer</div>';
document.body.appendChild(overlay);
var query=overlay.querySelector('#pl-query'),results=overlay.querySelector('#pl-results');
var status=overlay.querySelector('#pl-status'),cats=overlay.querySelector('#pl-cats');
function nativeCall(name,a,b){
  if(typeof window.PLNative==='undefined'){status.textContent='Требуется Android APK PL с поддержкой Jackett.';return;}
  try{if(arguments.length===3)PLNative[name](a,b);else if(arguments.length===2)PLNative[name](a);else PLNative[name]();}
  catch(e){status.textContent='Ошибка PL: '+e.message;}
}
function find(){state.query=query.value.trim();status.textContent='Ищем на PornoLab…';results.textContent='';nativeCall('search',state.query,parseInt(state.category||'0',10));}
function showCategory(id){
  state.category=id;
  Array.prototype.forEach.call(cats.children,function(el){el.classList.toggle('pl-active',el.getAttribute('data-cat')===id);});
  find();
}
categories.forEach(function(cat){
  var b=document.createElement('button');b.className='pl-filter'+(cat[0]===''?' pl-active':'');
  b.setAttribute('data-cat',cat[0]);b.textContent=cat[1];b.onclick=function(){showCategory(cat[0]);};cats.appendChild(b);
});
query.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();e.stopPropagation();find();}});
overlay.querySelector('#pl-find').onclick=find;
overlay.querySelector('#pl-config').onclick=function(){nativeCall('settings');};
overlay.querySelector('#pl-close').onclick=function(){close();};
overlay.querySelector('#pl-order').onclick=function(){
  state.sort=state.sort==='seeders'?'size':state.sort==='size'?'title':'seeders';
  this.textContent='Сортировка: '+(state.sort==='seeders'?'сиды':state.sort==='size'?'размер':'название');draw();
};
function draw(){
  results.textContent='';var arr=state.data.slice();
  arr.sort(function(a,b){
    if(state.sort==='seeders')return (b.seeders||0)-(a.seeders||0);
    if(state.sort==='size')return (b.size||0)-(a.size||0);
    return String(a.title||'').localeCompare(String(b.title||''),'ru');
  });
  arr.forEach(function(r){
    var b=document.createElement('button');b.className='pl-result';
    var t=document.createElement('span');t.className='pl-result-title';t.textContent=r.title||'Без названия';
    var meta=document.createElement('span');meta.className='pl-result-meta';
    var gb=(r.size||0)/1073741824;meta.textContent=(gb?gb.toFixed(gb>=10?0:1)+' ГБ · ':'')+'Сиды: '+(r.seeders||0);
    b.appendChild(t);b.appendChild(meta);
    b.onclick=function(){status.textContent='Передаём раздачу TorrServer…';nativeCall('play',r.id);};
    results.appendChild(b);
  });
}
function open(){
  state.open=true;overlay.style.display='block';nativeCall('overlay',true);
  overlay.querySelector('#pl-find').focus();if(!state.data.length)find();
}
function close(){
  state.open=false;overlay.style.display='none';nativeCall('overlay',false);
  var launcher=document.getElementById('pl-menu-link');if(launcher)launcher.focus();
}
function bindMenu(){
  var menu=document.querySelector('.menu__list');if(!menu)return false;
  if(document.getElementById('pl-menu-link'))return true;
  var li=document.createElement('li');li.id='pl-menu-link';li.className='menu__item selector';li.tabIndex=0;
  li.innerHTML='<div class="menu__ico">▶</div><div class="menu__text">PL · PornoLab</div>';
  li.addEventListener('click',open);li.addEventListener('hover:enter',open);
  li.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();open();}});
  menu.insertBefore(li,menu.firstChild);return true;
}
function focusInDirection(key){
  var nodes=Array.prototype.slice.call(overlay.querySelectorAll('button,input'));
  var active=document.activeElement,idx=nodes.indexOf(active);if(idx===-1){nodes[0].focus();return;}
  var curr=active.getBoundingClientRect(),x=curr.left+curr.width/2,y=curr.top+curr.height/2,target=null,best=Infinity;
  nodes.forEach(function(el){
    if(el===active||el.offsetParent===null)return;
    var r=el.getBoundingClientRect(),xx=r.left+r.width/2,yy=r.top+r.height/2,dx=xx-x,dy=yy-y;
    if(key==='ArrowRight'&&dx<=0||key==='ArrowLeft'&&dx>=0||key==='ArrowDown'&&dy<=0||key==='ArrowUp'&&dy>=0)return;
    var primary=key==='ArrowLeft'||key==='ArrowRight'?Math.abs(dx):Math.abs(dy);
    var cross=key==='ArrowLeft'||key==='ArrowRight'?Math.abs(dy):Math.abs(dx);
    var cost=primary+cross*2.5;if(cost<best){best=cost;target=el;}
  });
  if(target){target.focus();target.scrollIntoView({block:'nearest',inline:'nearest'});}
}
document.addEventListener('keydown',function(e){
  if(!state.open)return;
  if(e.key==='Escape'||e.key==='BrowserBack'||(e.key==='Backspace'&&document.activeElement!==query)){
    e.preventDefault();e.stopImmediatePropagation();close();return;
  }
  if(['ArrowRight','ArrowLeft','ArrowUp','ArrowDown'].indexOf(e.key)!==-1&&document.activeElement!==query){
    e.preventDefault();e.stopImmediatePropagation();focusInDirection(e.key);return;
  }
  if((e.key==='Enter'||e.key===' ')&&document.activeElement!==query){
    var el=document.activeElement;
    if(el&&el.tagName==='BUTTON'){e.preventDefault();e.stopImmediatePropagation();el.click();}
  }
},true);
window.PLHost={version:'0.3.0',open:open,close:close,
  results:function(json){
    try{var data=JSON.parse(json);state.data=data.items||[];
      status.textContent=data.message||('Найдено раздач: '+state.data.length);draw();
      var first=results.querySelector('button');if(first)first.focus();
    }catch(e){status.textContent='Ошибка ответа: '+e.message;}
  },
  error:function(message){status.textContent='Ошибка: '+message;},
  ready:function(){bindMenu();}
};
bindMenu();
var attempts=0,check=setInterval(function(){if(bindMenu()||++attempts>200)clearInterval(check);},350);
if(window.Lampa&&window.Lampa.Listener&&window.Lampa.Listener.follow){
  try{window.Lampa.Listener.follow('app',function(e){if(e.type==='ready')bindMenu();});}catch(e){}
}
setTimeout(open,1200);
})();