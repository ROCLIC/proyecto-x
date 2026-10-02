const {JSDOM}=require('jsdom');
const {MessageChannel}=require('node:worker_threads');
const fs=require('node:fs');
const assert=require('node:assert/strict');
const source=fs.readFileSync(require('node:path').resolve(__dirname,'../ONXPhone/phone-bridge.js'),'utf8');
const transport=fs.readFileSync(require('node:path').resolve(__dirname,'../ONXPhone/ios-transport.js'),'utf8');
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function run(){
 const dom=new JSDOM('<!doctype html><html><head><title>ONX Phone</title></head><body><img src="https://static.onx.gg/phone/apps/calls.png"><div id="cards"></div></body></html>',{url:'https://game-fivem-ui-es.onx.gg/',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,events=[];w.MessageChannel=MessageChannel;
 w.webkit={messageHandlers:{onx:{postMessage:s=>events.push(JSON.parse(s))}}};
 try{
  w.eval(source);w.eval(transport);await sleep(80);
  assert.equal(events.find(e=>e.type==='status').value,'ready');
  w.document.querySelector('#cards').innerHTML='<div style="position:relative;display:flex;align-items:flex-start;border-top:1px solid transparent"><img src="https://static.onx.gg/phone/apps/calls.png"><p>Contacto de prueba</p><span>Llamada entrante...</span><div tabindex="0" id="answer"><i class="fa-phone"></i></div><div tabindex="0" id="reject"><i class="fa-phone-slash"></i></div></div>';
  let answered=0,rejected=0;w.document.querySelector('#answer').onclick=()=>answered++;w.document.querySelector('#reject').onclick=()=>rejected++;
  await sleep(250);const call=events.find(e=>e.type==='call');assert.ok(call);
  w.__onxNativeAction(JSON.stringify({type:'action',action:'answer',id:'expired-id'}));await sleep(60);assert.equal(answered,0);
  w.__onxNativeAction(JSON.stringify({type:'action',action:'answer',id:call.id}));await sleep(60);assert.equal(answered,1);assert.ok(events.some(e=>e.type==='actionResult'&&e.ok));
  w.__onxNativeAction(JSON.stringify({type:'action',action:'reject',id:call.id}));await sleep(60);assert.equal(rejected,1);
  w.document.querySelector('#cards').innerHTML='';await sleep(250);assert.ok(events.some(e=>e.type==='callEnd'&&e.id===call.id));
  const old=w.__onxNativeChannel;w.eval(transport);assert.notEqual(w.__onxNativeChannel,old);await sleep(60);
 }finally{w.__onxNativeChannel?.port1.close();w.__onxNativeChannel?.port2.close();w.close();}
 for(const url of ['https://auth.onx.gg/','http://game-fivem-ui-es.onx.gg/','https://game-fivem-ui-es.onx.gg:444/']){
  const other=new JSDOM('',{url,runScripts:'outside-only'});other.window.MessageChannel=MessageChannel;other.window.webkit={messageHandlers:{onx:{postMessage(){throw Error('unexpected')}}}};other.window.eval(transport);assert.equal(other.window.__onxNativeChannel,undefined);other.window.close();
 }
 console.log('PASS: iOS transport over real MessagePorts, status/call/end, answer/reject, stale call rejection, reconnect and forbidden origins. This does not execute WKWebView.');
}
run().catch(e=>{console.error(e);process.exit(1)});
