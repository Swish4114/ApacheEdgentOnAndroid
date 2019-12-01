package uims.ostfalia.streamprocessingproject

import android.hardware.SensorEvent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MyViewModel<T> : ViewModel() {
    private val data: MutableLiveData<T> = MutableLiveData()
    fun getData(): MutableLiveData<T> {
        return data
    }

}