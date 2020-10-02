package com.example.myapp.Utils;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import com.example.myapp.MainActivity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ConnectionStateMonitor extends LiveData<Boolean> {

    private static final String TAG = "debinf ConStateMon";

    // http://mobologicplus.com/monitor-network-connectivity-change-for-available-and-lost-in-android-app/

    private Context context;
    private ConnectivityManager.NetworkCallback networkCallback = null;
    private NetworkReceiver networkReceiver;
    private ConnectivityManager connectivityManager;

    public ConnectionStateMonitor(Context context) {
        this.context = context;
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkCallback = new NetworkCallback(this);
        } else {
            networkReceiver = new NetworkReceiver();
        }
    }

    @Override
    protected void onActive() {
        super.onActive();
        Log.i(TAG, "onActive: ConnectionStateMonitor");
        updateConnection();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        } else {
            IntentFilter networkFilter = new IntentFilter();
            networkFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            context.registerReceiver(networkReceiver,networkFilter);
        }
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        Log.i(TAG, "onInactive: ConnectionStateMonitor");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } else {
            context.unregisterReceiver(networkReceiver);
        }
    }

    @SuppressLint("NewApi")
    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        private  ConnectionStateMonitor connectionStateMonitor;

        public NetworkCallback(ConnectionStateMonitor connectionStateMonitor) {
            this.connectionStateMonitor = connectionStateMonitor;
        }

        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.i(TAG, "onAvailable: ");
            if (network != null) {
                if (connectionStateMonitor.getValue() != null && connectionStateMonitor.getValue()) return;
                new CheckInternetAccessAsyncTask(connectionStateMonitor).execute();
                //connectionStateMonitor.postValue(true);
                //updateConnection();
            }
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.i(TAG, "onLost: ");
            connectionStateMonitor.postValue(false);
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            Log.i(TAG, "onUnavailable: ");
            connectionStateMonitor.postValue(false);
        }
    }

    public void updateConnection() {
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                Log.i(TAG, "updateConnection: init background");
                if (getValue() != null && getValue()) return;
                new CheckInternetAccessAsyncTask(this).execute();
                
                //postValue(true);
            } else {
                Log.i(TAG, "updateConnection: ELSE");
                postValue(false);
            }
        }
    }

    private void createCheckInternetWorkManager() {
        Log.i(TAG, "createCheckInternetWorkManager: ");
        
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest checkInternetAccessWorkRequest = new OneTimeWorkRequest
                .Builder(checkInternetAccessWorker.class)
                .setConstraints(constraints)
                .build();

        // Execute and Manage the background service
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.beginUniqueWork("checkInternet",ExistingWorkPolicy.REPLACE,checkInternetAccessWorkRequest).enqueue();
        /*workManager.enqueueUniqueWork(
                "checkInternet",
                ExistingWorkPolicy.REPLACE,
                checkInternetAccessWorkRequest);*/

        /*WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("checkInternet").observeForever(new Observer<List<WorkInfo>>() {
            @Override
            public void onChanged(List<WorkInfo> workInfos) {

            }
        });*/

    }

    private class NetworkReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            /*if (intent.getAction().equals("android.net.conn.CONNECTIVITY_CHANGE")) {
                updateConnection();
            }*/
            updateConnection();
        }
    }

    // checking data transfer for the internet connection
    private class CheckInternetAccessAsyncTask extends AsyncTask<Void, Void, Boolean> {
        private  ConnectionStateMonitor connectionStateMonitor;

        public CheckInternetAccessAsyncTask(ConnectionStateMonitor connectionStateMonitor) {
            this.connectionStateMonitor = connectionStateMonitor;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                //Log.i(TAG, "doInBackground: ");
                Socket sock = new Socket();
                sock.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
                sock.close();
                return true;
            } catch (IOException e) {
                //Log.i(TAG, "doInBackground: error is : "+e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean hasInternet) {
            super.onPostExecute(hasInternet);
            Log.i(TAG, "onPostExecute: hasInternet: "+hasInternet);
            connectionStateMonitor.postValue(hasInternet);
        }
    }

    public class checkInternetAccessWorker extends Worker {

        public checkInternetAccessWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            try {
                Log.i(TAG, "doInBackground: Worker");
                Socket sock = new Socket();
                sock.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
                sock.close();
                //return true;
            } catch (IOException e) {
                Log.i(TAG, "doInBackground: error is : "+e);
                //return false;
            }
            Log.i(TAG, "doWork: return Result");
            return null;
        }
    }
}
