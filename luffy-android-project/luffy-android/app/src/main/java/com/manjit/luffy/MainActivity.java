package com.manjit.luffy;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    WebView web; SpeechRecognizer sr; Assistant as;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        as=Assistant.get(this);
        web=new WebView(this); web.setBackgroundColor(0xFFBFE3FF); setContentView(web);
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(),"Luffy");
        web.loadUrl("file:///android_asset/index.html");
        String[] p=Build.VERSION.SDK_INT>=33
            ? new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CALL_PHONE,Manifest.permission.POST_NOTIFICATIONS}
            : new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CALL_PHONE};
        requestPermissions(p,1);
    }
    void js(String fn,String arg){ runOnUiThread(()->web.evaluateJavascript(fn+"("+JSONObject.quote(arg)+")",null)); }

    void startListening(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){ js("onReply","Speech recognition isn’t available on this phone."); return; }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1); return; }
        as.stopSpeaking();
        if(sr==null){ sr=SpeechRecognizer.createSpeechRecognizer(this); sr.setRecognitionListener(new SimpleListener(){
            @Override public void onResults(android.os.Bundle b){ String t=first(b); if(t.isEmpty()){ js("onState","idle"); return; }
                js("onHeard",t); js("onState","thinking"); as.handle(t,r->js("onReply",r)); }
            @Override public void onError(int e){ js("onState","idle"); } }); }
        sr.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM));
        js("onState","listening");
    }

    class Bridge {
        @JavascriptInterface public void send(String t){ as.handle(t,r->js("onReply",r)); }
        @JavascriptInterface public void listen(){ runOnUiThread(()->startListening()); }
        @JavascriptInterface public void setWake(boolean on){ Intent i=new Intent(MainActivity.this,WakeService.class); if(on) startForegroundService(i); else stopService(i); }
        @JavascriptInterface public String getKey(){ return Assistant.key(MainActivity.this); }
        @JavascriptInterface public void setKey(String k){ Assistant.saveKey(MainActivity.this,k); }
        @JavascriptInterface public boolean overlayGranted(){ return Settings.canDrawOverlays(MainActivity.this); }
        @JavascriptInterface public void askOverlay(){ startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); }
    }
    @Override protected void onDestroy(){ if(sr!=null) sr.destroy(); super.onDestroy(); }
}
