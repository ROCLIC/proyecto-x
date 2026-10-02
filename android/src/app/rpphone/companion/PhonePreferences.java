package app.rpphone.companion;
import android.content.*;
final class PhonePreferences {
 static boolean flag(Context c,String k,boolean fallback){return c.getSharedPreferences("options",0).getBoolean(k,fallback);}
 static void putFlag(Context c,String k,boolean v){c.getSharedPreferences("options",0).edit().putBoolean(k,v).apply();}
 static String value(Context c,String k,String fallback){return c.getSharedPreferences("options",0).getString(k,fallback);}
 static void putValue(Context c,String k,String v){c.getSharedPreferences("options",0).edit().putString(k,v).apply();}
}
