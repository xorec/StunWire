package com.example.stunwire.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

const val SELECTION_KEY = "selection"

class SessionsListViewModel(private val state: SavedStateHandle): ViewModel() {
    private var selection: ArrayList<String> = if (state.get<ArrayList<String>>(SELECTION_KEY) != null)
        state.get<ArrayList<String>>(SELECTION_KEY)!! else ArrayList()

    fun addDatabaseName(databaseName: String) {
        if (!selection.contains(databaseName)) selection.add(databaseName)
        state[SELECTION_KEY] = selection
    }

    fun removeDatabaseName(databaseName: String) {
        selection.remove(databaseName)
        state[SELECTION_KEY] = selection
    }

    fun getSelection(): ArrayList<String> {
        return selection
    }
}