package com.jerry.client_app;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int MESSAGE_RECEIVE_NEW_MSG = 1;
    private static final int MESSAGE_SEND_MSG = 2;
    private static final int MESSAGE_SOCKET_CONNECTED = 3;
    private static final int MESSAGE_SOCKET_DISCONNECTED = 4;

    private TextView tvShowMessage;
    private EditText etMessageContent;
    private Button btnConnectServer;
    private Button btnSendMessage;

    private ExecutorService threadPool;

    private PrintWriter writer;
    private BufferedReader reader;
    private Socket clientSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvShowMessage = (TextView) findViewById(R.id.tv_show_message);
        etMessageContent = (EditText) findViewById(R.id.et_message_content);
        btnConnectServer = (Button) findViewById(R.id.btn_connect_server);
        btnSendMessage = (Button) findViewById(R.id.btn_send_message);

        btnConnectServer.setOnClickListener(this);
        btnSendMessage.setOnClickListener(this);

        threadPool = Executors.newCachedThreadPool();

        startChattingRoomService();
    }

    private void startChattingRoomService() {
        Intent intentToServer = new Intent("com.jerry.server.chatting");
        intentToServer.setPackage("com.jerry.server_app");
        startService(intentToServer);
    }

    private void connectToServer() {
        Socket socket = null;
        while (socket == null || !socket.isConnected()) {
            try {
                // 访问本地端口8688（即服务器）
                socket = new Socket("localhost", 8688);
                clientSocket = socket;

                handler.sendEmptyMessage(MESSAGE_SOCKET_CONNECTED);
                Log.i(TAG, "connectToServer: ");
            } catch (IOException e) {
                // 如果没连上则间隔1秒后重试
                SystemClock.sleep(1000);
                Log.i(TAG, "connection break, retry...");
            }
        }

        try {
            // 用于读取服务端发送的消息
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // 用于给服务端发送消息
            writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            while (!MainActivity.this.isFinishing() && socket.isConnected()) {
                // 不断的读取服务端发送的消息（会阻塞线程）
                String serverMessage = reader.readLine();
                Log.i(TAG, "receive message: " + serverMessage);

                if (serverMessage != null) {
                    final String showMessage = "Server " + formatDateTime(System.currentTimeMillis()) + ":" + serverMessage + "\n";
                    handler.obtainMessage(MESSAGE_RECEIVE_NEW_MSG, showMessage).sendToTarget();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            disconnectToServer();
            Log.i(TAG, "client closed");
        }
    }

    private String formatDateTime(long time) {
        return new SimpleDateFormat("[HH:mm:ss]", Locale.CHINA).format(new Date(time));
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_RECEIVE_NEW_MSG:
                    tvShowMessage.setText(tvShowMessage.getText() + (String) msg.obj);
                    break;
                case MESSAGE_SEND_MSG:
                    etMessageContent.setText("");

                    final String showMessage = "Client " + formatDateTime(System.currentTimeMillis()) + ":" + msg.obj + "\n";
                    tvShowMessage.setText(tvShowMessage.getText() + showMessage);
                    break;
                case MESSAGE_SOCKET_CONNECTED:
                    btnConnectServer.setClickable(true);
                    btnConnectServer.setText("Disconnect");
                    btnSendMessage.setEnabled(true);
                    break;
                case MESSAGE_SOCKET_DISCONNECTED:
                    btnConnectServer.setClickable(true);
                    btnConnectServer.setText("Connect");
                    btnSendMessage.setEnabled(false);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_connect_server:
                if (btnSendMessage.isEnabled()) {
                    // 如果客户端可以发送消息，说明与服务端相连
                    disconnectToServer();
                } else {
                    if (threadPool == null || threadPool.isShutdown()) {
                        return;
                    }

                    btnConnectServer.setClickable(false);
                    btnConnectServer.setText("Connecting");

                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            connectToServer();
                        }
                    });
                }
                break;

            case R.id.btn_send_message:
                if (threadPool == null || threadPool.isShutdown()) {
                    return;
                }

                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        String message = etMessageContent.getText().toString().trim();
                        if (clientSocket != null && clientSocket.isConnected() && writer != null) {
                            if (!message.isEmpty()) {
                                writer.println(message);

                                handler.obtainMessage(MESSAGE_SEND_MSG, message).sendToTarget();
                            }
                        }
                    }
                });
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDestroy() {
        disconnectToServer();
        super.onDestroy();
    }

    private void disconnectToServer() {
        try {
            if (clientSocket != null)
                clientSocket.close();
            if (writer != null)
                writer.close();
            if (reader != null)
                reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        handler.obtainMessage(MESSAGE_SOCKET_DISCONNECTED).sendToTarget();
    }
}
