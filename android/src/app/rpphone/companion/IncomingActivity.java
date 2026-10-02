package app.rpphone.companion;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;

public final class IncomingActivity extends Activity {
    static WeakReference<IncomingActivity> visible=new WeakReference<>(null);
    String id; boolean demo;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(android.os.Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        visible=new WeakReference<>(this);
        id=getIntent().getStringExtra("callId");demo=getIntent().getBooleanExtra("demo",false);
        PhoneService s=PhoneService.instance;if(s==null || id==null || !id.equals(s.callId)){finish();return;}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setBackground(PhoneStyle.gradient(this,0,Color.rgb(40,22,62),PhoneStyle.BG,Color.rgb(22,16,35)));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(28),dp(38),dp(28),dp(38));scroll.addView(box);
        box.addView(PhoneStyle.logo(this,20),new LinearLayout.LayoutParams(dp(64),dp(64)));PhoneStyle.space(box,22);
        TextView label=PhoneStyle.label(this,demo?"PRUEBA DE LLAMADA":"ONX PHONE · LLAMADA ENTRANTE");label.setGravity(Gravity.CENTER);box.addView(label);PhoneStyle.space(box,34);
        TextView avatar=PhoneStyle.text(this,initials(s.caller),38,PhoneStyle.INK);avatar.setGravity(Gravity.CENTER);avatar.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));
        android.graphics.drawable.GradientDrawable circle=PhoneStyle.gradient(this,100,Color.rgb(94,54,142),Color.rgb(39,27,59));circle.setStroke(dp(1),Color.rgb(146,107,189));avatar.setBackground(circle);avatar.setElevation(dp(6));box.addView(avatar,new LinearLayout.LayoutParams(dp(116),dp(116)));PhoneStyle.space(box,26);
        TextView name=PhoneStyle.text(this,s.caller,27,PhoneStyle.INK);name.setGravity(Gravity.CENTER);name.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));box.addView(name,new LinearLayout.LayoutParams(-1,-2));PhoneStyle.space(box,8);
        TextView subtitle=PhoneStyle.text(this,demo?"Así verás tus llamadas":"Te están llamando desde la ciudad",14,PhoneStyle.MUTED);subtitle.setGravity(Gravity.CENTER);box.addView(subtitle);PhoneStyle.space(box,40);
        add(box,"Contestar",true,()->{
            s.silence();
            Runnable open=()->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("callAction",demo?"demo":"answer").putExtra("callId",id));finish();};
            KeyguardManager keyguard=getSystemService(KeyguardManager.class);
            if(keyguard.isKeyguardLocked())keyguard.requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback(){@Override public void onDismissSucceeded(){open.run();}@Override public void onDismissCancelled(){s.incoming(demo);}});
            else open.run();
        });
        add(box,"Rechazar",false,()->{if(demo)s.endCall();else s.callAction("reject",id);finish();});
        TextView mute=PhoneStyle.text(this,"Silenciar el tono",13,PhoneStyle.MUTED);mute.setGravity(Gravity.CENTER);mute.setPadding(0,dp(20),0,dp(12));mute.setMinimumHeight(dp(48));mute.setOnClickListener(v->s.silence());box.addView(mute);PhoneStyle.space(box,26);box.addView(PhoneStyle.credit(this));setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets n=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(n.left,n.top,n.right,n.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });scroll.requestApplyInsets();
    }
    int dp(float n){return PhoneStyle.dp(this,n);}
    String initials(String name){String clean=name.replaceAll("[^\\p{L}\\p{N} ]","").trim();if(clean.isEmpty())return "ONX";String[] parts=clean.split("\\s+");String a=parts[0].substring(0,1);return (a+(parts.length>1?parts[1].substring(0,1):"")).toUpperCase(java.util.Locale.ROOT);}
    void add(LinearLayout box,String label,boolean answer,Runnable action){
        Button button=PhoneStyle.button(this,label,true,action);
        android.graphics.drawable.GradientDrawable bg=PhoneStyle.gradient(this,28,answer?Color.rgb(30,120,91):Color.rgb(128,45,77),answer?Color.rgb(26,88,75):Color.rgb(87,31,54));bg.setStroke(dp(1),answer?Color.rgb(64,151,120):Color.rgb(163,67,105));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),bg,null));
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(answer?R.drawable.ic_answer:R.drawable.ic_hangup,0,0,0);button.setCompoundDrawablePadding(dp(12));button.setPadding(dp(24),0,dp(24),0);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(60));params.topMargin=dp(12);box.addView(button,params);
    }
    static void closeIfEnded(){IncomingActivity a=visible.get();if(a!=null)a.finish();}
    @Override public void onResume(){super.onResume();PhoneService s=PhoneService.instance;if(s==null || id==null || !id.equals(s.callId)){finish();return;}s.screenShown(id);}
    @Override public void onDestroy(){if(visible.get()==this)visible.clear();super.onDestroy();}
}
