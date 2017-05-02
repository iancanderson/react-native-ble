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

import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
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


  @ReactMethod
  public void discoverServices(final String peripheralUuid, ReadableArray _uuids){
    BleDevice device = bleManager.getDevice(peripheralUuid);
    WritableArray serviceUuids = Arguments.createArray();

    for (UUID uuid : device.getAdvertisedServices()) {
      serviceUuids.pushString(toNobleUuid(uuid.toString()));
    }

    WritableMap params = Arguments.createMap();
    params.putString("peripheralUuid", peripheralUuid);
    params.putArray("serviceUuids", serviceUuids);

    sendEvent("ble.servicesDiscover", params);
  }

  @ReactMethod
  public void discoverCharacteristics(final String peripheralUuid, final String serviceUuid, ReadableArray _characteristicUuids){
    BleDevice device = bleManager.getDevice(peripheralUuid);
    WritableArray requestedCharacteristics = Arguments.createArray();

    List<BluetoothGattCharacteristic> nativeCharacteristics = device.getNativeCharacteristics_List();

    for(BluetoothGattCharacteristic c : nativeCharacteristics) {
      WritableArray properties = Arguments.createArray();
      int propertyBitmask = c.getProperties();

      if((propertyBitmask & BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0){
        properties.pushString("broadcast");
      }

      if((propertyBitmask & BluetoothGattCharacteristic.PROPERTY_READ) != 0){
        properties.pushString("read");
      }

      if((propertyBitmask & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0){
        properties.pushString("writeWithoutResponse");
      }

      if((propertyBitmask & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0){
        properties.pushString("write");
      }

      if((propertyBitmask & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0){
        properties.pushString("notify");
      }

      if((propertyBitmask & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0){
        properties.pushString("indicate");
      }

      if((propertyBitmask & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0){
        properties.pushString("authenticatedSignedWrites");
      }

      if((propertyBitmask & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0){
        properties.pushString("extendedProperties");
      }

      WritableMap characteristicObject = Arguments.createMap();
      characteristicObject.putArray("properties", properties);
      characteristicObject.putString("uuid", toNobleUuid(c.getUuid().toString()));

      requestedCharacteristics.pushMap(characteristicObject);
    }

    WritableMap params = Arguments.createMap();
    params.putString("peripheralUuid", peripheralUuid);
    params.putString("serviceUuid", toNobleUuid(serviceUuid));
    params.putArray("characteristics", requestedCharacteristics);
    this.sendEvent("ble.characteristicsDiscover", params);
  }

  private String toNobleUuid(String uuid) {
    String result = uuid.replaceAll("[\\s\\-()]", "");
    return result.toLowerCase();
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

