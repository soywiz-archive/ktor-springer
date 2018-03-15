package io.ktor.springer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.response.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import java.lang.reflect.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

suspend inline fun <reified T : Routes> createClient(fact: HttpClientEngineFactory<*>, rootUrl: String): T =
    createClient(T::class.java, fact, rootUrl)

suspend fun <T : Routes> createClient(clazz: Class<T>, fact: HttpClientEngineFactory<*>, rootUrl: String): T {
    val rootUrlTrim = rootUrl.trimEnd('/')
    val client = HttpClient(fact)

    return Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz),
        { proxy, method, args ->
            val path = method.getDeclaredAnnotation(Get::class.java)?.path
                    ?: method.getDeclaredAnnotation(Post::class.java)?.path
                    ?: method.getDeclaredAnnotation(WS::class.java)?.path
                    ?: throw IllegalArgumentException("Can't find path for $method")

            val cont = args.lastOrNull() as? Continuation<String>?
                    ?: throw RuntimeException("Just implemented suspend functions")

            val pathTrim = path.trimStart('/')
            val pathPattern = PathPattern(pathTrim)

            var argindex = 0
            val pathReplaced = pathPattern.replace { "${args[argindex++]}" }

            launch {
                try {
                    val fullUrl = "$rootUrlTrim/$pathReplaced"
                    val res = client.call(fullUrl)
                    if (res.response.status.value < 400) {
                        cont.resume(res.response.readText())
                    } else {
                        throw HttpExceptionWithContent(res.response.status, res.response.readText())
                    }
                } catch (e: Throwable) {
                    cont.resumeWithException(e)
                }
            }
            COROUTINE_SUSPENDED
        }
    ) as T
}

class HttpExceptionWithContent(val code: HttpStatusCode, val content: String) :
    RuntimeException("HTTP ERROR $code : $content")
