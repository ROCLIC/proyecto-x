package app.rpphone.companion;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

final class PhoneStyle {
    static final int BG=Color.rgb(12,10,21), INK=Color.rgb(248,246,255), MUTED=Color.rgb(173,165,191), PURPLE=Color.rgb(196,164,255), GREEN=Color.rgb(119,226,191);
    static int dp(Context c,float n){return (int)(c.getResources().getDisplayMetrics().density*n+.5f);}
    static GradientDrawable panel(Context c,int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
    }
    static GradientDrawable gradient(Context c,int radius,int... colors){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);d.setCornerRadius(dp(c,radius));return d;
    }
    static TextView text(Context c,String value,int size,int color){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setIncludeFontPadding(false);t.setLineSpacing(dp(c,4),1);return t;
    }
    static TextView label(Context c,String value){TextView t=text(c,value,11,PURPLE);t.setLetterSpacing(.12f);t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
    static ImageView logo(Context c,int radius){
        ImageView image=new ImageView(c);image.setImageResource(R.drawable.brand_art);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(panel(c,BG,radius));image.setClipToOutline(true);image.setContentDescription("ONX phone");return image;
    }
    static Button button(Context c,String title,boolean primary,Runnable action){
        Button b=new Button(c);b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(INK);b.setLetterSpacing(.015f);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setStateListAnimator(null);
        GradientDrawable base=primary?gradient(c,17,Color.rgb(104,57,180),Color.rgb(133,58,165)):panel(c,Color.rgb(26,22,39),17);
        base.setStroke(dp(c,1),primary?Color.rgb(152,103,199):Color.rgb(49,42,64));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),base,null));b.setPadding(dp(c,16),0,dp(c,16),0);b.setMinimumHeight(dp(c,52));
        b.setOnClickListener(v->action.run());return b;
    }
    static LinearLayout card(Context c){
        LinearLayout card=new LinearLayout(c);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(c,20),dp(c,20),dp(c,20),dp(c,20));
        GradientDrawable d=gradient(c,24,Color.rgb(29,24,43),Color.rgb(21,18,32));d.setStroke(dp(c,1),Color.rgb(48,40,64));card.setBackground(d);card.setElevation(dp(c,2));return card;
    }
    static TextView credit(Context c){TextView t=text(c,"ONX phone · por roclic",10,Color.rgb(139,128,157));t.setGravity(Gravity.CENTER);t.setLetterSpacing(.06f);return t;}
    static void space(LinearLayout parent,int height){View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));}
}
