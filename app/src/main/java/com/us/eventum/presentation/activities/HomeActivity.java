package com.us.eventum.presentation.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.us.eventum.adapters.EventAdapter;
import com.us.eventum.models.Event;
import com.us.eventum.R;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity implements EventAdapter.OnEventClickListener {
    private static final String TAG = "HomeActivity";
    private FloatingActionButton settingsButton, createEventButton;
    private TextView welcomeText;
    private RecyclerView futureEventsRecyclerView, pastEventsRecyclerView;
    private FirebaseFirestore db;
    private String userId;
    private String userName;
    private EventAdapter futureEventAdapter, pastEventAdapter;
    private FirebaseAuth mAuth;
    private List<Event> futureEvents = new ArrayList<>();
    private List<Event> pastEvents = new ArrayList<>();
    private TextView futureEventsCount, pastEventsCount, noFutureEventsText, noPastEventsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        userId = mAuth.getCurrentUser().getUid();

        // Inicializar vistas
        initializeViews();
        setupClickListeners();
        loadUserData();
        loadEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }

    private void initializeViews() {
        createEventButton = findViewById(R.id.createEventFab);
        settingsButton = findViewById(R.id.settingsButton);
        welcomeText = findViewById(R.id.welcomeText);
        futureEventsCount = findViewById(R.id.futureEventsCount);
        pastEventsCount = findViewById(R.id.pastEventsCount);
        futureEventsRecyclerView = findViewById(R.id.futureEventsRecyclerView);
        pastEventsRecyclerView = findViewById(R.id.pastEventsRecyclerView);
        noFutureEventsText = findViewById(R.id.noFutureEventsText);
        noPastEventsText = findViewById(R.id.noPastEventsText);
        
        futureEventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pastEventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        futureEventAdapter = new EventAdapter(this);
        pastEventAdapter = new EventAdapter(this);
        
        futureEventsRecyclerView.setAdapter(futureEventAdapter);
        pastEventsRecyclerView.setAdapter(pastEventAdapter);

        setupEventClickListeners();
    }

    private void loadUserData() {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    // Los datos actualmente están guardados en el campo "nombre"
                    userName = documentSnapshot.getString("nombre");
                    if (userName != null) {
                        welcomeText.setText("¡Hola, " + userName + "!");
                        welcomeText.setVisibility(View.VISIBLE);
                    }
                }
            });
    }

    private void setupClickListeners() {
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        createEventButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateEventActivity.class);
            startActivity(intent);
        });
    }

    private void loadEvents() {
        if (mAuth.getCurrentUser() == null) return;

        // Obtener la fecha actual
        Date now = new Date();
        
        // Obtener todos los eventos del usuario
        db.collection("events")
            .whereEqualTo("userId", mAuth.getCurrentUser().getUid())
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                // Limpiar las listas actuales
                futureEvents.clear();
                pastEvents.clear();
                
                // Clasificar los eventos según su fecha
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    Event event = document.toObject(Event.class);
                    event.setId(document.getId());
                    
                    if (event.getDate().after(now)) {
                        futureEvents.add(event);
                    } else {
                        pastEvents.add(event);
                    }
                }
                
                // Ordenar eventos futuros por fecha ascendente
                futureEvents.sort((e1, e2) -> e1.getDate().compareTo(e2.getDate()));
                
                // Ordenar eventos pasados por fecha descendente
                pastEvents.sort((e1, e2) -> e2.getDate().compareTo(e1.getDate()));
                
                // Actualizar los adaptadores
                futureEventAdapter.setEvents(futureEvents);
                pastEventAdapter.setEvents(pastEvents);
                
                // Actualizar contadores y visibilidad
                updateUI();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error al cargar eventos", e);
                Toast.makeText(this, "Error al cargar eventos: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            });
    }

    private void updateUI() {
        // Actualizar contador de eventos futuros
        String futureCountText = String.format(Locale.getDefault(), 
            "Eventos futuros (%d)", 
            futureEvents.size());
        futureEventsCount.setText(futureCountText);
        
        // Actualizar contador de eventos pasados
        String pastCountText = String.format(Locale.getDefault(), 
            "Eventos pasados (%d)", 
            pastEvents.size());
        pastEventsCount.setText(pastCountText);
        
        // Mostrar/ocultar mensajes y listas según corresponda
        if (futureEvents.isEmpty()) {
            noFutureEventsText.setVisibility(View.VISIBLE);
            futureEventsRecyclerView.setVisibility(View.GONE);
            futureEventsCount.setVisibility(View.GONE);
        } else {
            noFutureEventsText.setVisibility(View.GONE);
            futureEventsRecyclerView.setVisibility(View.VISIBLE);
            futureEventsCount.setVisibility(View.VISIBLE);
        }
        
        if (pastEvents.isEmpty()) {
            noPastEventsText.setVisibility(View.VISIBLE);
            pastEventsRecyclerView.setVisibility(View.GONE);
            pastEventsCount.setVisibility(View.GONE);
        } else {
            noPastEventsText.setVisibility(View.GONE);
            pastEventsRecyclerView.setVisibility(View.VISIBLE);
            pastEventsCount.setVisibility(View.VISIBLE);
        }
    }

    private void setupEventClickListeners() {
        futureEventAdapter.setOnItemClickListener(this);
        pastEventAdapter.setOnItemClickListener(this);
    }

    @Override
    public void onEventClick(Event event) {
        Intent intent = new Intent(this, EventDetailsActivity.class);
        intent.putExtra("event", event);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mAuth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
} 