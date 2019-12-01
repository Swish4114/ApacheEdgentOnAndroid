package uims.ostfalia.streamprocessingproject

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.util.Log
import org.apache.edgent.android.hardware.runtime.SensorChangeEvents
import org.apache.edgent.function.Consumer

class SensorSourceSetup(val sensorManager: SensorManager, vararg val sensors: Sensor): Consumer<Consumer<SensorEvent>> {
    private lateinit var events: SensorChangeEvents

    override fun accept(submitter: Consumer<SensorEvent>?) {
        Log.d("Setup", "accept")
        events = SensorChangeEvents(submitter)
        registerListeners()
    }

    private fun registerListeners() {
        sensors.forEach { sensor ->
            sensorManager.registerListener(events, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun unregisterListeners() {
        sensors.forEach { sensor ->
            sensorManager.unregisterListener(events, sensor)
        }
    }

}