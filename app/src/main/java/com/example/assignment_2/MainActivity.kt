package com.example.assignment_2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException

@Serializable
data class WeatherResponse(
    val hourly: HourlyResponse
)

@Serializable
data class HourlyResponse(
    val time: List<String>,
    val temperature_2m: List<Double>
)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherApp()
        }
    }
}

@Composable
fun WeatherApp() {
    val selectedDate = remember { mutableStateOf(TextFieldValue()) }
    val maxTemp = remember { mutableStateOf<Double?>(null) }
    val minTemp = remember { mutableStateOf<Double?>(null) }
    val scaffoldState = rememberScaffoldState()
    val coroutineScope = rememberCoroutineScope()

    // Navigation
    val navController = rememberNavController()

    Scaffold(
        scaffoldState = scaffoldState,
    ) {
        NavHost(navController, startDestination = "weather_input_screen") {
            composable("weather_input_screen") {
                WeatherInputScreen(selectedDate, scaffoldState, coroutineScope, navController, maxTemp, minTemp)
            }
            composable("weather_details_screen") {
                WeatherDetailsScreen(maxTemp.value!!, minTemp.value!!) {
                    navController.popBackStack()
                }
            }
        }
    }
}



@Composable
fun WeatherInputScreen(
    selectedDate: MutableState<TextFieldValue>,
    scaffoldState: ScaffoldState,
    coroutineScope: CoroutineScope,
    navController: NavHostController,
    maxTemp: MutableState<Double?>,
    minTemp: MutableState<Double?>
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = selectedDate.value,
            onValueChange = {
                selectedDate.value = it
            },
            label = { Text("Enter date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            coroutineScope.launch {
                try {
                    println("Fetching weather data for date: ${selectedDate.value.text}")
                    val weatherData = fetchWeatherData(selectedDate.value.text)
                    if (weatherData != null) {
                        println("Weather data fetched successfully")
                        // Assuming the list is ordered by time, so we can take the first and last temperatures
                        maxTemp.value = weatherData.temperature_2m.maxOrNull()
                        minTemp.value = weatherData.temperature_2m.minOrNull()

                        // Navigate to details screen
                        navController.navigate("weather_details_screen")
                    } else {
                        scaffoldState.snackbarHostState.showSnackbar("Failed to fetch weather data.")
                    }
                } catch (e: Exception) {
                    println("Error fetching weather data: ${e.message}")
                    e.printStackTrace()
                    scaffoldState.snackbarHostState.showSnackbar("Error: ${e.message}")
                }
            }
        }) {
            Text("Get Weather")
        }
    }
}


@Composable
fun WeatherDetailsScreen(maxTemp: Double, minTemp: Double, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Max Temperature: $maxTemp°C",
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Min Temperature: $minTemp°C",
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onClose) {
            Text("Close")
        }
    }
}

suspend fun fetchWeatherData(date: String): HourlyResponse? {
    return withContext(Dispatchers.Default) {
        val client = OkHttpClient()

        val url = "https://archive-api.open-meteo.com/v1/era5?" +
                "latitude=52.52&" +
                "longitude=13.41&" +
                "start_date=$date&" +
                "end_date=$date&" +
                "hourly=temperature_2m"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                println("Weather data received: $responseBody")
                val json = Json { ignoreUnknownKeys = true }
                val jsonObject = json.parseToJsonElement(responseBody)
                val hourlyResponse = HourlyResponse(
                    time = jsonObject.jsonObject["hourly"]!!.jsonObject["time"]!!.jsonArray.map { it.jsonPrimitive.content },
                    temperature_2m = jsonObject.jsonObject["hourly"]!!.jsonObject["temperature_2m"]!!.jsonArray.map { it.jsonPrimitive.double }
                )
                hourlyResponse
            } else {
                null
            }
        } catch (e: IOException) {
            println("IOException: ${e.message}")
            e.printStackTrace()
            null
        } catch (e: Exception) {
            println("Exception: ${e.message}")
            e.printStackTrace()
            null
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        }
    }
}



