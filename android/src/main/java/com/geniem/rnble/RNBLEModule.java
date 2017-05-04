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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.LifecycleEventListener;

import com.idevicesinc.sweetblue.BleDevice;
import com.idevicesinc.sweetblue.BleDeviceState;
import com.idevicesinc.sweetblue.BleManager.DiscoveryListener;
import com.idevicesinc.sweetblue.BleManager;
import com.idevicesinc.sweetblue.BleManagerConfig.ScanFilter;
import com.idevicesinc.sweetblue.BleManagerConfig;
import com.idevicesinc.sweetblue.BleManagerState;
import com.idevicesinc.sweetblue.DeviceStateListener;

class RNBLEModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
  private Context context;
  private BleManager bleManager;
  private static final String TAG = "RNBLE";

  public RNBLEModule(ReactApplicationContext reactContext) {
    super(reactContext);
    context = reactContext;
  }

  @Override
  public void initialize() {
    super.initialize();
    BleManagerConfig managerConfig = new BleManagerConfig();
    managerConfig.allowDuplicatePollEntries = true;

    bleManager = BleManager.get(this.context, managerConfig);
    bleManager.setListener_State(new BleManager.StateListener() {
      @Override public void onEvent(BleManager.StateListener.StateEvent e) {
        boolean didEnterTrackedState = e.didEnterAny(BleManagerState.ON, BleManagerState.OFF, BleManagerState.TURNING_OFF, BleManagerState.TURNING_ON);

        if (didEnterTrackedState) {
          sendStateChangeEvent();
        }
      }
    });
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
    sendStateChangeEvent();
  }

  @ReactMethod
  public void startScanning(ReadableArray _serviceUuids, Boolean _allowDuplicates) {
    final ScanFilter scanFilter = new ScanFilter() {
      @Override public Please onEvent(ScanEvent e) {
        return Please.acknowledgeIf(e.name_normalized().contains("axa"));
      }
    };

    final DiscoveryListener discoveryListener = new DiscoveryListener() {
      @Override public void onEvent(DiscoveryEvent e) {
        Log.d("RNBLE", "Discovery event");

        if( e.was(LifeCycle.DISCOVERED) || e.was(LifeCycle.REDISCOVERED) ) {
          sendDiscoveryEvent(e.device());
        }
      }
    };

    Log.d("RNBLE", "startScanning");
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

        if (e.didEnter(BleDeviceState.DISCONNECTED)) {
          WritableMap params = Arguments.createMap();
          params.putString("peripheralUuid", e.macAddress());
          sendEvent("ble.disconnect", params);
        }
        if (e.didEnter(BleDeviceState.DISCOVERING_SERVICES)) {
          Log.d("RNBLE", "DISCOVERING_SERVICES");
        }
        if (e.didEnter(BleDeviceState.SERVICES_DISCOVERED)) {
          Log.d("RNBLE", "SERVICES_DISCOVERED");
          sendServicesDiscoverEvent(e.device(), e.macAddress());
        }
      }
    });
  }


  @ReactMethod
  public void discoverServices(final String _peripheralUuid, ReadableArray _uuids){
    Log.d("RNBLE", "discoverServices is a no-op - service discovery happens after connecting");
  }

  @ReactMethod
  public void discoverCharacteristics(final String peripheralUuid, final String serviceUuid, ReadableArray _characteristicUuids){
    Log.d("RNBLE", "discoverCharacteristics");

    BleDevice device = bleManager.getDevice(peripheralUuid);
    WritableArray requestedCharacteristics = Arguments.createArray();

    List<BluetoothGattCharacteristic> nativeCharacteristics = device.getNativeCharacteristics_List();
    Log.d("RNBLE", String.format("Characterstic count: %d", nativeCharacteristics.size()));

    for(BluetoothGattCharacteristic c : nativeCharacteristics) {
      Log.d("RNBLE", String.format("Characterstic: %s", c.getUuid().toString()));

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
    sendEvent("ble.characteristicsDiscover", params);
  }

  @ReactMethod
  public void disconnect(final String peripheralUuid) {
    Log.d(TAG, "disconnecting from device");
    BleDevice device = bleManager.getDevice(peripheralUuid);
    device.disconnect();

    WritableMap params = Arguments.createMap();
    params.putString("peripheralUuid", peripheralUuid);
    sendEvent("ble.disconnect.", params);
  }

  @ReactMethod
  public void notify(final String peripheralUuid, final String serviceUuidString, final String characteristicUuidString, Boolean notify){
    UUID serviceUuid = UUID.fromString(serviceUuidString);
    UUID characteristicUuid = UUID.fromString(characteristicUuidString);

    BleDevice device = bleManager.getDevice(peripheralUuid);
    if (notify) {
      Log.d("RNBLE", "Enabling notifications");

      device.enableNotify(characteristicUuid, new BleDevice.ReadWriteListener() {
        @Override public void onEvent(BleDevice.ReadWriteListener.ReadWriteEvent e) {
          if (e.status() == BleDevice.ReadWriteListener.Status.SUCCESS) {
            Log.d("RNBLE", "Read notification succeeded");

            sendDataEvent(
              peripheralUuid,
              serviceUuidString,
              characteristicUuidString,
              e.data(),
              true
            );
          } else {
            Log.w("RNBLE", String.format("Write failed with status: %s", e.status().toString()));
          }
        }
      });
    } else {
      Log.d("RNBLE", "Disabling notififcations");
      device.disableNotify(characteristicUuid);
    }

    WritableMap params = Arguments.createMap();
    params.putString("peripheralUuid", peripheralUuid);
    params.putString("serviceUuid", toNobleUuid(serviceUuidString));
    params.putString("characteristicUuid", toNobleUuid(characteristicUuidString));
    params.putBoolean("state", notify);
    sendEvent("ble.notify", params);
  }

  private void sendDataEvent(String peripheralUuid, String serviceUuidString, String characteristicUuidString, byte[] data, boolean isNotification) {
    WritableMap params = Arguments.createMap();

    params.putString("peripheralUuid", peripheralUuid);
    params.putString("serviceUuid", toNobleUuid(serviceUuidString));
    params.putString("characteristicUuid", toNobleUuid(characteristicUuidString));
    params.putString("data", Arrays.toString(data));
    params.putBoolean("isNotification", isNotification);

    Log.d("RNBLE", "Sending data event to JS");
    sendEvent("ble.data", params);
  }

  @ReactMethod
  public void write(final String peripheralUuid, final String serviceUuidString, final String characteristicUuidString, String data, Boolean withoutResponse){
    Log.d("RNBLE", "Writing to device");

    UUID serviceUuid = UUID.fromString(serviceUuidString);
    UUID characteristicUuid = UUID.fromString(characteristicUuidString);

    BleDevice device = bleManager.getDevice(peripheralUuid);
    byte[] byteArray = Base64.decode(data, Base64.DEFAULT);

    if (withoutResponse) {
      device.write(characteristicUuid, byteArray);
    } else {
      device.write(characteristicUuid, byteArray, new BleDevice.ReadWriteListener() {
        @Override public void onEvent(BleDevice.ReadWriteListener.ReadWriteEvent e) {
          if (e.status() == BleDevice.ReadWriteListener.Status.SUCCESS) {
            Log.d("RNBLE", "Write succeeded");
            WritableMap params = Arguments.createMap();

            params.putString("peripheralUuid", peripheralUuid);
            params.putString("serviceUuid", toNobleUuid(serviceUuidString));
            params.putString("characteristicUuid", toNobleUuid(characteristicUuidString));

            Log.d("RNBLE", "Sending write event");
            sendEvent("ble.write", params);
          } else {
            Log.w("RNBLE", String.format("Write failed with status: %s", e.status().toString()));
          }
        }
      });
    }
  }

  @ReactMethod
  public void read(final String peripheralUuid, final String serviceUuidString, final String characteristicUuidString) {
    Log.d("RNBLE", "Reading from device");

    UUID characteristicUuid = UUID.fromString(characteristicUuidString);

    BleDevice device = bleManager.getDevice(peripheralUuid);
    device.read(characteristicUuid, new BleDevice.ReadWriteListener() {
      @Override public void onEvent(BleDevice.ReadWriteListener.ReadWriteEvent e) {
        if (e.status() == BleDevice.ReadWriteListener.Status.SUCCESS) {
          Log.d("RNBLE", "Read succeeded");

          sendDataEvent(
            peripheralUuid,
            serviceUuidString,
            characteristicUuidString,
            e.data(),
            false
          );
        } else {
          Log.w("RNBLE", String.format("Write failed with status: %s", e.status().toString()));
        }
      }
    });
  }

  private String toNobleUuid(String uuid) {
    String result = uuid.replaceAll("[\\s\\-()]", "");
    return result.toLowerCase();
  }

  private void sendDiscoveryEvent(BleDevice device) {
    WritableMap params = Arguments.createMap();
    WritableMap advertisement = Arguments.createMap();

    WritableArray serviceUuids = Arguments.createArray();

    for(UUID uuid : device.getAdvertisedServices()) {
      serviceUuids.pushString(toNobleUuid(uuid.toString()));
    }

    advertisement.putArray("serviceUuids", serviceUuids);

    WritableArray serviceData = Arguments.createArray();
    WritableMap serviceDataMap = Arguments.createMap();

    Map<UUID,byte[]> serviceDataMapSource = device.getAdvertisedServiceData();

    for (UUID uuid : device.getAdvertisedServices()) {
      byte[] data = serviceDataMapSource.get(uuid);

      if(uuid != null && data != null){
        serviceDataMap.putString("uuid", toNobleUuid(uuid.toString()));
        serviceDataMap.putString("data", Arrays.toString(data));
        serviceData.pushMap(serviceDataMap);
      }
    }
    advertisement.putArray("serviceData", serviceData);

    advertisement.putString("localName", device.getName_normalized());
    advertisement.putInt("txPowerLevel", device.getTxPower());

    params.putMap("advertisement", advertisement);

    params.putInt("rssi", device.getRssi());
    params.putString("id", device.getMacAddress());
    params.putString("address", device.getMacAddress());
    params.putString("addressType", "unknown");
    params.putBoolean("connectable", device.isConnectable());

    sendEvent("ble.discover", params);
  }

  private void sendServicesDiscoverEvent(BleDevice device, String peripheralUuid) {
    WritableArray serviceUuids = Arguments.createArray();

    int servicesSize = device.getNativeServices_List().size();
    Log.d("RNBLE", String.format("Looping over BluetoothGattService: %d", servicesSize));

    for(BluetoothGattService service : device.getNativeServices_List()){
      String uuid = service.getUuid().toString();
      serviceUuids.pushString(toNobleUuid(uuid));
    }

    WritableMap params = Arguments.createMap();
    params.putString("peripheralUuid", peripheralUuid);
    params.putArray("serviceUuids", serviceUuids);

    Log.d("RNBLE", "Sending servicesDiscover");

    sendEvent("ble.servicesDiscover", params);
  }

  private void sendStateChangeEvent() {
    WritableMap params = Arguments.createMap();
    params.putString("state", getStringState());
    sendEvent("ble.stateChange", params);
  }

  private void sendEvent(String eventName, WritableMap params) {
    getReactApplicationContext()
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  private String getStringState() {
    if (!bleManager.isBleSupported()) {
      return "unsupported";
    } else if (bleManager.isAny(BleManagerState.OFF)) {
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

  @Override
  public void onHostResume() {
    Log.d(TAG, "onHostResume");
  }

  @Override
  public void onHostPause() {
    Log.v(TAG, "onHostPause");
    bleManager.disconnectAll();
  }

  @Override
  public void onHostDestroy() {
    Log.v(TAG, "onHostDestroy");
    bleManager.disconnectAll();
  }
}

