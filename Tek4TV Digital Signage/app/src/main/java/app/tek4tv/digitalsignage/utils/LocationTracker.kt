package app.tek4tv.digitalsignage.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class LocationTracker {
    var mlocation: Location? = null
    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            mlocation = location
        }

        override fun onStatusChanged(s: String, i: Int, bundle: Bundle) {}
        override fun onProviderEnabled(s: String) {}
        override fun onProviderDisabled(s: String) {}
    }

    fun getLocation(context: Context) {
        try {
            val locationManager =
                context.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager

            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("location", "location permissions not granted")
                return
            }


            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 100, 100f, locationListener)
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, 100, 100f, locationListener)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}