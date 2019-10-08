package com.example.org.boardfinder.Utilities

const val SHOW_END_POSITION = false

const val COLOR_LIGHT_GREEN_ARGB = 0xff18f075
const val COLOR_GREEN_ARGB = 0xff13b057
const val COLOR_DARK_GREEN_ARGB = 0xff0c7038
const val COLOR_YELLOW_ARGB = 0xffebe121
const val COLOR_YELLOW_ORANGE_ARGB = 0xffebbc21
const val COLOR_ORANGE_ARGB = 0xffeb7c21
const val COLOR_RED_ARGB = 0xffeb3e21
const val COLOR_DARK_RED_ARGB = 0xffab2218
const val COLOR_MAGENTA_ARGB = 0xffdb1fd2
const val LOCATION_INTERVAL:Long = 10000
const val FASTEST_LOCATION_INTERVAL:Long = 1000
const val ONGOING_NOTIFICATION_ID:Int = 1
const val EXTRA_TEXT = "EXTRA_TEXT"
const val EXTRA_STATE = "EXTRA_STATE"
const val EXTRA_LOST_LAT = "EXTRA_LOST_LAT"
const val EXTRA_LOST_LNG = "EXTRA_LOST_LNG"
const val EXTRA_END_LAT = "EXTRA_END_LAT"
const val EXTRA_END_LNG = "EXTRA_END_LNG"
const val EXTRA_CURRENT_LAT = "EXTRA_CURRENT_LAT"
const val EXTRA_CURRENT_LNG = "EXTRA_CURRENT_LNG"
const val EXTRA_FOUND_LAT = "EXTRA_FOUND_LAT"
const val EXTRA_FOUND_LNG = "EXTRA_FOUND_LNG"
const val EXTRA_ZOOM_LEVEL = "EXTRA_ZOOM_LEVEL"

const val BOARD_TO_KITE_DRIFT_RATIO = 0.7

const val MIN_DISTANCE_BETWEEN_LOCATIONS = 3.0
const val MIN_SAMPLES_DILUTION = 50
const val DILUTION_FREQUENCY = 50
const val TIME_TO_LATLNG_FACTOR = 1E-7
const val EPSILON = 1.5E-5

const val COMMUNICATION_PACKET_SIZE = 50

const val BASE_URL = "https://chattinghere.herokuapp.com/v1/"
const val SOCKET_URL = "https://chattinghere.herokuapp.com/"
const val URL_REGISTER = "${BASE_URL}account/register"
const val URL_LOGIN = "${BASE_URL}account/login"
const val URL_CREATE_USER = "${BASE_URL}user/add"
const val URL_GET_USER = "${BASE_URL}user/byEmail/"
const val URL_GET_CHANNELS = "${BASE_URL}channel"
const val URL_GET_MESSAGES = "${BASE_URL}message/byChannel/"

// Boradcast constants
const val BROADCAST_USER_DATA_CHANGE = "BROADCAST_USER_DATA_CHANGE"