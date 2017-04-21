package com.github.typ0520.instantrun.client;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.fd.common.ProtocolConstants;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MainClass {

    public static void main(String[] args) throws IOException {
        IDevice device;
        AndroidDebugBridge.initIfNeeded(false);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge("/Users/tong/Applications/android-sdk-macosx/platform-tools/adb", false);
        waitForDevice(bridge);
        IDevice devices[] = bridge.getDevices();
        System.out.println("device count: " + devices.length);
        for (IDevice d : devices) {
            System.out.println(d);
        }

        if (devices == null || devices.length <= 0) {
            return;
        }
        device = devices[0];
        if (device == null) {
            System.out.println("device not found");
            return;
        }

        ServiceCommunicator serviceCommunicator = new ServiceCommunicator("com.dx168.fastdex.sample",new NullLogger(),46622);
        showToast(serviceCommunicator,devices[0]);
        restartApp(serviceCommunicator,device);
    }

    private static void showToast(ServiceCommunicator serviceCommunicator,IDevice device) throws IOException {
        serviceCommunicator.talkToService(device, new Communicator<Boolean>() {
            @Override
            Boolean communicate(DataInputStream input, DataOutputStream output) throws IOException {
                output.writeInt(ProtocolConstants.MESSAGE_SHOW_TOAST);
                output.writeUTF("来自pc的数据");
                return false;
            }
        });
    }

    private static void restartApp(ServiceCommunicator serviceCommunicator,IDevice device) throws IOException {
        serviceCommunicator.talkToService(device, new Communicator<Boolean>() {
            @Override
            Boolean communicate(DataInputStream input, DataOutputStream output) throws IOException {
                output.writeInt(ProtocolConstants.MESSAGE_RESTART_ACTIVITY);
                output.writeLong(0L);
                return false;
            }
        });
    }

    private static void waitForDevice(AndroidDebugBridge bridge) {
        int count = 0;
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException ignored) {
            }
            if (count > 300) {
                System.err.print("Time out");
                break;
            }
        }
    }
}
