package com.us.eventum.data.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestor del estado de la red
 * Monitorea la conectividad y notifica cambios a los observadores
 */
public class NetworkStateManager {
    
    private static volatile NetworkStateManager INSTANCE;
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final CopyOnWriteArrayList<NetworkStateListener> listeners;
    private boolean isOnline;
    
    private NetworkStateManager(Context context) {
        this.context = context.getApplicationContext();
        this.connectivityManager = (ConnectivityManager) this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listeners = new CopyOnWriteArrayList<>();
        this.isOnline = isNetworkAvailable();
        registerNetworkCallback();
    }
    
    /**
     * Obtener instancia singleton
     */
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
    
    /**
     * Verificar si hay conexión a internet
     */
    public boolean isOnline() {
        boolean currentState = isOnline;
        boolean actualState = isNetworkAvailable();
        if (currentState != actualState) {
            System.out.println("NetworkStateManager: Estado de red inconsistente. isOnline: " + currentState + ", actual: " + actualState);
            updateNetworkState(actualState);
        }
        return isOnline;
    }
    
    /**
     * Verificar disponibilidad de red
     */
    private boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            return false;
        }

        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }
    
    /**
     * Registrar callback de red
     */
    private void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        
        connectivityManager.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                updateNetworkState(true);
            }
            
            @Override
            public void onLost(Network network) {
                super.onLost(network);
                updateNetworkState(false);
            }
        });
    }
    
    /**
     * Actualizar estado de la red y notificar a los listeners
     */
    private void updateNetworkState(boolean online) {
        if (this.isOnline != online) {
            System.out.println("NetworkStateManager: Cambio de estado de red: " + this.isOnline + " -> " + online);
            this.isOnline = online;
            notifyListeners(online);
        }
    }
    
    /**
     * Notificar a todos los listeners del cambio de estado
     */
    private void notifyListeners(boolean online) {
        for (NetworkStateListener listener : listeners) {
            try {
                if (online) {
                    listener.onNetworkAvailable();
                } else {
                    listener.onNetworkLost();
                }
            } catch (Exception e) {
                // Log error but don't crash
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Agregar listener para cambios de red
     */
    public void addNetworkStateListener(NetworkStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remover listener de cambios de red
     */
    public void removeNetworkStateListener(NetworkStateListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Interfaz para escuchar cambios de estado de red
     */
    public interface NetworkStateListener {
        void onNetworkAvailable();
        void onNetworkLost();
    }
}
