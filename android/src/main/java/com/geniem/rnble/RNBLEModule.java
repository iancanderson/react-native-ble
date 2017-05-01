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
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.BleDeviceState;
import com.idevicesinc.sweetblue.BleManager.DiscoveryListener;
import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleManagerConfig.ScanFilter;

class RNBLEModule extends ReactContextBaseJavaModule {
  private Context context;
  private BluetoothAdapter bluetoothAdapter;

  public RNBLEModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
  }

  @Override
  public void initialize() {
    super.initialize();
    Log.i("", "RNSweetBlue initialized");
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
    if (bluetoothAdapter == null) {
      params.putString("state", "unsupported");
    } else {
      params.putString("state", stateToString(bluetoothAdapter.getState()));
    }
    sendEvent("ble.stateChange", params);
  }

  @ReactMethod
  public void startScanning() {
    final ScanFilter scanFilter = new ScanFilter()
    {
        @Override public Please onEvent(ScanEvent e)
        {
          Log.i("", String.format("BLEBLE Received ScanEvent: %s", e.name_normalized()));
          return Please.acknowledgeIf(e.name_normalized().contains("axa"));
        }
    };

    Log.i("", "BLEBLE onCreate");

    // New BleDevice instances are provided through this listener.
    // Nested listeners then listen for connection and read results.
    // Obviously you will want to structure your actual code a little better.
    // The deep nesting simply demonstrates the async-callback-based nature of the API.
    final DiscoveryListener discoveryListener = new DiscoveryListener()
    {
      @Override public void onEvent(DiscoveryEvent e)
      {
        Log.i("", "BLEBLE Discovery event");

        if( e.was(LifeCycle.DISCOVERED) )
        {
          // e.device().connect(new BleDevice.StateListener()
          // {
          //   @Override public void onEvent(StateEvent e)
          //   {
          //     if( e.didEnter(BleDeviceState.INITIALIZED) )
          //     {
          //       String name = e.device().getName_normalized();
          //       Log.i("", String.format("BLEBLE DEVICE INITIALIZED: %s", name));
          //     }
          //   }
          // });
        }
      }
    };

    BleManager bleManager = BleManager.get(this.context);
    bleManager.startScan(scanFilter, discoveryListener);
  }

  private void sendEvent(String eventName, WritableMap params) {
    getReactApplicationContext()
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  private String stateToString(int state){
    switch (state) {
      case BluetoothAdapter.STATE_OFF:
        return "poweredOff";
      case BluetoothAdapter.STATE_TURNING_OFF:
        return "turningOff";
      case BluetoothAdapter.STATE_ON:
        return "poweredOn";
      case BluetoothAdapter.STATE_TURNING_ON:
        return "turningOn";
      default:
        return "unknown";
    }
  }
}

