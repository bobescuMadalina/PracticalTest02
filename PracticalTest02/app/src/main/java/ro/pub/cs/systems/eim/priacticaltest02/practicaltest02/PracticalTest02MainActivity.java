package ro.pub.cs.systems.eim.priacticaltest02.practicaltest02;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class PracticalTest02MainActivity extends AppCompatActivity {

    Button serverConnect, getTimeClient;
    EditText serverPort, clientPort, clientAddress;
    TextView result;

    private ServerThread serverThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practical_test02_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        serverConnect = (Button) findViewById(R.id.connect_server_button);
        serverConnect.setOnClickListener(new ServerConnectClickListener());
        getTimeClient = (Button) findViewById(R.id.get_time_button);
        getTimeClient.setOnClickListener(new GetTimeOnClickListener());

        serverPort = (EditText) findViewById(R.id.server_port);
        clientPort = (EditText) findViewById(R.id.client_port);
        clientAddress = (EditText) findViewById(R.id.client_address);

        result = (TextView) findViewById(R.id.text_view_result);


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_practical_test02_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private class ServerConnectClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            String serverPortString = serverPort.getText().toString();
            if (serverPortString == null || serverPortString.isEmpty()) {
                Toast.makeText(
                        getApplicationContext(),
                        "Server port should be filled!",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            serverThread = new ServerThread(Integer.parseInt(serverPortString));
            serverThread.startServer();
            Log.d("APP_TAG", "Server started");
        }
    }


    private class ServerThread extends Thread {

        private boolean isRunning;
        int serverPort;
        HashMap<String, Date> data = new HashMap<>();

        public ServerThread(int serverPort) {
            this.serverPort = serverPort;
        }

        private ServerSocket serverSocket;

        public void startServer() {
            isRunning = true;
            start();

        }

        public void stopServer() {
            isRunning = false;
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

        }

        public synchronized HashMap<String, Date> getData() {
            return data;
        }

        public synchronized  void setData(String key, Date value) {
            this.data.put(key, value);
        }



        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(serverPort);
                while (isRunning) {
                    Socket socket = serverSocket.accept();
                    if (socket != null) {
                        CommunicationThread communicationThread = new CommunicationThread(socket);
                        communicationThread.start();
                    }
                }
            } catch (IOException ioException) {

                ioException.printStackTrace();

            }
        }
    }

    private class CommunicationThread extends Thread {

        private Socket socket;

        public CommunicationThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            if (socket != null) {
                try {
                    PrintWriter printWriter = Utilities.getWriter(socket);
                    String responseToSend;
                    if (printWriter != null) {
                        Log.i("APP_TAG", "[COMMUNICATION THREAD] Waiting for parameters from client (city / information type)!");
                        HashMap<String, Date> data = serverThread.getData();
                        if (data.containsKey(socket.getInetAddress().toString())) {
                            Date date = data.get(socket.getInetAddress().toString());
                            long currentDate = System.currentTimeMillis();
                            Date dateCurrent = new Date(currentDate);
                            long different = dateCurrent.getTime() - date.getTime();
                            long secondsInMilli = 1000;
                            long minutesInMilli = secondsInMilli * 60;

                            long elapsedMinutes = different / minutesInMilli;
                            //different = different % minutesInMilli;
                            if (elapsedMinutes > 1) {
                                HttpClient httpClient = new DefaultHttpClient();
                                HttpGet httpGet = new HttpGet("http://www.timeapi.org/utc/now");
                                ResponseHandler<String> responseHandler = new BasicResponseHandler();
                                String pageSourceCode = httpClient.execute(httpGet, responseHandler);
                                responseToSend = pageSourceCode;
                                serverThread.setData(socket.getInetAddress().toString(), dateCurrent
                                );
                            } else {
                                responseToSend = "S-a trimis o cerere in mai putin de un minut.";
                            }

                        } else {
                            long currentDate = System.currentTimeMillis();
                            serverThread.setData(socket.getInetAddress().toString(), new Date(currentDate));
                            HttpClient httpClient = new DefaultHttpClient();
                            HttpGet httpGet = new HttpGet("http://www.timeapi.org/utc/now");
                            ResponseHandler<String> responseHandler = new BasicResponseHandler();
                            String pageSourceCode = httpClient.execute(httpGet, responseHandler);
                            responseToSend = pageSourceCode;
                        }

                        printWriter.println(responseToSend);
                        printWriter.flush();


                    } else{
                            Log.e("APP_TAG", "[COMMUNICATION THREAD] Error receiving parameters from client (city / information type)!");

                    }
                    socket.close();
                } catch (IOException ioException) {
                    Log.e("APP_TAG", "[COMMUNICATION THREAD] An exception has occurred: " + ioException.getMessage());

                    ioException.printStackTrace();

                }
            } else {
                Log.d("APP_TAG", "[COMMUNICATION THREAD] Socket is null!");
            }

        }
    }

    private class ClientThread extends Thread {

        Socket socket;
        String address;

        int port;

        public ClientThread(String address, int port) {
            this.address = address;
            this.port = port;

        }

        @Override
        public void run() {
            try {
                socket = new Socket(address, port);
                if (socket == null) {
                    Log.e("TAG", "[CLIENT THREAD] Could not create socket!");
                    return;
                }
                BufferedReader bufferedReader = Utilities.getReader(socket);
                if (bufferedReader != null) {
                    final String time;
                    time = bufferedReader.readLine();
                    result.post(new Runnable() {
                        @Override
                        public void run() {
                            result.append(time);
                        }
                    });
                } else {
                    Log.e("APP_TAG", "[CLIENT THREAD] BufferedReader / PrintWriter are null!");
                }
                socket.close();
            } catch (IOException ioException) {
                Log.e("APP_TAG", "[CLIENT THREAD] An exception has occurred: " + ioException.getMessage());

                ioException.printStackTrace();

            }
        }
    }


    private class GetTimeOnClickListener implements  View.OnClickListener {
        ClientThread clientThread;

        @Override
        public void onClick(View v) {
            String clientAddressString = clientAddress.getText().toString();
            String clientPortString    = clientPort.getText().toString();
            if (clientAddressString == null || clientAddressString.isEmpty() ||
                    clientPortString == null || clientPortString.isEmpty()) {
                Toast.makeText(
                        getApplicationContext(),
                        "Client connection parameters should be filled!",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            if (serverThread == null || !serverThread.isAlive()) {
                Log.e("APP_TAG", "[MAIN ACTIVITY] There is no server to connect to!");
                return;
            }

            clientThread = new ClientThread(
                    clientAddressString,
                    Integer.parseInt(clientPortString));
            clientThread.start();


        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serverThread != null) {
            serverThread.stopServer();
        }
    }
}
