package com.mido.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

// check network

object Constants {

    const val APP_ID : String = "213dfa62601b7c7f90b22762510f7f3d"
    const val BASE_URL : String = "https://api.openweathermap.org/data/"
    const val METRIC_UNIT : String = "metric"
    const val PREFERENCE_NAME = "WeatherAppPreference"
    const val WEATHER_RESPONSE_DATA = "weather_response_date"

    fun isNetworkAvailable(context: Context): Boolean{

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

            // new android versions
            val network = connectivityManager.activeNetwork ?: return false // If that is empty return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false // If that is empty return false

            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true // when this is the case return true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true // when this is the case return true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true // when this is the case return true
                else -> false // otherwise return false
            }
        }else{
            // old android versions (M = api 23)
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }

    }
}