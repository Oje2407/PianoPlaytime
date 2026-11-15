package app.littlekeys.piano;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listens to a real piano or keyboard over MIDI (USB, or Bluetooth on Android 12+)
 * and passes each key press to the web app as a "note" event.
 * Nothing is stored or sent anywhere.
 */
@SuppressLint("MissingPermission")
@CapacitorPlugin(
    name = "PianoMidi",
    permissions = {
        @Permission(alias = "bluetooth", strings = { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT })
    }
)
public class PianoMidiPlugin extends Plugin {

    /** The standard Bluetooth LE MIDI service. */
    private static final UUID BLE_MIDI = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700");
    private static final long SCAN_MS = 12000;

    private static class Opened {
        MidiDevice device;
        final List<MidiOutputPort> ports = new ArrayList<>();
        String name;
    }

    private MidiManager midi;
    private Handler main;
    private boolean started = false;
    private final Map<Integer, Opened> open = new HashMap<>();
    private final Set<Integer> pending = new HashSet<>();
    private final List<MidiDevice> bluetoothHolds = new ArrayList<>();
    private final Map<String, BluetoothDevice> found = new HashMap<>();
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;

    @Override
    public void load() {
        main = new Handler(Looper.getMainLooper());
        if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            midi = (MidiManager) getContext().getSystemService(Context.MIDI_SERVICE);
        }
    }

    /* ---------- start listening ---------- */

    @PluginMethod
    public void start(PluginCall call) {
        if (midi == null) {
            JSObject r = new JSObject();
            r.put("supported", false);
            r.put("bluetooth", false);
            r.put("connected", new JSArray());
            call.resolve(r);
            return;
        }
        if (!started) {
            started = true;
            midi.registerDeviceCallback(deviceCallback, main);
            for (MidiDeviceInfo info : currentDevices()) openInfo(info);
        }
        call.resolve(status());
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        call.resolve(status());
    }

    @SuppressWarnings("deprecation")
    private Collection<MidiDeviceInfo> currentDevices() {
        if (Build.VERSION.SDK_INT >= 33) return midi.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM);
        MidiDeviceInfo[] all = midi.getDevices();
        List<MidiDeviceInfo> list = new ArrayList<>();
        Collections.addAll(list, all);
        return list;
    }

    private final MidiManager.DeviceCallback deviceCallback = new MidiManager.DeviceCallback() {
        @Override
        public void onDeviceAdded(MidiDeviceInfo info) {
            openInfo(info);
        }

        @Override
        public void onDeviceRemoved(MidiDeviceInfo info) {
            closeId(info.getId());
            emitStatus();
        }
    };

    private void openInfo(final MidiDeviceInfo info) {
        if (info == null || info.getOutputPortCount() == 0) return;
        final int id = info.getId();
        if (open.containsKey(id) || pending.contains(id)) return;
        pending.add(id);
        midi.openDevice(info, device -> {
            pending.remove(id);
            if (device == null) return;
            Opened o = new Opened();
            o.device = device;
            o.name = nameOf(info);
            for (int i = 0; i < info.getOutputPortCount(); i++) {
                MidiOutputPort port = device.openOutputPort(i);
                if (port != null) {
                    port.connect(new NoteParser());
                    o.ports.add(port);
                }
            }
            open.put(id, o);
            emitStatus();
        }, main);
    }

    private static String nameOf(MidiDeviceInfo info) {
        Bundle p = info.getProperties();
        String[] keys = { MidiDeviceInfo.PROPERTY_NAME, MidiDeviceInfo.PROPERTY_PRODUCT, MidiDeviceInfo.PROPERTY_MANUFACTURER };
        for (String k : keys) {
            String v = p.getString(k);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "Keyboard";
    }

    private void closeId(int id) {
        Opened o = open.remove(id);
        if (o == null) return;
        for (MidiOutputPort port : o.ports) {
            try { port.close(); } catch (Exception ignored) { }
        }
        try { o.device.close(); } catch (Exception ignored) { }
        for (int i = bluetoothHolds.size() - 1; i >= 0; i--) {
            MidiDevice d = bluetoothHolds.get(i);
            if (d.getInfo() != null && d.getInfo().getId() == id) {
                try { d.close(); } catch (Exception ignored) { }
                bluetoothHolds.remove(i);
            }
        }
    }

    private JSObject status() {
        JSObject r = new JSObject();
        r.put("supported", midi != null);
        r.put("bluetooth", bluetoothPossible());
        JSArray names = new JSArray();
        for (Opened o : open.values()) names.put(o.name);
        r.put("connected", names);
        return r;
    }

    private void emitStatus() {
        notifyListeners("status", status());
    }

    /* ---------- reading key presses ---------- */

    /** Turns raw MIDI bytes into note on / note off events, including running status. */
    private class NoteParser extends MidiReceiver {
        private int status = 0;
        private int first = -1;

        @Override
        public void onSend(byte[] msg, int offset, int count, long timestamp) {
            for (int i = offset; i < offset + count; i++) {
                int b = msg[i] & 0xFF;
                if (b >= 0xF8) continue;               /* clock and other real-time bytes */
                if (b >= 0xF0) { status = 0; first = -1; continue; } /* system messages */
                if (b >= 0x80) { status = b; first = -1; continue; }
                if (status == 0) continue;
                int type = status & 0xF0;
                boolean oneByte = type == 0xC0 || type == 0xD0;
                if (oneByte) { first = -1; continue; }
                if (first < 0) { first = b; continue; }
                int data1 = first;
                first = -1;
                if (type == 0x90 || type == 0x80) {
                    final int note = data1;
                    final int velocity = b;
                    final boolean on = type == 0x90 && velocity > 0;
                    main.post(() -> {
                        JSObject e = new JSObject();
                        e.put("note", note);
                        e.put("on", on);
                        e.put("velocity", velocity);
                        notifyListeners("note", e);
                    });
                }
            }
        }
    }

    /* ---------- Bluetooth keyboards (Android 12 and newer) ---------- */

    private boolean bluetoothPossible() {
        return Build.VERSION.SDK_INT >= 31
            && midi != null
            && getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    @PluginMethod
    public void scanBluetooth(PluginCall call) {
        if (!bluetoothPossible()) { call.reject("Bluetooth keyboards need Android 12 or newer.", "unsupported"); return; }
        if (getPermissionState("bluetooth") != PermissionState.GRANTED) {
            requestPermissionForAlias("bluetooth", call, "bluetoothPermission");
            return;
        }
        doScan(call);
    }

    @PermissionCallback
    private void bluetoothPermission(PluginCall call) {
        if (getPermissionState("bluetooth") == PermissionState.GRANTED) doScan(call);
        else call.reject("Bluetooth permission was not given.", "denied");
    }

    private void doScan(PluginCall call) {
        BluetoothAdapter a = adapter();
        if (a == null) { call.reject("This device has no Bluetooth.", "unsupported"); return; }
        if (!a.isEnabled()) { call.reject("Bluetooth is turned off.", "off"); return; }
        stopScan(false);
        scanner = a.getBluetoothLeScanner();
        if (scanner == null) { call.reject("Bluetooth is turned off.", "off"); return; }
        found.clear();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BLE_MIDI)).build());
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int type, ScanResult result) {
                BluetoothDevice d = result.getDevice();
                if (d == null || found.containsKey(d.getAddress())) return;
                found.put(d.getAddress(), d);
                String name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
                if (name == null) {
                    try { name = d.getName(); } catch (Exception ignored) { }
                }
                JSObject e = new JSObject();
                e.put("id", d.getAddress());
                e.put("name", name != null ? name : "Bluetooth keyboard");
                notifyListeners("found", e);
            }

            @Override
            public void onScanFailed(int errorCode) {
                stopScan(true);
            }
        };
        scanner.startScan(filters, settings, scanCallback);
        main.postDelayed(scanTimeout, SCAN_MS);
        call.resolve();
    }

    private final Runnable scanTimeout = () -> stopScan(true);

    private void stopScan(boolean tell) {
        main.removeCallbacks(scanTimeout);
        if (scanCallback != null && scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) { }
        }
        boolean was = scanCallback != null;
        scanCallback = null;
        if (tell && was) notifyListeners("scanDone", new JSObject());
    }

    @PluginMethod
    public void stopBluetoothScan(PluginCall call) {
        stopScan(true);
        call.resolve();
    }

    @PluginMethod
    public void connectBluetooth(PluginCall call) {
        String id = call.getString("id");
        final BluetoothDevice d = id == null ? null : found.get(id);
        if (d == null || midi == null) { call.reject("That keyboard was not found. Search again.", "missing"); return; }
        stopScan(true);
        if (!started) {
            started = true;
            midi.registerDeviceCallback(deviceCallback, main);
        }
        midi.openBluetoothDevice(d, device -> {
            if (device == null) {
                notifyListeners("bluetoothFailed", new JSObject());
                return;
            }
            bluetoothHolds.add(device); /* keeps the Bluetooth link open */
            openInfo(device.getInfo());
        }, main);
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        stopScan(false);
        for (Integer id : new ArrayList<>(open.keySet())) closeId(id);
        for (MidiDevice d : bluetoothHolds) {
            try { d.close(); } catch (Exception ignored) { }
        }
        bluetoothHolds.clear();
        if (midi != null && started) {
            try { midi.unregisterDeviceCallback(deviceCallback); } catch (Exception ignored) { }
        }
    }
}
