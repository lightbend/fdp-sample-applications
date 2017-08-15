package com.lightbend.killrweather.utils

/**
 * @param id Composite of Air Force Datsav3 station number and NCDC WBAN number
 * @param name Name of reporting station
 * @param countryCode 2 letter ISO Country ID // TODO restrict
 * @param callSign International station call sign
 * @param lat Latitude in decimal degrees
 * @param long Longitude in decimal degrees
 * @param elevation Elevation in meters
 */
case class WeatherStation(
  id: String,
  name: String,
  countryCode: String,
  callSign: String,
  lat: Double,
  long: Double,
  elevation: Double
) extends Serializable
