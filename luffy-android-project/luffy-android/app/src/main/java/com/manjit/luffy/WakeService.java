package com.manjit.luffy;
import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.regex.*;

/** Foreground service that keeps listening for the wake word "Luffy". */
public class WakeService extends Service {
    static final Pattern WAKE=Pattern.compile("\\b(luffy|luffi|lufi|loofy|lucy)\\b[,.!?]?\\s*",Pattern.CASE_INSENSITIVE);
    SpeechRecognizer sr; final Handler h=new Handler(Looper.getMainLooper()); boolean on; long awaiting; Assistant as;

    @Override public IBinder onBind(Intent i){ return null; }
    @Override public int onStartCommand(Intent i,int f,int id){
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("luffy","Luffy listening",NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"luffy").setContentTitle("Luffy is listening").setContentText("Say “Luffy”, then your command")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(pi).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29) startForeground(1,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE); else startForeground(1,n);
        as=Assistant.get(this);
        if(!on){ on=true; h.post(this::listen); }
        return START_STICKY;
    }
    void listen(){
        if(!on) return;
        if(as.speaking){ h.postDelayed(this::listen,700); return; }
        try{
            if(sr==null){ sr=SpeechRecognizer.createSpeechRecognizer(this); sr.setRecognitionListener(new SimpleListener(){
                @Override public void onResults(Bundle b){ handleText(first(b)); h.postDelayed(WakeService.this::listen,400); }
                @Override public void onError(int e){ h.postDelayed(WakeService.this::listen,e==SpeechRecognizer.ERROR_RECOGNIZER_BUSY?1500:500); } }); }
            sr.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName()));
        }catch(Exception e){ h.postDelayed(this::listen,2000); }
    }
    void handleText(String t){
        if(t.isEmpty()||as.speaking) return;
        Matcher m=WAKE.matcher(t);
        if(m.find()){ String cmd=t.substring(m.end()).trim();
            if(cmd.length()>1) as.handle(cmd,null); else { awaiting=System.currentTimeMillis(); as.say("Yes?"); } }
        else if(awaiting>0&&System.currentTimeMillis()-awaiting<8000){ awaiting=0; as.handle(t,null); }
    }
    @Override public void onDestroy(){ on=false; h.removeCallbacksAndMessages(null); if(sr!=null){ sr.destroy(); sr=null; } super.onDestroy(); }
}
