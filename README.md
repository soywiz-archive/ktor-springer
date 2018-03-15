Support configuring KTOR applications using class methods for routing:

## Basic usage

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

## Retrofit

Supports creating asynchronous servers and clients with the same code just like retrofits does:

```kotlin
val client = createClient<MyService>(Apache.config { followRedirects = true }, "http://127.0.0.1:4000")
val ip = client.getIp()
val helloWorld = client.hello("world")
```

```kotlin
// Code in Common, use createClient to create a client implementing this interface by calling to an HTTP endpoint
interface MyService : Routes {
    @Get("/ip")
    suspend fun getIp(): String

    @Get("/hello/{}")
    suspend fun hello(name: String): String

    @Get("/redirect")
    suspend fun redirect(): String
}

// Code in the Backend
class MyServiceBackend : MyService, RoutesBackend {
    override suspend fun getIp(): String {
        return call().request.origin.remoteHost
    }

    override suspend fun hello(name: String): String {
        return "Hello $name"
    }

    override suspend fun redirect(): String {
        redirect("/ip", permanent = true)
    }
}
```

