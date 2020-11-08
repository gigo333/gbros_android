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
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    Integer touchX=0;
    Integer touchY=0;
    String bleScan="";
    boolean touched =false;
    boolean scanned =false;
    ArrayList<Character> sendTypes = new ArrayList<>();
    boolean connected = false;
    boolean newimg=false;
    Bitmap bmp;
    Socket so = null;
    private static final int REQUEST_ENABLE_BT = 0;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        //Bluetooth
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
                    /*System.out.println(result.getDevice().getAddress());
                    System.out.println(result.getDevice().getName());
                    System.out.println(result.getRssi());
                    System.out.println(result.getTxPower());*/
                    Integer rssi = result.getRssi();
                    bleScan =rssi.toString()+"+"+result.getDevice().getName()+",";
                    scanned=true;
            }
        };

        //MAC address
        final String macAddress = getMacAddr();

        //Image
        final ImageView img= findViewById(R.id.imageView);
        View v =findViewById(R.id.view);

        //Touch
        v.setOnTouchListener(handleTouch);

        // Threads
        final Thread sendThread= new Thread() {
            @Override
            public void run(){
                while(connected){
                    try {
                        String sendString="";

                        if(sendTypes.contains('b') && scanned) {
                            //sendString += 'b' + bleScan + '\n';
                            scanned=false;
                            //System.out.println("Sending BLE");
                        } else if(sendTypes.contains('t') && touched) {
                            sendString += 't' + touchX.toString() + "," + touchY.toString()+'\n';
                            //System.out.println(sendString);
                            touched=false;
                            //System.out.println("Sending touch");
                        }

                        if(sendString.length()>0){
                            byte[] toSend = sendString.getBytes(StandardCharsets.UTF_8);
                            //byte[] sendLen = ByteBuffer.allocate(4).putInt(toSend.length).array();
                            //so.getOutputStream().write(sendLen);
                            //so.getOutputStream().flush();
                            so.getOutputStream().write(toSend);
                            so.getOutputStream().flush();
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
                            so = new Socket("93.50.231.147", 10000);
                            byte[] toSend = macAddress.getBytes(StandardCharsets.UTF_8);
                            so.getOutputStream().write(toSend);
                            so.getOutputStream().flush();
                            connected = true;
                        } catch (IOException e) { e.printStackTrace(); }
                    }
                        //BufferedReader rd= new BufferedReader(new InputStreamReader(so.getInputStream()));
                        //BufferedWriter wr= new BufferedWriter(new OutputStreamWriter(so.getOutputStream()));
                        /*Bitmap b= BitmapFactory.decodeStream(so.getInputStream());
                        if (b!=null)
                            System.out.println(b.getPixel(1, 3));*/
                    sendTypes.clear();
                    if(sendThread.isAlive()) {
                        connected = false;
                        try {
                            sendThread.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        connected = true;
                    }
                    sendThread.start();
                    byte[] stream = null;
                    String params=null;
                    long lastTime=0;
                    while (connected) {
                        try {
                            byte[] l = new byte[4];
                            so.getInputStream().read(l);
                            int len = convertByteArrayToInt2(l);
                            //System.out.print("LEN: ");
                            //System.out.println(len);
                            byte[] b= new byte[1];
                            so.getInputStream().read(b);
                            char recvType = (char) b[0];

                            int recv_len = len - 1;
                            if(recv_len>0){

                                stream = new byte[recv_len];

                                int received_len = 0;
                                int old_received_len = 0;
                                byte[] chunk = null;

                                while(recv_len != received_len) {
                                    chunk = new byte[recv_len - received_len];
                                    old_received_len = received_len;
                                    received_len += so.getInputStream().read(chunk);
                                    System.arraycopy(chunk, 0, stream, old_received_len, chunk.length);
                                    //System.out.print("RECV LEN: ");
                                    //System.out.println(received_len);
                                }

                            }
                            if(recvType=='l') {
                                byte[] toSend = "l".getBytes(StandardCharsets.UTF_8);
                                so.getOutputStream().write(toSend);
                                so.getOutputStream().write(stream);
                                toSend = "\n".getBytes(StandardCharsets.UTF_8);
                                so.getOutputStream().write(toSend);
                                so.getOutputStream().flush();
                                if(!sendTypes.contains('t'))
                                    sendTypes.add((Character) 't');
                            } else if(recvType=='b'){
                                params=new String(stream, StandardCharsets.UTF_8);
                                if(params.contains("1")){
                                    //System.out.println("starting BLE scan");
                                    sendTypes.remove((Character) 't');
                                    scanner.stopScan(callback);
                                } else if (params.contains("0")){
                                    //System.out.println("stopping BLE scan");
                                    scanned = false;
                                    scanner.startScan(callback);
                                    if(!sendTypes.contains('b'))
                                        sendTypes.add((Character) 'b');
                                }
                            } else if(recvType=='t'){
                                params=new String(stream, StandardCharsets.UTF_8);
                                if(params.contains("1")){
                                    //System.out.println("starting touch");
                                    sendTypes.remove((Character) 't');
                                } else if (params.contains("0")) {
                                    //System.out.println("stopping touch");
                                    touched = false;
                                    if(!sendTypes.contains('t'))
                                        sendTypes.add((Character)'t');
                                }
                            } else if(recvType=='d' || recvType=='v'){
                                if(stream.length>1) {
                                    System.out.println(System.currentTimeMillis() - lastTime);
                                    lastTime = System.currentTimeMillis();
                                    System.out.println("Received frame");
                                    bmp = BitmapFactory.decodeByteArray(stream, 0, stream.length);
                                    newimg = true;
                                }
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

        //Display image runnable
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

    // Get device's WIFI MAC adress
    public static String getMacAddr() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(Integer.toHexString(b & 0xFF));
                }

                return res1.toString();
            }
        } catch (Exception ex) {
            //handle exception
        }
        return "";
    }

    //Touchscreen listener
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
