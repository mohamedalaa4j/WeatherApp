package com.mido.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.contentcapture.ContentCaptureCondition
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.mido.weatherapp.models.WeatherResponse
import com.mido.weatherapp.network.WeatherService
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.properties.Delegates

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences : SharedPreferences

    var myLatitdue by Delegates.notNull<Double>()
    var myLongitude by Delegates.notNull<Double>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off, Please turn it on",
                Toast.LENGTH_SHORT
            ).show()

            // move to gps in settings
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )

                .withListener(object : MultiplePermissionsListener {

                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                       }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permissions please enable them ",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }


                    @SuppressLint("MissingPermission")
                    private fun requestLocationData() {
                        val mLocationRequest = com.google.android.gms.location.LocationRequest()
                        mLocationRequest.priority =
                            com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY

                        mFusedLocationClient.requestLocationUpdates(
                            mLocationRequest, mLocationCallback, Looper.myLooper()!!
                        )


                    }

                    private val mLocationCallback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            val mLastLocation: Location = locationResult.lastLocation

                            val latitude = mLastLocation.latitude
                            Log.i("Current Latitude", "$latitude")
                            myLatitdue = latitude

                            val longitude = mLastLocation.longitude
                            Log.i("Current Longitude", "$longitude")
                            myLongitude = longitude

                            getLocationWeatherDetails(latitude, longitude)

                            mFusedLocationClient.removeLocationUpdates(this);


                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                        showRationalDialogForPermissions()

                    }

                }).onSameThread().check() //withListener()

        }

    }

    private fun isLocationEnabled(): Boolean {

        // this provides access to system location services
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    private fun getLocationWeatherDetails(latitdue: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {

            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitdue, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response!!.isSuccessful) {

                        hideProgressDialog()

                        val weatherList: WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()

                        Log.i("response Result", "$weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.i("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.i("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }

                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrr", t!!.message.toString())
                    hideProgressDialog()
                }

            })
        } else {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()

        }
    }

    fun showRationalDialogForPermissions() {

        // use Builder class for dialog constructor
        val builder = AlertDialog.Builder(this)

        // title
        builder.setTitle("Alert")
        // message
        builder.setMessage("It looks like you have turned off permissions required for this feature .It can be enabled under application settings")
        // Icon image
        builder.setIcon(R.drawable.ic_alert)


        // positive action
        builder.setPositiveButton("Go to settings") { _, _ ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }

        // negative action
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        // cancel action
        builder.setNeutralButton("Cancel") { dialogInterface, _ ->
            Toast.makeText(applicationContext, "Canceled", Toast.LENGTH_SHORT).show()
            dialogInterface.dismiss()
        }

        // create the Alertdialog
        val alertdialog: AlertDialog = builder.create()

        // dialog properties
        alertdialog.setCancelable(false)

        // show dialog
        alertdialog.show()
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                getLocationWeatherDetails(myLatitdue, myLongitude)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI() {

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices) {


                Log.i("Weather Name", weatherList.weather.toString())

                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                //////////////////////////////////////////////////////////////////////////////////////// tv_temp.text
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    tv_temp.text =
                        weatherList.main.temp.toString() + getUnit(Locale.getDefault().toString())
                } else {

                    tv_temp.text =
                        weatherList.main.temp.toString() + getUnit(Locale.getDefault().toString())

                }
                //////////////////////////////////////////////////////////////////////////////////////// tv_temp.text

                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text = unixTime(weatherList.sys.sunset)

                tv_humidity.text = weatherList.main.humidity.toString() + "%"
                tv_min.text = weatherList.main.temp_min.toString() + " Min"
                tv_max.text = weatherList.main.temp_max.toString() + " Max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country

                when(weatherList.weather[i].icon){

                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                    "50d" -> iv_main.setImageResource(R.drawable.mist)
                    "50n" -> iv_main.setImageResource(R.drawable.mist)

                }
            }
        }




    }

    fun getUnit(value: String) : String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex : Long): String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }


}