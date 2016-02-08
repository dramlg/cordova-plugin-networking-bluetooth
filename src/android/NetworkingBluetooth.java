// Copyright 2016 Franco Bugnano
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cordova.plugin.networking.bluetooth;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.util.SparseArray;

public class NetworkingBluetooth extends CordovaPlugin {
	public static final String TAG = "NetworkingBluetooth";
	public static final int REQUEST_ENABLE_BT = 1773;

	public BluetoothAdapter mBluetoothAdapter = null;
	public SparseArray<CallbackContext> mContextForActivity = new SparseArray<CallbackContext>();
	public CallbackContext mContextForAdapterStateChange = null;
	public CallbackContext mContextForEnable = null;
	public CallbackContext mContextForDisable = null;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);

		this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (this.mBluetoothAdapter == null) {
			callbackContext.error("Device does not support Bluetooth");
			return false;
		}

		if (action.equals("registerAdapterStateChanged")) {
			this.mContextForAdapterStateChange = callbackContext;
			IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
			cordova.getActivity().registerReceiver(this.mReceiver, filter);
			return true;
		} else if (action.equals("getAdapterState")) {
			this.getAdapterState(callbackContext, false);
			return true;
		} else if (action.equals("requestEnable")) {
			if (!this.mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				this.prepareActivity(action, args, callbackContext, enableBtIntent, REQUEST_ENABLE_BT);
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("enable")) {
			// If there already is another enable action pending, call the error callback in order
			// to notify that the previous action has been cancelled
			if (this.mContextForEnable != null) {
				this.mContextForEnable.error(1);
				this.mContextForEnable = null;
			}

			if (!this.mBluetoothAdapter.isEnabled()) {
				if (!this.mBluetoothAdapter.enable()) {
					callbackContext.error(0);
				} else {
					// Save the context, in order to send the result once the action has been completed
					this.mContextForEnable = callbackContext;
				}
			} else {
				callbackContext.success();
			}
			return true;
		} else if (action.equals("disable")) {
			// If there already is another disable action pending, call the error callback in order
			// to notify that the previous action has been cancelled
			if (this.mContextForDisable != null) {
				this.mContextForDisable.error(1);
				this.mContextForDisable = null;
			}

			if (this.mBluetoothAdapter.isEnabled()) {
				if (!this.mBluetoothAdapter.disable()) {
					callbackContext.error(0);
				} else {
					// Save the context, in order to send the result once the action has been completed
					this.mContextForDisable = callbackContext;
				}
			} else {
				callbackContext.success();
			}
			return true;
		} else {
			callbackContext.error("Invalid action");
			return false;
		}
	}

	public void getAdapterState(CallbackContext callbackContext, boolean keepCallback) {
		try {
			JSONObject adapterState = new JSONObject();
			adapterState.put("address", this.mBluetoothAdapter.getAddress());
			adapterState.put("name", this.mBluetoothAdapter.getName());
			adapterState.put("discovering", this.mBluetoothAdapter.isDiscovering());
			adapterState.put("enabled", this.mBluetoothAdapter.isEnabled());
			adapterState.put("discoverable", this.mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);

            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, adapterState);
            pluginResult.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(pluginResult);
		} catch (JSONException e) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, 0);
            pluginResult.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(pluginResult);
		}
	}

	public void prepareActivity(String action, JSONArray args, CallbackContext callbackContext, Intent intent, int requestCode) {
		// First of all check whether there already is another activity pending with the same requestCode
		CallbackContext oldContext = this.mContextForActivity.get(requestCode);
		this.mContextForActivity.remove(requestCode);

		// TO DO -- The activity should be canceled here

		// If there already is another activity with this request code, call the error callback in order
		// to notify that the previous activity has been cancelled
		if (oldContext != null) {
			oldContext.error(1);
		}

		// Store the callbackContext, in order to send the result once the activity has been completed
		this.mContextForActivity.put(requestCode, callbackContext);

		// Store the callbackContext, in order to send the result once the activity has been completed
		cordova.startActivityForResult(this, intent, requestCode);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		CallbackContext callbackContext = this.mContextForActivity.get(requestCode);
		this.mContextForActivity.remove(requestCode);

		if (callbackContext != null) {
			if (resultCode == Activity.RESULT_OK) {
				callbackContext.success();
			} else {
				callbackContext.error(0);
			}
		} else {
			// TO DO -- This may be a bug on the JavaScript side, as we get here only if the
			// activity has been started twice, before waiting the completion of the first one.
			Log.e(TAG, "BUG: onActivityResult -- (callbackContext == null)");
		}
	}

	public final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
				int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);

				// If there was an enable request pending, send the result
				if ((previousState == BluetoothAdapter.STATE_TURNING_ON) && (mContextForEnable != null)) {
					if (state == BluetoothAdapter.STATE_ON) {
						mContextForEnable.success();
					} else {
						mContextForEnable.error(2);
					}
					mContextForEnable = null;
				}

				// If there was a disable request pending, send the result
				if ((previousState == BluetoothAdapter.STATE_TURNING_OFF) && (mContextForDisable != null)) {
					if (state == BluetoothAdapter.STATE_OFF) {
						mContextForDisable.success();
					} else {
						mContextForDisable.error(2);
					}
					mContextForDisable = null;
				}

				// Send the state changed event only if the state is not a transitioning one
				if ((state == BluetoothAdapter.STATE_OFF) || (state == BluetoothAdapter.STATE_ON)) {
					getAdapterState(mContextForAdapterStateChange, true);
				}
			}
		}
	};
}

