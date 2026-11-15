package app.littlekeys.piano;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PianoMidiPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
