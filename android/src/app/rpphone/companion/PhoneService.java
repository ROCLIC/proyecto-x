package app.rpphone.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.net.*;
import android.provider.Settings;
import android.view.*;
import android.widget.FrameLayout;
import android.os.*;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;

public final class PhoneService extends Service {
    public static PhoneService instance;
    static final String HOST = "game-fivem-ui-es.onx.gg";
    static final int CONNECTION = 10, CALL = 20;
    final Handler main = new Handler(Looper.getMainLooper());
    WebView web; MutableContextWrapper webContext; WebMessagePort port;
    WeakReference<MainActivity> activity = new WeakReference<>(null);
    NotificationManager notifications; Ringtone ringtone;
    PowerManager.WakeLock wake;
    String callId = "", caller = "", status = "Conectando…";
    long heartbeat = 0, callAt = 0;
    int nextNotice = 100; boolean connected = false, microphone = false, camera = false;
    PermissionRequest permissionRequest;
    ValueCallback<Uri[]> uploadCallback;
    private int generation = 0;
    final ReconnectPolicy recovery=new ReconnectPolicy();
    ConnectivityManager networkManager; ConnectivityManager.NetworkCallback networkCallback;
    boolean networkUp=false, activeMedia=false, authPage=false, sessionNotice=false, destroying=false;
    long loadingAt=0;
    Runnable reconnectTask;
    boolean reconnectOnReturn=false;
    FrameLayout backgroundHost;
    Vibrator vibrator;
    Runnable demoTask;
    String shownCallId="";
    int priorAudioMode=-1; boolean priorSpeaker=false;

    @Override public void onCreate() {
        super.onCreate(); instance = this;
        notifications = getSystemService(NotificationManager.class);
        NotificationChannel connection = new NotificationChannel("connection", "Conexión al celular", NotificationManager.IMPORTANCE_LOW);
        connection.setDescription("Mantiene el celular web activo mientras recibes avisos.");
        notifications.createNotificationChannel(connection);
        NotificationChannel calls = new NotificationChannel("calls-v3", "Llamadas de FiveM", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Avisos de llamadas entrantes del personaje"); calls.setSound(null,null);
        calls.enableVibration(false); calls.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notifications.createNotificationChannel(calls);
        NotificationChannel messages = new NotificationChannel("messages", "Mensajes y avisos", NotificationManager.IMPORTANCE_DEFAULT);
        notifications.createNotificationChannel(messages);
        NotificationChannel silent=new NotificationChannel("messages-silent","Avisos silenciosos",NotificationManager.IMPORTANCE_LOW);
        silent.setSound(null,null);silent.enableVibration(false);notifications.createNotificationChannel(silent);
        foreground();
        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RPPhone:connection");
        wake.setReferenceCounted(false); wake.acquire(600000);
        vibrator=getSystemService(Vibrator.class);
        createWeb();
        networkManager=getSystemService(ConnectivityManager.class);
        updateNetwork();
        networkCallback=new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network n){main.post(()->networkChanged());}
            @Override public void onLost(Network n){main.post(()->networkChanged());}
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){main.post(()->networkChanged());}
        };
        networkManager.registerDefaultNetworkCallback(networkCallback);
        main.postDelayed(watchdog,5000);
    }
    private final Runnable watchdog = new Runnable() {
        public void run() {
            if (instance != PhoneService.this || destroying) return;
            if (!wake.isHeld()) wake.acquire(600000);
            networkChanged();
            if(Build.VERSION.SDK_INT<28 && ringtone!=null && !ringtone.isPlaying())try{ringtone.play();}catch(RuntimeException ignored){}
            if(reconnectOnReturn && !activeMedia && callId.isEmpty())scheduleReconnect();
            if(networkUp && !authPage && web!=null && phoneOrigin(Uri.parse(web.getUrl()==null?"":web.getUrl())))
                web.evaluateJavascript("window.__onxPhonePoll && window.__onxPhonePoll()",null);
            if(!networkUp){connected=false;setStatus("Sin internet");}
            if (networkUp && !authPage && !recovery.login && heartbeat > 0 && SystemClock.elapsedRealtime() - heartbeat > 45000) {
                connected = false; setStatus("Sin señal · intentando reconectar");
                if(!activeMedia && callId.isEmpty())scheduleReconnect();
            }
            if(!connected && loadingAt>0 && SystemClock.elapsedRealtime()-loadingAt>45000 && !authPage)scheduleReconnect();
            if (!callId.isEmpty() && SystemClock.elapsedRealtime() - callAt > 90000) endCall();
            main.postDelayed(this,5000);
        }
    };
    void updateNetwork(){NetworkCapabilities c=networkManager.getNetworkCapabilities(networkManager.getActiveNetwork());networkUp=c!=null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);}
    void networkChanged(){
        if(destroying)return;boolean before=networkUp;updateNetwork();
        if(!networkUp){connected=false;setStatus("Sin internet");}
        else if(!before){recovery.next=0;reconnectOnReturn=true;if(!recovery.login && !authPage)scheduleReconnect();}
    }
    void scheduleReconnect(){
        long now=SystemClock.elapsedRealtime();
        if(reconnectTask!=null || !recovery.allowed(now,networkUp,!callId.isEmpty()||activeMedia,authPage))return;
        recovery.next=now+recovery.delay();
        reconnectTask=()->{
            reconnectTask=null;
            if(destroying || !networkUp || authPage || recovery.login || activeMedia || !callId.isEmpty())return;
            String url=Vault.read(this);if(url.isEmpty())return;
            reconnectOnReturn=false;
            setStatus("Reconectando…");web.loadUrl(url);
        };
        main.postDelayed(reconnectTask,Math.max(0,recovery.next-now));
    }
    void cancelReconnect(){if(reconnectTask!=null){main.removeCallbacks(reconnectTask);reconnectTask=null;}}
    void reload(){cancelReconnect();reconnectOnReturn=false;recovery.ready();sessionNotice=false;notifications.cancel(30);web.loadUrl(Vault.read(this));}
    void requireLogin(){
        cancelReconnect();recovery.login=true;connected=false;setStatus("Sesión vencida · inicia sesión");
        if(!sessionNotice){sessionNotice=true;notifications.notify(30,new Notification.Builder(this,"messages").setSmallIcon(R.drawable.ic_notification).setContentTitle("ONX phone · inicia sesión").setContentText("Abre tu celular para renovar el acceso o actualizar el enlace.").setContentIntent(openIntent("","",30)).setAutoCancel(true).build());}
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        String link = Vault.read(this);
        if (link.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
        if (web.getUrl() == null || (intent != null && intent.getBooleanExtra("reload",false))) reload();
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
    void foreground() {
        Notification n = new Notification.Builder(this,"connection")
            .setSmallIcon(app.rpphone.companion.R.drawable.ic_notification).setContentTitle("ONX phone · " + status)
            .setContentText("Toca para abrir el celular · Detener apaga la conexión").setOngoing(true)
            .setContentIntent(openIntent("", "", 1)).setShowWhen(false)
            .addAction(new Notification.Action.Builder(null,"Detener",broadcast("stop","",2)).build()).build();
        int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
        if (microphone) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        if (camera) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        if (Build.VERSION.SDK_INT >= 34) startForeground(CONNECTION,n,types);
        else if (Build.VERSION.SDK_INT >= 30) startForeground(CONNECTION,n,(microphone ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : 0) | (camera ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA : 0));
        else startForeground(CONNECTION,n);
    }
    void setStatus(String value) {
        if (value.equals(status)) return;
        status = value; foreground();
        MainActivity a = activity.get(); if (a != null) a.updateStatus();
    }
    static boolean phoneOrigin(Uri u) { return u != null && "https".equalsIgnoreCase(u.getScheme()) && HOST.equalsIgnoreCase(u.getHost()) && (u.getPort()==-1 || u.getPort()==443); }
    static boolean allowedNavigation(Uri u) {
        if (!"https".equalsIgnoreCase(u.getScheme()) || (u.getPort()!=-1 && u.getPort()!=443)) return false;
        String h = u.getHost();
        return HOST.equalsIgnoreCase(h) || "auth.onx.gg".equalsIgnoreCase(h) || "onx.gg".equalsIgnoreCase(h) || "steamcommunity.com".equalsIgnoreCase(h) || "store.steampowered.com".equalsIgnoreCase(h);
    }
    private void createWeb() {
        webContext = new MutableContextWrapper(this); web = new WebView(webContext);
        web.setBackgroundColor(PhoneStyle.BG);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false); settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(true);
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT,false);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                if (allowedNavigation(r.getUrl())) return false;
                MainActivity a = activity.get();
                // Never forward the character token or an arbitrary URL to another app.
                if (a != null) Toast.makeText(a,"Enlace externo bloqueado. Usa el navegador para otras páginas.",Toast.LENGTH_LONG).show();
                return true;
            }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                cancelReconnect();
                closePort(); connected=false; heartbeat=0; endCall();
                loadingAt=SystemClock.elapsedRealtime();activeMedia=false;authPage=!phoneOrigin(Uri.parse(url));
                setStatus(authPage ? "Inicia sesión en ONX / Steam" : networkUp?"Reconectando…":"Sin internet");
            }
            @Override public void onPageFinished(WebView v,String url) {
                CookieManager.getInstance().flush();
                if (!phoneOrigin(Uri.parse(url))) return;
                final int page = generation;
                try {
                    String js = readAsset("phone-bridge.js");
                    v.evaluateJavascript(js, ignored -> {
                        if (page != generation || !phoneOrigin(Uri.parse(v.getUrl()))) return;
                        WebMessagePort[] ports = v.createWebMessageChannel(); port = ports[0];
                        port.setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
                            @Override public void onMessage(WebMessagePort p,WebMessage m) {
                                if (page==generation && phoneOrigin(Uri.parse(web.getUrl()))) receive(m.getData());
                            }
                        }, main);
                        v.postWebMessage(new WebMessage("rp-phone-connect",new WebMessagePort[]{ports[1]}),Uri.parse("https://"+HOST));
                    });
                } catch (Exception e) { setStatus("No se pudo activar el detector"); }
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error) {
                if (r.isForMainFrame()) { connected=false;endCall();setStatus(networkUp?"Error de conexión · reintentando":"Sin internet");scheduleReconnect(); }
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame() && phoneOrigin(r.getUrl()) && (response.getStatusCode()==401 || response.getStatusCode()==403))requireLogin();}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail) {
                closePort(); endCall();
                if (v.getParent() instanceof ViewGroup) ((ViewGroup)v.getParent()).removeView(v);
                unparkWeb();v.destroy(); createWeb();
                MainActivity a=activity.get(); if(a!=null) a.attachPhone();
                String link=Vault.read(PhoneService.this); if(!link.isEmpty()) web.loadUrl(link);
                setStatus("Reconectando…");parkWeb();return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                main.post(() -> {
                    MainActivity a=activity.get();
                    if (!phoneOrigin(request.getOrigin()) || a==null || !a.isResumed) { request.deny(); return; }
                    if(permissionRequest!=null) permissionRequest.deny(); permissionRequest=request;
                    a.requestWebPermissions(request.getResources());
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest r) { if(permissionRequest==r) permissionRequest=null; }
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params) {
                MainActivity a=activity.get(); if(a==null || !phoneOrigin(Uri.parse(v.getUrl()))) return false;
                if(uploadCallback!=null) uploadCallback.onReceiveValue(null); uploadCallback=callback;
                a.chooseFile(params); return true;
            }
        });
        web.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1920,android.view.View.MeasureSpec.EXACTLY));
        web.layout(0,0,1080,1920);
    }
    String readAsset(String name) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=getAssets().open(name)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
        return out.toString("UTF-8");
    }
    void resolvePermission() {
        PermissionRequest r=permissionRequest; permissionRequest=null; if(r==null)return;
        MainActivity a=activity.get();
        if(a==null || !a.isResumed || !phoneOrigin(r.getOrigin())){r.deny();return;}
        java.util.ArrayList<String> grants=new java.util.ArrayList<>();
        for(String resource:r.getResources()) {
            if(resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){microphone=true;grants.add(resource);}
            if(resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){camera=true;grants.add(resource);}
        }
        try { foreground(); if(grants.isEmpty())r.deny();else r.grant(grants.toArray(new String[0])); }
        catch(RuntimeException e){microphone=false;camera=false;r.deny();setStatus("Abre la app para habilitar el audio");}
    }
    void enableMicWhileVisible() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){microphone=true;foreground();}
    }
    void receive(String data) {
        if(data==null || data.length()>8192)return;
        try {
            JSONObject j=new JSONObject(data); String type=j.optString("type");
            if(type.equals("status")) {
                heartbeat=SystemClock.elapsedRealtime(); String state=j.optString("value");activeMedia=j.optBoolean("media"); connected=state.equals("ready") && networkUp;
                if(state.equals("login")){requireLogin();}
                else if(connected){loadingAt=0;if(!reconnectOnReturn){cancelReconnect();recovery.ready();}sessionNotice=false;notifications.cancel(30);setStatus("Conectado · web activa");}
                else {if(loadingAt==0)loadingAt=SystemClock.elapsedRealtime();setStatus(!networkUp || state.equals("offline")?"Sin internet":recovery.login?"Sesión vencida · inicia sesión":"Reconectando…");}
            } else if(type.equals("call")) {
                PhonePreferences.putValue(this,"lastEvent","Llamada entrante detectada en ONX.");
                String id=j.optString("id"); if(id.isEmpty() || id.equals(callId))return;
                endCall(); callId=id; caller=j.optString("name","Llamada de FiveM"); callAt=SystemClock.elapsedRealtime();if(PhonePreferences.flag(this,"calls",true))incoming(false);
            } else if(type.equals("callEnd")) {
                if(j.optString("id").equals(callId))endCall();
            } else if(type.equals("notification")) {
                PhonePreferences.putValue(this,"lastEvent","Aviso genérico de ONX, no detectado como llamada.");
                String title=j.optString("title","Aviso de FiveM"); String body=j.optString("body");
                String kind=j.optString("kind");boolean isMessage=kind.equals("messages") || kind.equals("emails") || kind.equals("groups");
                if(!PhonePreferences.flag(this,isMessage?"messages":"other",true))return;
                Notification n=new Notification.Builder(this,PhonePreferences.flag(this,"messageSound",true)?"messages":"messages-silent").setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(openIntent("","",5)).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
                notifications.notify(nextNotice++,n); if(nextNotice>130)nextNotice=100;
            } else if(type.equals("actionResult") && j.optString("id").equals(callId)) {
                if(j.optBoolean("ok"))silence();
                else Toast.makeText(this,"La llamada ya terminó o cambió. Abre el celular.",Toast.LENGTH_LONG).show();
            }
        } catch(Exception ignored){}
    }
    PendingIntent openIntent(String action,String id,int code) {
        Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("callAction",action).putExtra("callId",id);
        return PendingIntent.getActivity(this,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    PendingIntent broadcast(String action,String id,int code) {
        Intent i=new Intent(this,ActionReceiver.class).setAction(action).putExtra("callId",id);
        return PendingIntent.getBroadcast(this,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    boolean fullScreenAllowed(){return Build.VERSION.SDK_INT<34 || notifications.canUseFullScreenIntent();}
    boolean highCallChannel(){NotificationChannel c=notifications.getNotificationChannel("calls-v3");return c!=null && c.getImportance()>=NotificationManager.IMPORTANCE_HIGH;}
    Intent incomingIntent(boolean demo){return new Intent(this,IncomingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("callId",callId).putExtra("caller",caller).putExtra("demo",demo);}
    PendingIntent incomingPending(Intent intent){
        Bundle options=Build.VERSION.SDK_INT>=35?CallOptions35.create():null;
        return PendingIntent.getActivity(this,7,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE,options);
    }
    private static final class CallOptions35 {
        static Bundle create(){return ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();}
    }
    void presentationResult(String result){PhonePreferences.putValue(this,"presentation",result);}
    void screenShown(String id){if(id!=null && id.equals(callId)){shownCallId=id;presentationResult("La pantalla de llamada se abrió.");}}
    String presentationChecks(){
        boolean full=fullScreenAllowed(),high=highCallChannel();
        return "Android "+Build.VERSION.RELEASE+" · "+Build.MANUFACTURER+" "+Build.MODEL+
            "\nNotificaciones: "+(notifications.areNotificationsEnabled()?"permitidas":"bloqueadas")+
            "\nPantalla completa: "+(full?"permitida":"NO permitida")+
            "\nCanal de llamadas: "+(high?"prioridad alta":"prioridad insuficiente o bloqueado")+
            "\nMostrar sobre otras apps: "+(Settings.canDrawOverlays(this)?"permitido":"no permitido")+
            "\nNo molestar: "+(notifications.getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL?"desactivado":"activo o restringido")+
            "\nÚltimo evento web: "+PhonePreferences.value(this,"lastEvent","Sin avisos todavía.")+
            "\nÚltimo resultado: "+PhonePreferences.value(this,"presentation","Sin prueba todavía.");
    }
    void ensureCallScreen(String id,boolean demo){
        boolean locked=getSystemService(KeyguardManager.class).isKeyguardLocked();
        if(!CallPresentationPolicy.mayOpen(id.equals(callId),id.equals(shownCallId),locked,PhonePreferences.flag(this,"fullScreen",true) && (demo || PhonePreferences.flag(this,"calls",true)),notifications.areNotificationsEnabled(),fullScreenAllowed(),highCallChannel(),notifications.getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL,Build.VERSION.SDK_INT<29 || Settings.canDrawOverlays(this)))return;
        // A user-granted overlay permission is a documented background launch exception.
        // Do not use it to override denied full-screen, channel, or DND settings.
        try{startActivity(incomingIntent(demo));presentationResult("Apertura solicitada; esperando confirmación de Android.");}
        catch(RuntimeException e){presentationResult("Android rechazó abrir la pantalla desde segundo plano.");}
    }
    void incoming(boolean demo) {
        Notification.Builder b=new Notification.Builder(this,"calls-v3").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(caller).setContentText(demo?"Prueba de llamada de ONX phone":"Llamada entrante de FiveM")
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(incomingPending(incomingIntent(demo))).setTimeoutAfter(90000);
        PendingIntent reject=broadcast(demo?"dismiss":"reject",callId,4), answer=openIntent(demo?"demo":"answer",callId,6);
        if(Build.VERSION.SDK_INT>=31)b.setStyle(Notification.CallStyle.forIncomingCall(new Person.Builder().setName(caller).setImportant(true).build(),reject,answer));
        else {b.addAction(new Notification.Action.Builder(null,"Rechazar",reject).build());b.addAction(new Notification.Action.Builder(null,"Contestar",answer).build());}
        if(PhonePreferences.flag(this,"fullScreen",true) && fullScreenAllowed()) {
            b.setFullScreenIntent(incomingPending(incomingIntent(demo)),true);
        }
        presentationResult(!fullScreenAllowed()?"Falta el permiso de pantalla completa.":!highCallChannel()?"El canal de llamadas no tiene prioridad alta.":"Aviso enviado; esperando apertura de pantalla.");
        notifications.notify(CALL,b.build());
        AudioManager audio=getSystemService(AudioManager.class);
        if(PhonePreferences.flag(this,"callVibration",true) && audio.getRingerMode()!=AudioManager.RINGER_MODE_SILENT && notifications.getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL && vibrator!=null)
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,500,400,500,1800},0));
        if(PhonePreferences.flag(this,"callSound",true) && audio.getRingerMode()==AudioManager.RINGER_MODE_NORMAL && notifications.getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL) {
            String tone=PhonePreferences.value(this,"ringtone","default");
            Uri toneUri=tone.equals("default")?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE):tone.isEmpty()?null:Uri.parse(tone);
            try{ringtone=toneUri==null?null:RingtoneManager.getRingtone(this,toneUri);}catch(RuntimeException ignored){ringtone=RingtoneManager.getRingtone(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));}
            if(ringtone!=null) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                if(Build.VERSION.SDK_INT>=28)ringtone.setLooping(true);
                try{ringtone.play();}catch(RuntimeException ignored){}
            }
        }
        final String shownId=callId;
        main.postDelayed(()->ensureCallScreen(shownId,demo),1200);
        main.postDelayed(()->{if(callId.equals(shownId) && !shownCallId.equals(shownId))presentationResult("La llamada llegó, pero no se confirmó apertura de pantalla. Revisa permisos, prioridad del canal y No molestar.");},4000);
        if(demo)main.postDelayed(() -> { if(callId.equals(shownId))endCall(); },20000);
    }
    void demoCall() {
        if(!callId.isEmpty())return;
        callId="demo-"+SystemClock.elapsedRealtime();caller="Prueba · ONX phone";callAt=SystemClock.elapsedRealtime();incoming(true);
    }
    void delayedDemo(){
        if(demoTask!=null)main.removeCallbacks(demoTask);
        presentationResult("Prueba programada para dentro de 15 segundos.");
        demoTask=()->{demoTask=null;demoCall();};main.postDelayed(demoTask,15000);
    }
    void silence() { if(ringtone!=null){ringtone.stop();ringtone=null;}if(vibrator!=null)vibrator.cancel(); }
    void callAction(String action,String id) {
        if(id==null || !id.equals(callId) || callId.isEmpty())return;
        if(callId.startsWith("demo-")){endCall();return;}
        if(port==null){Toast.makeText(this,"Abre el celular para responder",Toast.LENGTH_LONG).show();return;}
        try{JSONObject cmd=new JSONObject().put("type","action").put("action",action).put("id",id);port.postMessage(new WebMessage(cmd.toString()));}
        catch(Exception e){Toast.makeText(this,"No se pudo enviar la respuesta",Toast.LENGTH_LONG).show();}
    }
    void endCall() { silence(); callId="";shownCallId="";caller="";notifications.cancel(CALL);IncomingActivity.closeIfEnded(); }
    void closePort() { generation++;if(port!=null){try{port.close();}catch(Exception ignored){}port=null;} }
    void detach(MainActivity a) {
        if(activity.get()!=a)return;
        unparkWeb();
        if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);
        webContext.setBaseContext(this);activity.clear();CookieManager.getInstance().flush();parkWeb();
    }
    void unparkWeb(){if(backgroundHost!=null){backgroundHost.removeAllViews();try{getSystemService(WindowManager.class).removeView(backgroundHost);}catch(RuntimeException ignored){}backgroundHost=null;}}
    void parkWeb(){
        if(destroying || web==null || web.getParent()!=null || !PhonePreferences.flag(this,"background",false) || !Settings.canDrawOverlays(this))return;
        backgroundHost=new FrameLayout(this);backgroundHost.setClipChildren(true);
        int width=Math.max(web.getWidth(),320),height=Math.max(web.getHeight(),640);
        backgroundHost.addView(web,new FrameLayout.LayoutParams(width,height));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;p.alpha=.01f;
        try{getSystemService(WindowManager.class).addView(backgroundHost,p);web.onResume();}
        catch(RuntimeException e){backgroundHost.removeAllViews();backgroundHost=null;setStatus("Modo bloqueado no disponible · revisa permisos");}
    }
    void audioRoute(int type){
        AudioManager audio=getSystemService(AudioManager.class);
        if(priorAudioMode==-1){priorAudioMode=audio.getMode();priorSpeaker=audio.isSpeakerphoneOn();}
        try{
            if(type==0){restoreAudio();return;}
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if(Build.VERSION.SDK_INT>=31){
                AudioDeviceInfo selected=null;for(AudioDeviceInfo d:audio.getAvailableCommunicationDevices())if(d.getType()==type || (type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO && d.getType()==AudioDeviceInfo.TYPE_BLE_HEADSET)){selected=d;break;}
                if(selected==null || !audio.setCommunicationDevice(selected)){Toast.makeText(this,"Esa salida no está disponible",Toast.LENGTH_LONG).show();restoreAudio();return;}
            }else{
                if(type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO){boolean found=false;for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))if(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO)found=true;if(!found){restoreAudio();Toast.makeText(this,"Conecta un auricular Bluetooth compatible con llamadas",Toast.LENGTH_LONG).show();return;}}
                audio.setSpeakerphoneOn(type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);if(type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO){audio.startBluetoothSco();audio.setBluetoothScoOn(true);}else{audio.stopBluetoothSco();audio.setBluetoothScoOn(false);}
            }
            Toast.makeText(this,"Salida seleccionada · comprueba el audio de ONX",Toast.LENGTH_LONG).show();
        }catch(RuntimeException e){restoreAudio();Toast.makeText(this,"No se pudo cambiar la salida de audio",Toast.LENGTH_LONG).show();}
    }
    void restoreAudio(){if(priorAudioMode==-1)return;AudioManager a=getSystemService(AudioManager.class);try{if(Build.VERSION.SDK_INT>=31)a.clearCommunicationDevice();else{a.stopBluetoothSco();a.setBluetoothScoOn(false);}a.setSpeakerphoneOn(priorSpeaker);a.setMode(priorAudioMode);}catch(RuntimeException ignored){}priorAudioMode=-1;}
    @Override public void onDestroy() {
        destroying=true;unparkWeb();restoreAudio();if(networkManager!=null && networkCallback!=null)try{networkManager.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}
        main.removeCallbacksAndMessages(null);closePort();endCall();
        if(permissionRequest!=null)permissionRequest.deny();if(uploadCallback!=null)uploadCallback.onReceiveValue(null);
        MainActivity a=activity.get();
        if(web!=null){if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);web.destroy();}
        if(wake!=null && wake.isHeld())wake.release();
        stopForeground(STOP_FOREGROUND_REMOVE);instance=null;
        if(a!=null)a.serviceStopped();super.onDestroy();
    }
}

