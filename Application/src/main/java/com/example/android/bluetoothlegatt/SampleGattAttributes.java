/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    public static String THINGY_MICROPHONE_CHARACTERISTIC = "ef680504-9b35-4933-9b10-52ffa9740042";


    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        //wanshih doppler radar
        attributes.put("0000ffe0-0000-1000-8000-00805f9b34fb", "Doppler radar Info");
        attributes.put("0000ffe1-0000-1000-8000-00805f9b34fb", "Info from BLE Module");
        //FLowEz pbm
        attributes.put("ef680500-9b35-4933-9b10-52ffa9740042", "Thingy sound service");
        attributes.put("ef680501-9b35-4933-9b10-52ffa9740042", "Config characteristic");
        attributes.put("ef680502-9b35-4933-9b10-52ffa9740042", "Speaker data characteristic");
        attributes.put("ef680503-9b35-4933-9b10-52ffa9740042", "Speaker status characteristic");
        attributes.put("ef680504-9b35-4933-9b10-52ffa9740042", "Microphone characteristic");

    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
