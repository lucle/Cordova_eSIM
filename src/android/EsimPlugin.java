package com.dreamcloud;
// The native android API
import android.telephony.euicc.EuiccManager;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.TelephonyManager;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.PendingIntent;
// Cordova-required packages
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.LOG;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EsimPlugin extends CordovaPlugin{
    protected static final String LOG_TAG = "eSIM";
    private static final String HAS_ESIM_ENABLED = "hasEsimEnabled";
    private static final String INSTALL_ESIM = "installEsim";
    private static final String ACTION_DOWNLOAD_SUBSCRIPTION = "download_subscription";
    private Context mainContext;
    private EuiccManager mgr;
    
    private static final String LPA_DECLARED_PERMISSION = "com.starhub.aduat.torpedo.lpa.permission.BROADCAST";
    String address;
    String matchingID;
    String activationCode ;

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mainContext = this.cordova.getActivity().getApplicationContext();
     }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (HAS_ESIM_ENABLED.equals(action)) {
            hasEsimEnabled(callbackContext);
        }else if (INSTALL_ESIM.equals(action)) {   
            installEsim(args, callbackContext);
        }else {
            return false;
        }    
        return true;
    }

    private void initMgr() {
        if (mgr == null) {
          mgr = (EuiccManager) mainContext.getSystemService(Context.EUICC_SERVICE);
        }
    }

    private void hasEsimEnabled(CallbackContext callbackContext) {
        initMgr();
        boolean result = mgr.isEnabled();
        callbackContext.sendPluginResult(new PluginResult(Status.OK, result));
    }

    private void installEsim(JSONArray args, CallbackContext callbackContext) {   
        try{
            initMgr(); 
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!ACTION_DOWNLOAD_SUBSCRIPTION.equals(intent.getAction())) {
                        return;
                    }
                   int resultCode = getResultCode();
                   int detailedCode = intent.getIntExtra(
                        EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                        0 /* defaultValue*/);
            
                    // If the result code is a resolvable error, call startResolutionActivity
                    if (resultCode == EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR) {
                        try{
                            PendingIntent callbackIntent = PendingIntent.getBroadcast(
                                mainContext, 0 /* requestCode */, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                            mgr.startResolutionActivity(
                                cordova.getActivity(),
                                0 /* requestCode */,
                                intent,
                                callbackIntent);
                        } catch (Exception e) {
                          callbackContext.error("EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR - Can't setup eSim due to Activity error " + e.getLocalizedMessage());
                        }
                    }
                }
            };
            mainContext.registerReceiver(receiver,
                new IntentFilter(ACTION_DOWNLOAD_SUBSCRIPTION),
                LPA_DECLARED_PERMISSION /* broadcastPermission*/,
                null /* handler */);
    
            address = args.getString(0);
            matchingID = args.getString(1);
            activationCode = "1$" + address + "$" + matchingID;
            // Download subscription asynchronously.
            DownloadableSubscription sub = DownloadableSubscription.forActivationCode(activationCode /* encodedActivationCode*/);
            Intent intent = new Intent(ACTION_DOWNLOAD_SUBSCRIPTION);
            PendingIntent callbackIntent = PendingIntent.getBroadcast(mainContext, 0 /* requestCode */, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            mgr.downloadSubscription(sub, true /* switchAfterDownload */, callbackIntent);
        }catch (Exception e) {
            callbackContext.error("Error install eSIM "  + e.getMessage());
            callbackContext.sendPluginResult(new PluginResult(Status.ERROR));
        }
    }       
}
