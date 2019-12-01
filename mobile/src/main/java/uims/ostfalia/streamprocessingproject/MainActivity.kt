package uims.ostfalia.streamprocessingproject

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.google.android.gms.wearable.Wearable
import org.apache.edgent.providers.direct.DirectProvider
import org.apache.edgent.providers.direct.DirectTopology
import org.apache.edgent.topology.TStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    //mostly stolen from: https://developer.android.com/training/wearables/data-layer/data-items
    //                    https://github.com/android/wear-os-samples/tree/master/DataLayer
    //                    https://code.tutsplus.com/tutorials/get-wear-os-and-android-talking-exchanging-information-via-the-wearable-data-layer--cms-30986

    private lateinit var viewModelDataLayer: DataLayerSourceViewModel

    private lateinit var eventsListView: ListView
    private lateinit var eventsListViewAdapter: ArrayAdapter<String>
    private val eventsList = ArrayList<String>()

    private lateinit var setupDataLayer: DataLayerSourceSetup

    private val dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val TAG = "MY_PHONE_TAG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "onCreate")


        eventsListView = findViewById(R.id.eventsListView)
        eventsListViewAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, eventsList)
        eventsListView.adapter = eventsListViewAdapter

        viewModelDataLayer = DataLayerSourceViewModel()
        viewModelDataLayer.getData().observe(this, Observer { eventStr ->
            val date = LocalDateTime.now().format(dateFormat)
            val str = "$date: $eventStr"
            eventsList.add(str)
            eventsListViewAdapter.notifyDataSetChanged()
        })

        setupDataLayer = DataLayerSourceSetup(this)
        //setupCheck = CheckSourceSetup(this)
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")

        val dp = DirectProvider()
        val topology: DirectTopology = dp.newTopology()
        val heartRateStream: TStream<Boolean> = topology.events(setupDataLayer)
        heartRateStream.sink { data: Boolean ->
            viewModelDataLayer.getData().postValue(data)
        }
        dp.submit(topology)
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        Wearable.getDataClient(this).removeListener(setupDataLayer.events)
    }
}
