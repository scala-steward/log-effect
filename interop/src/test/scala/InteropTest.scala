/*
 * Copyright (c) 2018-2025 LaserDisc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import cats.effect.{Resource, Sync}
import log.effect.LogWriter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.annotation.nowarn

sealed trait RedisClient[F[_]] {
  def address: String
  def write: F[Unit]
}
object RedisClient {
  def apply[F[_]: LogWriter](addr: String)(implicit F: Sync[F]): Resource[F, RedisClient[F]] =
    Resource.make(
      F.pure(
        new RedisClient[F] {
          val address        = addr
          def write: F[Unit] = LogWriter.info(address)
        }
      )
    )(_ => F.unit)
}

final class InteropTest extends AnyWordSpecLike with Matchers {

  "A LogWriter instance can be derived from a log4cats Logger" in {
    import cats.effect.IO
    import org.typelevel.log4cats.testing.StructuredTestingLogger
    import log.effect.internal.Show

    final class A()
    object A {
      implicit val showA: Show[A] =
        (_: A) => "an A"
    }

    implicit val testLogger: StructuredTestingLogger[IO] = StructuredTestingLogger.impl[IO]()

    import log.effect.interop.log4cats._

    val lw = implicitly[LogWriter[IO]]

    val logs = lw.trace("a message") >>
      lw.trace(new Exception("an exception")) >>
      lw.trace("a message", new Exception("an exception")) >>
      lw.trace(new A()) >>
      lw.debug("a message") >>
      lw.debug(new Exception("an exception")) >>
      lw.debug("a message", new Exception("an exception")) >>
      lw.debug(new A()) >>
      lw.info("a message") >>
      lw.info(new Exception("an exception")) >>
      lw.info("a message", new Exception("an exception")) >>
      lw.info(new A()) >>
      lw.warn("a message") >>
      lw.warn(new Exception("an exception")) >>
      lw.warn("a message", new Exception("an exception")) >>
      lw.warn(new A()) >>
      lw.error("a message") >>
      lw.error(new Exception("an exception")) >>
      lw.error("a message", new Exception("an exception")) >>
      lw.error(new A())

    val loggedQty = (logs >> testLogger.logged.map(_.size)).syncStep(Int.MaxValue).unsafeRunSync()

    loggedQty shouldBe Right(20)
  }

  "The readme interop example compiles" in {
    import cats.syntax.flatMap._
    import org.typelevel.log4cats.Logger

    import log.effect.interop.log4cats._

    def buildLog4catsLogger[F[_]]: F[Logger[F]] = ???

    @nowarn def storeOwnAddress[F[_]: Sync](address: String): F[Unit] =
      buildLog4catsLogger[F] >>= { implicit l =>
        RedisClient[F](address).use { cl =>
          cl.write
        }
      }
  }
}
