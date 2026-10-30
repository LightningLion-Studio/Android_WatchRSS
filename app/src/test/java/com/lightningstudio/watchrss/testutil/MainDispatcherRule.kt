package com.lightningstudio.watchrss.testutil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    private val viewModels = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { viewModels += it }

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        try {
            // A ViewModel scope is not a child of runTest. Its IO/DataStore continuations
            // must finish before Main is restored, just as clearing a real ViewModelStore.
            runTest(dispatcher) {
                viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
            }
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }
}
