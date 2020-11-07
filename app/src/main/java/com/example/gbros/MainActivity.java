package com.example.gbros;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    Integer touchX=0;
    Integer touchY=0;
    String bleScan="";
    boolean touched =false;
    boolean scanned =false;
    char sendType = '0';
    boolean connected = false;
    boolean newimg=false;
    Bitmap bmp;
    Socket so = null;
    private static final int REQUEST_ENABLE_BT = 0;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        // recuperiamo un riferimento all'adapter Bluetooth
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        // verifichiamo che Bluetooth sia attivo nel dispositivo
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);

        }
        final BluetoothLeScanner scanner=bluetoothAdapter.getBluetoothLeScanner();
        final ScanCallback callback=new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                    System.out.println(result.getDevice().getAddress());
                    System.out.println(result.getDevice().getName());
                    System.out.println(result.getRssi());
                    System.out.println(result.getTxPower());
                    Integer rssi = result.getRssi();
                    bleScan = result.getDevice().getAddress()+";"+rssi.toString();
                    scanned=true;
            }
        };

        final ImageView img= findViewById(R.id.imageView);
        View v =findViewById(R.id.view);
        v.setOnTouchListener(handleTouch);
        final Thread sendThread= new Thread() {
            @Override
            public void run(){
                while(connected){
                    try {
                        if(sendType=='b' && scanned) {
                            String s =bleScan;
                            byte[] toSend = s.getBytes(StandardCharsets.UTF_8);
                            byte[] sendLen = ByteBuffer.allocate(4).putInt(toSend.length).array();
                            so.getOutputStream().write(sendLen);
                            so.getOutputStream().flush();
                            so.getOutputStream().write(toSend);
                            so.getOutputStream().flush();
                            scanned=false;
                        } if(sendType=='t' && touched) {
                            String s = sendType + touchX.toString() + ";" + touchY.toString();
                            byte[] toSend = s.getBytes(StandardCharsets.UTF_8);
                            byte[] sendLen = ByteBuffer.allocate(4).putInt(toSend.length).array();
                            so.getOutputStream().write(sendLen);
                            so.getOutputStream().flush();
                            so.getOutputStream().write(toSend);
                            so.getOutputStream().flush();
                            touched=false;
                        }
                    } catch (SocketException e) {
                        connected = false;
                    } catch (IOException e) {
                        connected = false;
                    }
                }
            }
        };

        Thread recvThred= new Thread(){
            @Override
            public void run(){
                while (true) {
                    while (!connected) {
                        try {
                            so = new Socket("192.168.0.104", 10000);
                            connected = true;
                        } catch (IOException e) { }
                    }
                        //BufferedReader rd= new BufferedReader(new InputStreamReader(so.getInputStream()));
                        //BufferedWriter wr= new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
                        /*Bitmap b= BitmapFactory.decodeStream(so.getInputStream());
                        if (b!=null)
                            System.out.println(b.getPixel(1, 3));*/
                    sendType = '0';
                    sendThread.start();
                    byte[] stream = null;
                    while (connected) {
                        try {
                            byte[] l = new byte[4];
                            so.getInputStream().read(l);
                            int len = convertByteArrayToInt2(l);
                            byte[] b= new byte[1];
                            char recvType = (char) b[0];
                            if(len-1>0){
                                stream = new byte[len-1];
                                so.getInputStream().read(stream);
                            }
                            if(recvType=='b'){
                                if(sendType=='b'){
                                    sendType='0';
                                    scanner.stopScan(callback);
                                } else {
                                    scanned = false;
                                    scanner.startScan(callback);
                                    sendType = 'b';
                                }
                            } else if(recvType=='t'){
                                if(sendType=='t'){
                                    sendType='0';
                                } else {
                                    touched = false;
                                    sendType = 't';
                                }
                            } else if(recvType=='i'){
                                bmp = BitmapFactory.decodeByteArray(stream, 0, stream.length);
                                newimg = true;
                            }
                            //System.out.println(stream[0]);
                            //Bitmap bmp=BitmapFactory.decodeByteArray(stream,0,stream.length);
                            //System.out.println(bmp.getHeight());
                            //System.out.println(bmp.getWidth());
                            //wr.write(x.toString()+" "+y.toString());
                            //wr.flush();
                        } catch (SocketException e) {
                            connected = false;
                        } catch (IOException e) {
                            connected = false;
                        }
                    }

                }
            }
        };
        recvThred.start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final Handler imgHandler=new Handler();
        imgHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                //your code
                if (newimg) {
                    img.setImageBitmap(bmp);
                    img.getLayoutParams().height=bmp.getHeight()*2;
                    img.getLayoutParams().width=bmp.getWidth()*2;
                    newimg=false;
                }
                imgHandler.postDelayed(this,10);
            }
        },10);
    }

    public static int convertByteArrayToInt2(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                ((bytes[3] & 0xFF) << 0);
    }

    private View.OnTouchListener handleTouch = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            touchX = (int) event.getRawX();
            touchY = (int) event.getRawY();
            touched=true;

            return true;
        }
    };
}
