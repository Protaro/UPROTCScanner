package com.example.uprotcscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tableLayout: TableLayout
    private lateinit var editTextName: AutoCompleteTextView
    private lateinit var editTextLRN: AutoCompleteTextView
    private lateinit var btnScan: FloatingActionButton
    private lateinit var btnAddRow: Button
    private lateinit var btnClear: Button
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var firestore: FirebaseFirestore
    private lateinit var nameAdapter: ArrayAdapter<String>
    private lateinit var lrnAdapter: ArrayAdapter<String>
    private var userEmail: String = ""
    private var userCollection: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Firestore
        firestore = FirebaseFirestore.getInstance()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // Get user email and determine collection
        userEmail = sharedPreferences.getString("user_email", "") ?: ""
        userCollection = when {
            userEmail.contains("alpha@rotc.com") -> "Alpha"
            userEmail.contains("bravo@rotc.com") -> "Bravo"
            userEmail.contains("hhs@rotc.com") -> "HHS"
            else -> "Default" // Add a default collection
        }

        Log.d("MainActivity", "User Collection: $userCollection") // Debug log

        // Initialize views
        tableLayout = findViewById(R.id.idTableLayoutAttendance)
        editTextName = findViewById(R.id.idEdtName)
        editTextLRN = findViewById(R.id.idEdtLRN)
        btnScan = findViewById(R.id.fabScan)
        btnAddRow = findViewById(R.id.idBtnAddRow)
        btnClear = findViewById(R.id.idBtnClear)

        // Set up adapters for AutoCompleteTextView
        nameAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line)
        lrnAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line)
        editTextName.setAdapter(nameAdapter)
        editTextLRN.setAdapter(lrnAdapter)

        // Fetch autofill suggestions
        fetchAutofillSuggestions()

        // Set up the logout button
        setupLogoutButton()

        // Set up event listeners
        setupEventListeners()
    }

    private fun fetchAutofillSuggestions() {
        if (userCollection.isNotEmpty()) {
            firestore.collection(userCollection).document("Students").get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val students = document.data?.get("students") as? List<Map<String, String>>
                        students?.let {
                            val names = it.mapNotNull { student -> student["name"] }
                            val lrns = it.mapNotNull { student -> student["lrn"] }
                            nameAdapter.clear()
                            nameAdapter.addAll(names)
                            lrnAdapter.clear()
                            lrnAdapter.addAll(lrns)
                        }
                    } else {
                        Log.e("Firestore", "Document does not exist")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error fetching students: ${e.message}")
                }
        } else {
            Log.e("Firestore", "User collection is empty")
        }
    }

    private fun setupLogoutButton() {
        val signOutButton: Button = findViewById(R.id.btnLogout)
        signOutButton.setOnClickListener {
            Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show()
            sharedPreferences.edit().apply {
                putBoolean("isLoggedIn", false)
                putLong("logTime", 0)
                remove("remembered_email") // Clear saved email
                apply()
            }
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupEventListeners() {
        // Scan button click listener
        btnScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            scannerLauncher.launch(intent)
        }

        // Add row button click listener
        btnAddRow.setOnClickListener {
            val name = editTextName.text.toString().trim()
            val lrn = editTextLRN.text.toString().trim()
            if (name.isNotEmpty() && lrn.isNotEmpty()) {
                addRowToTable(name, lrn)
                addAttendanceToFirestore(name, lrn)
                editTextName.text.clear()
                editTextLRN.text.clear()
            } else {
                Toast.makeText(this, "Please enter both name and LRN", Toast.LENGTH_SHORT).show()
            }
        }

        // Clear button click listener
        btnClear.setOnClickListener {
            editTextName.text.clear()
            editTextLRN.text.clear()
        }

        // Autocomplete LRN based on Name
        editTextName.setOnItemClickListener { _, _, position, _ ->
            val selectedName = nameAdapter.getItem(position)
            if (selectedName != null) {
                firestore.collection(userCollection).document("Students").get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val students = document.data?.get("students") as? List<Map<String, String>>
                            students?.let {
                                val selectedStudent = it.find { student -> student["name"] == selectedName }
                                selectedStudent?.let { student ->
                                    editTextLRN.setText(student["lrn"])
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error fetching LRN: ${e.message}")
                    }
            }
        }
    }

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scannedData = result.data?.getStringExtra("SCAN_RESULT")
                if (scannedData != null) {
                    lifecycleScope.launch {
                        handleScannedData(scannedData)
                    }
                } else {
                    Log.e("QRScan", "No data received from QR scanner.")
                }
            }
        }

    private suspend fun handleScannedData(scannedData: String) {
        Log.d("QRScan", "Scanned Data: $scannedData")
        val student = firestore.collection(userCollection).document("Students").get().await()
            .data?.get("students") as? List<Map<String, String>>
        student?.let {
            val scannedStudent = it.find { student -> student["lrn"] == scannedData }
            scannedStudent?.let { student ->
                editTextName.setText(student["name"])
                editTextLRN.setText(student["lrn"])
                addRowToTable(student["name"] ?: "", student["lrn"] ?: "")
                addAttendanceToFirestore(student["name"] ?: "", student["lrn"] ?: "")
            }
        }
    }

    private fun addRowToTable(name: String, lrn: String) {
        val tableRow = TableRow(this).apply {
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
        }

        val textViewName = TextView(this).apply {
            text = name
            setPadding(10, 10, 10, 10)
            gravity = Gravity.CENTER
        }

        val textViewLRN = TextView(this).apply {
            text = lrn
            setPadding(10, 10, 10, 10)
            gravity = Gravity.CENTER
        }

        val textViewTimestamp = TextView(this).apply {
            text = getCurrentTimestamp()
            setPadding(10, 10, 10, 10)
            gravity = Gravity.CENTER
        }

        tableRow.addView(textViewName)
        tableRow.addView(textViewLRN)
        tableRow.addView(textViewTimestamp)
        tableLayout.addView(tableRow)
    }

    private fun addAttendanceToFirestore(name: String, lrn: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val attendanceData = hashMapOf(
            "name" to name,
            "lrn" to lrn,
            "timestamp" to getCurrentTimestamp()
        )

        firestore.collection(userCollection).document("Attendance").collection(currentDate)
            .add(attendanceData)
            .addOnSuccessListener {
                Log.d("Firestore", "Attendance added successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error adding attendance: ${e.message}")
            }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}