package com.mido.weatherapp.models

import java.io.Serializable

data class Sys(
    val country : String,
    val sunrise : Long,
    val sunset : Long,
    val type : Int
): Serializable
