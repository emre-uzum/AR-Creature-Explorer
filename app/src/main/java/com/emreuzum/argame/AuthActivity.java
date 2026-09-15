package com.emreuzum.argame;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.emreuzum.argame.cloud.CloudPlayerRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestoreException;

public class AuthActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private boolean signInMode = true;

    private EditText usernameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private View usernameContainer;
    private View confirmPasswordContainer;
    private TextView authTitleText;
    private TextView authSubtitleText;
    private TextView authHintText;
    private TextView authStatusText;
    private TextView togglePasswordText;
    private TextView toggleConfirmPasswordText;
    private Button signInTabButton;
    private Button signUpTabButton;
    private Button primaryAuthButton;
    private boolean passwordVisible = false;
    private boolean confirmPasswordVisible = false;
    private CloudPlayerRepository cloudPlayerRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        auth = FirebaseAuth.getInstance();
        cloudPlayerRepository = new CloudPlayerRepository();

        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        usernameContainer = findViewById(R.id.usernameContainer);
        confirmPasswordContainer = findViewById(R.id.confirmPasswordContainer);
        authTitleText = findViewById(R.id.authTitleText);
        authSubtitleText = findViewById(R.id.authSubtitleText);
        authHintText = findViewById(R.id.authHintText);
        authStatusText = findViewById(R.id.authStatusText);
        togglePasswordText = findViewById(R.id.togglePasswordText);
        toggleConfirmPasswordText = findViewById(R.id.toggleConfirmPasswordText);
        signInTabButton = findViewById(R.id.signInTabButton);
        signUpTabButton = findViewById(R.id.signUpTabButton);
        primaryAuthButton = findViewById(R.id.primaryAuthButton);

        signInTabButton.setOnClickListener(v -> updateAuthMode(true));
        signUpTabButton.setOnClickListener(v -> updateAuthMode(false));
        togglePasswordText.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            applyPasswordVisibility(passwordInput, togglePasswordText, passwordVisible);
        });
        toggleConfirmPasswordText.setOnClickListener(v -> {
            confirmPasswordVisible = !confirmPasswordVisible;
            applyPasswordVisibility(confirmPasswordInput, toggleConfirmPasswordText, confirmPasswordVisible);
        });
        primaryAuthButton.setOnClickListener(v -> {
            if (signInMode) {
                signIn();
            } else {
                signUp();
            }
        });

        applyPasswordVisibility(passwordInput, togglePasswordText, false);
        applyPasswordVisibility(confirmPasswordInput, toggleConfirmPasswordText, false);
        updateAuthMode(true);
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            ensurePlayerProfileAndOpenMap(currentUser, null);
        }
    }

    private void updateAuthMode(boolean useSignInMode) {
        signInMode = useSignInMode;

        int activeBg = getColor(R.color.auth_tab_active);
        int inactiveBg = getColor(R.color.auth_tab_inactive);
        int activeText = getColor(R.color.auth_tab_text_active);
        int inactiveText = getColor(R.color.auth_tab_text_inactive);

        signInTabButton.setBackgroundTintList(ColorStateList.valueOf(signInMode ? activeBg : inactiveBg));
        signUpTabButton.setBackgroundTintList(ColorStateList.valueOf(signInMode ? inactiveBg : activeBg));
        signInTabButton.setTextColor(signInMode ? activeText : inactiveText);
        signUpTabButton.setTextColor(signInMode ? inactiveText : activeText);

        if (signInMode) {
            authTitleText.setText("Welcome Back");
            authSubtitleText.setText("Sign in to continue your adventure and keep your captures in sync.");
            authHintText.setText("Use the same email and password you registered with.");
            primaryAuthButton.setText("Sign In");
            authStatusText.setText("Ready to sign in");
            usernameContainer.setVisibility(View.GONE);
            usernameInput.setText("");
            usernameInput.setError(null);
            confirmPasswordContainer.setVisibility(View.GONE);
            confirmPasswordInput.setText("");
            confirmPasswordInput.setError(null);
            confirmPasswordVisible = false;
            applyPasswordVisibility(confirmPasswordInput, toggleConfirmPasswordText, false);
        } else {
            authTitleText.setText("Create Your Account");
            authSubtitleText.setText("Set up a cloud account so your progress can follow you beyond one device.");
            authHintText.setText("Choose a unique username and a password with uppercase, lowercase, and a number.");
            primaryAuthButton.setText("Create Account");
            authStatusText.setText("Ready to create account");
            usernameContainer.setVisibility(View.VISIBLE);
            confirmPasswordContainer.setVisibility(View.VISIBLE);
        }
    }

    private void signIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        clearFieldErrors();

        if (!validateInputs(null, email, password, null, true)) {
            return;
        }

        authStatusText.setText("Signing in...");
        setAuthFormEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        ensurePlayerProfileAndOpenMap(auth.getCurrentUser(), null);
                    } else {
                        setAuthFormEnabled(true);
                        authStatusText.setText("Sign in failed");
                        Toast.makeText(
                                this,
                                task.getException() != null
                                        ? task.getException().getMessage()
                                        : "Authentication failed.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void signUp() {
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        clearFieldErrors();

        if (!validateInputs(username, email, password, confirmPassword, false)) {
            return;
        }

        authStatusText.setText("Creating account...");
        setAuthFormEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        ensurePlayerProfileAndOpenMap(auth.getCurrentUser(), username);
                    } else {
                        setAuthFormEnabled(true);
                        authStatusText.setText("Sign up failed");
                        Toast.makeText(
                                this,
                                task.getException() != null
                                        ? task.getException().getMessage()
                                        : "Account creation failed.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private boolean validateInputs(
            String username,
            String email,
            String password,
            String confirmPassword,
            boolean isSignIn
    ) {
        if (!isSignIn) {
            if (TextUtils.isEmpty(username)) {
                usernameInput.setError("Username is required");
                usernameInput.requestFocus();
                return false;
            }

            if (!CloudPlayerRepository.isUsernameValid(username)) {
                usernameInput.setError("Use 3-16 letters, numbers, or underscores");
                usernameInput.requestFocus();
                return false;
            }
        }

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email address");
            emailInput.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            return false;
        }

        if (!isSignIn) {
            String passwordValidationMessage = validateStrongPassword(password);
            if (passwordValidationMessage != null) {
                passwordInput.setError(passwordValidationMessage);
                passwordInput.requestFocus();
                return false;
            }

            if (TextUtils.isEmpty(confirmPassword)) {
                confirmPasswordInput.setError("Please re-enter your password");
                confirmPasswordInput.requestFocus();
                return false;
            }

            if (!password.equals(confirmPassword)) {
                confirmPasswordInput.setError("Passwords do not match");
                confirmPasswordInput.requestFocus();
                return false;
            }
        }

        return true;
    }

    private String validateStrongPassword(String password) {
        if (password.length() < 8) {
            return "Password must be at least 8 characters";
        }

        if (!password.matches(".*[A-Z].*")) {
            return "Password must include an uppercase letter";
        }

        if (!password.matches(".*[a-z].*")) {
            return "Password must include a lowercase letter";
        }

        if (!password.matches(".*\\d.*")) {
            return "Password must include a number";
        }

        return null;
    }

    private void clearFieldErrors() {
        usernameInput.setError(null);
        emailInput.setError(null);
        passwordInput.setError(null);
        confirmPasswordInput.setError(null);
    }

    private void setAuthFormEnabled(boolean enabled) {
        usernameInput.setEnabled(enabled);
        emailInput.setEnabled(enabled);
        passwordInput.setEnabled(enabled);
        confirmPasswordInput.setEnabled(enabled);
        signInTabButton.setEnabled(enabled);
        signUpTabButton.setEnabled(enabled);
        togglePasswordText.setEnabled(enabled);
        toggleConfirmPasswordText.setEnabled(enabled);
        primaryAuthButton.setEnabled(enabled);
    }

    private void applyPasswordVisibility(EditText targetInput, TextView toggleView, boolean visible) {
        if (visible) {
            targetInput.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            toggleView.setText("Hide");
        } else {
            targetInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
            toggleView.setText("Show");
        }

        targetInput.setSelection(targetInput.getText().length());
    }

    private void ensurePlayerProfileAndOpenMap(FirebaseUser firebaseUser, String requestedUsername) {
        if (firebaseUser == null) {
            authStatusText.setText("Authentication state was lost");
            Toast.makeText(this, "No authenticated user found.", Toast.LENGTH_LONG).show();
            return;
        }

        authStatusText.setText("Loading cloud profile...");
        setAuthFormEnabled(false);

        cloudPlayerRepository.ensurePlayerProfile(firebaseUser, requestedUsername, new CloudPlayerRepository.Callback() {
            @Override
            public void onSuccess() {
                setAuthFormEnabled(true);

                if (requestedUsername != null) {
                    Toast.makeText(AuthActivity.this, "Account created.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AuthActivity.this, "Signed in successfully.", Toast.LENGTH_SHORT).show();
                }

                openMapScreen();
            }

            @Override
            public void onError(@NonNull Exception exception) {
                setAuthFormEnabled(true);

                if (requestedUsername != null && isUsernameTakenError(exception)) {
                    handleUsernameTakenAfterSignup(firebaseUser);
                    return;
                }

                authStatusText.setText("Failed to load cloud profile");
                Toast.makeText(
                        AuthActivity.this,
                        exception.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private boolean isUsernameTakenError(Exception exception) {
        if (exception instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) exception;
            if (firestoreException.getCode() == FirebaseFirestoreException.Code.ABORTED) {
                return true;
            }
        }

        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("username");
    }

    private void handleUsernameTakenAfterSignup(FirebaseUser firebaseUser) {
        authStatusText.setText("Username already in use. Choose another one.");
        Toast.makeText(
                this,
                "That username is already taken. Please choose another one.",
                Toast.LENGTH_LONG
        ).show();

        updateAuthMode(false);
        usernameInput.requestFocus();

        firebaseUser.delete()
                .addOnCompleteListener(task -> auth.signOut());
    }

    private void openMapScreen() {
        authStatusText.setText("Authenticated");

        Intent intent = new Intent(this, MapActivity.class);
        startActivity(intent);
        finish();
    }
}
