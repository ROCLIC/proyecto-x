package app.rpphone.companion;
import android.content.*;
public final class ActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        PhoneService s = PhoneService.instance;
        if (s == null) return;
        if ("stop".equals(i.getAction())) s.stopSelf();
        else if ("dismiss".equals(i.getAction())) s.silence();
        else if ("reject".equals(i.getAction())) s.callAction("reject", i.getStringExtra("callId"));
    }
}
