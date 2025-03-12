package com.example.uprotcscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val studentMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        window.statusBarColor = ContextCompat.getColor(this, R.color.my_primary)

        firestore = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        userEmail = sharedPreferences.getString("user_email", "") ?: ""
        userCollection = when {
            userEmail.contains("alpha@rotc.com") -> "Alpha"
            userEmail.contains("bravo@rotc.com") -> "Bravo"
            userEmail.contains("hhs@rotc.com") -> "HHS"
            userEmail.contains("admin@rotc.com") -> "Admin"
            else -> "Default"
        }
        Log.d("SharedPreferences", "Stored email: ${sharedPreferences.getString("user_email", "Not found")}")
        Log.d("MainActivity", "User Collection: $userCollection")
        Log.d("Firestore", "Using collection: $userCollection")


        tableLayout = findViewById(R.id.idTableLayoutAttendance)
        editTextName = findViewById(R.id.idEdtName)
        editTextLRN = findViewById(R.id.idEdtLRN)
        btnScan = findViewById(R.id.fabScan)
        btnAddRow = findViewById(R.id.idBtnAddRow)
        btnClear = findViewById(R.id.idBtnClear)

        nameAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line)
        lrnAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line)
        editTextName.setAdapter(nameAdapter)
        editTextLRN.setAdapter(lrnAdapter)

        // Set numeric keyboard for LRN input
        editTextLRN.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        fetchAutofillSuggestions()
        setupLogoutButton()
        setupEventListeners()
        restoreTableData()
    }

    private fun fetchAutofillSuggestions() {
        if (userCollection.isNotEmpty()) {
            firestore.collection(userCollection).get()
                .addOnSuccessListener { documents ->
                    studentMap.clear()
                    for (document in documents) {
                        val lrn = document.id
                        val name = document.getString("Name") ?: ""
                        if (name.isNotEmpty()) {
                            studentMap[lrn] = name
                        }
                    }

                    Log.d("Firestore", "Fetched students: $studentMap")
                    nameAdapter.clear()
                    nameAdapter.addAll(studentMap.values)
                    lrnAdapter.clear()
                    lrnAdapter.addAll(studentMap.keys)
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error fetching students: ${e.message}")
                }
        }
    }

    private fun setupLogoutButton() {
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show()
            sharedPreferences.edit().apply {
                putBoolean("isLoggedIn", false)
                putLong("logTime", 0)
                apply()
            }
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupEventListeners() {
        btnScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            scannerLauncher.launch(intent)
        }

        btnAddRow.setOnClickListener {
            val name = editTextName.text.toString().trim()
            val lrn = editTextLRN.text.toString().trim()
            if (name.isNotEmpty() && lrn.matches(Regex("\\d{9}")) && studentMap[lrn] == name) {
                addRowToTable(name, lrn)
                addAttendanceToFirestore(lrn)
                editTextName.text.clear()
                editTextLRN.text.clear()
            } else {
                Toast.makeText(this, "Invalid name or LRN", Toast.LENGTH_SHORT).show()
            }
        }

        btnClear.setOnClickListener {
            editTextName.text.clear()
            editTextLRN.text.clear()
        }

        editTextLRN.setOnItemClickListener { _, _, position, _ ->
            val selectedLRN = lrnAdapter.getItem(position)
            editTextName.setText(studentMap[selectedLRN])
        }

        editTextName.setOnItemClickListener { _, _, position, _ ->
            val selectedName = nameAdapter.getItem(position)
            val selectedLRN = studentMap.entries.find { it.value == selectedName }?.key
            editTextLRN.setText(selectedLRN)
        }
    }

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scannedData = result.data?.getStringExtra("SCAN_RESULT")
                scannedData?.let { handleScannedData(it) }
            }
        }

    private fun handleScannedData(scannedData: String) {
        studentMap[scannedData]?.let { name ->
            editTextName.setText(name)
            editTextLRN.setText(scannedData)
        }
    }

    // Function to save table data locally
    private fun saveTableData() {
        val tableData = mutableListOf<Map<String, String>>()

        for (i in 1 until tableLayout.childCount) {
            val row = tableLayout.getChildAt(i) as? TableRow
            row?.let {
                val name = (row.getChildAt(0) as TextView).text.toString()
                val lrn = (row.getChildAt(1) as TextView).text.toString()
                val timestamp = (row.getChildAt(2) as TextView).text.toString()

                tableData.add(mapOf("name" to name, "lrn" to lrn, "timestamp" to timestamp))
            }
        }

        val editor = sharedPreferences.edit()
        editor.putString("attendance_data", Gson().toJson(tableData))
        editor.putString("last_saved_date", getCurrentDate()) // Store date to clear next day
        editor.apply()
    }

    // Function to restore table data
    private fun restoreTableData() {
        val savedDate = sharedPreferences.getString("last_saved_date", "")
        val currentDate = getCurrentDate()
        if (savedDate != currentDate) {
            // If a new day has started, clear the stored data
            sharedPreferences.edit().remove("attendance_data").apply()
            return
        }

        val json = sharedPreferences.getString("attendance_data", null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<Map<String, String>>>() {}.type
            val tableData: List<Map<String, String>> = Gson().fromJson(json, type)

            for (entry in tableData) {
                addRowToTable(entry["name"] ?: "", entry["lrn"] ?: "", entry["timestamp"] ?: "")
            }
        }
    }

    // Modify addRowToTable to include timestamp
    private fun addRowToTable(name: String, lrn: String, timestamp: String = getCurrentTimestamp()) {
        val tableRow = TableRow(this)
        tableRow.addView(createTextView(name))
        tableRow.addView(createTextView(lrn))
        tableRow.addView(createTextView(timestamp))
        tableLayout.addView(tableRow)

        saveTableData() // Save data every time a row is added
    }

    // Function to get the current date (YYYY-MM-DD)
    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun addAttendanceToFirestore(lrn: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timestamp = getCurrentTimestamp()
        val attendanceCollection = "$userCollection Attendance"

        firestore.collection(attendanceCollection).document(currentDate)
            .update(timestamp, lrn)
            .addOnFailureListener {
                firestore.collection(attendanceCollection).document(currentDate)
                    .set(mapOf(timestamp to lrn))
            }
    }


    private fun createTextView(text: String) = TextView(this).apply {
        this.text = text
        setPadding(10, 10, 10, 10)
        gravity = Gravity.CENTER
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
