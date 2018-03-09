package io.ktor.springer

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.slf4j.*
import java.lang.reflect.*
import kotlin.coroutines.experimental.*
import kotlin.reflect.*

// @TODO: Use asm to generate classes to call methods directly without reflection
fun Routing.registerRoutesInstance(obj: Any) = runBlocking {
    // Maybe Routing could be suspend to not require runBlocking
    val instance = obj
    val clazz = obj.javaClass
    val clazzInterceptorAnnotation: Interceptor? = clazz.getDeclaredAnnotation(Interceptor::class.java)
    // val logger = application.environment.log
    val logger = LoggerFactory.getLogger("ktor.springer")

    clazzInterceptorAnnotation?.clazz?.java?.newInstance()?.apply {
        intercept()
    }

    for (method in clazz.declaredMethods) {
        val getAnnotation = method.getDeclaredAnnotation(Get::class.java)
        val postAnnotation = method.getDeclaredAnnotation(Post::class.java)
        val wsAnnotation = method.getDeclaredAnnotation(WS::class.java)
        val interceptorAnnotation = method.getDeclaredAnnotation(Interceptor::class.java)
        val path = getAnnotation?.path ?: postAnnotation?.path ?: wsAnnotation?.path
        if (path != null) {
            val routeMethod = when {
                (postAnnotation != null) -> HttpMethod.Post
                else -> HttpMethod.Get
            }
            route(path, routeMethod) {
                logger.info("REGISTERED PATH: $path")
                interceptorAnnotation?.clazz?.java?.newInstance()?.apply {
                    runBlocking {
                        intercept()
                    }
                }

                if (wsAnnotation != null) {
                    application.install(WebSockets)
                    webSocket {
                        try {
                            val args = generateParameters(method, call, this)
                            val res = method.invokeSuspend(instance, args)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            throw e
                        }
                    }
                } else {
                    handle {
                        try {
                            // @TODO: Use asm to generate classes to call methods directly without reflection
                            val args = generateParameters(method, call)
                            val res = method.invokeSuspend(instance, args)
                            if (res != null) {
                                when (res) {
                                    is String -> call.respondText(res)
                                    else -> call.respond(res)
                                }
                            } else {
                                throw RuntimeException("Returned null!")
                            }
                        } catch (e: ResponseException) {
                            // @TODO: Mechanism for copying headers?
                            for ((name, value) in e.headers.entries()) {
                                for (v in value) call.response.headers.append(name, v)
                            }
                            call.respond(e.code, e.content ?: "")
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            throw e
                        }
                    }
                }
            }
        }
        //println(method)
        //println("$getAnnotation, $postAnnotation, $interceptorAnnotation")
    }
}

private fun generateParameters(
    method: Method,
    call: ApplicationCall,
    ws: DefaultWebSocketSession? = null
): List<Any?> {
    val args = arrayListOf<Any?>()
    for ((index, paramType) in method.parameterTypes.withIndex()) {
        when {
            ApplicationCall::class.java.isAssignableFrom(paramType) -> args += call
            ApplicationRequest::class.java.isAssignableFrom(paramType) -> args += call.request
            ApplicationResponse::class.java.isAssignableFrom(paramType) -> args += call.response
            Continuation::class.java.isAssignableFrom(paramType) -> Unit // Ignored, handled by invokeSuspend
            // WebSockets
            DefaultWebSocketSession::class.java.isAssignableFrom(paramType) -> args += ws // Ignored, handled by invokeSuspend
            ReceiveChannel::class.java.isAssignableFrom(paramType) -> args += ws!!.incoming
            SendChannel::class.java.isAssignableFrom(paramType) -> args += ws!!.outgoing
            else -> {
                throw RuntimeException("Unsupported param: $index: $paramType")
            }
        }
    }
    return args
}

/*
fun Routing.registerClass(clazz: Class<*>) {
}
inline fun <reified T> Routing.registerClass() = registerClass(T::class.java)
*/

interface BaseInterceptor {
    suspend fun ApplicationCallPipeline.intercept()
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Interceptor(val clazz: KClass<out BaseInterceptor>)

annotation class WS(val path: String)
annotation class Get(val path: String)
annotation class Post(val path: String)

class ResponseException(val content: Any?, val headers: Headers, val code: HttpStatusCode) : RuntimeException()

fun redirect(location: String, permanent: Boolean = false): Nothing {
    throw ResponseException(
        null, headersOf(
            "Location", location
        ), if (permanent) HttpStatusCode.PermanentRedirect else HttpStatusCode.Found
    )
}
