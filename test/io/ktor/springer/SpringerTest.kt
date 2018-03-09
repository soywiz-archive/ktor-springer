package io.ktor.springer

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.junit.Test
import java.util.*
import kotlin.test.*

class SpringerTest {
    fun Application.module() {
        routing {
            registerRoutesInstance(MyClass())
        }
    }

    @Test
    fun checkRoot() = withTestApplication({ module() }) {
        handleRequest(HttpMethod.Get, "/").let { call ->
            call.awaitCompletion()
            assertEquals("hello /", call.response.content)
        }
    }

    @Test
    fun checkRedirect() = withTestApplication({ module() }) {
        handleRequest(HttpMethod.Get, "/redirect").let { call ->
            assertEquals("/", call.response.headers["Location"])
            assertEquals(HttpStatusCode.PermanentRedirect, call.response.status())
        }
    }

    @Test
    fun checkAdminInterceptor() = withTestApplication({ module() }) {
        handleRequest(HttpMethod.Get, "/admin").let { call ->
            //call.awaitCompletion()
            assertEquals("Basic realm=myrealm", call.response.headers["WWW-Authenticate"])
            assertEquals(null, call.response.content)
        }
        handleRequest(HttpMethod.Get, "/admin") {
            addHeader(
                "Authorization",
                "Basic " + Base64.getEncoder().encode("test:test".toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
            )
        }.let { call ->
            //call.awaitCompletion()
            assertEquals(null, call.response.headers["WWW-Authenticate"])
            assertEquals("just admins /admin", call.response.content)
        }
    }


    //@Interceptor(PasswordProtected::class)
    @Suppress("unused")
    class MyClass {
        @Get("/")
        suspend fun root(request: ApplicationRequest): String {
            delay(10)
            return "hello ${request.path()}"
        }

        // @TODO: Params
        //@Get("/param/{param}")
        //suspend fun param(param: String): String {
        //    return "param: $param"
        //}

        @Get("/redirect")
        suspend fun redirect(): String {
            redirect("/", permanent = true)
        }

        @Get("/admin")
        @Interceptor(PasswordProtected::class) // WOULD BE AWESOME IF KOTLIN ALLOWED HERE LAMBDAS INTERNALLY CREATING A CLASS
        suspend fun admin(request: ApplicationRequest): String {
            return "just admins ${request.path()}"
        }

        @WS("/echo")
        suspend fun echo(recv: ReceiveChannel<Frame>, send: SendChannel<Frame>) {
            while (true) {
                val frame = recv.receive() as Frame.Text
                send.send(Frame.Text("You said: " + frame.readText()))
            }
        }
    }

    class PasswordProtected : BaseInterceptor {
        override suspend fun ApplicationCallPipeline.intercept() {
            authentication {
                basicAuthentication("myrealm") {
                    if (it.name == it.password) UserIdPrincipal(it.name) else null
                }
            }
        }
    }
}
