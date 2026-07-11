package com.github.gbandszxc.goodtvplorer.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class RefCountedRequestRegistry(private val scope: CoroutineScope) {
    private val requests = mutableMapOf<String, Request>()

    fun request(key: String, block: suspend () -> Unit) {
        requests[key]?.let {
            it.references++
            return
        }
        lateinit var request: Request
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                if (requests[key] === request) requests.remove(key)
            }
        }
        request = Request(job)
        requests[key] = request
        job.start()
    }

    fun release(key: String) {
        val request = requests[key] ?: return
        request.references--
        if (request.references <= 0) {
            requests.remove(key)
            request.job.cancel()
        }
    }

    fun cancelAll() {
        val jobs = requests.values.map(Request::job)
        requests.clear()
        jobs.forEach(Job::cancel)
    }

    fun cancelAllExcept(key: String) {
        val kept = requests[key]
        val jobs = requests.filterKeys { it != key }.values.map(Request::job)
        requests.clear()
        if (kept != null) requests[key] = kept
        jobs.forEach(Job::cancel)
    }

    private data class Request(val job: Job, var references: Int = 1)
}
