package com.yourcompany.blufi;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import blufi.espressif.BlufiCallback;
import blufi.espressif.BlufiClient;
import blufi.espressif.params.BlufiConfigureParams;
import blufi.espressif.params.BlufiParameter;
import blufi.espressif.response.BlufiScanResult;
import blufi.espressif.response.BlufiStatusResponse;
import blufi.espressif.response.BlufiVersionResponse;

public class BlufiPlugin extends CordovaPlugin {
    private static final int REQ_PERMS = 32941;
    private CallbackContext eventChannel;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanCallback scanCallback;
    private boolean scanning = false;
    private BlufiClient client;
    private BluetoothDevice currentDevice;
    private boolean gattPreparedOk = false;
    private boolean mtuReady = false;
    private int currentMtu = -1;
    private String resumeAction;
    private JSONArray resumeArgs;
    @Override
    protected void pluginInitialize() {
        Context ctx = cordova.getContext();
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bm != null ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
    }
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "events":
                eventChannel = callbackContext;
                sendEvent("ready", kv("ok", true));
                PluginResult pr = new PluginResult(PluginResult.Status.NO_RESULT);
                pr.setKeepCallback(true);
                callbackContext.sendPluginResult(pr);
                return true;
            case "scan":
                ensurePermissionsAndThen(action, args);
                return true;
            case "stopScan":
                stopScan();
                callbackContext.success();
                return true;
            case "connect": {
                ensurePermissionsAndThen(action, args);
                return true;
            }
            case "requestPermissions": {
                if (hasAllRequiredPermissions()) {
                    sendEvent("permission", kv("granted", true));
                    callbackContext.success();
                } else {
                    resumeAction = null;
                    resumeArgs = null;
                    ActivityCompat.requestPermissions(cordova.getActivity(), requiredPermissions(), REQ_PERMS);
                    sendEvent("permission", kv("requested", true));
                }
                return true;
            }
            case "hasPermissions": {
                boolean granted = hasAllRequiredPermissions();
                sendEvent("permission", kv("granted", granted));
                callbackContext.success();
                return true;
            }
            case "negotiateSecurity": {
                if (client == null) {
                    callbackContext.error("Client not connected");
                    return true;
                }
                if (!gattPreparedOk) {
                    callbackContext.error("Gatt not prepared. Wait for gattPrepared: status=0");
                    return true;
                }
                if (!mtuReady || currentMtu < 23) {
                    try { client.setPostPackageLengthLimit(20); } catch (Throwable ignore) {}
                }
                cordova.getThreadPool().execute(() -> {
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                    try { client.negotiateSecurity(); } catch (Throwable ignore) {}
                });
                callbackContext.success();
                return true;
            }
            case "configureSta": {
                String ssid = args.optString(0, null);
                String password = args.optString(1, null);
                if (client == null || ssid == null || password == null) {
                    callbackContext.error("Invalid state or params");
                    return true;
                }
                if (!gattPreparedOk) {
                    callbackContext.error("Gatt not prepared. Wait for gattPrepared: status=0");
                    return true;
                }
                BlufiConfigureParams params = new BlufiConfigureParams();
                params.setOpMode(BlufiParameter.OP_MODE_STA);
                params.setStaSSIDBytes(ssid.getBytes());
                params.setStaPassword(password);
                client.configure(params);
                callbackContext.success();
                return true;
            }
            case "deviceStatus": {
                if (client == null) { callbackContext.error("Client not connected"); return true; }
                if (!gattPreparedOk) { callbackContext.error("Gatt not prepared. Wait for gattPrepared: status=0"); return true; }
                client.requestDeviceStatus();
                callbackContext.success();
                return true;
            }
            case "deviceVersion": {
                if (client == null) { callbackContext.error("Client not connected"); return true; }
                if (!gattPreparedOk) { callbackContext.error("Gatt not prepared. Wait for gattPrepared: status=0"); return true; }
                client.requestDeviceVersion();
                callbackContext.success();
                return true;
            }
            case "deviceWifiScan": {
                if (client == null) { callbackContext.error("Client not connected"); return true; }
                if (!gattPreparedOk) { callbackContext.error("Gatt not prepared. Wait for gattPrepared: status=0"); return true; }
                client.requestDeviceWifiScan();
                callbackContext.success();
                return true;
            }
            case "disconnect": {
                if (client != null) {
                    try { client.requestCloseConnection(); } catch (Exception ignore) {}
                    try { client.close(); } catch (Exception ignore) {}
                    client = null;
                }
                gattPreparedOk = false;
                mtuReady = false;
                currentMtu = -1;
                sendEvent("disconnected", new JSONObject());
                callbackContext.success();
                return true;
            }
        }
        return false;
    }
    private void ensurePermissionsAndThen(String action, JSONArray args) {
        if (hasAllRequiredPermissions()) {
            if ("scan".equals(action)) {
                startScan();
            } else if ("connect".equals(action)) {
                String address = args.optString(0, null);
                connect(address);
            }
            return;
        }
        resumeAction = action;
        resumeArgs = args;
        String[] perms = requiredPermissions();
        ActivityCompat.requestPermissions(cordova.getActivity(), perms, REQ_PERMS);
        sendEvent("permission", kv("requested", true));
    }
    private boolean hasAllRequiredPermissions() {
        Context ctx = cordova.getContext();
        for (String p : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    private String[] requiredPermissions() {
        List<String> list = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            list.add(Manifest.permission.BLUETOOTH_SCAN);
            list.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            list.add(Manifest.permission.BLUETOOTH);
            list.add(Manifest.permission.BLUETOOTH_ADMIN);
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        list.add(Manifest.permission.ACCESS_WIFI_STATE);
        list.add(Manifest.permission.CHANGE_WIFI_STATE);
        return list.toArray(new String[0]);
    }
    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode != REQ_PERMS) return;
        boolean allGranted = true;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
        }
        sendEvent("permission", kv("granted", allGranted));
        if (allGranted && resumeAction != null) {
            JSONArray args = resumeArgs != null ? resumeArgs : new JSONArray();
            if ("scan".equals(resumeAction)) {
                startScan();
            } else if ("connect".equals(resumeAction)) {
                String address = args.optString(0, null);
                connect(address);
            }
        }
        resumeAction = null;
        resumeArgs = null;
    }
    private void startScan() {
        if (bluetoothAdapter == null) {
            sendError("Bluetooth adapter not available");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            sendError("Bluetooth is disabled");
            return;
        }
        if (scanning) return;
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            sendError("BLE scanner not available");
            return;
        }
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice dev = result.getDevice();
                try {
                    JSONObject obj = new JSONObject()
                            .put("type", "scan")
                            .put("name", dev.getName())
                            .put("address", dev.getAddress())
                            .put("rssi", result.getRssi());
                    sendEventObj(obj);
                } catch (JSONException ignored) {}
            }
        };
        bleScanner.startScan(scanCallback);
        scanning = true;
        sendEvent("scanStarted", new JSONObject());
    }
    private void stopScan() {
        if (!scanning) return;
        try {
            bleScanner.stopScan(scanCallback);
        } catch (Exception ignore) {}
        scanning = false;
        sendEvent("scanStopped", new JSONObject());
    }
    private void connect(String address) {
        if (address == null || address.isEmpty()) {
            sendError("address required");
            return;
        }
        stopScan();
        if (bluetoothAdapter == null) {
            sendError("Bluetooth adapter not available");
            return;
        }
        try {
            currentDevice = bluetoothAdapter.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            sendError("invalid address");
            return;
        }
        client = new BlufiClient(cordova.getContext(), currentDevice);
        client.printDebugLog(true);
        gattPreparedOk = false;
        mtuReady = false;
        currentMtu = -1;
        try { client.setGattWriteTimeout(15000); } catch (Throwable ignore) {}
        client.setBlufiCallback(new BlufiCallback() {
            @Override
            public void onGattPrepared(BlufiClient client, int status, BluetoothGatt gatt) {
                gattPreparedOk = (status == STATUS_SUCCESS);
                if (gattPreparedOk && gatt != null) {
                    boolean mtuReq = gatt.requestMtu(270);
                    if (!mtuReq) {
                        try { client.setPostPackageLengthLimit(20); } catch (Throwable ignore) {}
                        mtuReady = false;
                    }
                }
                try {
                    JSONObject obj = new JSONObject()
                            .put("type", "gattPrepared")
                            .put("status", status)
                            .put("address", currentDevice != null ? currentDevice.getAddress() : null);
                    sendEventObj(obj);
                } catch (JSONException ignored) {}
            }
            @Override
            public void onNegotiateSecurityResult(BlufiClient client, int status) {
                sendEvent("security", kv("status", status));
            }
            @Override
            public void onPostConfigureParams(BlufiClient client, int status) {
                sendEvent("configure", kv("status", status));
            }
            @Override
            public void onDeviceVersionResponse(BlufiClient client, int status, BlufiVersionResponse response) {
                String ver = response != null ? response.getVersionString() : null;
                sendEvent("version", kv("status", status, "version", ver));
            }
            @Override
            public void onDeviceStatusResponse(BlufiClient client, int status, BlufiStatusResponse resp) {
                JSONObject data = new JSONObject();
                try {
                    data.put("status", status);
                    if (resp != null) {
                        data.put("opMode", resp.getOpMode());
                        data.put("staSSID", resp.getStaSSID());
                        data.put("staBSSID", resp.getStaBSSID());
                        data.put("staConnStatus", resp.getStaConnectionStatus());
                        data.put("softAPSSID", resp.getSoftAPSSID());
                        data.put("softAPChannel", resp.getSoftAPChannel());
                        data.put("softAPMaxConn", resp.getSoftAPMaxConnectionCount());
                        data.put("softAPConn", resp.getSoftAPConnectionCount());
                    }
                } catch (JSONException ignored) {}
                sendEvent("deviceStatus", data);
            }
            @Override
            public void onDeviceScanResult(BlufiClient client, int status, java.util.List<BlufiScanResult> results) {
                JSONArray arr = new JSONArray();
                if (results != null) {
                    for (BlufiScanResult r : results) {
                        JSONObject o = new JSONObject();
                        try {
                            o.put("type", r.getType());
                            o.put("ssid", r.getSsid());
                            o.put("rssi", r.getRssi());
                        } catch (JSONException ignored) {}
                        arr.put(o);
                    }
                }
                JSONObject data = new JSONObject();
                try { data.put("status", status).put("list", arr); } catch (JSONException ignored) {}
                sendEvent("wifiList", data);
            }
            @Override
            public void onError(BlufiClient client, int errCode) {
                sendEvent("error", kv("code", errCode));
            }
        });
        client.setGattCallback(new BluetoothGattCallback() {
            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                currentMtu = mtu;
                mtuReady = (status == BluetoothGatt.GATT_SUCCESS);
                if (!mtuReady) {
                    try { client.setPostPackageLengthLimit(20); } catch (Throwable ignore) {}
                }
                sendEvent("mtuChanged", kv("status", status, "mtu", mtu));
            }
            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                sendEvent("charWrite", kv("status", status));
            }
        });
        client.connect();
        sendEvent("connecting", kv("address", address));
    }
    private void sendEvent(String type, JSONObject payload) {
        if (eventChannel == null) return;
        try {
            JSONObject obj = new JSONObject().put("type", type).put("payload", payload);
            PluginResult pr = new PluginResult(PluginResult.Status.OK, obj);
            pr.setKeepCallback(true);
            eventChannel.sendPluginResult(pr);
        } catch (JSONException ignored) {}
    }
    private void sendEventObj(JSONObject obj) {
        if (eventChannel == null) return;
        PluginResult pr = new PluginResult(PluginResult.Status.OK, obj);
        pr.setKeepCallback(true);
        eventChannel.sendPluginResult(pr);
    }
    private JSONObject kv(Object... kvPairs) {
        JSONObject o = new JSONObject();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            try { o.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]); } catch (JSONException ignored) {}
        }
        return o;
    }
    private void sendError(String message) {
        sendEvent("error", kv("message", message));
    }
}
