package com.tw.conv.testapp.provider;

import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;
import retrofit.http.GET;
import retrofit.http.QueryMap;

public class TCIceServersProvider {
    public static final String REALM = "realm";

    /* Define the Retrofit Token Service */
    interface IceService {
        @GET("/ice")
        void obtainTwilioIceServers(@QueryMap Map<String, String> options, Callback<TwilioIceResponse> tokenCallback);
    }

    private static IceService iceService = new RestAdapter.Builder()
            .setEndpoint("https://simple-signaling.appspot.com")
            .setRequestInterceptor(new TwilioAuthorizationInterceptor())
            .setConverter(new GsonConverter(new GsonBuilder().create()))
            .build()
            .create(IceService.class);


    public static void obtainTwilioIceServers(String realm, Callback<TwilioIceResponse> callback) {
        HashMap<String,String> options = new HashMap<>();
        options.put(REALM, realm);
        iceService.obtainTwilioIceServers(options, callback);
    }



}
