package shop

import cats.effect._
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

object Main extends IOApp {

//  implicit val logger = Slf4jLogger.getLogger[IO]
//
//  override def run(args: List[String]): IO[ExitCode] =
////    config.load[IO]
//
//    //    config.load[IO].flatMap { cfg =>
//    //      Logger[IO].info(s"Loaded config $cfg") >>
//    //        AppResources.
//    //    }
//    for {
//      security <- Security.make[IO]()
//      algebras <- Algebras.make[IO]()
//      programs <- Programs.make[IO]()
//      api <- HttpApi.make[IO](algebras, programs, security)
//      _ <- BlazeServerBuilder[IO](ExecutionContext.global)
//            .bindHttp(8080, "0.0.0.0")
//            .withHttpApp(api.httpApp)
//            .serve
//            .compile
//            .drain
//    } yield ExitCode.Success
  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- BlazeServerBuilder[IO](ExecutionContext.global)
        .bindHttp(8080, "0.0.0.0")
        .serve
        .compile
        .drain
    } yield ExitCode.Success
}
