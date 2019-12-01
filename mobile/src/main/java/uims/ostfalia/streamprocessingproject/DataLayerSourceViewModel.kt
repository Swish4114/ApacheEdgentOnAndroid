package uims.ostfalia.streamprocessingproject

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DataLayerSourceViewModel : ViewModel() {
    private val data: MutableLiveData<Boolean> = MutableLiveData()

    fun getData(): MutableLiveData<Boolean> {
        return data
    }
}