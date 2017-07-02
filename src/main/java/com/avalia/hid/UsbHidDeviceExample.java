package com.avalia.hid;

import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * <p>Demonstrate the USB HID interface using a production Bitcoin Trezor</p>
 *
 * @since 0.0.1
 */
public class UsbHidDeviceExample implements HidServicesListener {

    private static final Integer VENDOR_ID = 0x3eb;
    private static final Integer PRODUCT_ID = 0xffffaa55;
    private static final int PACKET_LENGTH = 64;
    public static final String SERIAL_NUMBER = null;

    public static void main(String[] args) throws HidException {

        UsbHidDeviceExample example = new UsbHidDeviceExample();
        example.executeExample();

    }

    public void executeExample() throws HidException {

        // Configure to use custom specification
        HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();
        hidServicesSpecification.setAutoShutdown(true);
        hidServicesSpecification.setScanInterval(500);
        hidServicesSpecification.setPauseInterval(5000);
        hidServicesSpecification.setScanMode(ScanMode.SCAN_AT_FIXED_INTERVAL_WITH_PAUSE_AFTER_WRITE);

        // Get HID services using custom specification
        HidServices hidServices = HidManager.getHidServices(hidServicesSpecification);
        hidServices.addHidServicesListener(this);

        // Start the services
        System.out.println("Starting HID services.");
        hidServices.start();

        System.out.println("Enumerating attached devices...");

        // Provide a list of attached devices
        for (HidDevice hidDevice : hidServices.getAttachedHidDevices()) {
            System.out.println(hidDevice);
            System.out.println(hidDevice.getProductId());
        }

        // Open the device device by Vendor ID and Product ID with wildcard serial number
        HidDevice hidDevice = hidServices.getHidDevice(VENDOR_ID, PRODUCT_ID, SERIAL_NUMBER);
        if (hidDevice != null) {
            // Consider overriding dropReportIdZero on Windows
            // if you see "The parameter is incorrect"
            // HidApi.dropReportIdZero = true;

            // Device is already attached and successfully opened so send message
            //sendMessage(hidDevice);
            byte[] arr = new byte[256];
            int read = hidDevice.read(arr);
            System.out.println(arr);
        }

        System.out.printf("Waiting 30s to demonstrate attach/detach handling. Watch for slow response after write if configured.%n");

        // Stop the main thread to demonstrate attach and detach events
        sleepUninterruptibly(30, TimeUnit.SECONDS);

        // Shut down and rely on auto-shutdown hook to clear HidApi resources
        hidServices.shutdown();

    }

    public void hidDeviceAttached(HidServicesEvent event) {

        System.out.println("Device attached: " + event);

        // Add serial number when more than one device with the same
        // vendor ID and product ID will be present at the same time
        if (event.getHidDevice().isVidPidSerial(VENDOR_ID, PRODUCT_ID, null)) {
            sendMessage(event.getHidDevice());
        }

    }

    public void hidDeviceDetached(HidServicesEvent event) {

        System.err.println("Device detached: " + event);

    }

    public void hidFailure(HidServicesEvent event) {

        System.err.println("HID failure: " + event);

    }

    private void sendMessage(HidDevice hidDevice) {

        // Ensure device is open after an attach/detach event
        if (!hidDevice.isOpen()) {
            hidDevice.open();
        }

        // Send the Initialise message
        byte[] message = new byte[PACKET_LENGTH];
        message[0] = 0x3f; // USB: Payload 63 bytes
        message[1] = 0x23; // Device: '#'
        message[2] = 0x23; // Device: '#'
        message[3] = 0x00; // INITIALISE

        int val = hidDevice.write(message, PACKET_LENGTH, (byte) 0x00);
        if (val >= 0) {
            System.out.println("> [" + val + "]");
        } else {
            System.err.println(hidDevice.getLastErrorMessage());
        }

        // Prepare to read a single data packet
        boolean moreData = true;
        while (moreData) {
            byte data[] = new byte[PACKET_LENGTH];
            // This method will now block for 500ms or until data is read
            val = hidDevice.read(data, 500);
            switch (val) {
                case -1:
                    System.err.println(hidDevice.getLastErrorMessage());
                    break;
                case 0:
                    moreData = false;
                    break;
                default:
                    System.out.print("< [");
                    for (byte b : data) {
                        System.out.printf(" %02x", b);
                    }
                    System.out.println("]");
                    break;
            }
        }
    }

    /**
     * Invokes {@code unit.}{@link java.util.concurrent.TimeUnit#sleep(long) sleep(sleepFor)}
     * uninterruptibly.
     */
    public static void sleepUninterruptibly(long sleepFor, TimeUnit unit) {
        boolean interrupted = false;
        try {
            long remainingNanos = unit.toNanos(sleepFor);
            long end = System.nanoTime() + remainingNanos;
            while (true) {
                try {
                    // TimeUnit.sleep() treats negative timeouts just like zero.
                    NANOSECONDS.sleep(remainingNanos);
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                    remainingNanos = end - System.nanoTime();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
