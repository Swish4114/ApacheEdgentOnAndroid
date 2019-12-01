package uims.ostfalia.streamprocessingproject

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.support.wearable.activity.WearableActivity
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.apache.edgent.function.Functions.unpartitioned
import org.apache.edgent.providers.direct.DirectProvider
import org.apache.edgent.providers.direct.DirectTopology
import org.apache.edgent.topology.TStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class MainActivity : WearableActivity(), LifecycleOwner {
    //mostly stolen from: https://developer.android.com/training/wearables/data-layer/data-items
    //                    https://github.com/android/wear-os-samples/tree/master/DataLayer
    //                    https://code.tutsplus.com/tutorials/get-wear-os-and-android-talking-exchanging-information-via-the-wearable-data-layer--cms-30986
    //                    https://developer.android.com/training/permissions/requesting
    //                    http://edgent.incubator.apache.org/docs/edgent-getting-started.html
    //                    http://edgent.incubator.apache.org/docs/streaming-concepts.html

    private lateinit var lifeCycleRegistry: LifecycleRegistry
    private lateinit var sensorManager: SensorManager
    private lateinit var heartRateSensor: Sensor
    private lateinit var linearAccelerationSensor: Sensor
    private lateinit var sensorSourceSetup: SensorSourceSetup
    private val sensorEventViewModel = MyViewModel<SensorEvent>()
    private val batchValuesViewModel = MyViewModel<Pair<Int, String>>()
    private val isHeartRateTooHighiewModel = MyViewModel<Boolean>()


    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    private val TAG = "MyTag"
    private val SENSOR_PATH = "/sensor"
    private val KEY = "data"

    private val dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enables Always-on
        setAmbientEnabled()

        //request permission
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.BODY_SENSORS),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }

        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }

        lifeCycleRegistry = LifecycleRegistry(this)
        lifeCycleRegistry.markState(Lifecycle.State.CREATED)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) //TODO: Der name passt nicht mehr
        val textView1: TextView = findViewById(R.id.text1)
        val textView2: TextView = findViewById(R.id.text2)
        sensorEventViewModel.getData().observe(this, Observer { event ->
            val str = createEventString(event)
            if (event.sensor.type == Sensor.TYPE_HEART_RATE)
                textView1.text = str
            else
                textView2.text = str

        })
        val textView3: TextView = findViewById(R.id.text3)
        val textView4: TextView = findViewById(R.id.text4)
        batchValuesViewModel.getData().observe(this, Observer { pair: Pair<Int, String> ->
            val date = LocalDateTime.now().format(dateFormat)
            val str = "$date: ${pair.second}"
            if (pair.first == Sensor.TYPE_HEART_RATE)
                textView3.text = str
            else
                textView4.text = str
        })
        val textView5: TextView = findViewById(R.id.text5)
        isHeartRateTooHighiewModel.getData().observe(this, Observer { isTooHigh: Boolean ->
            val date = LocalDateTime.now().format(dateFormat)
            textView5.text = "$date: $isTooHigh"
        })
        sensorSourceSetup = SensorSourceSetup(sensorManager, heartRateSensor, linearAccelerationSensor)
    }

    override fun onStart() {
        super.onStart()
        lifeCycleRegistry.markState(Lifecycle.State.STARTED)

        val dp = DirectProvider()
        val topology: DirectTopology = dp.newTopology()

        val stream: TStream<SensorEvent> = topology.events(sensorSourceSetup)

        val splits = stream.split(2) {event ->
            val result = when(event.sensor.type) {
                Sensor.TYPE_HEART_RATE -> 0
                Sensor.TYPE_STEP_DETECTOR -> 1
                else -> 0
            }
            result
        }
        val heartRateStream: TStream<SensorEvent> = splits.get(0)
        val stepDetectionStream: TStream<SensorEvent> = splits.get(1)
        heartRateStream.union(stepDetectionStream).sink { event -> //TODO: Just use stream instead of union!?
            sensorEventViewModel.getData().postValue(event)
        }


        val heartRateStreamMap: TStream<Pair<Int, Int>> = splits.get(0).map { event -> Pair(event.accuracy, event.values[0].toInt())}
        val stepDetectionStreamMap: TStream<Boolean> = splits.get(1).map { event -> true}

        val heartRateAccuracyFiltered = heartRateStreamMap.filter { pair -> pair.first >  -3}

        // see https://kotlinlang.org/docs/reference/lambdas.html#passing-a-lambda-to-the-last-parameter for syntax
        val heartRateWindow = heartRateAccuracyFiltered.last(5, TimeUnit.SECONDS, unpartitioned())
        val weightedHeartRates: TStream<Int> = heartRateWindow.batch { sensorEvents, key ->
            var sumHeartRates = 0
            var sumWeights = 0
            sensorEvents.forEach {sensorEvent ->
                val (a, b) = sensorEvent
                sumHeartRates += a * b
                sumWeights += a
            }
            if (sumWeights == 0)
                sumWeights = 1
            sumHeartRates / sumWeights //Integer division is ok here, we don't care about the decimal places
        }

        val stepDetectionLastSeconds: TStream<Boolean> = stepDetectionStreamMap
            .last(5, TimeUnit.SECONDS, unpartitioned())
            .batch {tuples, key -> tuples.isNotEmpty() }

        val heartRateTooHighWhenNotMoving: TStream<Boolean> = weightedHeartRates.joinLast({tuple -> 0}, stepDetectionLastSeconds, {tuple -> 0}) { weightedHeartRate: Int, isMoving: Boolean ->
            weightedHeartRate > 60 && isMoving
        }

        heartRateTooHighWhenNotMoving.sink { isHeartRateTooHigh ->
            Log.d(TAG, "Is heart rate too high?: $isHeartRateTooHigh")
            val dataMapRequest = PutDataMapRequest.create(SENSOR_PATH)
            val dataMap = dataMapRequest.dataMap
            dataMap.putBoolean(KEY, isHeartRateTooHigh)
            val request = dataMapRequest.asPutDataRequest()
            request.setUrgent()
            val dataItemTask = Wearable.getDataClient(this@MainActivity).putDataItem(request)
            dataItemTask.addOnSuccessListener { dataItem -> Log.d(TAG, "Sending check successful: $dataItem") }
            isHeartRateTooHighiewModel.getData().postValue(isHeartRateTooHigh)
        }

        weightedHeartRates.sink {heartRate ->
            batchValuesViewModel.getData().postValue(Pair(Sensor.TYPE_HEART_RATE, heartRate.toString()))
        }

        stepDetectionLastSeconds.sink {isMoving ->
            batchValuesViewModel.getData().postValue(Pair(Sensor.TYPE_STEP_DETECTOR, isMoving.toString()))
        }

        dp.submit(topology)
    }

    override fun onResume() {
        super.onResume()
        lifeCycleRegistry.markState(Lifecycle.State.RESUMED)
    }

    override fun onStop() {
        super.onStop()
        sensorSourceSetup.unregisterListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifeCycleRegistry.markState(Lifecycle.State.DESTROYED)
    }



    private fun createEventString(event: SensorEvent): String {
        val name: String = event.sensor.name
        val type = when(event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> "heart rate"
            Sensor.TYPE_STEP_DETECTOR -> "step detector"
            else -> "unknown"
        }
        val values = event.values.joinToString(", ", "[", "]")
        val timeStampDeltaMillis: Long = (event.timestamp - SystemClock.elapsedRealtimeNanos()) / 1000000
        val timeStamp: LocalDateTime = LocalDateTime.now().minus(timeStampDeltaMillis, ChronoUnit.MILLIS)
        val timeStampStr: String = timeStamp.format(dateFormat)

        val accuracy: String = when(event.accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "unreliable"
            SensorManager.SENSOR_STATUS_NO_CONTACT -> "no contact"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "low"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "high"
            else -> "unknown"
        }
        return """$type $timeStampStr
                  |$accuracy $values""".trimMargin()
    }

    override fun getLifecycle(): Lifecycle {
        return lifeCycleRegistry
    }
}
