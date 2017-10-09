/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosavefrontend.health

import java.time.LocalDate

import akka.actor.{Actor, ActorLogging, Cancellable, Props, Scheduler}
import akka.pattern.pipe
import cats.instances.int._
import cats.syntax.eq._
import configs.syntax._
import com.typesafe.config.Config
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavefrontend.util.Email

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal

class NSIConnectionHealthTest {

}

object NSIConnectionHealthTest {

  private[health] class NSIConnectionHealthTestRunner(config: Config,
                                                      nsiConnector: NSIConnector,
                                                      scheduler: Scheduler,
                                                      metrics: Metrics,
                                                      pagerDutyAlert: () ⇒ Unit
                                                     ) extends Actor with ActorLogging {

    import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthTest.NSIConnectionHealthTestRunner._

    implicit val ec: ExecutionContext = context.dispatcher

    val timeBetweenTests: FiniteDuration =
      config.get[FiniteDuration]("health.nsi-connection.poll-period").value.max(1.minute)

    val numberOfTestsBetweenUpdates: Int =
      config.get[Int]("health.nsi-connection.poll-count-between-updates").value

    val maximumConsecutiveFailures: Int =
      config.get[Int]("health.nsi-connection.poll-count-failures-to-alert").value

    val numberOfTestsBetweenAlerts: Int =
      config.get[Int]("health.nsi-connection.poll-count-between-pager-duty-alerts").value

    var payload: Payload = Payload.next()

    var performTestTask: Option[Cancellable] = None

    override def receive: Receive = ok(0)

    def ok(count: Int): Receive = ready orElse {

      case TestResult.Success(nanos) ⇒
        log.debug(s"Health check OK ${timeString(nanos)}")
        val newCount = count + 1
        if(newCount === numberOfTestsBetweenUpdates){
          payload = Payload.next()
        }
        context become ok(newCount)

      case TestResult.Failure(message, nanos) ⇒
        log.warning(s"Health check has started to fail $message ${timeString(nanos)}")

        if (maximumConsecutiveFailures > 1) {
          becomeFailing(1)
        } else if (maximumConsecutiveFailures === 1) {
          pagerDutyAlert()
          becomeFailed(0)
        } else {
          ()
        }

    }

    def failing(fails: Int): Receive = ready orElse {
      case TestResult.Success(nanos) ⇒
        log.info(s"Health check was failing but now OK ${timeString(nanos)}")
        becomeOK()

      case TestResult.Failure(message, nanos) ⇒
        log.warning(s"Health check failed: $message ${timeString(nanos)}")
        val newFails = fails + 1

        if (newFails < maximumConsecutiveFailures) {
          becomeFailing(newFails)
        } else {
          pagerDutyAlert()
          becomeFailed(0)
        }

    }

    def failed(fails: Int): Receive = ready orElse {
      case TestResult.Success(nanos)       ⇒
        log.info(s"Health check had failed but now OK ${timeString(nanos)}")
        becomeOK()

      case TestResult.Failure(message, nanos) ⇒
        log.warning(s"Health check still failing: $message ${timeString(nanos)}")
        val newFails = fails + 1
        metrics.healthCheckFailuresHistogram.update(maximumConsecutiveFailures + newFails)

        if(newFails % numberOfTestsBetweenAlerts === 0){
          pagerDutyAlert()
        }
    }

    def becomeOK(): Unit = {
      metrics.healthCheckFailuresHistogram.update(0)
      context become ok(0)
    }

    def becomeFailing(fails: Int): Unit = {
      metrics.healthCheckFailuresHistogram.update(fails)
      context become failing(fails)
    }

    def becomeFailed(fails: Int): Unit = {
      metrics.healthCheckFailuresHistogram.update(maximumConsecutiveFailures + fails)
      context become failed(fails)
    }

    def ready: Receive = {
      case PerformTest ⇒ performTest() pipeTo self
    }

    def performTest(): Future[TestResult] = {
      val timer = metrics.healthCheckTimer.time()

      nsiConnector.test(payload.value).value.map[TestResult]{ result ⇒
        val time = timer.stop()
        result.fold[TestResult](e ⇒ TestResult.Failure(e, time), _ ⇒ TestResult.Success(time))
      }.recover{
        case NonFatal(e) ⇒
          val time = timer.stop()
          TestResult.Failure(e.getMessage, time)
      }
    }

    def timeString(nanos: Long): String = s"(time: ${nanosToPrettyString(nanos)})"

    override def preStart(): Unit = {
      super.preStart()
      performTestTask = Some(scheduler.schedule(timeBetweenTests, timeBetweenTests, self, PerformTest))
    }

    override def postStop(): Unit = {
      super.postStop()
      performTestTask.foreach(_.cancel())
    }
  }

  private[health] object NSIConnectionHealthTestRunner {

    def props(config: Config,
              nsiConnector: NSIConnector,
              scheduler: Scheduler,
              metrics: Metrics,
              pagerDutyAlert: () ⇒ Unit): Props =
      Props(new NSIConnectionHealthTestRunner(config, nsiConnector, scheduler, metrics, pagerDutyAlert))

    case object PerformTest

    sealed trait TestResult

    object TestResult {

      case class Success(nanos: Long) extends TestResult

      case class Failure(message: String, nanos: Long) extends TestResult
    }

    sealed trait Payload {
      val value: NSIUserInfo
    }

    object Payload {

      private def payload(email: Email): NSIUserInfo = NSIUserInfo(
        "Service", "Account", LocalDate.ofEpochDay(0L), "XX999999X",
        ContactDetails("Health", "Check", None, None, None, "AB12CD", None, email))

      private case object Payload1 extends Payload {
        override val value = payload("healthcheck_ping@noreply.com")
      }

      private case object Payload2 extends Payload {
        override val value = payload("healthcheck_pong@noreply.com")
      }

      private val payloads: Iterator[Payload] = Iterator.iterate[Payload](Payload1){
        case Payload1 ⇒ Payload2
        case Payload2 ⇒ Payload1
      }

      def next(): Payload = payloads.next()
    }

  }

}
