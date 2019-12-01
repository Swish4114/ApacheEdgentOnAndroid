package uims.ostfalia.streamprocessingproject

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.Wearable
import org.apache.edgent.function.Consumer

class DataLayerSourceSetup (val mainActivity: AppCompatActivity): Consumer<Consumer<Boolean>> {

    public lateinit var events: DataLayerChangeEvents

    override fun accept(submitter: Consumer<Boolean>) {
        Log.d("Setup", "accept")
        events = DataLayerChangeEvents(submitter)

        Wearable.getDataClient(mainActivity).addListener(events)
    }
}