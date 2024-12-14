package com.example.wear;

import android.content.Context;
import android.util.Log;

import com.example.wear.util.SSLUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;

import javax.net.ssl.SSLContext;

public class NatsManager {

    private static final String TAG = "Nats Service";
    private static final ExecutorService callbackExecutor = Executors.newFixedThreadPool(10); // Adjust thread pool size as needed
    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);
    public static Connection nc;
    private final MainActivity datacollector;
    public static boolean connect = false;
    // Event event;
    private Context context;//----

    public NatsManager(MainActivity datacollector,/**/Context context) {
        this.datacollector = datacollector;
        this.context = context;//-----
    }
    public static void pub(String topic, String data) {

        if(connect){
            nc.publish(topic, data.getBytes());
        }
    }
    public void connect() {
        Log.d(TAG, "TRY TO CONNECT");
        new Thread(() -> {


            /* This code can be used to connect to nats server which requires encryption and authentication with public key

            InputStream certStream = null;
            try {
                certStream = context.getAssets().open("fullchain2.pem");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Pass the InputStream to your SSL setup
            SSLContext sslContext = null;
            try {
                sslContext = SSLUtils.createSSLContext(certStream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Example NATS connection setup
            Options options = new Options.Builder()
                    .server("tls://chocolatefrog@cssmartfall1.cose.txstate.edu:4222")
                    .sslContext(sslContext)
                    .build();

             */

            //Below code can be used to connect to nats server which requires encryption and authentication without public key

            SSLContext sslContext = null;
            try {
                sslContext = SSLUtils.createInsecureSSLContext();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Configure the NATS connection
            Options options = new Options.Builder()
                    .server("tls://chocolatefrog@cssmartfall1.cose.txstate.edu:4224") // Use "tls" protocol
                    .sslContext(sslContext) // Pass the SSL context
                    .build();


            try {
                nc = Nats.connect(options);

                //Log.d(TAG, "Connected to Nats server " + options.getServers().get(0));
                Log.d(TAG, "Connected to Nats server ");
                connect = true;
                //datacollector.setConnect(true);

            } catch (Exception exp) {
                Log.e(TAG, "Failed to connect to NATS server");
                exp.printStackTrace();
                connect = false;
                // datacollector.setConnect(false);
            }
        }).start();
    }
    public void close() {
        try {
            nc.close();
            Log.d(TAG, "Nats connection closed");
        } catch (InterruptedException e) {
            Log.e(TAG, "Error closing Nats connection", e);
            Thread.currentThread().interrupt();
        }
    }
}