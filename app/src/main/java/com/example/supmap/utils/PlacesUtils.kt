// Fichier: com/example/supmap/util/PlacesUtils.kt
package com.example.supmap.utils

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.NonCancellable.cancel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    continuation.invokeOnCancellation {
        if (isComplete) return@invokeOnCancellation
        cancel()
    }
}
