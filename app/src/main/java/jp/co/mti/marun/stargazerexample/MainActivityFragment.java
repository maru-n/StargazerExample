package jp.co.mti.marun.stargazerexample;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.widget.Toast;


public class MainActivityFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {

    private final String TAG = this.getClass().getSimpleName();

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            MainActivityFragment.this.openSerialIOPort(device);
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };
    private static final String ACTION_USB_PERMISSION = "jp.co.mti.marun.stargazerexample.USB_PERMISSION";

    private SerialInputOutputManager mSerialIoManager;
    private TextView mRawDataTextView;
    private TextView mDataTextview;
    private Switch mLoggingSwitch;
    private NavigationDisplayView mNavDisplay;
    private PendingIntent mPermissionIntent;
    private BufferedWriter mLogWriter = null;

    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        mDataTextview = (TextView)view.findViewById(R.id.data_text);
        mRawDataTextView = (TextView)view.findViewById(R.id.raw_data_text);
        mLoggingSwitch = (Switch)view.findViewById(R.id.logging_switch);
        mNavDisplay = (NavigationDisplayView)view.findViewById(R.id.navigation_display);
        mLoggingSwitch.setOnCheckedChangeListener(this);

        mPermissionIntent = PendingIntent.getBroadcast(this.getActivity(), 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        this.getActivity().registerReceiver(mUsbReceiver, filter);
        setupUsbSerialIO();
        return view;

    }

    private void setupUsbSerialIO() {
        final UsbManager usbManager = (UsbManager) this.getActivity().getSystemService(this.getActivity().USB_SERVICE);

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            Log.w(TAG, "no available drivers.");
            return;
        }
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbSerialPort port = driver.getPorts().get(0);
        UsbDevice device = port.getDriver().getDevice();
        if (usbManager.hasPermission(device)) {
            openSerialIOPort(device);
        } else {
            usbManager.requestPermission(port.getDriver().getDevice(), mPermissionIntent);
        }
    }

    private void openSerialIOPort(UsbDevice device) {

        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        UsbSerialPort port = driver.getPorts().get(0);

        Activity activity = MainActivityFragment.this.getActivity();
        final UsbManager usbManager = (UsbManager)activity.getSystemService(activity.USB_SERVICE);
        UsbDeviceConnection connection = usbManager.openDevice(port.getDriver().getDevice());
        if (connection == null) {
            Log.d(TAG, "Opening device failed");
            return;
        }

        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
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

        mSerialIoManager = new SerialInputOutputManager(port, new SerialInputOutputManager.Listener() {
            private StringBuffer buffer = new StringBuffer();
            private final Pattern OutputPattern = Pattern.compile("~(.+?)`");
            @Override
            public void onRunError(Exception e) {
                Log.d(TAG, "Runner stopped.");
            }
            @Override
            public void onNewData(final byte[] data) {
                buffer.append(new String(data));
                String str = buffer.toString();
                Matcher m = OutputPattern.matcher(str);
                int lastMatchIndex = 0;
                while (m.find()) {
                    final String line = m.group();
                    try {
                        final StarGazerData sgdata = new StarGazerData(line);
                        try {
                            if (mLogWriter != null) {
                                mLogWriter.write(sgdata.toLogString());
                                mLogWriter.newLine();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (sgdata.isDeadZone) {

                        } else {
                            mNavDisplay.setCurrentPoint(sgdata);
                        }

                        ((Activity) MainActivityFragment.this.getActivity()).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mRawDataTextView.setText(sgdata.rawDataString);
                                mDataTextview.setText(sgdata.toXYString());
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    lastMatchIndex = m.end();
                }
                buffer.delete(0, lastMatchIndex);
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(mSerialIoManager);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        String toastMessage = "";
        if (isChecked) {
            try {
                File file = this.createNewLogFile();
                mLogWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
                mLogWriter.write("# unix_time[msec] id angle[degree] x[m] y[m] z[m]");
                mLogWriter.newLine();
                toastMessage = file.getPath();
            } catch (Exception e) {
                e.printStackTrace();
                mLogWriter = null;
                mLoggingSwitch.setChecked(false);
                toastMessage = e.getMessage();
            }
        } else {
            try {
                mLogWriter.flush();
                mLogWriter.close();
                mLogWriter = null;
                toastMessage = "Saved";
            } catch (IOException e) {
                e.printStackTrace();
                mLoggingSwitch.setChecked(true);
                toastMessage = e.getMessage();
            }
        }
        Toast.makeText(this.getActivity(), toastMessage, Toast.LENGTH_LONG).show();
    }

    private File createNewLogFile() {
        String filename = "/stargazer/";
        filename += DateFormat.format("yyyyMMdd-kkmmss", Calendar.getInstance());
        filename += ".log";

        String filePath = Environment.getExternalStorageDirectory() + filename;
        File file = new File(filePath);
        file.getParentFile().mkdir();
        return file;
    }
}
