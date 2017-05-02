/* 

The MIT License (MIT)

Copyright (c) 2016 Esa Riihinen

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


*/

package com.geniem.rnble;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.BleDeviceState;
import com.idevicesinc.sweetblue.BleManager.DiscoveryListener;
import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleManagerConfig.ScanFilter;
import com.idevicesinc.sweetblue.BleManagerState;
import com.idevicesinc.sweetblue.DeviceStateListener;

class RNBLEModule extends ReactContextBaseJavaModule {
  private Context context;
  private BleManager bleManager;

  public RNBLEModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
  }

  @Override
  public void initialize() {
    super.initialize();
    this.bleManager = BleManager.get(this.context);
  }

  /**
   * @return the name of this module. This will be the name used to {@code require()} this module
   * from javascript.
   */
  @Override
  public String getName() {
    return "RNBLE";
  }

  @ReactMethod
  public void getState() {
    WritableMap params = Arguments.createMap();
    params.putString("state", getStringState());
    sendEvent("ble.stateChange", params);
  }

  @ReactMethod
  public void startScanning(ReadableArray _serviceUuids, Boolean _allowDuplicates) {
    final ScanFilter scanFilter = new ScanFilter() {
      @Override public Please onEvent(ScanEvent e) {
        Log.i("", String.format("BLEBLE Received ScanEvent: %s", e.name_normalized()));
        return Please.acknowledgeIf(e.name_normalized().contains("axa"));
      }
    };

    final DiscoveryListener discoveryListener = new DiscoveryListener() {
      @Override public void onEvent(DiscoveryEvent e) {
        Log.i("", "BLEBLE Discovery event");

        if( e.was(LifeCycle.DISCOVERED) ) {
          sendDiscoveryEvent(e.device());
        }
      }
    };

    Log.i("", "BLEBLE startScanning");
    bleManager.startScan(scanFilter, discoveryListener);
  }

  @ReactMethod
  public void stopScanning() {
    bleManager.stopScan();
  }

  @ReactMethod
  public void connect(final String peripheralUuid) { //in android peripheralUuid is the mac address of the BLE device
    BleDevice device = bleManager.getDevice(peripheralUuid);

    device.connect(new BleDevice.StateListener() {
      @Override public void onEvent(BleDevice.StateListener.StateEvent e) {
        if (e.didEnter(BleDeviceState.CONNECTED)) {
          WritableMap params = Arguments.createMap();
          params.putString("peripheralUuid", e.macAddress());
          sendEvent("ble.connect", params);
        }
      }
    });
  }

  private void sendDiscoveryEvent(BleDevice device) {
    WritableMap params = Arguments.createMap();
    WritableMap advertisement = Arguments.createMap();

    advertisement.putString("localName", device.getName_normalized());
    params.putMap("advertisement", advertisement);

    params.putInt("rssi", device.getRssi());
    params.putString("id", device.getMacAddress());

    sendEvent("ble.discover", params);
  }

  private void sendEvent(String eventName, WritableMap params) {
    getReactApplicationContext()
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  private String getStringState() {
    if (bleManager.isAny(BleManagerState.OFF)) {
      return "poweredOff";
    } else if (bleManager.isAny(BleManagerState.TURNING_OFF)) {
      return "turningOff";
    } else if (bleManager.isAny(BleManagerState.ON)) {
      return "poweredOn";
    } else if (bleManager.isAny(BleManagerState.TURNING_ON)) {
      return "turningOn";
    } else {
      return "unknown";
    }
  }
}

