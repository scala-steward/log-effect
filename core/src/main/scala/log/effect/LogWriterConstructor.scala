package log.effect

import java.util.{Calendar, logging => jul}

import cats.Show
import cats.effect.Sync
import cats.syntax.functor._
import cats.syntax.show._
import com.github.ghik.silencer.silent
import log.effect.LogWriter.{Console, FailureMessage, Jul, Log4s}
import org.{log4s => l4s}

sealed trait LogWriterConstructor[T, F[_]] {
  type LogWriterType
  def evaluation(g: F[LogWriterType]): F[LogWriter[F]]
}

object LogWriterConstructor extends LogWriterConstructorInstances {

  @inline final def apply[F[_]]: LogWriterConstructorPartially[F] = new LogWriterConstructorPartially()

  private[effect] type AUX[T, F[_], LWT] = LogWriterConstructor[T, F] { type LogWriterType = LWT }

  private[effect] final class LogWriterConstructorPartially[F[_]](private val d: Boolean = true) extends AnyVal {
    @inline @silent def apply[T](t: T)(implicit LWC: LogWriterConstructor[T, F]): F[LWC.LogWriterType] => F[LogWriter[F]] = LWC.evaluation
  }
}

private[effect] sealed trait LogWriterConstructorInstances {

  implicit def log4sConstructor[F[_]](implicit F: Sync[F]): LogWriterConstructor.AUX[Log4s, F, l4s.Logger] =
    new LogWriterConstructor[Log4s, F] {

      type LogWriterType = l4s.Logger

      def evaluation(g: F[l4s.Logger]): F[LogWriter[F]] =
        g map {
          l4sLogger => new LogWriter[F] {
            def write[A: Show](level: LogWriter.LogLevel, a: =>A): F[Unit] = {

              val l4sLevel = level match {
                case LogWriter.Trace  => l4s.Trace
                case LogWriter.Debug  => l4s.Debug
                case LogWriter.Info   => l4s.Info
                case LogWriter.Error  => l4s.Error
                case LogWriter.Warn   => l4s.Warn
              }

              F.delay {
                a match {
                  case FailureMessage(msg, th) => l4sLogger(l4sLevel)(th)(msg)
                  case _                       => l4sLogger(l4sLevel)(a.show)
                }
              }
            }
          }
        }
    }

  implicit def julConstructor[F[_]](implicit F: Sync[F]): LogWriterConstructor.AUX[Jul, F, jul.Logger] =
    new LogWriterConstructor[Jul, F] {

      type LogWriterType = jul.Logger

      def evaluation(g: F[jul.Logger]): F[LogWriter[F]] =
        g map {
          julLogger => new LogWriter[F] {
            def write[A: Show](level: LogWriter.LogLevel, a: =>A): F[Unit] = {

              val jdkLevel = level match {
                case LogWriter.Trace  => jul.Level.FINEST
                case LogWriter.Debug  => jul.Level.FINE
                case LogWriter.Info   => jul.Level.INFO
                case LogWriter.Warn   => jul.Level.WARNING
                case LogWriter.Error  => jul.Level.SEVERE
              }

              F.delay {
                if (julLogger.isLoggable(jdkLevel)) {
                  julLogger.log(
                    a match {
                      case FailureMessage(msg, th) =>
                        val r = new jul.LogRecord(jdkLevel, msg)
                        r.setThrown(th)
                        r
                      case _ => new jul.LogRecord(jdkLevel, a.show)
                    }
                  )
                }
              }
            }
          }
        }
    }

  implicit def consoleConstructor[F[_]](implicit F: Sync[F]): LogWriterConstructor.AUX[Console, F, Unit] =
    new LogWriterConstructor[Console, F] {

      type LogWriterType = Unit

      def evaluation(g: F[Unit]): F[LogWriter[F]] =
        F.pure(
          new LogWriter[F] {
            def write[A: Show](level: LogWriter.LogLevel, a: =>A): F[Unit] = {
              F.delay { println(
                s"${Calendar.getInstance().getTime} - [${level.show}] - [Thread ${Thread.currentThread().getId}] - ${a.show}"
              )}
            }
          }
        )
    }
}