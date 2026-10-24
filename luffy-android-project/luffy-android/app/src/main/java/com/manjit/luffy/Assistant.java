package com.manjit.luffy;
import android.Manifest;
import android.content.*;
import android.content.pm.*;
import android.hardware.camera2.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.speech.tts.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/** Phone commands + Claude answers + speech. Used by the app screen and the wake-word service. */
public class Assistant {
    public interface Out { void reply(String t); }
    static Assistant inst;
    public static synchronized Assistant get(Context c){ if(inst==null) inst=new Assistant(c.getApplicationContext()); return inst; }
    public static String key(Context c){ return c.getSharedPreferences("luffy",0).getString("key",""); }
    public static void saveKey(Context c,String k){ c.getSharedPreferences("luffy",0).edit().putString("key",k).apply(); }

    static final String SYSTEM = "You are Luffy, a cheerful voice assistant inside a phone app, made by Manjit. Answer in plain text with no markdown, in 1 to 4 short sentences unless asked for more. Reply in the language the person uses.";
    final Context ctx; TextToSpeech tts; public volatile boolean speaking; final List<JSONObject> hist=new ArrayList<>();

    Assistant(Context c){
        ctx=c;
        tts=new TextToSpeech(c,st->{ if(st==TextToSpeech.SUCCESS){ tts.setLanguage(Locale.getDefault());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                public void onStart(String u){speaking=true;} public void onDone(String u){speaking=false;} public void onError(String u){speaking=false;} }); }});
    }
    public void say(String t){ tts.speak(t,TextToSpeech.QUEUE_FLUSH,null,"u"); }
    public void stopSpeaking(){ tts.stop(); speaking=false; }

    public void handle(String t,Out out){
        String s=t.toLowerCase(Locale.ROOT).trim().replaceAll("[.!?]+$","");
        String r; try{ r=local(s); }catch(Exception e){ r="Something went wrong with that command."; }
        if(r!=null){ done(t,r,out); return; }
        new Thread(()->ask(t,out)).start();
    }
    JSONObject msg(String role,String text){ try{ return new JSONObject().put("role",role).put("content",text);}catch(JSONException e){return new JSONObject();} }
    void done(String q,String a,Out out){ hist.add(msg("user",q)); hist.add(msg("assistant",a)); say(a); if(out!=null) out.reply(a); }
    void fail(String a,Out out){ say(a); if(out!=null) out.reply(a); }

    void ask(String t,Out out){
        String key=key(ctx);
        if(key.isEmpty()){ fail("I need an Anthropic API key to answer questions. Add it in the app, top right.",out); return; }
        try{
            JSONArray msgs=new JSONArray(); int from=Math.max(0,hist.size()-8); if(from%2==1) from++;
            for(int i=from;i<hist.size();i++) msgs.put(hist.get(i));
            msgs.put(msg("user",t));
            JSONObject body=new JSONObject().put("model","claude-sonnet-5-5").put("max_tokens",300).put("system",SYSTEM).put("messages",msgs);
            HttpURLConnection c=(HttpURLConnection)new URL("https://api.anthropic.com/v1/messages").openConnection();
            c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(40000); c.setDoOutput(true);
            c.setRequestProperty("x-api-key",key); c.setRequestProperty("anthropic-version","2023-06-01"); c.setRequestProperty("content-type","application/json");
            try(OutputStream o=c.getOutputStream()){ o.write(body.toString().getBytes("UTF-8")); }
            int code=c.getResponseCode();
            InputStream is=code<400?c.getInputStream():c.getErrorStream();
            StringBuilder sb=new StringBuilder(); try(BufferedReader br=new BufferedReader(new InputStreamReader(is,"UTF-8"))){ String l; while((l=br.readLine())!=null) sb.append(l); }
            if(code==401){ fail("That API key doesn’t work. Check it in the app.",out); return; }
            if(code>=400){ fail("I couldn’t get an answer just now. Try again in a moment.",out); return; }
            String a=new JSONObject(sb.toString()).getJSONArray("content").getJSONObject(0).getString("text").trim();
            done(t,a,out);
        }catch(Exception e){ fail("I couldn’t reach the internet. Check your connection and try again.",out); }
    }

    boolean launch(Intent i){ try{ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); return true; }catch(Exception e){ return false; } }
    String go(Intent i,String ok){ return launch(i)?ok:"I couldn’t open that. If I’m in the background, allow “Display over other apps” for Luffy."; }
    String url(String u,String ok){ return go(new Intent(Intent.ACTION_VIEW,Uri.parse(u)),ok); }
    static String enc(String s){ try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;} }

    String local(String s){
        Matcher m;
        if(s.matches(".*(who (made|created|built|developed|programmed) you|your (creator|maker)|who are you|your name).*")) return "I’m Luffy, your voice assistant. Manjit made me!";
        m=Pattern.compile("^(?:call|dial|phone)\\s+([\\d\\s+\\-]{5,})$").matcher(s);
        if(m.find()){ String n=m.group(1).replaceAll("[\\s-]","");
            boolean ok=ctx.checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED;
            return go(new Intent(ok?Intent.ACTION_CALL:Intent.ACTION_DIAL,Uri.parse("tel:"+n)),"Calling "+n+"."); }
        m=Pattern.compile("^(?:text|sms)\\s+([\\d+]{5,})(?:\\s+(?:saying|that)\\s+(.+))?$").matcher(s);
        if(m.find()){ Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+m.group(1))); if(m.group(2)!=null) i.putExtra("sms_body",m.group(2)); return go(i,"Opening a message to "+m.group(1)+"."); }
        m=Pattern.compile("^whatsapp\\s+([\\d+]{5,})(?:\\s+(.+))?$").matcher(s);
        if(m.find()) return url("https://wa.me/"+m.group(1).replace("+","")+(m.group(2)!=null?"?text="+enc(m.group(2)):""),"Opening WhatsApp.");
        m=Pattern.compile("^(?:navigate|directions?|take me)\\s+(?:to\\s+)?(.+)").matcher(s);
        if(m.find()) return url("https://www.google.com/maps/dir/?api=1&destination="+enc(m.group(1)),"Getting directions to "+m.group(1)+".");
        m=Pattern.compile("^(?:play|watch)\\s+(.+?)(?:\\s+on youtube)?$").matcher(s);
        if(m.find()) return url("https://www.youtube.com/results?search_query="+enc(m.group(1)),"Searching YouTube for "+m.group(1)+".");
        m=Pattern.compile("^(?:search|google)\\s+(?:for\\s+)?(.+)").matcher(s);
        if(m.find()) return url("https://www.google.com/search?q="+enc(m.group(1)),"Searching for "+m.group(1)+".");
        m=Pattern.compile("^open\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$").matcher(s);
        if(m.find()) return open(m.group(1));
        m=Pattern.compile("(?:timer|remind me|alarm)\\D*(\\d+)\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)").matcher(s);
        if(m.find()){ int n=Integer.parseInt(m.group(1)); char u=m.group(2).charAt(0); int sec=n*(u=='h'?3600:u=='m'?60:1);
            return go(new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,sec).putExtra(AlarmClock.EXTRA_SKIP_UI,true),"Timer set for "+n+" "+m.group(2)+"."); }
        m=Pattern.compile("alarm\\D*(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?").matcher(s);
        if(m.find()){ int h=Integer.parseInt(m.group(1)), mi=m.group(2)!=null?Integer.parseInt(m.group(2)):0; String ap=m.group(3)==null?"":m.group(3).replace(".","");
            if(ap.equals("pm")&&h<12) h+=12; if(ap.equals("am")&&h==12) h=0;
            return go(new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,h).putExtra(AlarmClock.EXTRA_MINUTES,mi).putExtra(AlarmClock.EXTRA_SKIP_UI,true),"Alarm set for "+h+":"+String.format("%02d",mi)+"."); }
        if(s.matches(".*(flash\\s?light|torch).*")) return torch(!s.matches(".*\\b(off|stop)\\b.*"));
        AudioManager am=(AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
        if(s.matches(".*(volume up|louder|increase volume).*")){ am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI); return "Volume up."; }
        if(s.matches(".*(volume down|quieter|lower the volume|decrease volume).*")){ am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI); return "Volume down."; }
        if(s.matches(".*\\bmute\\b.*")){ am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,AudioManager.FLAG_SHOW_UI); return "Muted."; }
        if(s.matches(".*wi-?fi.*")) return go(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),"Opening Wi‑Fi. Android doesn’t let apps switch it directly.");
        if(s.matches(".*bluetooth.*")) return go(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),"Opening Bluetooth settings.");
        if(s.matches(".*vibrat.*")){ ((Vibrator)ctx.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(500,VibrationEffect.DEFAULT_AMPLITUDE)); return "Buzzing your phone."; }
        if(s.matches(".*battery.*")){ BatteryManager bm=(BatteryManager)ctx.getSystemService(Context.BATTERY_SERVICE);
            return "Battery is at "+bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)+"%"+(bm.isCharging()?" and charging.":"."); }
        if(s.matches(".*what.*time.*|.*time now.*|.*current time.*")) return "It’s "+new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date())+".";
        if(s.matches(".*what.*(date|day).*")) return "Today is "+new SimpleDateFormat("EEEE, d MMMM yyyy",Locale.getDefault()).format(new Date())+".";
        return null;
    }

    String open(String name){
        PackageManager pm=ctx.getPackageManager();
        for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
            String label=ri.loadLabel(pm).toString(), l=label.toLowerCase(Locale.ROOT);
            if(l.length()>2&&(l.contains(name)||name.contains(l))){
                Intent li=pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if(li!=null&&launch(li)) return "Opening "+label+".";
            }
        }
        return "I couldn’t find an app called "+name+".";
    }

    String torch(boolean on){
        try{ CameraManager cm=(CameraManager)ctx.getSystemService(Context.CAMERA_SERVICE);
            for(String id:cm.getCameraIdList()){ CameraCharacteristics cc=cm.getCameraCharacteristics(id);
                Boolean f=cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE); Integer face=cc.get(CameraCharacteristics.LENS_FACING);
                if(f!=null&&f&&face!=null&&face==CameraCharacteristics.LENS_FACING_BACK){ cm.setTorchMode(id,on); return on?"Flashlight is on.":"Flashlight is off."; } }
        }catch(Exception e){}
        return "I couldn’t reach the flashlight.";
    }
}
