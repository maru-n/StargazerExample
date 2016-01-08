package jp.co.mti.marun.stargazer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by maruyama_n on 2015/12/21.
 */
public class StarGazerManager implements SerialInputOutputManager.Listener {

    private static final String ACTION_USB_PERMISSION = "jp.co.mti.marun.stargazer.USB_PERMISSION";
    private static final int BAUD_RATE = 115200;
    private final String TAG = this.getClass().getSimpleName();
    private final Pattern OutputPattern = Pattern.compile("~(.+?)`");


    private UsbManager mUsbManager = null;
    private PendingIntent mPermissionIntent;
    private SerialInputOutputManager mSerialIoManager = null;
    private StarGazerListener mListener = null;
    private StringBuffer buffer = new StringBuffer();

    private final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);

                            UsbSerialPort port = driver.getPorts().get(0);
                            StarGazerManager.this.openSerialIOPort(port);
                        }
                    }
                    else {
                        Log.w(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };


    public StarGazerManager(Context context) {
        mUsbManager = (UsbManager) context.getSystemService(context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        //mPermissionIntent = PendingIntent.getActivity(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        //mPermissionIntent = PendingIntent.getService(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);

        context.registerReceiver(mUsbPermissionReceiver, filter);
    }

    public void connect() throws StarGazerException {
        UsbSerialPort port = findDefaultPort();
        UsbDevice device = port.getDriver().getDevice();
        if (mUsbManager.hasPermission(device)) {
            openSerialIOPort(port);
        } else {
            mUsbManager.requestPermission(device, mPermissionIntent);
        }
    }

    public void setListener(StarGazerListener listener) {
        mListener = listener;
    }

    public void removeListener() {
        mListener = null;
    }

    private UsbSerialPort findDefaultPort() throws StarGazerException {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);
        if (availableDrivers.isEmpty()) {
            StarGazerException e = new StarGazerException("no available drivers.");
            Log.w(TAG, e.getMessage());
            throw e;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbSerialPort port = driver.getPorts().get(0);

        return port;
    }

    private void openSerialIOPort(UsbSerialPort port) {
        UsbDeviceConnection connection = mUsbManager.openDevice(port.getDriver().getDevice());
        if (connection == null) {
            Log.d(TAG, "Opening device failed");
            return;
        }

        try {
            port.open(connection);
            port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (IOException e) {
            Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
            Log.d(TAG, "Error opening device: " + e.getMessage());
            try {
                port.close();
            } catch (IOException e2) {
                e.printStackTrace();
            }
            return;
        }
        Log.d(TAG, "Serial device: " + port.getClass().getSimpleName());

        mSerialIoManager = new SerialInputOutputManager(port, this);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(mSerialIoManager);
    }

    private void callOnNewDataListener(StarGazerData d) {
        if (mListener != null) {
            mListener.onNewData(d);
        }
    }

    private void callOnErrorListener(StarGazerException e) {
        if (mListener != null) {
            mListener.onError(e);
        }
    }

    @Override
    public void onNewData(byte[] bytes) {
        buffer.append(new String(bytes));
        String str = buffer.toString();
        Matcher m = OutputPattern.matcher(str);
        int lastMatchIndex = 0;
        while (m.find()) {
            final String line = m.group();
            try {
                final StarGazerData data = new StarGazerData(line);
                Log.d(TAG, data.rawDataString);
                callOnNewDataListener(data);
            } catch (StarGazerException e) {
                callOnErrorListener(e);
            }
            lastMatchIndex = m.end();
        }
        buffer.delete(0, lastMatchIndex);
    }

    @Override
    public void onRunError(Exception e) {
        Log.d(TAG, "Runner stopped.");
        StarGazerException sge = new StarGazerException("SerialInputOutputManager error.", e);
        callOnErrorListener(sge);
    }
}
