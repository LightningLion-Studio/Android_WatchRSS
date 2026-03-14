package com.lightningstudio.watchrss.testutil

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch

fun <T> TestScope.collectFlow(flow: Flow<T>): Job {
    return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        flow.collect()
    }
}
