package com.kustomer.kustomersdk.Helpers;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import com.facebook.imagepipeline.cache.BitmapMemoryCacheFactory;
import com.kustomer.kustomersdk.API.KUSUserSession;
import com.kustomer.kustomersdk.Enums.KUSRequestType;
import com.kustomer.kustomersdk.Interfaces.KUSImageUploadListener;
import com.kustomer.kustomersdk.Interfaces.KUSRequestCompletionListener;
import com.kustomer.kustomersdk.Models.KUSChatAttachment;
import com.kustomer.kustomersdk.Utils.JsonHelper;
import com.kustomer.kustomersdk.Utils.KUSConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Created by Junaid on 1/20/2018.
 */

public class KUSUpload {

    //region Properties
    private boolean sendingComplete = false;
    int uploadedCount = 0;
    List<KUSChatAttachment> attachments;
    //endregion

    //region LifeCycle

    //endregion

    //region Public Methods
    public void uploadImages(final List<Bitmap> images, KUSUserSession userSession, final KUSImageUploadListener listener){

        if(images == null || images.size() == 0){
            if(listener != null){
                listener.onCompletion(null,new ArrayList<KUSChatAttachment>());
            }
            return;
        }

        attachments = new ArrayList<>(images.size());

        for(int i = 0; i<images.size(); i++){
            Bitmap bitmap = images.get(i);

            final int index = i;
            uploadImage(bitmap, userSession, new ImageUploadListener() {
                @Override
                public void onUploadComplete(Error error, KUSChatAttachment attachment) {
                    uploadCompleted(index,error,attachment,listener,images);
                }
            });
        }

    }
    //endregion

    //region Private Method
    private void uploadCompleted(int index, Error error, KUSChatAttachment attachment,
                                 KUSImageUploadListener listener, List<Bitmap> images){
        if(error != null){
            if(listener != null && !sendingComplete){
                sendingComplete = true;
                listener.onCompletion(error,null);
            }

            return;
        }

        uploadedCount++;
        attachments.add(index,attachment);
        if(uploadedCount == images.size()){
            if(listener !=null && !sendingComplete){
                sendingComplete = true;
                listener.onCompletion(null, attachments);
            }
        }
    }

    private void uploadImage(Bitmap image, final KUSUserSession userSession, final ImageUploadListener listener){

        if(image != null) {
            final byte[] imageBytes = KUSImage.getByteArrayFromBitmap(image);
            final String filename = String.format("%s.jpg", UUID.randomUUID().toString());

            userSession.getRequestManager().performRequestType(
                    KUSRequestType.KUS_REQUEST_TYPE_POST,
                    KUSConstants.URL.CHAT_ATTACHMENT_ENDPOINT,
                    new HashMap<String, Object>() {{
                        put("name", filename);
                        put("contentLength", imageBytes.length);
                        put("contentType", "image/jpeg");
                    }},
                    true,
                    new KUSRequestCompletionListener() {
                        @Override
                        public void onCompletion(Error error, JSONObject response) {

                            if(error != null){
                                if(listener != null){
                                    listener.onUploadComplete(error,null);
                                }

                                return;
                            }

                            final KUSChatAttachment chatAttachment;
                            try {
                                chatAttachment = new KUSChatAttachment(JsonHelper.jsonObjectFromKeyPath(response,"data"));
                            } catch (KUSInvalidJsonException e) {
                                return;
                            }

                            try {
                                URL uploadURL = new URL(JsonHelper.stringFromKeyPath(response,"meta.upload.url"));
                                HashMap<String, String> uploadFields =
                                        JsonHelper.hashMapFromKeyPath(response,"meta.upload.fields");

//                                String boundary = "----FormBoundary";
//                                final String contentType = String.format("multipart/form-data; boundary=%s",boundary);
//                                final byte[] bodyData = uploadBodyDataFromImageAndFileNameAndFieldsAndBoundary(
//                                        imageBytes,
//                                        filename,
//                                        uploadFields,
//                                        boundary
//                                );
//
//                                userSession.getRequestManager().performRequestType(
//                                        KUSRequestType.KUS_REQUEST_TYPE_POST,
//                                        uploadURL,
//                                        null,
//                                        bodyData,
//                                        false,
//                                        new HashMap<String,String>(){{
//                                                put("Content-Type",contentType);
//                                            }},
//                                        new KUSRequestCompletionListener(){
//
//                                            @Override
//                                            public void onCompletion(Error error, JSONObject response) {
//                                                if(error != null){
//                                                    if(listener != null)
//                                                        listener.onUploadComplete(error, null);
//
//                                                    return;
//                                                }
//
//                                                if(listener != null)
//                                                    listener.onUploadComplete(null,chatAttachment);
//                                            }
//                                        }
//                                );

                                uploadImageOnS3(uploadURL,
                                        filename,
                                        imageBytes,
                                        uploadFields,
                                        new KUSRequestCompletionListener() {
                                            @Override
                                            public void onCompletion(Error error, JSONObject response) {
                                                if(error != null){
                                                    if(listener != null)
                                                        listener.onUploadComplete(error, null);

                                                    return;
                                                }

                                                if(listener != null)
                                                    listener.onUploadComplete(null,chatAttachment);
                                            }
                                        });


                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            }

                        }
                    }
            );
        }
    }

    private byte[] uploadBodyDataFromImageAndFileNameAndFieldsAndBoundary(byte[] imageBytes,
                                                                        String fileName,
                                                                        HashMap<String, String> uploadFields,
                                                                        String boundary){

        byte[] bodyData = null;

        String [] fieldArrays = new String[uploadFields.keySet().size()];
        fieldArrays = uploadFields.keySet().toArray(fieldArrays);

        List<String> fieldKeys = new ArrayList<>(Arrays.asList(fieldArrays));
        if(fieldKeys.contains("key")){
            fieldKeys.remove("key");
            fieldKeys.add(0,"key");
        }

        try {
            bodyData = String.format("\r\n--%s\r\n",boundary).getBytes("UTF-8");

            for(String field : fieldKeys){
                String value = uploadFields.get(field);
                bodyData = concatByteArrays(
                        bodyData,
                        String.format("Content-Disposition: form-data; name=\"%s\"\r\n\r\n%s",field,value)
                                .getBytes("UTF-8"));

                bodyData = concatByteArrays(
                        bodyData,
                        String.format("\r\n--%s\r\n",boundary)
                                .getBytes("UTF-8"));
            }

            bodyData = concatByteArrays(
                    bodyData,
                    String.format("Content-Disposition: form-data; name=\"file\"; filename=\"%s\"\r\n",
                            fileName).getBytes("UTF-8"));

            bodyData = concatByteArrays(
                    bodyData,
                    "Content-Type: image/jpeg\r\n\r\n".getBytes("UTF-8"));

            bodyData = concatByteArrays(bodyData, imageBytes);

            bodyData = concatByteArrays(
                    bodyData,
                    String.format("\r\n--%s--", boundary).getBytes("UTF-8"));

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return bodyData;
    }

    public void uploadImageOnS3(URL url, String filename, byte[] imageBytes,
                                HashMap<String, String> uploadFields,
                                final KUSRequestCompletionListener completionListener){

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        String [] fieldArrays = new String[uploadFields.keySet().size()];
        fieldArrays = uploadFields.keySet().toArray(fieldArrays);

        List<String> fieldKeys = new ArrayList<>(Arrays.asList(fieldArrays));
        if(fieldKeys.contains("key")){
            fieldKeys.remove("key");
            fieldKeys.add(0,"key");
        }

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        for(String field : fieldKeys){
            String value = uploadFields.get(field);

            builder.addFormDataPart(field,value);
        }

        builder.addFormDataPart("file", filename);
        builder.addPart(RequestBody.create(MediaType.parse("image/jpeg"), imageBytes));

        RequestBody requestBody = builder.build();
        Request request = new Request.Builder().url(url).post(requestBody).build();


        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                completionListener.onCompletion(new Error(e.getMessage()), null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if(response.body() != null) {
                    String body = response.body().string();

                    try {
                        JSONObject jsonObject = new JSONObject(body);
                        completionListener.onCompletion(null, jsonObject);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private static byte[] concatByteArrays(byte[]... inputs) {
        int i = 0;
        for (byte[] b : inputs) {
            i += b.length;
        }
        byte[] r = new byte[i];
        i = 0;
        for (byte[] b : inputs) {
            System.arraycopy(b, 0, r, i, b.length);
            i += b.length;
        }
        return r;
    }
    //endregion

    //region Interface
    public interface ImageUploadListener{
        void onUploadComplete(Error error, KUSChatAttachment attachment);
    }
    //endregion

}
