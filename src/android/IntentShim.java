package com.darryncampbell.cordova.plugin.intent;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IntentShim extends CordovaPlugin {

    private static final String LOG_TAG = "Cordova Intents Shim";
    private CallbackContext onNewIntentCallbackContext = null;
    private CallbackContext onBroadcastCallbackContext = null;
    private CallbackContext onActivityResultCallbackContext = null;

    public IntentShim() {

    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException
    {
        Log.d(LOG_TAG, "Action: " + action);
        if (action.equals("startActivity") || action.equals("startActivityForResult"))
        {
            //  Credit: https://github.com/chrisekelley/cordova-webintent
            if (args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }

            final CordovaResourceApi resourceApi = webView.getResourceApi();
            JSONObject obj = args.getJSONObject(0);
            String type = obj.has("type") ? obj.getString("type") : null;
            Uri uri = obj.has("url") ? resourceApi.remapUri(Uri.parse(obj.getString("url"))) : null;
            JSONObject extras = obj.has("extras") ? obj.getJSONObject("extras") : null;
            Map<String, String> extrasMap = new HashMap<String, String>();
            int requestCode = obj.has("requestCode") ? obj.getInt("requestCode") : 1;

            // Populate the extras if any exist
            if (extras != null) {
                JSONArray extraNames = extras.names();
                for (int i = 0; i < extraNames.length(); i++) {
                    String key = extraNames.getString(i);
                    String value = extras.getString(key);
                    extrasMap.put(key, value);
                }
            }

            boolean bExpectResult = false;
            if (action.equals("startActivityForResult"))
            {
                bExpectResult = true;
                this.onActivityResultCallbackContext = callbackContext;
            }
            else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            }
            startActivity(obj.getString("action"), uri, type, extrasMap, bExpectResult, requestCode);

            return true;
        }
        else if (action.equals("sendBroadcast"))
        {
            //  Credit: https://github.com/chrisekelley/cordova-webintent
            if (args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }

            // Parse the arguments
            JSONObject obj = args.getJSONObject(0);
            JSONObject extras = obj.has("extras") ? obj.getJSONObject("extras") : null;
            Map<String, Object> extrasMap = new HashMap<String, Object>();

            if (extras != null) {
                JSONArray extraNames = extras.names();
                for (int i = 0; i < extraNames.length(); i++) {
                    String key = extraNames.getString(i);
                    //String value = extras.getString(key);
                    Object value = extras.get(key);
                    extrasMap.put(key, value);
                }
            }

            sendBroadcast(obj.getString("action"), extrasMap);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            return true;
        } else if (action.equals("registerBroadcastReceiver")) {
            try
            {
                //  Ensure we only have a single registered broadcast receiver
                ((CordovaActivity)this.cordova.getActivity()).unregisterReceiver(myBroadcastReceiver);
            }
            catch (IllegalArgumentException e) {}

            //  No error callback
            if(args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }

            //  Expect an array of filterActions
            JSONObject obj = args.getJSONObject(0);
            JSONArray filters = obj.has("filterActions") ? obj.getJSONArray("filterActions") : null;
            if (filters == null || filters.length() == 0)
            {
                //  The arguments are not correct
                Log.w(LOG_TAG, "filterActions argument is not in the expected format");
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }

			//  Optionally, an array of filterCategories
            JSONArray filterCategories = obj.has("filterCategories") ? obj.getJSONArray("filterCategories") : null;
            			
            this.onBroadcastCallbackContext = callbackContext;

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);

            IntentFilter filter = new IntentFilter();
            for (int i = 0; i < filters.length(); i++) {
                Log.d(LOG_TAG, "Registering broadcast receiver for filter: " + filters.getString(i));
                filter.addAction(filters.getString(i));
            }
			if (filterCategories != null)
			{
				for (int i = 0; i < filterCategories.length(); i++)
				{
					Log.d(LOG_TAG, "Registering broadcast receiver for filter category: " + filterCategories.getString(i));
					filter.addCategory(Intent.CATEGORY_DEFAULT);
				}
			}
            ((CordovaActivity)this.cordova.getActivity()).registerReceiver(myBroadcastReceiver, filter);

            callbackContext.sendPluginResult(result);
        }
        else if (action.equals("unregisterBroadcastReceiver"))
        {
			try
			{
				((CordovaActivity)this.cordova.getActivity()).unregisterReceiver(myBroadcastReceiver);
			}
            catch (IllegalArgumentException e) {}
        }
        else if (action.equals("onIntent"))
        {
            //  Credit: https://github.com/napolitano/cordova-plugin-intent
            if(args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }

            this.onNewIntentCallbackContext = callbackContext;

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
            return true;
        }
        else if (action.equals("onActivityResult"))
        {
            if(args.length() != 1) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }

            this.onActivityResultCallbackContext = callbackContext;

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
            return true;
        }
        else if (action.equals("getIntent"))
        {
            //  Credit: https://github.com/napolitano/cordova-plugin-intent
            if(args.length() != 0) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
                return false;
            }

            Intent intent = cordova.getActivity().getIntent();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, getIntentJson(intent)));
            return true;
        }

        return true;
    }

    private void startActivity(String action, Uri uri, String type, Map<String, String> extras, boolean bExpectResult, int requestCode) {
        //  Credit: https://github.com/chrisekelley/cordova-webintent
        Intent i = (uri != null ? new Intent(action, uri) : new Intent(action));

        if (type != null && uri != null) {
            i.setDataAndType(uri, type); //Fix the crash problem with android 2.3.6
        } else {
            if (type != null) {
                i.setType(type);
            }
        }

        for (String key : extras.keySet()) {
            String value = extras.get(key);
            // If type is text html, the extra text must sent as HTML
            if (key.equals(Intent.EXTRA_TEXT) && type.equals("text/html")) {
                i.putExtra(key, Html.fromHtml(value));
            } else if (key.equals(Intent.EXTRA_STREAM)) {
                // allowes sharing of images as attachments.
                // value in this case should be a URI of a file
                final CordovaResourceApi resourceApi = webView.getResourceApi();
                i.putExtra(key, resourceApi.remapUri(Uri.parse(value)));
            } else if (key.equals(Intent.EXTRA_EMAIL)) {
                // allows to add the email address of the receiver
                i.putExtra(Intent.EXTRA_EMAIL, new String[] { value });
            } else {
                i.putExtra(key, value);
            }
        }
        if (bExpectResult)
        {
            cordova.setActivityResultCallback(this);
            ((CordovaActivity) this.cordova.getActivity()).startActivityForResult(i, requestCode);
        }
        else
            ((CordovaActivity)this.cordova.getActivity()).startActivity(i);
    }

    private void sendBroadcast(String action, Map<String, Object> extras) {
        //  Credit: https://github.com/chrisekelley/cordova-webintent
        Intent intent = new Intent();
        intent.setAction(action);
        //  This method can handle sending broadcasts of Strings, Booleans and String Arrays.
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (value instanceof String)
                intent.putExtra(key, (String)value);
            else if (value instanceof Boolean)
                intent.putExtra(key, (Boolean)value);
            else if (value instanceof JSONArray)
            {
                //  String Array
                JSONArray valueArray = (JSONArray)value;
                String[] values = new String[valueArray.length()];
                for (int i = 0; i < valueArray.length(); i++)
                    try {
                        values[i] = valueArray.getString(i);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                intent.putExtra(key, values);
            }

        }

        ((CordovaActivity)this.cordova.getActivity()).sendBroadcast(intent);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (this.onNewIntentCallbackContext != null) {

            PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
            result.setKeepCallback(true);
            this.onNewIntentCallbackContext.sendPluginResult(result);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        if (onActivityResultCallbackContext != null && intent != null)
        {
            intent.putExtra("requestCode", requestCode);
            intent.putExtra("resultCode", resultCode);
            PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
            result.setKeepCallback(true);
            onActivityResultCallbackContext.sendPluginResult(result);
        }
        else if (onActivityResultCallbackContext != null)
        {
            Intent canceledIntent = new Intent();
            canceledIntent.putExtra("requestCode", requestCode);
            canceledIntent.putExtra("resultCode", resultCode);
            PluginResult canceledResult = new PluginResult(PluginResult.Status.OK, getIntentJson(canceledIntent));
            canceledResult.setKeepCallback(true);
            onActivityResultCallbackContext.sendPluginResult(canceledResult);
        }

    }

    private BroadcastReceiver myBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (onBroadcastCallbackContext != null)
            {
                PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
                result.setKeepCallback(true);
                onBroadcastCallbackContext.sendPluginResult(result);
            }
        }
    };

    /**
     * Return JSON representation of intent attributes
     *
     * @param intent
     * Credit: https://github.com/napolitano/cordova-plugin-intent
     */
    private JSONObject getIntentJson(Intent intent) {
        JSONObject intentJSON = null;
        ClipData clipData = null;
        JSONObject[] items = null;
        ContentResolver cR = this.cordova.getActivity().getApplicationContext().getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clipData = intent.getClipData();
            if(clipData != null) {
                int clipItemCount = clipData.getItemCount();
                items = new JSONObject[clipItemCount];

                for (int i = 0; i < clipItemCount; i++) {

                    ClipData.Item item = clipData.getItemAt(i);

                    try {
                        items[i] = new JSONObject();
                        items[i].put("htmlText", item.getHtmlText());
                        items[i].put("intent", item.getIntent());
                        items[i].put("text", item.getText());
                        items[i].put("uri", item.getUri());

                        if (item.getUri() != null) {
                            String type = cR.getType(item.getUri());
                            String extension = mime.getExtensionFromMimeType(cR.getType(item.getUri()));

                            items[i].put("type", type);
                            items[i].put("extension", extension);
                        }

                    } catch (JSONException e) {
                        Log.d(LOG_TAG, " Error thrown during intent > JSON conversion");
                        Log.d(LOG_TAG, e.getMessage());
                        Log.d(LOG_TAG, Arrays.toString(e.getStackTrace()));
                    }

                }
            }
        }

        try {
            intentJSON = new JSONObject();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if(items != null) {
                    intentJSON.put("clipItems", new JSONArray(items));
                }
            }

            intentJSON.put("type", intent.getType());
            intentJSON.put("extras", toJsonObject(intent.getExtras()));
            intentJSON.put("action", intent.getAction());
            intentJSON.put("categories", intent.getCategories());
            intentJSON.put("flags", intent.getFlags());
            intentJSON.put("component", intent.getComponent());
            intentJSON.put("data", intent.getData());
            intentJSON.put("package", intent.getPackage());

            return intentJSON;
        } catch (JSONException e) {
            Log.d(LOG_TAG, " Error thrown during intent > JSON conversion");
            Log.d(LOG_TAG, e.getMessage());
            Log.d(LOG_TAG, Arrays.toString(e.getStackTrace()));

            return null;
        }
    }

    private static JSONObject toJsonObject(Bundle bundle) {
        //  Credit: https://github.com/napolitano/cordova-plugin-intent
        try {
            return (JSONObject) toJsonValue(bundle);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Cannot convert bundle to JSON: " + e.getMessage(), e);
        }
    }

    private static Object toJsonValue(final Object value) throws JSONException {
        //  Credit: https://github.com/napolitano/cordova-plugin-intent
        if (value == null) {
            return null;
        } else if (value instanceof Bundle) {
            final Bundle bundle = (Bundle) value;
            final JSONObject result = new JSONObject();
            for (final String key : bundle.keySet()) {
                result.put(key, toJsonValue(bundle.get(key)));
            }
            return result;
        } else if (value.getClass().isArray()) {
            final JSONArray result = new JSONArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; ++i) {
                result.put(i, toJsonValue(Array.get(value, i)));
            }
            return result;
        } else if (
                value instanceof String
                        || value instanceof Boolean
                        || value instanceof Integer
                        || value instanceof Long
                        || value instanceof Double) {
            return value;
        } else {
            return String.valueOf(value);
        }
    }
}

