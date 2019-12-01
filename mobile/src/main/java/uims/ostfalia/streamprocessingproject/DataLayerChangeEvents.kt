package uims.ostfalia.streamprocessingproject

import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import org.apache.edgent.function.Consumer

class DataLayerChangeEvents (val submitter: Consumer<Boolean>) : DataClient.OnDataChangedListener {
    private val TAG = "HeartRateChangeEvents"
    private val SENSOR_PATH = "/sensor"
    private val KEY = "data"

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach {event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    Log.d(TAG, "data changed: ${item.uri.path}")
                    if (item.uri.path?.compareTo(SENSOR_PATH) == 0) {
                        DataMapItem.fromDataItem(item).dataMap.apply {
                            submitter.accept(getBoolean(KEY))
                        }
                    }
                }
            }
        }
    }
}