import config.AwsConfig
import controllers.UserController
import dao.UserDao
import services.UserService
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import zio.aws.core.config.{CommonAwsConfig, AwsConfig => ZioAwsConfig}
import zio.aws.core.httpclient.HttpClient
import zio.aws.dynamodb.DynamoDb
import zio.aws.netty.NettyHttpClient
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.DynamoDBExecutor
import zio.http.Server
import zio.{&, Scope, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}

object Main extends ZIOAppDefault {
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    
    val awsKeyId = Option(System.getenv("AWS_ACCESS_KEY_ID"))
    val awsSecretKey = Option(System.getenv("AWS_SECRET_ACCESS_KEY"))
    
    awsKeyId.foreach(System.setProperty("AWS_ACCESS_KEY_ID", _))
    awsSecretKey.foreach(System.setProperty("AWS_SECRET_ACCESS_KEY", _))

    // 1. Load our config from application.conf
    val configLayer: ZLayer[Any, Throwable, AwsConfig] =
      ZLayer.fromZIO(TypesafeConfigProvider.fromResourcePath().load[AwsConfig])

    // 2. Convert our AwsConfig to CommonAwsConfig (for zio-aws)
    val zioAwsConfigLayer: ZLayer[AwsConfig, Nothing, CommonAwsConfig] =
      ZLayer.fromZIO {
        ZIO.serviceWith[AwsConfig] { awsConfig =>
          println(s"AWS Config loaded: region=${awsConfig.region}, accessKeyId=${awsConfig.accessKeyId.take(5)}***")
          CommonAwsConfig(
            region = Some(Region.of(awsConfig.region)),
            credentialsProvider = StaticCredentialsProvider.create(
              AwsBasicCredentials.create(awsConfig.accessKeyId, awsConfig.secretAccessKey)
            ),
            endpointOverride = None,
            commonClientConfig = None
          )
        }
      }

    // 3. HTTP client layer
    val httpClientLayer = NettyHttpClient.default

    // 4. Create low-level DynamoDb client
    val dynamoDbLayer =
      (httpClientLayer ++ zioAwsConfigLayer) >>>
      ZioAwsConfig.configured() >>>
      DynamoDb.live

    // 5. Create high-level DynamoDBExecutor
    val executorLayer = DynamoDBExecutor.live

    // 6. Compose all layers
    val fullStack =
      configLayer >>>
      dynamoDbLayer >>>
      executorLayer >>>
      UserDao.live >>>
      UserService.live

    // 7. Start the server
    val program = for {
      _ <- ZIO.logInfo("Starting application...")
      _ <- ZIO.logInfo(s"AWS_ACCESS_KEY_ID present: ${System.getenv("AWS_ACCESS_KEY_ID") != null}")
      _ <- ZIO.logInfo(s"AWS_SECRET_ACCESS_KEY present: ${System.getenv("AWS_SECRET_ACCESS_KEY") != null}")
      userService <- ZIO.service[UserService]
      _ <- ZIO.logInfo("UserService loaded successfully")
      _ <- ZIO.logInfo("Starting HTTP server on port 8080...")
      _ <- ZIO.logInfo("Swagger UI available at: http://localhost:8080/docs")
      _ <- Server.serve(UserController.routes(userService))
        .provide(Server.defaultWithPort(8080))
    } yield ()
    
    program
      .provide(fullStack)
      .catchAll { error =>
        ZIO.logError(s"Application failed: ${error}") *>
        ZIO.fail(error)
      }
  }
}