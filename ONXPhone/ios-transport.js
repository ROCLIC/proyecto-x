(() => {
  'use strict';
  if (location.protocol !== 'https:' || location.hostname !== 'game-fivem-ui-es.onx.gg' ||
      (location.port !== '' && location.port !== '443') || !window.webkit?.messageHandlers?.onx) return;
  if (window.__onxNativeChannel) {
    window.__onxNativeChannel.port1.close();
    window.__onxNativeChannel.port2.close();
  }
  const channel = new MessageChannel();
  window.__onxNativeChannel = channel;
  channel.port1.onmessage = event => {
    if (typeof event.data === 'string' && event.data.length <= 8192)
      window.webkit.messageHandlers.onx.postMessage(event.data);
  };
  window.__onxNativeAction = payload => {
    if (typeof payload === 'string' && payload.length <= 8192) channel.port1.postMessage(payload);
  };
  window.dispatchEvent(new MessageEvent('message', {data: 'rp-phone-connect', ports: [channel.port2]}));
})();
