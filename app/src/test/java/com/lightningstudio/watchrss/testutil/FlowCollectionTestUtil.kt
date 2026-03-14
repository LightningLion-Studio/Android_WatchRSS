package com.lightningstudio.watchrss.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.collectFlow(flow: Flow<*>): Job {
    return backgroundScope.launch {
        flow.collect { }
    }
}
