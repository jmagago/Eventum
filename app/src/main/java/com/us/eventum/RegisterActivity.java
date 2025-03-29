package com.us.eventum;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {
    // Variables para Firebase
    private FirebaseAuth mAuth; // Para manejar la autenticación
    private DatabaseReference mDatabase; // Para guardar datos en la base de datos

    // Variables para los campos de texto
    private TextInputEditText emailEditText, passwordEditText, nombreEditText, 
                            primerApellidoEditText, segundoApellidoEditText, 
                            fechaNacimientoEditText, lugarNacimientoEditText;
    
    // Variables para los layouts que contienen los campos de texto
    private TextInputLayout emailLayout, passwordLayout, nombreLayout, 
                           primerApellidoLayout, segundoApellidoLayout, 
                           fechaNacimientoLayout, lugarNacimientoLayout;
    
    // Variables para la UI
    private MaterialButton registerButton; // Botón para registrar
    private View progressBar; // Barra de progreso
    private Calendar calendar; // Calendario para el selector de fecha

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Configurar el idioma español para toda la app
        Locale locale = new Locale("es", "ES");
        Locale.setDefault(locale);
        
        setContentView(R.layout.activity_register);

        // Inicializar Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Configurar el calendario para que empiece en lunes
        calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);

        // Inicializar todas las vistas
        initializeViews();
        
        // Configurar los listeners de los botones
        setupListeners();
        
        // Iniciar las animaciones de entrada
        startAnimations();

        // Configurar el teclado virtual para que no tape los campos
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /**
     * Inicializa todas las vistas del layout
     * Aquí conectamos los elementos del XML con las variables de Java
     */
    private void initializeViews() {
        // Inicializar los campos de texto
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        nombreEditText = findViewById(R.id.nombreEditText);
        primerApellidoEditText = findViewById(R.id.primerApellidoEditText);
        segundoApellidoEditText = findViewById(R.id.segundoApellidoEditText);
        fechaNacimientoEditText = findViewById(R.id.fechaNacimientoEditText);
        lugarNacimientoEditText = findViewById(R.id.lugarNacimientoEditText);

        // Inicializar los layouts que contienen los campos
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        nombreLayout = findViewById(R.id.nombreLayout);
        primerApellidoLayout = findViewById(R.id.primerApellidoLayout);
        segundoApellidoLayout = findViewById(R.id.segundoApellidoLayout);
        fechaNacimientoLayout = findViewById(R.id.fechaNacimientoLayout);
        lugarNacimientoLayout = findViewById(R.id.lugarNacimientoLayout);

        // Inicializar botones y barra de progreso
        registerButton = findViewById(R.id.registerButton);
        progressBar = findViewById(R.id.progressBar);
    }

    /**
     * Configura los listeners de los botones
     * Aquí definimos qué hacer cuando el usuario interactúa con los botones
     */
    private void setupListeners() {
        // Listener para el botón de registro
        registerButton.setOnClickListener(v -> registerUser());
        
        // Listener para el campo de fecha de nacimiento
        fechaNacimientoEditText.setOnClickListener(v -> showDatePicker());
        
        // Listener para el botón de volver
        findViewById(R.id.backButton).setOnClickListener(v -> {
            // Ocultar el teclado virtual si está visible
            View view = this.getCurrentFocus();
            if (view != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
            // Volver a la pantalla de login
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    /**
     * Inicia las animaciones de entrada de los elementos
     * Cada elemento aparece con un pequeño retraso para crear un efecto cascada
     */
    private void startAnimations() {
        // Lista de vistas a animar
        View[] views = {
            findViewById(R.id.logoImageView),
            findViewById(R.id.titleTextView),
            findViewById(R.id.emailLayout),
            findViewById(R.id.passwordLayout),
            findViewById(R.id.nombreLayout),
            findViewById(R.id.primerApellidoLayout),
            findViewById(R.id.segundoApellidoLayout),
            findViewById(R.id.fechaNacimientoLayout),
            findViewById(R.id.lugarNacimientoLayout),
            registerButton
        };

        // Animar cada vista
        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (view.getAlpha() == 0f) {
                view.setAlpha(0f);
                view.setTranslationY(50f);

                // Crear animaciones de fade in y slide up
                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
                ObjectAnimator translationAnimator = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f);

                // Combinar las animaciones
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(alphaAnimator, translationAnimator);
                animatorSet.setDuration(500);
                animatorSet.setStartDelay(i * 100); // Retraso para efecto cascada
                animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
                animatorSet.start();
            }
        }
    }

    /**
     * Muestra el selector de fecha
     * Permite al usuario elegir su fecha de nacimiento
     */
    private void showDatePicker() {
        // Listener para cuando se selecciona una fecha
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateLabel();
        };

        // Crear y mostrar el diálogo de selección de fecha
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH));

        // Configurar el primer día de la semana como lunes
        datePickerDialog.getDatePicker().setFirstDayOfWeek(Calendar.MONDAY);
        
        // Configurar el idioma español
        Locale locale = new Locale("es", "ES");
        Locale.setDefault(locale);
        
        datePickerDialog.show();
    }

    /**
     * Actualiza el campo de fecha con el formato español
     */
    private void updateDateLabel() {
        String format = "dd/MM/yyyy";
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.getDefault());
        fechaNacimientoEditText.setText(dateFormat.format(calendar.getTime()));
    }

    /**
     * Proceso de registro del usuario
     * 1. Valida todos los campos
     * 2. Crea el usuario en Firebase Auth
     * 3. Guarda los datos adicionales en Firebase Database
     */
    private void registerUser() {
        // Limpiar errores anteriores
        clearError(emailLayout);
        clearError(passwordLayout);
        clearError(nombreLayout);
        clearError(primerApellidoLayout);
        clearError(segundoApellidoLayout);
        clearError(fechaNacimientoLayout);
        clearError(lugarNacimientoLayout);

        // Obtener valores de los campos
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String nombre = nombreEditText.getText().toString().trim();
        String primerApellido = primerApellidoEditText.getText().toString().trim();
        String segundoApellido = segundoApellidoEditText.getText().toString().trim();
        String fechaNacimiento = fechaNacimientoEditText.getText().toString().trim();
        String lugarNacimiento = lugarNacimientoEditText.getText().toString().trim();

        // Validar todos los campos
        boolean isValid = true;

        // Validar email
        if (email.isEmpty()) {
            showError(emailLayout, "El email es obligatorio");
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(emailLayout, "Email no válido");
            isValid = false;
        }

        // Validar contraseña
        if (password.isEmpty()) {
            showError(passwordLayout, "La contraseña es obligatoria");
            isValid = false;
        } else if (password.length() < 6) {
            showError(passwordLayout, "La contraseña debe tener al menos 6 caracteres");
            isValid = false;
        }

        // Validar nombre
        if (nombre.isEmpty()) {
            showError(nombreLayout, "El nombre es obligatorio");
            isValid = false;
        }

        // Validar primer apellido
        if (primerApellido.isEmpty()) {
            showError(primerApellidoLayout, "El primer apellido es obligatorio");
            isValid = false;
        }

        // Validar fecha de nacimiento
        if (fechaNacimiento.isEmpty()) {
            showError(fechaNacimientoLayout, "La fecha de nacimiento es obligatoria");
            isValid = false;
        }

        // Validar lugar de nacimiento
        if (lugarNacimiento.isEmpty()) {
            showError(lugarNacimientoLayout, "El lugar de nacimiento es obligatorio");
            isValid = false;
        }

        // Si hay errores, no continuar
        if (!isValid) return;

        // Mostrar barra de progreso y deshabilitar el botón
        progressBar.setVisibility(View.VISIBLE);
        registerButton.setEnabled(false);

        // Crear usuario en Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    // Usuario creado exitosamente
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        // Crear objeto con los datos del usuario
                        User userData = new User(
                            user.getUid(),
                            email,
                            nombre,
                            primerApellido,
                            segundoApellido,
                            fechaNacimiento,
                            lugarNacimiento
                        );

                        // Guardar datos en Firebase Database
                        mDatabase.child("users").child(user.getUid()).setValue(userData)
                            .addOnSuccessListener(aVoid -> {
                                // Navegar a la pantalla principal
                                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            })
                            .addOnFailureListener(e -> {
                                // Error al guardar datos
                                showError(null, "Error al guardar los datos: " + e.getMessage());
                                progressBar.setVisibility(View.GONE);
                                registerButton.setEnabled(true);
                            });
                    }
                } else {
                    // Error en la creación del usuario
                    showError(null, "Error al crear la cuenta: " + task.getException().getMessage());
                    progressBar.setVisibility(View.GONE);
                    registerButton.setEnabled(true);
                }
            });
    }

    /**
     * Muestra un mensaje de error en un campo específico
     * @param layout El layout donde mostrar el error
     * @param message El mensaje de error a mostrar
     */
    private void showError(TextInputLayout layout, String message) {
        if (layout != null) {
            layout.setError(message);
            layout.setErrorEnabled(true);
        }
        if (layout != null) {
            TextInputEditText editText = (TextInputEditText) layout.getEditText();
            if (editText != null) {
                editText.requestFocus();
            }
        }
    }

    /**
     * Limpia el mensaje de error de un campo
     * @param layout El layout donde limpiar el error
     */
    private void clearError(TextInputLayout layout) {
        if (layout != null) {
            layout.setErrorEnabled(false);
        }
    }
} 