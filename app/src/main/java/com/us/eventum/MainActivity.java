package com.us.eventum;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputEditText nombreEditText;
    private TextInputEditText apellidosEditText;
    private TextInputEditText fechaNacimientoEditText;
    private TextInputEditText lugarNacimientoEditText;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
    private MaterialCheckBox rememberMeCheckBox;
    private View progressBar;
    private Calendar calendar;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar Firebase Auth y Database
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        sharedPreferences = getSharedPreferences("EventumPrefs", MODE_PRIVATE);

        // Inicializar calendario
        calendar = Calendar.getInstance();

        // Inicializar vistas
        initializeViews();
        
        // Configurar listeners
        setupListeners();
        
        // Cargar datos guardados
        loadSavedData();
        
        // Iniciar animaciones
        startAnimations();
    }

    private void initializeViews() {
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        nombreEditText = findViewById(R.id.nombreEditText);
        apellidosEditText = findViewById(R.id.apellidosEditText);
        fechaNacimientoEditText = findViewById(R.id.fechaNacimientoEditText);
        lugarNacimientoEditText = findViewById(R.id.lugarNacimientoEditText);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        rememberMeCheckBox = findViewById(R.id.rememberMeCheckBox);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        loginButton.setOnClickListener(v -> loginUser());
        registerButton.setOnClickListener(v -> registerUser());
        fechaNacimientoEditText.setOnClickListener(v -> showDatePicker());
        findViewById(R.id.forgotPasswordTextView).setOnClickListener(v -> forgotPassword());
    }

    private void loadSavedData() {
        if (sharedPreferences.getBoolean("rememberMe", false)) {
            emailEditText.setText(sharedPreferences.getString("email", ""));
            passwordEditText.setText(sharedPreferences.getString("password", ""));
            rememberMeCheckBox.setChecked(true);
        }
    }

    private void startAnimations() {
        View[] views = {
            findViewById(R.id.logoImageView),
            findViewById(R.id.titleTextView),
            findViewById(R.id.emailLayout),
            findViewById(R.id.passwordLayout),
            findViewById(R.id.nombreLayout),
            findViewById(R.id.apellidosLayout),
            findViewById(R.id.fechaNacimientoLayout),
            findViewById(R.id.lugarNacimientoLayout),
            rememberMeCheckBox,
            findViewById(R.id.forgotPasswordTextView),
            loginButton,
            registerButton
        };

        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (view.getAlpha() == 0f) {  // Solo animar si no está visible
                view.setAlpha(0f);
                view.setTranslationY(50f);

                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
                ObjectAnimator translationAnimator = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f);

                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(alphaAnimator, translationAnimator);
                animatorSet.setDuration(500);
                animatorSet.setStartDelay(i * 100);
                animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
                animatorSet.start();
            }
        }
    }

    private void showDatePicker() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateLabel();
        };

        new DatePickerDialog(this, dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel() {
        String format = "dd/MM/yyyy";
        SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.getDefault());
        fechaNacimientoEditText.setText(dateFormat.format(calendar.getTime()));
    }

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Limpiar errores anteriores
        clearError(emailEditText);
        clearError(passwordEditText);

        if (!validateEmail(email)) {
            showError(emailEditText, "Email inválido");
            return;
        }

        if (!validatePassword(password)) {
            showError(passwordEditText, "Contraseña inválida");
            return;
        }

        showProgress(true);

        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                showProgress(false);
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (rememberMeCheckBox.isChecked()) {
                        saveCredentials(email, password);
                    } else {
                        clearSavedCredentials();
                    }
                    Toast.makeText(MainActivity.this, 
                        "Bienvenido " + user.getEmail(), 
                        Toast.LENGTH_SHORT).show();
                    // TODO: Navegar a la siguiente pantalla
                } else {
                    Toast.makeText(MainActivity.this, 
                        "Error al iniciar sesión: " + task.getException().getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void registerUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String nombre = nombreEditText.getText().toString().trim();
        String apellidos = apellidosEditText.getText().toString().trim();
        String fechaNacimiento = fechaNacimientoEditText.getText().toString().trim();
        String lugarNacimiento = lugarNacimientoEditText.getText().toString().trim();

        if (!validateAllFields(email, password, nombre, apellidos, fechaNacimiento, lugarNacimiento)) {
            return;
        }

        showProgress(true);

        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    saveUserToDatabase(user, nombre, apellidos, fechaNacimiento, lugarNacimiento);
                } else {
                    showProgress(false);
                    Toast.makeText(MainActivity.this, 
                        "Error al crear usuario: " + task.getException().getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private boolean validateAllFields(String email, String password, String nombre, 
                                    String apellidos, String fechaNacimiento, String lugarNacimiento) {
        // Limpiar errores anteriores
        clearError(emailEditText);
        clearError(passwordEditText);
        clearError(nombreEditText);
        clearError(apellidosEditText);
        clearError(fechaNacimientoEditText);
        clearError(lugarNacimientoEditText);

        boolean isValid = true;

        if (!validateEmail(email)) {
            showError(emailEditText, "Email inválido");
            isValid = false;
        }

        if (!validatePassword(password)) {
            showError(passwordEditText, "La contraseña debe tener al menos 6 caracteres");
            isValid = false;
        }

        if (nombre.isEmpty()) {
            showError(nombreEditText, "El nombre es requerido");
            isValid = false;
        }

        if (apellidos.isEmpty()) {
            showError(apellidosEditText, "Los apellidos son requeridos");
            isValid = false;
        }

        if (fechaNacimiento.isEmpty()) {
            showError(fechaNacimientoEditText, "La fecha de nacimiento es requerida");
            isValid = false;
        }

        if (lugarNacimiento.isEmpty()) {
            showError(lugarNacimientoEditText, "El lugar de nacimiento es requerido");
            isValid = false;
        }

        return isValid;
    }

    private boolean validateEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean validatePassword(String password) {
        return password.length() >= 6;
    }

    private void showError(TextInputEditText editText, String message) {
        View parent = (View) editText.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) {
            parent = (View) parent.getParent();
        }
        if (parent instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) parent;
            layout.setError(message);
            layout.setErrorEnabled(true);
        }
        editText.requestFocus();
    }

    private void clearError(TextInputEditText editText) {
        View parent = (View) editText.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) {
            parent = (View) parent.getParent();
        }
        if (parent instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) parent;
            layout.setErrorEnabled(false);
        }
    }

    private void saveUserToDatabase(FirebaseUser user, String nombre, String apellidos, 
                                  String fechaNacimiento, String lugarNacimiento) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("uid", user.getUid());
        userData.put("nombre", nombre);
        userData.put("apellidos", apellidos);
        userData.put("fechaNacimiento", fechaNacimiento);
        userData.put("lugarNacimiento", lugarNacimiento);
        userData.put("createdAt", System.currentTimeMillis());

        mDatabase.child("users").child(user.getUid()).setValue(userData)
            .addOnCompleteListener(task -> {
                showProgress(false);
                if (task.isSuccessful()) {
                    Toast.makeText(MainActivity.this, 
                        "Usuario creado exitosamente", 
                        Toast.LENGTH_SHORT).show();
                    // TODO: Navegar a la siguiente pantalla
                } else {
                    Toast.makeText(MainActivity.this, 
                        "Error al guardar datos: " + task.getException().getMessage(), 
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void forgotPassword() {
        String email = emailEditText.getText().toString().trim();
        if (!validateEmail(email)) {
            showError(emailEditText, "Email inválido");
            return;
        }

        showProgress(true);
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener(task -> {
                showProgress(false);
                if (task.isSuccessful()) {
                    Toast.makeText(MainActivity.this,
                        "Se ha enviado un correo para restablecer tu contraseña",
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this,
                        "Error al enviar el correo: " + task.getException().getMessage(),
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void saveCredentials(String email, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("email", email);
        editor.putString("password", password);
        editor.putBoolean("rememberMe", true);
        editor.apply();
    }

    private void clearSavedCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("email");
        editor.remove("password");
        editor.putBoolean("rememberMe", false);
        editor.apply();
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!show);
        registerButton.setEnabled(!show);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Toast.makeText(this, 
                "Ya estás autenticado como " + currentUser.getEmail(), 
                Toast.LENGTH_SHORT).show();
            // TODO: Navegar a la siguiente pantalla
        }
    }
}