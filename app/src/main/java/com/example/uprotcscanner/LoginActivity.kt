package com.example.uprotcscanner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.util.Patterns
import android.view.MotionEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private var isPasswordVisible = false

    companion object {
        private const val UP_ROTC = "UserPrefs"
        private const val REMEMBERED_EMAIL = "remembered_email"
        private const val USER_EMAIL = "user_email"
        private const val IS_LOGGED_IN = "isLoggedIn"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences(UP_ROTC, Context.MODE_PRIVATE)

        // Auto-login if user is already authenticated
        if (sharedPreferences.getBoolean(IS_LOGGED_IN, false) && auth.currentUser != null) {
            Log.d("LoginActivity", "User is already logged in, redirecting to MainActivity.")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val emailEdt = findViewById<EditText>(R.id.idEdtEmail)
        val passwordEdt = findViewById<EditText>(R.id.idEdtPassword)
        val loginBtn = findViewById<Button>(R.id.idBtnLogin)
        val rememberMeCheckBox = findViewById<CheckBox>(R.id.idBtnRemember_me)

        val rememberedEmail = sharedPreferences.getString(REMEMBERED_EMAIL, null)
        if (!rememberedEmail.isNullOrEmpty()) {
            emailEdt.setText(rememberedEmail)
            rememberMeCheckBox.isChecked = true
        }

        emailEdt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val drawable: Drawable? =
                    if (Patterns.EMAIL_ADDRESS.matcher(s.toString()).matches()) {
                        ResourcesCompat.getDrawable(resources, R.drawable.done_icon, null)
                    } else {
                        ResourcesCompat.getDrawable(resources, R.drawable.mail_icon, null)
                    }
                emailEdt.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        passwordEdt.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (event.rawX >= (passwordEdt.right - passwordEdt.compoundDrawables[2].bounds.width())) {
                    isPasswordVisible = !isPasswordVisible
                    passwordEdt.transformationMethod =
                        if (isPasswordVisible) null else PasswordTransformationMethod()
                    passwordEdt.setCompoundDrawablesWithIntrinsicBounds(
                        null, null,
                        if (isPasswordVisible) ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.hide_pass_icon,
                            null
                        ) else ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.show_pass_icon,
                            null
                        ),
                        null
                    )
                    passwordEdt.setSelection(passwordEdt.text.length)
                    passwordEdt.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }

        loginBtn.setOnClickListener {
            val email = emailEdt.text.toString().trim()
            val password = passwordEdt.text.toString().trim()

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(email, password, rememberMeCheckBox.isChecked)
            }
        }
    }

    private fun loginUser(email: String, password: String, rememberMe: Boolean) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val editor = sharedPreferences.edit()
                    editor.putString(USER_EMAIL, email)
                    editor.putBoolean(IS_LOGGED_IN, true)

                    if (rememberMe) {
                        editor.putString(REMEMBERED_EMAIL, email)
                    } else {
                        editor.remove(REMEMBERED_EMAIL)
                    }

                    editor.apply()

                    Log.d("LoginActivity", "Stored email: ${sharedPreferences.getString(USER_EMAIL, "Not found")}")

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    val errorMessage = task.exception?.message ?: "Authentication failed"
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }
}