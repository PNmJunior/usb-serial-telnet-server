package com.clusterrr.usbserialtelnetserver;

import android.util.Log;
import android.view.ViewDebug;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import tech.gusavila92.websocketclient.WebSocketClient;
public class UsbSerialThread extends Thread {
    final static int WRITE_TIMEOUT = 1000;

    private final Object mReadBufferLock = new Object();
    private ByteBuffer mReadBuffer;
    private SerialInputOutputManager.Listener mListener; // Synchronized by 'this'
    public synchronized SerialInputOutputManager.Listener getListener() {return mListener;}
    private UsbSerialTelnetService mUsbSerialTelnetService;
    private WebSocketClient webSocketClient;
    private UsbSerialPort mSerialPort;

    public UsbSerialThread(UsbSerialTelnetService usbSerialTelnetService, UsbSerialPort serialPort, WebSocketClient wsc) {
        mUsbSerialTelnetService = usbSerialTelnetService;
        mSerialPort = serialPort;
        webSocketClient = wsc;
        //mListener;
        int llll = serialPort.getReadEndpoint().getMaxPacketSize();
        Log.d("Max velikost", Integer.toString(llll));
        mReadBuffer = ByteBuffer.allocate(serialPort.getReadEndpoint().getMaxPacketSize());
    }

    private String nalez;
    public  void zpracovani(String input)
    {
        nalez += input;
        Log.i("nalez:", nalez);
        int start = nalez.indexOf(";s");
        if (start != -1)
        {
            int stop = nalez.indexOf(";t");
            if(stop != -1)
            {
                webSocketClient.send(nalez.substring(start+2,stop));
                nalez = nalez.substring(stop + 2);
                zpracovani("");
            }
            else
            {
                nalez = nalez.substring(start);
            }
        }
    }

    @Override
    public void run() {
        //byte buffer[] = new byte[1024];
        try {
            while (true) {
                byte[] buffer;
                synchronized (mReadBufferLock) {
                    buffer = mReadBuffer.array();
                }
                if (mSerialPort == null) break;
                // Read data
                int l = mSerialPort.read(buffer, 0);
                if (l <= 0) break; // disconnect
                int len = l;
                if (len > 0) {
                    if (true) {
                        Log.d("TAG", "Read data len=" + len);
                    }
                    final SerialInputOutputManager.Listener listener = getListener();
                    if (listener != null) {
                        final byte[] data = new byte[len];
                        System.arraycopy(buffer, 0, data, 0, len);
                        listener.onNewData(data);
                    }
                }
                // Write data
                mUsbSerialTelnetService.writeClients(buffer, 0, l);
                final byte[] data2 = new byte[len];
                System.arraycopy(buffer, 0, data2, 0, len);
                Log.i("pridavani", new String(data2,StandardCharsets.UTF_8));
                zpracovani(new String(data2,StandardCharsets.UTF_8));
                //mUsbSerialTelnetService.writeClients(";s;e;t".getBytes(StandardCharsets.UTF_8), 0, l);
                //webSocketClient.send(";b");
            }
        }
        catch (IOException e) {
            Log.i(UsbSerialTelnetService.TAG, "Serial port: " + e.getMessage());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        close();
        Log.i(UsbSerialTelnetService.TAG, "Serial port closed");
        mUsbSerialTelnetService.stopSelf();
    }

    public void write(byte[] data) throws IOException {
        if (mSerialPort != null)
            mSerialPort.write(data, WRITE_TIMEOUT);
    }

    public void close() {
        try {
            if (mSerialPort != null)
                mSerialPort.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        mSerialPort = null;
    }
}
