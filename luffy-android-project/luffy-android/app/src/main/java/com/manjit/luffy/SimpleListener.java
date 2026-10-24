package com.manjit.luffy;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
public class SimpleListener implements RecognitionListener {
    public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float v){}
    public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){} public void onError(int e){}
    public void onResults(Bundle b){} public void onPartialResults(Bundle b){} public void onEvent(int t,Bundle b){}
    public static String first(Bundle b){ArrayList<String> l=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);return l==null||l.isEmpty()?"":l.get(0);}
}
