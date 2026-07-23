package com.meapet.mobile.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore 实例单例扩展。
 *
 * 在 Application 级别共享同一个 DataStore 实例。
 * Kotlin 扩展属性利用了 `preferencesDataStore` 的委托特性，
 * 确保只会创建一个 DataStore。
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "meapet_preferences")
