package app.rpphone.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public final class MainActivity extends Activity {
    boolean isResumed=false;
    LinearLayout root; FrameLayout browser; TextView state; EditText link;
    final Handler handler=new Handler(Looper.getMainLooper());
    boolean showingPhone=false;
    boolean backPending=false;
    Object backCallback;
    String pendingAction="", pendingId="";
    final int BG=PhoneStyle.BG, INK=PhoneStyle.INK, MINT=PhoneStyle.PURPLE;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        readAction(getIntent());
        if(!Vault.read(this).isEmpty()){
            startForegroundService(new Intent(this,PhoneService.class));showPhone();
        }else home();
    }
    @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);readAction(i);if(PhoneService.instance!=null)showPhone();}
    void readAction(Intent i){pendingAction=i.getStringExtra("callAction");pendingId=i.getStringExtra("callId");if(pendingAction==null)pendingAction="";}
    @Override public void onResume(){super.onResume();isResumed=true;if(PhoneService.instance!=null){if(showingPhone)attachPhone();else {PhoneService.instance.activity=new WeakReference<>(this);PhoneService.instance.parkWeb();updateStatus();}}handleAction();}
    @Override public void onPause(){isResumed=false;super.onPause();}
    @Override public void onStop(){PhoneService s=PhoneService.instance;if(s!=null)s.detach(this);super.onStop();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(Build.VERSION.SDK_INT>=33)BackApi33.remove(this);PhoneService s=PhoneService.instance;if(s!=null)s.detach(this);super.onDestroy();}
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String content,int size,int color){TextView t=new TextView(this);t.setText(content);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(6));return t;}
    Button button(String label,boolean primary,Runnable action){return PhoneStyle.button(this,label,primary,action);}
    void addButton(LinearLayout box,String label,boolean primary,Runnable action){Button b=button(label,primary,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(10);box.addView(b,p);}
    void base() {
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackground(PhoneStyle.gradient(this,0,Color.rgb(32,15,49),BG,BG));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets n=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(n.left,n.top,n.right,n.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });root.requestApplyInsets();
    }
    void home() {
        showingPhone=false;
        updateBackRegistration();
        PhoneService s=PhoneService.instance;if(s!=null)s.detach(this);
        if(s!=null)s.activity=new WeakReference<>(this);
        base();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(30),dp(22),dp(30));scroll.addView(box);
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setGravity(Gravity.CENTER);
        ImageView logo=PhoneStyle.logo(this,25);logo.setElevation(dp(6));brand.addView(logo,new LinearLayout.LayoutParams(dp(88),dp(88)));PhoneStyle.space(brand,14);
        TextView title=text("ONX phone",30,INK);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setLetterSpacing(-.035f);title.setGravity(Gravity.CENTER);brand.addView(title);
        TextView tagline=PhoneStyle.text(this,"Tu ciudad, siempre cerca.",13,PhoneStyle.MUTED);tagline.setGravity(Gravity.CENTER);brand.addView(tagline);box.addView(brand);
        PhoneStyle.space(box,24);
        state=PhoneStyle.text(this,s==null?"Sin conexión activa":s.status,12,s!=null && s.connected?PhoneStyle.GREEN:PhoneStyle.MUTED);
        state.setGravity(Gravity.CENTER);state.setPadding(dp(14),dp(11),dp(14),dp(11));GradientDrawable statusChip=shape(Color.rgb(27,23,39),14);statusChip.setStroke(dp(1),Color.rgb(49,40,65));state.setBackground(statusChip);box.addView(state,new LinearLayout.LayoutParams(-1,-2));
        PhoneStyle.space(box,22);
        final boolean saved=!Vault.read(this).isEmpty();
        LinearLayout access=PhoneStyle.card(this);box.addView(access);
        access.addView(PhoneStyle.label(this,saved?"TU ACCESO ESTÁ GUARDADO":"CONECTA TU PERSONAJE"));
        TextView accessTitle=text(saved?"Bienvenido de nuevo":"Todo empieza con tu enlace",21,INK);accessTitle.setTypeface(Typeface.create("sans-serif-medium",0));access.addView(accessTitle);
        access.addView(PhoneStyle.text(this,saved?"Abre tu celular y continúa donde lo dejaste.":"Pega tu enlace de ONX e inicia sesión con Steam. Tu acceso queda guardado en este móvil.",14,PhoneStyle.MUTED));
        if(saved)addButton(access,"Abrir mi celular  →",true,()->connect(false));
        final LinearLayout linkPanel=new LinearLayout(this);linkPanel.setOrientation(LinearLayout.VERTICAL);linkPanel.setVisibility(saved?View.GONE:View.VISIBLE);
        PhoneStyle.space(linkPanel,18);
        linkPanel.addView(PhoneStyle.label(this,"ENLACE PERSONAL"));PhoneStyle.space(linkPanel,8);
        link=new EditText(this);link.setSingleLine(false);link.setMaxLines(3);link.setMinLines(2);link.setTextColor(INK);link.setHintTextColor(Color.rgb(140,158,170));link.setTextSize(14);
        link.setHint("Pega aquí el enlace de tu celular");link.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        link.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);GradientDrawable input=shape(Color.rgb(14,11,23),16);input.setStroke(dp(1),Color.rgb(67,49,91));link.setBackground(input);link.setPadding(dp(15),dp(14),dp(15),dp(14));
        linkPanel.addView(link,new LinearLayout.LayoutParams(-1,dp(100)));
        addButton(linkPanel,saved?"Guardar nuevo enlace":"Conectar mi celular  →",true,this::saveAndConnect);
        access.addView(linkPanel);
        if(saved){TextView change=text("Cambiar mi enlace",13,MINT);change.setGravity(Gravity.CENTER);change.setPadding(0,dp(18),0,0);change.setOnClickListener(v->linkPanel.setVisibility(linkPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));access.addView(change);}
        PhoneStyle.space(box,26);box.addView(PhoneStyle.label(this,"AJUSTES DE TU CELULAR"));PhoneStyle.space(box,12);
        LinearLayout options=PhoneStyle.card(this);box.addView(options);
        boolean granted=getSystemService(NotificationManager.class).areNotificationsEnabled() && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        settingRow(options,"Notificaciones y audio",granted?"Permisos activados":"Permite los avisos y el micrófono",this::basicPermissions);
        divider(options);
        boolean fullAllowed=Build.VERSION.SDK_INT<34 || getSystemService(NotificationManager.class).canUseFullScreenIntent();
        settingRow(options,"Pantalla de llamada",fullAllowed?"Permiso de pantalla completa concedido":"Falta permitir pantalla completa",this::callScreenSettings);
        divider(options);
        settingRow(options,"Actividad en segundo plano","Revisa que la batería no restrinja la app",()->startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        divider(options);
        settingRow(options,"Recepción con pantalla bloqueada",PhonePreferences.flag(this,"background",false)?"Modo experimental activado":"Configura el modo experimental",this::backgroundSettings);
        divider(options);
        settingRow(options,"Llamadas y avisos","Tono, vibración y notificaciones silenciosas",this::notificationSettings);
        addButton(box,"Probar una llamada",false,()->{
            if(PhoneService.instance==null){Toast.makeText(this,"Conecta tu celular primero",Toast.LENGTH_SHORT).show();return;}
            if(!getSystemService(NotificationManager.class).areNotificationsEnabled()){basicPermissions();return;}
            PhoneService.instance.demoCall();
        });
        addButton(box,"Probar con el móvil bloqueado",false,this::delayedCallTest);
        if(s!=null)addButton(box,"Detener conexión",false,()->stopService(new Intent(this,PhoneService.class)));
        if(saved)addButton(box,"Cerrar sesión en este móvil",false,()->new AlertDialog.Builder(this).setTitle("¿Cerrar sesión en este teléfono?")
            .setMessage("Se borrarán el enlace guardado y la sesión web de este dispositivo.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Cerrar sesión",(d,w)->{
                stopService(new Intent(this,PhoneService.class));Vault.clear(this);
                CookieManager.getInstance().removeAllCookies(value->{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();home();});
            }).show());
        PhoneStyle.space(box,18);box.addView(PhoneStyle.text(this,"La conexión se recupera al volver internet. El modo bloqueado depende de Android y de la conexión web de ONX; puede consumir más batería.",12,PhoneStyle.MUTED));
        PhoneStyle.space(box,26);box.addView(PhoneStyle.credit(this));PhoneStyle.space(box,7);TextView version=PhoneStyle.text(this,"0.5",10,Color.rgb(114,103,133));version.setGravity(Gravity.CENTER);box.addView(version);
    }
    void callScreenSettings(){
        PhoneService s=PhoneService.instance;
        String checks=s==null?"Conecta tu celular para comprobar el canal de llamadas y el resultado de la prueba.":s.presentationChecks();
        new AlertDialog.Builder(this).setTitle("Pantalla de llamada")
            .setMessage(checks+"\n\nCon el móvil bloqueado se solicita pantalla completa con Contestar y Rechazar. Con el móvil en uso, Android puede mostrar un aviso emergente.")
            .setPositiveButton("Permiso pantalla completa",(d,w)->{
                if(Build.VERSION.SDK_INT>=34)try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){Toast.makeText(this,"Busca Pantalla completa en el acceso especial de aplicaciones",Toast.LENGTH_LONG).show();}
                else Toast.makeText(this,"Esta versión de Android usa el permiso de notificaciones y la prioridad del canal",Toast.LENGTH_LONG).show();
            })
            .setNeutralButton("Canal de llamadas",(d,w)->startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,"calls-v3")))
            .setNegativeButton("Cerrar",null).show();
    }
    void delayedCallTest(){
        PhoneService s=PhoneService.instance;
        if(s==null){Toast.makeText(this,"Conecta tu celular primero",Toast.LENGTH_SHORT).show();return;}
        if(!s.callId.isEmpty()){Toast.makeText(this,"Espera a que termine la llamada",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("Prueba con pantalla bloqueada")
            .setMessage("Al pulsar Programar tendrás 15 segundos para bloquear el móvil con el botón de encendido. La llamada de prueba durará 20 segundos. Comprueba si abre la pantalla con Contestar y Rechazar. Luego revisa Pantalla de llamada para ver permisos y resultado. Esta prueba comprueba el aviso de Android; no la conexión con ONX.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Programar",(d,w)->{s.delayedDemo();Toast.makeText(this,"Bloquea el móvil ahora: prueba en 15 segundos",Toast.LENGTH_LONG).show();}).show();
    }
    void backgroundSettings(){
        boolean enabled=PhonePreferences.flag(this,"background",false);
        new AlertDialog.Builder(this).setTitle("Recepción con pantalla bloqueada")
            .setMessage("Este modo experimental mantiene la vista web en una ventana mínima fuera de la app. Necesita el permiso Mostrar sobre otras apps y puede consumir más batería. Android aún puede suspender internet o cerrar la conexión. Para probarlo, permite también notificaciones, pantalla de llamada y batería sin restricciones.")
            .setNegativeButton("Cerrar",null)
            .setNeutralButton("Desactivar",(d,w)->{PhonePreferences.putFlag(this,"background",false);PhoneService s=PhoneService.instance;if(s!=null)s.unparkWeb();home();})
            .setPositiveButton(enabled && Settings.canDrawOverlays(this)?"Activado":"Activar",(d,w)->{
                PhonePreferences.putFlag(this,"background",true);
                if(!Settings.canDrawOverlays(this))try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){Toast.makeText(this,"Este Android no permite el modo experimental",Toast.LENGTH_LONG).show();}
                else {PhoneService s=PhoneService.instance;if(s!=null)s.parkWeb();home();}
            }).show();
    }
    void notificationSettings(){
        String[] labels={"Avisar llamadas","Sonido de llamada","Vibración de llamada","Abrir pantalla de llamada al bloquear","Avisar mensajes y correos","Avisar otras apps de ONX","Sonido de mensajes y otros avisos"};
        String[] keys={"calls","callSound","callVibration","fullScreen","messages","other","messageSound"};
        boolean[] values=new boolean[keys.length];for(int i=0;i<keys.length;i++)values[i]=PhonePreferences.flag(this,keys[i],true);
        new AlertDialog.Builder(this).setTitle("Llamadas y avisos").setMultiChoiceItems(labels,values,(d,which,on)->{
            PhonePreferences.putFlag(this,keys[which],on);
            PhoneService s=PhoneService.instance;if(s!=null && !on){
                if(keys[which].equals("calls")){s.silence();s.notifications.cancel(PhoneService.CALL);}
                else if(keys[which].equals("callSound") && s.ringtone!=null){s.ringtone.stop();s.ringtone=null;}
                else if(keys[which].equals("callVibration") && s.vibrator!=null)s.vibrator.cancel();
            }
        }).setPositiveButton("Listo",null).setNeutralButton("Elegir tono",(d,w)->chooseRingtone()).setNegativeButton("Ajustes Android",(d,w)->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()))).show();
    }
    void chooseRingtone(){
        String saved=PhonePreferences.value(this,"ringtone","default");
        Uri existing=saved.equals("default")?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE):saved.isEmpty()?null:Uri.parse(saved);
        Intent i=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_RINGTONE)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Tono de ONX phone").putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,true).putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,existing);
        try{startActivityForResult(i,23);}catch(ActivityNotFoundException e){Toast.makeText(this,"Este Android no incluye selector de tonos",Toast.LENGTH_LONG).show();}
    }
    void audioSettings(){
        PhoneService s=PhoneService.instance;if(s==null)return;
        new AlertDialog.Builder(this).setTitle("Salida de audio de llamada").setItems(new String[]{"Automática (ONX / Android)","Auricular del teléfono","Altavoz","Bluetooth conectado"},(d,w)->{
            if(w==3 && Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},14);return;}
            s.audioRoute(w==0?0:w==1?AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:w==2?AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        }).setNegativeButton("Cerrar",null).show();
    }
    void settingRow(LinearLayout parent,String title,String description,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(68));row.setPadding(0,dp(10),0,dp(10));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        TextView heading=PhoneStyle.text(this,title,15,INK);heading.setTypeface(Typeface.create("sans-serif-medium",0));copy.addView(heading);PhoneStyle.space(copy,4);copy.addView(PhoneStyle.text(this,description,12,PhoneStyle.MUTED));
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=PhoneStyle.text(this,"›",24,MINT);arrow.setGravity(Gravity.CENTER);arrow.setBackground(shape(Color.rgb(37,29,53),12));LinearLayout.LayoutParams arrowParams=new LinearLayout.LayoutParams(dp(30),dp(32));arrowParams.leftMargin=dp(10);row.addView(arrow,arrowParams);
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22BB8DFF),null,shape(Color.WHITE,10)));row.setOnClickListener(v->action.run());parent.addView(row);
    }
    void divider(LinearLayout parent){View line=new View(this);line.setBackgroundColor(Color.rgb(43,33,59));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(9);p.bottomMargin=dp(9);parent.addView(line,p);}
    void saveAndConnect(){
        String value=link.getText().toString().trim();
        try{
            Uri uri=Uri.parse(value);
            if(!PhoneService.phoneOrigin(uri) || !"/".equals(uri.getPath()) || uri.getUserInfo()!=null || uri.getQueryParameter("_e_t")==null || uri.getQueryParameter("_e_t").isEmpty())throw new Exception("Pega el enlace completo de tu celular ONX");
            Vault.save(this,value);connect(true);
        }catch(Exception e){Toast.makeText(this,e.getMessage()==null?"Enlace inválido":e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    void connect(boolean reload){
        if(Vault.read(this).isEmpty())return;
        basicPermissions();
        startForegroundService(new Intent(this,PhoneService.class).putExtra("reload",reload));
        showPhone();
    }
    void showPhone(){
        showingPhone=true;updateBackRegistration();base();
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(4),dp(8),dp(4));
        bar.addView(toolbarButton(R.drawable.ic_wrench,"Ajustes",this::home),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout.LayoutParams audioButton=new LinearLayout.LayoutParams(dp(44),dp(44));audioButton.leftMargin=dp(6);bar.addView(toolbarButton(R.drawable.ic_audio,"Salida de audio",this::audioSettings),audioButton);
        state=text("ONX phone · Conectando…",11,MINT);state.setGravity(Gravity.CENTER);state.setMaxLines(2);state.setPadding(dp(8),0,dp(8),0);bar.addView(state,new LinearLayout.LayoutParams(0,dp(44),1));
        bar.addView(toolbarButton(R.drawable.ic_refresh,"Recargar",()->{
            PhoneService s=PhoneService.instance;if(s==null)return;
            if(!s.callId.isEmpty()){Toast.makeText(this,"Espera a que termine la llamada",Toast.LENGTH_SHORT).show();return;}
            s.reload();
        }),new LinearLayout.LayoutParams(dp(44),dp(44)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(52)));
        browser=new FrameLayout(this);root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        attachWhenReady(0);
    }
    ImageButton toolbarButton(int image,String label,Runnable action){ImageButton button=new ImageButton(this);button.setImageResource(image);button.setImageTintList(android.content.res.ColorStateList.valueOf(MINT));button.setContentDescription(label);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33BB8DFF),shape(Color.rgb(28,23,43),14),null));button.setPadding(dp(10),dp(10),dp(10),dp(10));button.setOnClickListener(v->action.run());return button;}
    void attachWhenReady(int attempts){if(!showingPhone || isFinishing())return;if(PhoneService.instance!=null){attachPhone();handleAction();}else if(attempts<50)handler.postDelayed(()->attachWhenReady(attempts+1),100);else{Toast.makeText(this,"No se pudo iniciar la conexión",Toast.LENGTH_LONG).show();home();}}
    void attachPhone(){
        PhoneService s=PhoneService.instance;if(s==null || browser==null || !showingPhone)return;
        s.unparkWeb();
        if(s.web.getParent() instanceof ViewGroup)((ViewGroup)s.web.getParent()).removeView(s.web);
        s.webContext.setBaseContext(this);s.activity=new WeakReference<>(this);browser.addView(s.web,new FrameLayout.LayoutParams(-1,-1));
        if(isResumed)s.enableMicWhileVisible();updateStatus();
    }
    void updateStatus(){if(state!=null){PhoneService s=PhoneService.instance;state.setText(s==null?"Sin conexión activa":s.status);state.setTextColor(s!=null && s.connected?PhoneStyle.GREEN:PhoneStyle.MUTED);}}
    void serviceStopped(){showingPhone=false;home();}
    void basicPermissions(){
        ArrayList<String> list=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)list.add(Manifest.permission.POST_NOTIFICATIONS);
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)list.add(Manifest.permission.RECORD_AUDIO);
        if(!list.isEmpty())requestPermissions(list.toArray(new String[0]),11);
        else Toast.makeText(this,"Permisos listos",Toast.LENGTH_SHORT).show();
    }
    void requestWebPermissions(String[] resources){
        ArrayList<String> list=new ArrayList<>();
        for(String r:resources){String permission=r.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)?Manifest.permission.RECORD_AUDIO:r.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)?Manifest.permission.CAMERA:null;
            if(permission!=null && checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)list.add(permission);}
        if(list.isEmpty())PhoneService.instance.resolvePermission();else requestPermissions(list.toArray(new String[0]),12);
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants){
        super.onRequestPermissionsResult(code,names,grants);PhoneService s=PhoneService.instance;
        if(code==13 && (grants.length==0 || grants[0]!=PackageManager.PERMISSION_GRANTED)){
            pendingAction="";Toast.makeText(this,"Habilita el micrófono para contestar con audio",Toast.LENGTH_LONG).show();return;
        }
        if(code==12 && s!=null)s.resolvePermission();
        if(code==11 && s!=null && isResumed)s.enableMicWhileVisible();
        if(code==14 && s!=null){if(grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)s.audioRoute(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);else Toast.makeText(this,"Bluetooth necesita permiso para cambiar el audio",Toast.LENGTH_LONG).show();}
        handleAction();
    }
    void handleAction(){
        PhoneService s=PhoneService.instance;if(s==null || pendingAction.isEmpty() || !isResumed)return;
        if(!showingPhone){showPhone();return;}
        if(pendingAction.equals("answer") && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},13);return;}
        String action=pendingAction;pendingAction="";
        if(action.equals("demo"))s.endCall();else if(action.equals("answer")){s.enableMicWhileVisible();s.callAction("answer",pendingId);}
    }
    void chooseFile(WebChromeClient.FileChooserParams params){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        String[] accepts=params.getAcceptTypes();if(accepts.length>0 && !accepts[0].isEmpty())i.putExtra(Intent.EXTRA_MIME_TYPES,accepts);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);
        try{startActivityForResult(i,22);}catch(ActivityNotFoundException e){PhoneService.instance.uploadCallback.onReceiveValue(null);PhoneService.instance.uploadCallback=null;}
    }
    @Override public void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
        if(req==23 && result==RESULT_OK && data!=null){Uri tone=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);PhonePreferences.putValue(this,"ringtone",tone==null?"":tone.toString());Toast.makeText(this,"Tono guardado",Toast.LENGTH_SHORT).show();}
        PhoneService s=PhoneService.instance;if(req==22 && s!=null && s.uploadCallback!=null){s.uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));s.uploadCallback=null;}}
    void updateBackRegistration(){if(Build.VERSION.SDK_INT>=33){if(showingPhone)BackApi33.add(this);else BackApi33.remove(this);}}
    void handleBack(){
        if(backPending)return;
        PhoneService s=PhoneService.instance;
        if(!showingPhone){finish();return;}
        if(s==null){home();return;}
        String url=s.web.getUrl();
        if(url!=null && PhoneService.phoneOrigin(Uri.parse(url))){
            backPending=true;
            s.web.evaluateJavascript("(function(){return typeof window.__onxPhoneBack==='function' && window.__onxPhoneBack();})()",result->{
                if(isFinishing()){backPending=false;return;}
                if(!"true".equals(result))moveTaskToBack(true);
                handler.postDelayed(()->backPending=false,450);
            });
        }else if(s.web.canGoBack())s.web.goBack();else moveTaskToBack(true);
    }
    private static final class BackApi33 {
        static void add(MainActivity a){if(a.backCallback!=null)return;android.window.OnBackInvokedCallback callback=a::handleBack;a.backCallback=callback;a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback);}
        static void remove(MainActivity a){if(a.backCallback!=null){a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback)a.backCallback);a.backCallback=null;}}
    }
    @Override public void onBackPressed(){if(showingPhone)handleBack();else super.onBackPressed();}
}
