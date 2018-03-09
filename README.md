Support configuring KTOR applications using class methods for routing:

```kotlin
routing {
    registerRoutesInstance(Myroutes())
}
```

```kotlin

@Suppress("unused")
class Myroutes {
    @Get("/")
    suspend fun root(request: ApplicationRequest): String {
        delay(10)
        return "hello ${request.path()}"
    }

    // @TODO: Params
    @Get("/param/{param}")
    suspend fun param(param: String): String {
        return "param: $param"
    }

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
```