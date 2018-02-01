package com.kustomer.kustomersdk.DataSources;

import com.kustomer.kustomersdk.API.KUSUserSession;
import com.kustomer.kustomersdk.Helpers.KUSInvalidJsonException;
import com.kustomer.kustomersdk.Interfaces.KUSObjectDataSourceListener;
import com.kustomer.kustomersdk.Interfaces.KUSRequestCompletionListener;
import com.kustomer.kustomersdk.Models.KUSModel;
import com.kustomer.kustomersdk.Utils.JsonHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Junaid on 1/20/2018.
 */

public class KUSObjectDataSource {
    //region Properties
    private boolean fetching;
    private boolean fetched;
    private Error error;
    private KUSModel object;

    private KUSUserSession userSession;
    private Object requestMarker;

    private List<KUSObjectDataSourceListener> listeners;
    //endregion

    //region Initializer
    KUSObjectDataSource() {
        // NOT REQUIRED
    }

    KUSObjectDataSource(KUSUserSession userSession){
        this.userSession = userSession;
        listeners = new ArrayList<>();
    }
    //endregion

    //region public Methods
    public void fetch(){
        if(fetching){
            return;
        }

        fetching = true;
        error = null;

        final Object requestMarker = new Object();
        this.requestMarker = requestMarker;

        final KUSObjectDataSource instance = this;
        performRequest(new KUSRequestCompletionListener() {
            @Override
            public void onCompletion(Error errorObject, JSONObject response) {
                if(instance.requestMarker != requestMarker)
                    return;

                instance.requestMarker = null;

                KUSModel model = null;
                try {
                    model = objectFromJson(JsonHelper.jsonObjectFromKeyPath(response,"data"));
                } catch (KUSInvalidJsonException ignore) {}

                fetching = false;
                if(errorObject != null || model == null){
                    if(error == null)
                        error = new Error();
                    else
                        error = errorObject;

                    notifyAnnouncersOnError(error);
                }else {
                    object = model;
                    fetched = true;
                    notifyAnnouncersOnLoad();
                }
            }
        });

    }

    void cancel(){
        fetching = false;
        requestMarker = null;
    }

    public void addListener(KUSObjectDataSourceListener listener){
        listeners.add(listener);
    }

    public void removeListener(KUSObjectDataSourceListener listener){
        listeners.remove(listener);
    }
    //endregion

    //region Private Methods
    private void notifyAnnouncersOnError(Error error){
        for(KUSObjectDataSourceListener listener : listeners){
            listener.objectDataSouceOnError(this,error);
        }
    }

    private void notifyAnnouncersOnLoad(){
        for(KUSObjectDataSourceListener listener : listeners){
            listener.objectDataSourceOnLoad(this);
        }
    }
    //endregion

    //region subclass methods
    KUSModel objectFromJson(JSONObject jsonObject) throws KUSInvalidJsonException {
        return new KUSModel(jsonObject);
    }

    void performRequest(KUSRequestCompletionListener completionListener){}
    //endregion

    //region Accessors

    public boolean isFetching() {
        return fetching;
    }

    public boolean isFetched() {
        return fetched;
    }

    public Error getError() {
        return error;
    }

    public KUSModel getObject() {
        return object;
    }

    public KUSUserSession getUserSession() {
        return userSession;
    }

    public Object getRequestMarker() {
        return requestMarker;
    }

    public List<KUSObjectDataSourceListener> getListeners() {
        return listeners;
    }

    //endregion
}
