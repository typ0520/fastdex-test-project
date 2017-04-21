package com.github.typ0520.instantrun.client;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public static void main(String[] args) throws IOException {
        IDevice device;
        AndroidDebugBridge.init(false);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(
                "/Users/tong/Applications/android-sdk-macosx/platform-tools/adb", false);
        waitForDevice(bridge);
        IDevice devices[] = bridge.getDevices();
        //device = devices[0];

        System.out.println(devices.length);

        for (IDevice d : devices) {
            System.out.println(d);
        }

        ServerSocket serverSocket = new ServerSocket(46622);
        //mAppService = new ServiceCommunicator(packageName, logger, port);

        ServiceCommunicator serviceCommunicator = new ServiceCommunicator("com.dx168.fastdex.sample",new NullLogger(),46622);
        serviceCommunicator.talkToService(devices[0], new Communicator<Boolean>() {
            @Override
            Boolean communicate(DataInputStream input, DataOutputStream output) throws IOException {
                output.writeInt(ProtocolConstants.MESSAGE_SHOW_TOAST);
                output.writeUTF("哈哈哈哈1");
                return false;
            }
        });

        ServiceCommunicator serviceCommunicator2 = new ServiceCommunicator("com.dx168.fastdex.sample",new NullLogger(),46622);
        serviceCommunicator2.talkToService(devices[1], new Communicator<Boolean>() {
            @Override
            Boolean communicate(DataInputStream input, DataOutputStream output) throws IOException {
                output.writeInt(ProtocolConstants.MESSAGE_SHOW_TOAST);
                output.writeUTF("哈哈哈哈2");
                return false;
            }
        });

        serviceCommunicator.talkToService(devices[0], new Communicator<Boolean>() {
            @Override
            Boolean communicate(DataInputStream input, DataOutputStream output) throws IOException {
                output.writeInt(ProtocolConstants.MESSAGE_SHOW_TOAST);
                output.writeUTF("哈哈哈哈3");
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
