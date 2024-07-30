package com.example.myapplication2

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val tag = "BluetoothStatus"
    private val requestCodePermissions = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        checkPermissions()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    private fun checkPermissions() {
        val permissions = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE

            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), requestCodePermissions)
        } else {
            logBluetoothInfo()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePermissions) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                logBluetoothInfo()
            } else {
                Log.e(tag, "Permissions not granted by the user.")
            }
        }
    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                            Log.d(tag, "Device connected: ${it.name} - ${it.address}")
                        }
                    }
                    logBluetoothInfo(device)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                            Log.d(tag, "Device disconnected: ${it.name} - ${it.address}")
                        }
                    }
                    logBluetoothInfo(device)
                }
            }
        }
    }

    private fun logBluetoothInfo(device: BluetoothDevice? = null) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            device?.let {
                Log.d(tag, "Device: ${it.name} - ${it.address}")
                val profilesConnected = getProfilesConnected(it)
                Log.d(tag, "Profiles connected: $profilesConnected")
                Log.d(tag, "Phone: ${it.name}")
                Log.d(tag, "Model: ${Build.MODEL}")
                Log.d(tag, "OS: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Android" else "Other"}")
                Log.d(tag, "OS Version: ${Build.VERSION.RELEASE}")
            } ?: run {
                val connectedDevices = bluetoothAdapter.bondedDevices
                Log.d(tag, "Number of connected devices: ${connectedDevices.size}")
                connectedDevices.forEach { d ->
                    Log.d(tag, "Device: ${d.name} - ${d.address}")
                }
            }
        } else {
            Log.e(tag, "Missing required permissions.")
        }
    }

    private fun getProfilesConnected(device: BluetoothDevice): String {
        val profileNames = mutableListOf<String>()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val a2dp = bluetoothManager.adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP && proxy.getConnectedDevices().contains(device)) {
                    profileNames.add("Music")
                }
                bluetoothManager.adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)

        val headset = bluetoothManager.adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET && proxy.getConnectedDevices().contains(device)) {
                    profileNames.add("Phone")
                }
                bluetoothManager.adapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HEADSET)

        return profileNames.joinToString(", ")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
    }
}
