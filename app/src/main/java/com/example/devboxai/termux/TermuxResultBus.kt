package com.example.devboxai.termux

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TermuxResultBus {

    data class Result(
        val requestId: Int,
        val stdout: String,
        val stderr: String,
        val exit: Int
    )

    private val _results = MutableSharedFlow<Result>(extraBufferCapacity = 32)
    val results: SharedFlow<Result> = _results.asSharedFlow()

    fun publish(r: Result) { _results.tryEmit(r) }
}
