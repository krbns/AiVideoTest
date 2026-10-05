package com.rslnabk.aivideotest

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.rslnabk.aivideotest.data.CatalogRepository
import com.rslnabk.aivideotest.data.demo.*
import com.rslnabk.aivideotest.model.*

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val catalog: CatalogRepository = DemoCatalogRepository()
    private val session = DemoSession(PreferencesDemoStore(application.getSharedPreferences("demo_state_v1", Context.MODE_PRIVATE)), catalog)
    private val mutableSnapshot = MutableLiveData(session.snapshot)
    val snapshot: LiveData<DemoSnapshot> = mutableSnapshot
    fun toggleFavorite(id: String) { session.toggleFavorite(id); mutableSnapshot.value = session.snapshot }
    fun setAccount(account: DemoAccount) { session.setAccount(account); mutableSnapshot.value = session.snapshot }
    fun reset() { session.reset(); mutableSnapshot.value = session.snapshot }
}
