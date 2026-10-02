(() => {
  'use strict';
  if (window.top !== window || window.__rpPhoneInstalled) return;
  window.__rpPhoneInstalled = true;
  let viewport = document.querySelector('meta[name="viewport"]');
  if (!viewport) { viewport = document.createElement('meta'); viewport.name = 'viewport'; document.head.appendChild(viewport); }
  viewport.content = 'width=device-width, initial-scale=1, viewport-fit=cover';
  let port = null, active = null, first = true, seq = 0, scheduled = false;
  const seen = new WeakSet();
  const ids = new WeakMap();
  const text = e => (e?.textContent || '').trim();
  const visible = e => {
    for (let p = e; p && p !== document.body; p = p.parentElement) {
      const s = getComputedStyle(p);
      if (s.display === 'none' || s.visibility === 'hidden' || (s.opacity!=='' && Number(s.opacity)<=0.01)) return false;
    }
    return true;
  };
  const send = o => { if (port) port.postMessage(JSON.stringify(o)); };
  function clickable(e) {
    if (!e || e.disabled || !visible(e)) return false;
    for (let p=e; p && p!==document.body; p=p.parentElement) {
      const s=getComputedStyle(p);
      if (s.opacity!=='' && Number(s.opacity)<=0.01) return false;
    }
    if (getComputedStyle(e).pointerEvents==='none') return false;
    const r=e.getBoundingClientRect();
    return !(r.width>0 && r.height>0 && (r.right<=0 || r.bottom<=0 || r.left>=innerWidth || r.top>=innerHeight));
  }
  window.__onxPhoneBack = () => {
    // ONX's internal screens do not require browser history navigation.
    // Prefer their actual back button, then the status-bar Home control.
    const backs=[...document.querySelectorAll('i.fa-arrow-left')].map(e=>e.closest('button,[role="button"],[tabindex]')).filter(clickable);
    if (backs.length) { backs[backs.length-1].click(); return true; }
    const icon=[...document.querySelectorAll('i.fa-house')].find(visible);
    const home=icon?.closest('.MuiGrid-container');
    if (clickable(home)) { home.click(); return true; }
    return false;
  };
  function rows() {
    return [...document.querySelectorAll('div[style*="border-top"]')].filter(e =>
      e.style.position === 'relative' && e.style.alignItems === 'flex-start' &&
      e.querySelector('img[src*="/phone/apps/"]') && e.querySelector('p') && visible(e));
  }
  function callRow(row) {
    return [...row.querySelectorAll('span')].some(e => /Llamada entrante|Incoming call/i.test(text(e))) &&
      !!row.querySelector('.fa-phone-slash') && !!row.querySelector('.fa-phone');
  }
  function inspect() {
    scheduled = false;
    const cards = rows();
    let current = null;
    for (const row of cards) {
      if (callRow(row)) {
        if (!ids.has(row)) ids.set(row, 'call-' + Date.now() + '-' + (++seq));
        current = { row, id: ids.get(row), name: text(row.querySelector('p')) || 'Llamada de FiveM' };
      } else if (!seen.has(row) && !first) {
        send({type:'notification', title:text(row.querySelector('p')).slice(0,160),
          body:[...row.querySelectorAll('span')].map(text).filter(Boolean).join(' ').slice(0,500),
          kind:(row.querySelector('img[src*="/phone/apps/"]')?.getAttribute('src')||'').split('/').pop().replace('.png','')});
      }
      seen.add(row);
    }
    if (current && (!active || active.id !== current.id)) {
      if (active) send({type:'callEnd', id:active.id});
      active = current;
      send({type:'call', id:active.id, name:active.name.slice(0,160)});
    } else if (!current && active) {
      send({type:'callEnd',id:active.id}); active = null;
    }
    first = false;
  }
  function fit() {
    // This container was verified in the ONX phone DOM. Auth pages are untouched.
    const frame = [...document.querySelectorAll('div[style*="pointer-events"]')].find(e =>
      e.style.position === 'absolute' && e.style.minHeight === '648px' && e.style.minWidth === '302.4px');
    if (!frame) return;
    const width = window.visualViewport?.width || innerWidth;
    const height = window.visualViewport?.height || innerHeight;
    if (width <= 0 || height <= 0) return;
    const scale = Math.min(width / 302.4, height / 648);
    const desired = `position:absolute;pointer-events:auto;top:50%;left:50%;right:auto;width:302.4px;height:648px;min-width:302.4px;min-height:648px;transform:translate(-50%,-50%) scale(${scale});transform-origin:center center;`;
    if (frame.style.cssText !== desired) {
      // Browser normalizes cssText; compare individual geometry to avoid observer loops.
      if (frame.style.left !== '50%' || frame.dataset.rpScale !== String(scale)) {
        frame.style.cssText = desired; frame.dataset.rpScale = String(scale);
      }
    }
  }
  function status() {
    const body = text(document.body);
    const ready = document.title.includes('Phone') && !!document.querySelector('img[src*="/phone/apps/"]');
    const expired=!ready && /LOGIN REQUIRED|TOKEN EXPIRED|INVALID TOKEN|LINK EXPIRED|enlace (?:ha )?caducado/i.test(body);
    const media=[...document.querySelectorAll('audio,video')].some(e=>e.srcObject?.getTracks?.().some(t=>t.readyState==='live' && t.enabled));
    send({type:'status', value:!navigator.onLine ? 'offline' : expired ? 'login' : ready ? 'ready' : 'loading',media});
  }
  window.__onxPhonePoll=()=>{inspect();fit();status();};
  window.addEventListener('message', e => {
    if (e.data !== 'rp-phone-connect' || !e.ports?.length) return;
    if (port) port.close(); port = e.ports[0];
    port.onmessage = e => {
      try {
        const cmd = JSON.parse(e.data);
        if (cmd.type === 'action' && active && cmd.id === active.id) {
          const icon = active.row.querySelector(cmd.action === 'answer' ? '.fa-phone' : cmd.action === 'reject' ? '.fa-phone-slash' : 'rp-invalid');
          const control = icon?.closest('[tabindex="0"]');
          if (control && visible(active.row)) {
            control.click(); send({type:'actionResult',id:cmd.id,ok:true});
          } else send({type:'actionResult',id:cmd.id,ok:false});
        } else if (cmd.type === 'action') send({type:'actionResult',id:cmd.id,ok:false});
      } catch (_) {}
    };
    port.start();
    // Re-send an existing call when native reconnects to this page.
    active = null; inspect(); fit(); status();
  });
  new MutationObserver(() => {
    if (!scheduled) { scheduled = true; setTimeout(() => { inspect(); fit(); }, 150); }
  }).observe(document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['style','class']});
  window.addEventListener('resize',fit);
  window.visualViewport?.addEventListener('resize',fit);
  window.addEventListener('online',status); window.addEventListener('offline',status);
  setInterval(() => { inspect(); fit(); status(); }, 10000);
})();
