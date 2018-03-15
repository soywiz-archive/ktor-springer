package io.ktor.springer

import java.lang.reflect.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

suspend fun Method.invokeSuspend(obj: Any?, args: List<Any?>): Any? = suspendCoroutine { c ->
    val method = this@invokeSuspend

    val lastParam = method.parameterTypes.lastOrNull()
    val margs = java.util.ArrayList(args)

    if (lastParam != null && lastParam.isAssignableFrom(Continuation::class.java)) {
        margs += c
    }
    try {
        val result = method.invoke(obj, *margs.toTypedArray())
        if (result != COROUTINE_SUSPENDED) {
            c.resume(result)
        }
    } catch (e: InvocationTargetException) {
        c.resumeWithException(e.targetException)
    } catch (e: Throwable) {
        c.resumeWithException(e)
    }
}

inline fun <T> ignoreErrors(callback: () ->T): T? = try { callback() } catch (e: Throwable) { null }
