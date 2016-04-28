package com.android.capstone.bbs;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;


public class MainActivity extends Activity  implements NfcAdapter.CreateNdefMessageCallback, NfcAdapter.OnNdefPushCompleteCallback {

    //Nfc Field
    NfcAdapter mNfcAdapter;
    private static final int NFC_MESSAGE_SENT = -1;
    private String other_device_macaddr = "";

    //Bluetooth Field
    private static final int REQUEST_ENABLE_BT = 3;
    private BluetoothAdapter mBluetoothAdapter = null;
    private String mConnectedDeviceName = null;
    private BluetoothService mService = null;
    private static boolean IS_RECEIVER = false; // false : this is sender, true : this is receiver
    private static boolean IS_PLAYING = false; // true : playing, false : not playing


    //chatting Field
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mDataSendBtn;
    private Button mSendButton;
    private ArrayAdapter<String> mConversationArrayAdapter;
    private StringBuffer mOutStringBuffer;

    //music Field
    Thread sendThread = null;
    String filePath = null;
    File myDir = null;
    File file = null;


    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NFC_MESSAGE_SENT:
                    Toast.makeText(getApplicationContext(),"sent_macaddress for connecting bluetooth"
                            , Toast.LENGTH_LONG).show();
                    break;
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.FILE:
                    readBuf = (byte[]) msg.obj;

                    try {
                        file = new File(filePath + "/song.mp3");
                        FileOutputStream fis = new FileOutputStream(file);
                        fis.write(readBuf);
                        fis.close();
                    } catch (IOException e) {}

                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);

                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();

                    break;
                case Constants.MESSAGE_TOAST:

                    Toast.makeText(getApplicationContext(), msg.getData().getString(Constants.TOAST),
                            Toast.LENGTH_SHORT).show();

                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myDir = getExternalFilesDir("mydir");
        filePath = myDir.getAbsolutePath();

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            ActionBar bar = getActionBar();
            bar.setSubtitle("NFC_NOT_CONNECTED");
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mConversationView = (ListView) findViewById(R.id.in);
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mSendButton = (Button) findViewById(R.id.button_send);
        mDataSendBtn = (Button) findViewById(R.id.button_data_send);


        mNfcAdapter.setNdefPushMessageCallback(this, this);
        mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
    }

    public NdefRecord createMimeRecord(String mimeType, byte[] payload) {
        byte[] mimeBytes = mimeType.getBytes(Charset.forName("US-ASCII"));
        NdefRecord mimeRecord = new NdefRecord(
                NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], payload);
        return mimeRecord;
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        String macAddr = mBluetoothAdapter.getAddress();
        NdefMessage msg = new NdefMessage(
                new NdefRecord[] { createMimeRecord(
                        "text/plain", macAddr.getBytes())
                });
        return msg;
    }

    @Override
    public void onNdefPushComplete(NfcEvent event) {
        mHandler.obtainMessage(NFC_MESSAGE_SENT).sendToTarget();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    protected synchronized void onPause() {
        super.onPause();
        if (mNfcAdapter != null) mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reset();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (mService == null) setupChat();
        }
    }

    public void reset(){
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
        if (mService != null) {
            if (mService.getState() == BluetoothService.STATE_NONE) {
                mService.start();
            }
        }
    }
    private void setupBluetooth() {
        mService = new BluetoothService(this, mHandler);
    }

    void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        other_device_macaddr = new String(msg.getRecords()[0].getPayload());
        connectDevice(true);
    }

    private void connectDevice(boolean secure) {
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(other_device_macaddr);
        mService.connect(device, secure);
    }

    private void setStatus(CharSequence subTitle) {

        final ActionBar actionBar = this.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    private void setStatus(int resId) {

        final ActionBar actionBar = this.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }


    //메인 액티비티 화면에서 실제적으로 서비스를 제공하는 메소드.
    private void setupChat() {


        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);


        //메세지 send 기능을 구현
        // Initialize the send button with a listener that for click events
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget

                TextView textView = (TextView) findViewById(R.id.edit_text_out);
                String message = textView.getText().toString();
                sendMessage(message);
            }

        });

        mDataSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //send data file
                try {
                    file = new File(filePath + "/song.mp3");
                    InputStream inputStream = getResources().openRawResource(R.raw.song);
                    FileOutputStream outputStream = new FileOutputStream(file);
                    byte[] txt = new byte[inputStream.available()];
                    inputStream.read(txt);
                    outputStream.write(txt);
                    inputStream.close();
                    outputStream.close();

                } catch (IOException e) {}

                sendFile(file);

            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        setupBluetooth();

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };

    private void sendFile(File f) {
        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        if (f.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            InputStream is = null;
            byte[] buf = new byte[1024];

            try {
                is = new FileInputStream(f);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            int nread = 0;
            while (nread != -1) {
                try {
                    nread = is.read(buf, 0, 1024);
                    mService.file_send(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

}
