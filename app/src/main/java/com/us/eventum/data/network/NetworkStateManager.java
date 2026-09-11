package com.us.eventum.data.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestor del estado de la red usando {@link ConnectivityManager.NetworkCallback}
 * y {@link NetworkCapabilities} (sin APIs deprecadas como {@code NetworkInfo}).
 */
public class NetworkStateManager {

    private static final String TAG = "NetworkStateManager";

    private static volatile NetworkStateManager INSTANCE;
    private final ConnectivityManager connectivityManager;
    private final CopyOnWriteArrayList<NetworkStateListener> listeners;
    private boolean isOnline;

    private NetworkStateManager(Context context) {
        Context appContext = context.getApplicationContext();
        this.connectivityManager = appContext.getSystemService(ConnectivityManager.class);
        this.listeners = new CopyOnWriteArrayList<>();
        this.isOnline = isNetworkAvailable();
        registerNetworkCallback();
    }

    public static NetworkStateManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (NetworkStateManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new NetworkStateManager(context);
                }
            }
        }
        return INSTANCE;
    }

    public boolean isOnline() {
        boolean actualState = isNetworkAvailable();
        if (isOnline != actualState) {
            Log.d(TAG, "Estado de red desincronizado. cache=" + isOnline + ", actual=" + actualState);
            updateNetworkState(actualState);
        }
        return isOnline;
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            return false;
        }

        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        if (capabilities == null) {
            return false;
        }

        boolean hasInternetTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
        if (!hasInternetTransport) {
            return false;
        }

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void registerNetworkCallback() {
        if (connectivityManager == null) {
            return;
        }

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateNetworkState(isNetworkAvailable());
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateNetworkState(isNetworkAvailable());
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network,
                                              @NonNull NetworkCapabilities networkCapabilities) {
                updateNetworkState(isNetworkAvailable());
            }
        });
    }

    private void updateNetworkState(boolean online) {
        if (this.isOnline != online) {
            Log.d(TAG, "Cambio de estado de red: " + this.isOnline + " -> " + online);
            this.isOnline = online;
            notifyListeners(online);
        }
    }

    private void notifyListeners(boolean online) {
        for (NetworkStateListener listener : listeners) {
            try {
                if (online) {
                    listener.onNetworkAvailable();
                } else {
                    listener.onNetworkLost();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error notificando cambio de red", e);
            }
        }
    }

    public void addNetworkStateListener(NetworkStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeNetworkStateListener(NetworkStateListener listener) {
        listeners.remove(listener);
    }

    public interface NetworkStateListener {
        void onNetworkAvailable();

        void onNetworkLost();
    }
}
