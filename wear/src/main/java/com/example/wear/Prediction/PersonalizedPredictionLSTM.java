package com.example.wear.Prediction;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.wear.MainActivity;
import com.example.wear.NatsManager;
import com.example.wear.config.SmartFallConfig;
import com.google.gson.Gson;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.example.wear.Database.Couchbase;
import com.example.wear.config.ModelConfig;
import io.nats.client.Message;




/**
 * This class is responsible for making inference if the personalization strategy is LSTM.
 *
 * @author Bhargav Balusu (b_b515)
 * @version 1.0
 * @since 2022.05.15
 */
public class PersonalizedPredictionLSTM {

    private static final String TAG = "PersonalizedPredictionLSTM";
    private static ArrayList<Interpreter> interpreters = new ArrayList<>();
    private static float[] MODEL_WEIGHTS = {1.0f};
    private static float[] MODEL_THRESHOLDS = {0.50f};

    public static Context con;
    /**
     * Initializes the interpreters with downloaded models present in the local storage.
     * @param context
     * @throws IOException
     */
    public static void initialize(Context context) throws IOException {
        con = context;
//        if(interpreters.size()>0) {
//            for(Interpreter i:interpreters){
//                i.close();
//            }
//        }
//        interpreters = new ArrayList<>();
//        if(SmartFallConfig.MODEL_FROM == "ONLINE") {
//            checkAndDownloadNewModel(context);
//            for (String fileName : ModelConfig.getModelConfig(context).modelNames) {
//                Interpreter interpreter = new Interpreter(new File(context.getFilesDir().getAbsolutePath() + "/" + fileName));
//                interpreter.allocateTensors();
//                interpreters.add(interpreter);
//            }
//            MODEL_THRESHOLDS = ModelConfig.getModelConfig(context).thresholds;
//        }
//        else {
//            Interpreter interpreter = new Interpreter(loadMappedFile(SmartFallConfig.OFFLINE_MODEL_FILE));
//            interpreter.allocateTensors();
//            interpreters.add(interpreter);
//            MODEL_THRESHOLDS[0] = SmartFallConfig.OFFLINE_MODEL_THRESHOLD;
//        }
    }

    public static int getNumberOfInterpreter(){
        return interpreters.size();
    }

    /**
     * This method performs a weighted sum inference on received float samples using
     * the list of interpreters.
     *
     * @param samples float: Two-dimensional float array of samples
     * @return float: The weighted sum inference made my the interpreters
     */

    private static String format3DArray(float[][][] array) {
        StringBuilder result = new StringBuilder();
        result.append("[");
        for (int i = 0; i < array.length; i++) {
            result.append("[");
            for (int j = 0; j < array[i].length; j++) {
                result.append("[");
                for (int k = 0; k < array[i][j].length; k++) {
                    result.append(array[i][j][k]);
                    if (k < array[i][j].length - 1) {
                        result.append(",");
                    }
                }
                result.append("]");
                if (j < array[i].length - 1) {
                    result.append(",");
                }
            }
            result.append("]");
            if (i < array.length - 1) {
                result.append(",");
            }
        }
        result.append("]");
        return result.toString();
    }


    public static float makeInference(float[][] samples) throws Exception {
        float inference = 0.0f;
        float[][][] flattenedSamples = flattenInputTo3D(samples);

        String status=MainActivity.watchStatus;
        ModelConfig config = ModelConfig.getModelConfig(con);

        //Extract the unique identifier of watch user
        String uuid=config.uuid;

        //Construct the subject string for the NATS message
        String subject="m."+uuid;
        String data = format3DArray(flattenedSamples);

        if(status.equals("Activated")) {

            try {

                // Send a request to the NATS server with the subject and data, and wait for a response
                CompletableFuture<Message> future = NatsManager.nc.request(subject, data.getBytes());

                // Wait up to 1 second for a response
                Message m = future.get(1, TimeUnit.SECONDS);

                // Parse the received response data into a float value
                inference = Float.parseFloat(new String(m.getData()));

            } catch (ExecutionException e) {
                System.out.println("Something went wrong with the execution of the request: " + e);
            } catch (TimeoutException e) {
                System.out.println("We didn't get a response in time.");
            } catch (CancellationException e) {
                System.out.println("The request was cancelled due to no responders.");
            }
        }
        return inference;
    }

    public static float getThreshold() {
        return MODEL_THRESHOLDS[0];
    }


    /**
     * This method calls the ModelDownloader and writes the model files to local storage.
     * @param context
     */
    private static void checkAndDownloadNewModel(Context context) {

        try {
            ModelConfig modelConfig = ModelConfig.getModelConfig(context);
            ModelConfig config = new ModelDownloader().execute(modelConfig).get();

            if(config.isDownloaded){
                String[] modelNames = config.modelNames;
                MODEL_WEIGHTS = config.modelWeights;
                MODEL_THRESHOLDS = config.thresholds;
                byte[][] modelContent = config.modelContent;
                for(int i=0; i<modelNames.length; i++){
                    Log.e(TAG,modelContent[i].toString());


                    try (FileOutputStream fos = context.openFileOutput(modelNames[i], Context.MODE_PRIVATE)) {
                        fos.write(modelContent[i]);
                    }
                    catch (Exception e){
                        Log.e(TAG, "Error while writing model content : " + e.getMessage());
                    }
                    Log.e(TAG, context.getFilesDir().getAbsolutePath());
                }
                Couchbase.initialize(context);
                updateConfigFile(context);

            }
        }catch(Exception e){
            Log.e(TAG, "Exception while downloading new model : "+ e.getMessage());
        }

    }
    /**
     * This method serializes the ModelConfig object and writes the result to SmartWatchValues.json
     * @param context
     */
    public static void updateConfigFile(Context context){
        ModelConfig config = ModelConfig.getModelConfig(context);
        String filename =  "SmartWatchValues.json" ;

        Gson gson = new Gson();
        String jsonString = gson.toJson(config);

        Intent modelIntent = new Intent("modelInfo");
        modelIntent.putExtra("data", jsonString);
        LocalBroadcastManager.getInstance(context).sendBroadcast(modelIntent);

        try (FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE)) {
            fos.write(jsonString.getBytes());
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private static ByteBuffer loadModelFile(Context context, String fileName) throws IOException {

        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(fileName);
        FileInputStream fileInputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        ByteBuffer byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset,declaredLength);
        return byteBuffer;
    }


    /**
     * This method is used to transforms samples from a two-dimensional float array
     * into a three-dimensional float array
     *
     * @param samples float: Two-dimensional float array
     * @return float: Three-dimensional float array
     */
    private static float[][][] flattenInputTo3D(float[][] samples) {
        float[][][] multiDimArray = new float[1][samples.length][samples[0].length];
        multiDimArray[0] = samples;
        return multiDimArray;
    }

    public static MappedByteBuffer loadMappedFile(String filePath) throws IOException {
        AssetFileDescriptor fileDescriptor = con.getAssets().openFd(filePath);

        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


}


